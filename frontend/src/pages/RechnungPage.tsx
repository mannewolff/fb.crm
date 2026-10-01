import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Link from '@mui/material/Link';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { IconAlertTriangle, IconDeviceFloppy, IconTrash } from '@tabler/icons-react';
import { Fragment, useEffect, useState } from 'react';
import type { FormEvent, ReactNode } from 'react';
import { Link as RouterLink, useNavigate, useParams } from 'react-router-dom';

import type { Einheit } from '../api/angebote';
import type { FieldErrors } from '../api/client';
import { rechnungAendern, rechnungLesen, rechnungLoeschen } from '../api/rechnungen';
import type { AbrechnungsangabeEingabe, Rechnung } from '../api/rechnungen';
import AktionsMenue from '../components/AktionsMenue';
import Karte from '../components/Karte';
import { useKopfPfad } from '../components/KopfPfad';
import type { PfadVerweis } from '../components/KopfPfad';
import KupferTaste from '../components/KupferTaste';
import { EINHEIT_WORT } from '../components/Positionsmaske';
import RechnungszustandChip from '../components/RechnungszustandChip';
import Tafel from '../components/Tafel';
import { rechnungssummen, ueberschreitung } from '../lib/abrechnung';
import type { Rechnungssummen } from '../lib/abrechnung';
import { feldMeldungen, nichtGefunden } from '../lib/apifehler';
import { meldungAm } from '../lib/feldmeldung';
import { betrag, dezimal, euro, hundertstel } from '../lib/geld';
import { kennungAus } from '../lib/kennung';
import { ZAHLEN_KLASSE } from '../theme';

/**
 * Die Seite der einzelnen Rechnung: im Entwurf die Maske der Teilabrechnung (Issue #185).
 *
 * Oben die Kopfkarte mit Ueberschrift, Angaben und Aktionen; darunter im Entwurf die Angaben zur
 * Rechnung und die Positionstafel mit „jetzt abrechnen" und den drei Summen. Eine <b>gestellte</b>
 * Rechnung zeigt in diesem Stand nur den Kopf und einen Satz — ihre Ansicht und das Stellen selbst
 * entstehen in Issue #186.
 *
 * <b>Die Maske zeigt jede Position des Angebots</b>, auch eine, die dieser Entwurf nicht abrechnet
 * (Plan #169, E5). So sieht der Freiberufler beim Wiederoeffnen, was er beim ersten Mal weggelassen
 * hat. Hinaus geht darum ebenfalls jede Zeile — eine Menge 0 laesst die Position aus der Rechnung
 * herausfallen (`RechnungPositionRequest`), und so bleibt die Stelle einer Zeile im Rumpf dieselbe
 * wie in der Tafel. Nur deshalb trifft ein Feldfehler `positionen[n].bezeichnung` des Servers die
 * Zeile, die der Mensch sieht.
 *
 * <b>Die Liste ist Zustand der Seite</b>, wie in {@link AngebotMaske}: Getippt wird im Zustand,
 * geschickt wird beim Speichern als Ganzes. Nach dem Speichern gilt die Antwort — Mengen, Betraege
 * und Summen stehen danach so da, wie der Server sie fuehrt. Die Maske bleibt dabei stehen; sie ist
 * die Werkbank an der Rechnung.
 *
 * <b>Gerechnet wird mitgetippt und in ganzen Zahlen</b> (`lib/abrechnung.ts`): Betrag je Zeile auf
 * den Cent, Steuer aus der Netto-Summe — dieselbe Reihenfolge der Rundungen wie im Backend. Sonst
 * sprang der Betrag beim Speichern um einen Cent.
 *
 * <b>Eine Menge ueber dem Offenen verhindert nichts</b> (Kriterium 8): Sie steht als Hinweis mit
 * Wort und Symbol an der Zeile, und gespeichert wird trotzdem. Eine Teilabrechnung ueber das
 * Angebot hinaus ist eine Entscheidung des Freiberuflers, kein Eingabefehler. Was dagegen <b>keine
 * Menge</b> ist — leeres Feld, Buchstaben, drei Nachkommastellen, ein Minus —, meldet sich am Feld
 * und haelt das Speichern auf; ein stiller Ersatzwert waere ein Betrag, den niemand eingegeben hat.
 *
 * <b>„Entwurf löschen" steht im ⋯-Menue mit Rueckfrage</b> (CLAUDE-design.md, „Tasten"): Es ist
 * nicht umkehrbar und steht darum nicht gleichrangig neben „Speichern", der einen Kupfertaste
 * dieser Ansicht.
 *
 * Laden, „gibt es nicht" (404) und Ausfall wie in {@link AngebotPage}: Eine Kennung, die keine ist,
 * geht gar nicht erst ans Netz (`lib/kennung.ts`).
 */

const NICHT_GEFUNDEN = 'Diese Rechnung gibt es nicht.';
const AUSFALL = 'Die Rechnung ist gerade nicht zu erreichen. Bitte später erneut versuchen.';
const AUSFALL_SPEICHERN = 'Die Rechnung wurde nicht gespeichert. Bitte später erneut versuchen.';
const AUSFALL_LOESCHEN = 'Der Entwurf wurde nicht gelöscht. Bitte später erneut versuchen.';
const LAEDT = 'Die Rechnung wird geladen …';
const FOLGT = 'Die Ansicht der gestellten Rechnung folgt.';
const GESPEICHERT = 'Gespeichert.';
const DATUM_FEHLT = 'Bitte das Datum der Rechnung angeben.';
const ZEITRAUM_FEHLT = 'Die Rechnung braucht einen Leistungszeitraum.';
const BEZEICHNUNG_FEHLT = 'Jede Position braucht eine Bezeichnung.';
const MENGE_UNKLAR =
  'Bitte eine Menge mit höchstens zwei Nachkommastellen angeben, nicht negativ.';
const LOESCHEN_FRAGE =
  'Der Entwurf wird mit allen Positionen gelöscht. Das lässt sich nicht zurücknehmen.';

/** Die Grenzen der Felder — dieselben wie in `RechnungRequest` und `RechnungPositionRequest`. */
const ZEITRAUM_LAENGE = 100;
const BEZEICHNUNG_LAENGE = 300;

/** Die Symbolgroesse in den Tasten (wie in {@link AngebotPage}). */
const SYMBOL_TASTE = 16;

/** Die Symbolgroesse im Hinweis an einer Zeile. */
const SYMBOL_HINWEIS = 14;

/** Ueber jeder Rechnungsansicht steht die Liste der Rechnungen (E6). */
const ZU_RECHNUNGEN: PfadVerweis = { titel: 'Rechnungen', ziel: '/rechnungen' };

const SPALTEN: readonly string[] = [
  'Leistung',
  'Einheit',
  'Einzelpreis',
  'Angeboten',
  'Abgerechnet',
  'Offen',
  'Jetzt abrechnen',
  'Betrag',
];

/** Was die Ansicht gerade weiss. */
type Stand =
  | { readonly art: 'laedt' }
  | { readonly art: 'daten'; readonly rechnung: Rechnung }
  | { readonly art: 'unbekannt' }
  | { readonly art: 'ausfall' };

/** Die Angaben der Rechnung, so wie sie in den Feldern stehen. */
interface Texte {
  /** Tag (`YYYY-MM-DD`), wie das `date`-Feld ihn fuehrt. */
  readonly rechnungDatum: string;
  readonly leistungszeitraum: string;
}

const LEERE_TEXTE: Texte = { rechnungDatum: '', leistungszeitraum: '' };

/**
 * Eine Zeile der Maske: die festen Angaben der Position und die zwei Felder.
 *
 * Einheit, Einzelpreis und die drei Mengen kommen vom Server und sind an der Rechnung nicht
 * aenderbar (Kriterium 9); sie stehen hier mit, weil die Zeile ihren Betrag und ihre
 * Ueberschreitung selbst zeigt. Die Menge steht als <b>Text</b> und nicht als Zahl, aus demselben
 * Grund wie in {@link Positionsmaske}: `type="number"` liefert eine Gleitkommazahl, und
 * `lib/geld.ts` rechnet ausdruecklich nicht in Gleitkomma (E5).
 */
interface Maskenzeile {
  readonly angebotPositionId: number;
  readonly einheit: Einheit;
  readonly einzelpreisInCent: number;
  readonly angebotenInHundertsteln: number;
  readonly abgerechnetInHundertsteln: number;
  readonly offenInHundertsteln: number;
  readonly bezeichnung: string;
  readonly menge: string;
}

/** Eine Zeile mit ihrer gelesenen Menge — `null`, solange der Text keine ist. */
interface Reihe {
  readonly zeile: Maskenzeile;
  readonly mengeInHundertsteln: number | null;
}

/** Was sich aus lesbaren Mengen ergibt: der Rumpf der Anfrage und die drei Summen. */
interface Gerechnetes {
  readonly eingaben: readonly AbrechnungsangabeEingabe[];
  readonly summen: Rechnungssummen;
}

/** Die Ueberschrift: der Entwurf hat keine Nummer, er heisst nach seinem Zustand (Kriterium 15). */
function ueberschriftZu(rechnung: Rechnung): string {
  return rechnung.nummer === null ? 'Rechnung (Entwurf)' : `Rechnung ${rechnung.nummer}`;
}

/** Die Zeilen der Antwort als Zeilen der Maske. */
function alsZeilen(rechnung: Rechnung): readonly Maskenzeile[] {
  return rechnung.zeilen.map((zeile) => ({
    angebotPositionId: zeile.angebotPositionId,
    einheit: zeile.einheit,
    einzelpreisInCent: zeile.einzelpreisInCent,
    angebotenInHundertsteln: zeile.angebotenInHundertsteln,
    abgerechnetInHundertsteln: zeile.abgerechnetInHundertsteln,
    offenInHundertsteln: zeile.offenInHundertsteln,
    bezeichnung: zeile.bezeichnung,
    menge: dezimal(zeile.mengeInHundertsteln, ','),
  }));
}

/** Die Angaben der Antwort als Feldinhalte — `null` wird zum leeren Feld, nicht zu „null". */
function alsTexte(rechnung: Rechnung): Texte {
  return {
    rechnungDatum: rechnung.rechnungDatum,
    leistungszeitraum: rechnung.leistungszeitraum ?? '',
  };
}

/** Jede Zeile mit ihrer gelesenen Menge — einmal gelesen, von Tafel, Summen und Rumpf benutzt. */
function alsReihen(zeilen: readonly Maskenzeile[]): readonly Reihe[] {
  return zeilen.map((zeile) => ({ zeile, mengeInHundertsteln: hundertstel(zeile.menge) }));
}

/**
 * Rumpf und Summen — `null`, solange eine Menge keine Zahl ist.
 *
 * Beides in einem Durchlauf und alles oder nichts: Eine Rechnung wird als Ganzes geschrieben, und
 * eine Liste, aus der die unlesbaren Zeilen stillschweigend herausfielen, loeschte Positionen, die
 * der Mensch sieht. Die Summen gelten aus demselben Grund erst, wenn jede Zeile lesbar ist — eine
 * Summe ueber die lesbaren waere eine Zahl, die zu keiner Rechnung gehoert.
 */
function gerechnetesAus(
  reihen: readonly Reihe[],
  satzInHundertsteln: number,
): Gerechnetes | null {
  const eingaben: AbrechnungsangabeEingabe[] = [];
  const posten: { mengeInHundertsteln: number; einzelpreisInCent: number }[] = [];
  for (const reihe of reihen) {
    const menge = reihe.mengeInHundertsteln;
    if (menge === null) {
      return null;
    }
    eingaben.push({
      angebotPositionId: reihe.zeile.angebotPositionId,
      bezeichnung: reihe.zeile.bezeichnung,
      menge: dezimal(menge, '.'),
    });
    posten.push({ mengeInHundertsteln: menge, einzelpreisInCent: reihe.zeile.einzelpreisInCent });
  }
  return { eingaben, summen: rechnungssummen(posten, satzInHundertsteln) };
}

/**
 * Was die Seite selbst beanstandet, bevor etwas hinausgeht.
 *
 * Die Mengen stehen nicht darin: Ob eine Menge lesbar ist, zeigt die Zeile beim Tippen, und die
 * Stelle, an der das Speichern daran scheitert, ist {@link gerechnetesAus}. Clientseitige Pruefung
 * ist Nutzerfuehrung und kein Ersatz fuer die Pruefung des Servers (CLAUDE-react.md).
 */
function eigenePruefung(texte: Texte, zeilen: readonly Maskenzeile[]): FieldErrors {
  const fehler: Record<string, readonly string[]> = {};
  if (texte.rechnungDatum === '') {
    fehler.rechnungDatum = [DATUM_FEHLT];
  }
  if (texte.leistungszeitraum.trim() === '') {
    fehler.leistungszeitraum = [ZEITRAUM_FEHLT];
  }
  zeilen.forEach((zeile, stelle) => {
    if (zeile.bezeichnung.trim() === '') {
      fehler[`positionen[${String(stelle)}].bezeichnung`] = [BEZEICHNUNG_FEHLT];
    }
  });
  return fehler;
}

/** Der Hinweis an einer Zeile: Wort und Symbol, nicht Farbe allein (CLAUDE-design.md). */
function Ueberschreitungshinweis({
  mengeInHundertsteln,
  angebotPositionId,
}: {
  readonly mengeInHundertsteln: number;
  readonly angebotPositionId: number;
}) {
  return (
    <Box
      component="span"
      data-testid={`zeile-hinweis-${String(angebotPositionId)}`}
      sx={(theme) => ({
        display: 'inline-flex',
        alignItems: 'center',
        gap: '4px',
        color: theme.vars.palette.kupferwolke.toenung.bernstein.schrift,
      })}
    >
      <IconAlertTriangle size={SYMBOL_HINWEIS} stroke={1.8} aria-hidden />
      {`${dezimal(mengeInHundertsteln, ',')} über dem Angebot`}
    </Box>
  );
}

/** Eine Zahl in der Tafel: rechts, in Tabellenziffern, ohne Umbruch. */
function Zahlzelle({
  children,
  testid,
  stark = false,
}: {
  readonly children: ReactNode;
  readonly testid?: string;
  readonly stark?: boolean;
}) {
  return (
    <Box
      component="td"
      data-testid={testid}
      className={ZAHLEN_KLASSE}
      sx={{ textAlign: 'right', whiteSpace: 'nowrap', fontWeight: stark ? 600 : 400 }}
    >
      {children}
    </Box>
  );
}

/** Eine der drei Summen unter der Tafel. */
function Summenzeile({
  name,
  wert,
  testid,
  stark = false,
}: {
  readonly name: string;
  readonly wert: string;
  readonly testid: string;
  readonly stark?: boolean;
}) {
  return (
    <Box
      data-testid={testid}
      sx={{ display: 'flex', alignItems: 'baseline', gap: '10px', justifyContent: 'flex-end' }}
    >
      <Typography sx={{ fontSize: 13.5, fontWeight: stark ? 600 : 400 }}>{name}</Typography>
      <Typography
        className={ZAHLEN_KLASSE}
        sx={{ fontSize: stark ? 17 : 13.5, fontWeight: stark ? 800 : 600, minWidth: 120 }}
      >
        {wert}
      </Typography>
    </Box>
  );
}

/** Die Angaben des Kopfes als Stammdaten-Liste (Vorlage `.stamm` Z. 100–102). */
function Angaben({ rechnung }: { readonly rechnung: Rechnung }) {
  const eintraege: readonly { name: string; wert: ReactNode }[] = [
    {
      name: 'Firma',
      wert: (
        <Link
          component={RouterLink}
          to={`/firmen/${String(rechnung.firmaId)}`}
          underline="hover"
          sx={{ fontSize: 13.5, fontWeight: 500 }}
        >
          {rechnung.firmaName}
        </Link>
      ),
    },
    {
      name: 'Angebot',
      wert: (
        <Link
          component={RouterLink}
          to={`/angebote/${String(rechnung.angebotId)}`}
          underline="hover"
          sx={{ fontSize: 13.5, fontWeight: 500 }}
        >
          Zum Angebot
        </Link>
      ),
    },
    { name: 'Zustand', wert: <RechnungszustandChip zustand={rechnung.zustand} /> },
  ];
  return (
    <Box
      component="dl"
      data-testid="rechnung-angaben"
      sx={{
        display: 'grid',
        gridTemplateColumns: { xs: 'minmax(0, 1fr)', sm: 'auto minmax(0, 1fr)' },
        alignItems: 'center',
        gap: '10px 20px',
        margin: 0,
        fontSize: 13.5,
      }}
    >
      {eintraege.map((eintrag) => (
        <Fragment key={eintrag.name}>
          <Box
            component="dt"
            sx={(theme) => ({ color: theme.vars.palette.kupferwolke.textSchwach })}
          >
            {eintrag.name}
          </Box>
          <Box component="dd" sx={{ margin: 0, fontWeight: 500 }}>
            {eintrag.wert}
          </Box>
        </Fragment>
      ))}
    </Box>
  );
}

/**
 * Eine Zeile der Positionstafel (Kriterien 4, 7, 8).
 *
 * <b>Gesteuert, nicht selbststaendig</b>, wie {@link Positionsmaske}: Der Zustand liegt in der
 * Seite, denn die Rechnung wird als Ganzes geschickt. Dass diese Komponente auf Modulebene steht
 * und nicht in der Seite, ist kein Stilfrage: Eine im Rumpf der Seite erklaerte Komponente ist bei
 * jedem Zeichnen ein neuer Typ, React baut die Zeile dann neu auf — und der Fokus faellt nach dem
 * ersten getippten Zeichen aus dem Feld.
 */
function Positionszeile({
  reihe,
  stelle,
  meldung,
  aendere,
}: {
  readonly reihe: Reihe;
  readonly stelle: number;
  /** Die Meldung an einem Feld: die eigene der Seite oder die des Servers. */
  readonly meldung: (feld: string) => string | undefined;
  readonly aendere: (stelle: number, neu: Maskenzeile) => void;
}) {
  const { zeile, mengeInHundertsteln } = reihe;
  const nummer = String(stelle + 1);
  const zuViel =
    mengeInHundertsteln === null
      ? 0
      : ueberschreitung(
          zeile.abgerechnetInHundertsteln,
          zeile.angebotenInHundertsteln,
          mengeInHundertsteln,
        );
  const bezeichnungMeldung = meldung(`positionen[${String(stelle)}].bezeichnung`);
  const mengeMeldung =
    mengeInHundertsteln === null ? MENGE_UNKLAR : meldung(`positionen[${String(stelle)}].menge`);
  return (
    <Box component="tr">
      <Box component="td" sx={{ minWidth: 220 }}>
        <TextField
          value={zeile.bezeichnung}
          onChange={(ereignis) => {
            aendere(stelle, { ...zeile, bezeichnung: ereignis.target.value });
          }}
          error={bezeichnungMeldung !== undefined}
          helperText={bezeichnungMeldung}
          fullWidth
          size="small"
          slotProps={{
            htmlInput: {
              'aria-label': `Leistung, Position ${nummer}`,
              maxLength: BEZEICHNUNG_LAENGE,
            },
          }}
        />
      </Box>
      <Box component="td">{EINHEIT_WORT[zeile.einheit]}</Box>
      <Zahlzelle>{euro(zeile.einzelpreisInCent)}</Zahlzelle>
      <Zahlzelle>{dezimal(zeile.angebotenInHundertsteln, ',')}</Zahlzelle>
      <Zahlzelle testid="zeile-abgerechnet">
        {dezimal(zeile.abgerechnetInHundertsteln, ',')}
      </Zahlzelle>
      <Zahlzelle testid="zeile-offen">{dezimal(zeile.offenInHundertsteln, ',')}</Zahlzelle>
      <Box component="td" sx={{ minWidth: 150 }}>
        <TextField
          value={zeile.menge}
          onChange={(ereignis) => {
            aendere(stelle, { ...zeile, menge: ereignis.target.value });
          }}
          error={mengeMeldung !== undefined}
          helperText={
            mengeMeldung ??
            (zuViel > 0 ? (
              <Ueberschreitungshinweis
                mengeInHundertsteln={zuViel}
                angebotPositionId={zeile.angebotPositionId}
              />
            ) : undefined)
          }
          size="small"
          slotProps={{
            htmlInput: {
              'aria-label': `Jetzt abrechnen, Position ${nummer}`,
              inputMode: 'decimal',
              className: ZAHLEN_KLASSE,
            },
          }}
        />
      </Box>
      <Zahlzelle testid={`zeile-betrag-${String(zeile.angebotPositionId)}`} stark>
        {mengeInHundertsteln === null
          ? '—'
          : euro(betrag(mengeInHundertsteln, zeile.einzelpreisInCent))}
      </Zahlzelle>
    </Box>
  );
}

export default function RechnungPage() {
  const { rechnungId } = useParams();
  const kennung = kennungAus(rechnungId);
  const navigate = useNavigate();
  const [stand, setzeStand] = useState<Stand>({ art: 'laedt' });
  const [texte, setzeTexte] = useState<Texte>(LEERE_TEXTE);
  const [zeilen, setzeZeilen] = useState<readonly Maskenzeile[]>([]);
  const [eigeneFehler, setzeEigeneFehler] = useState<FieldErrors>({});
  const [feldFehler, setzeFeldFehler] = useState<FieldErrors>({});
  const [fehler, setzeFehler] = useState<string | null>(null);
  const [gespeichert, setzeGespeichert] = useState(false);
  const [laeuft, setzeLaeuft] = useState(false);
  useKopfPfad(
    [ZU_RECHNUNGEN],
    stand.art === 'daten' ? ueberschriftZu(stand.rechnung) : 'Rechnung',
  );

  useEffect(() => {
    if (kennung === null) {
      // Eine Kennung, die keine ist, geht gar nicht erst ans Netz (kennung.ts).
      setzeStand({ art: 'unbekannt' });
      return;
    }
    void rechnungLesen(kennung)
      .then((rechnung) => {
        setzeStand({ art: 'daten', rechnung });
        setzeTexte(alsTexte(rechnung));
        setzeZeilen(alsZeilen(rechnung));
      })
      .catch((ursache: unknown) => {
        setzeStand(nichtGefunden(ursache) ? { art: 'unbekannt' } : { art: 'ausfall' });
      });
  }, [kennung]);

  const reihen = alsReihen(zeilen);

  /** Die Meldung an einem Feld: die eigene geht vor der des Servers. */
  const meldung = (feld: string) => meldungAm(eigeneFehler, feld) ?? meldungAm(feldFehler, feld);

  /** Eine Zeile aendern — die Liste ist Zustand, geschickt wird sie beim Speichern als Ganzes. */
  const aendere = (stelle: number, neu: Maskenzeile) => {
    setzeZeilen(zeilen.map((alt, index) => (index === stelle ? neu : alt)));
  };

  const speichern = async (rechnung: Rechnung, gerechnet: Gerechnetes | null) => {
    setzeGespeichert(false);
    setzeFehler(null);
    setzeFeldFehler({});
    const eigene = eigenePruefung(texte, zeilen);
    setzeEigeneFehler(eigene);
    if (Object.keys(eigene).length > 0 || gerechnet === null) {
      // Was fehlt, steht am Feld beziehungsweise an der Zeile; hinaus geht nichts.
      return;
    }
    setzeLaeuft(true);
    try {
      const neu = await rechnungAendern(rechnung.id, {
        rechnungDatum: texte.rechnungDatum,
        leistungszeitraum: texte.leistungszeitraum.trim(),
        positionen: gerechnet.eingaben,
      });
      setzeStand({ art: 'daten', rechnung: neu });
      setzeTexte(alsTexte(neu));
      setzeZeilen(alsZeilen(neu));
      setzeGespeichert(true);
    } catch (ursache) {
      const felder = feldMeldungen(ursache);
      if (Object.keys(felder).length > 0) {
        setzeFeldFehler(felder);
      } else {
        setzeFehler(AUSFALL_SPEICHERN);
      }
    } finally {
      setzeLaeuft(false);
    }
  };

  const loeschen = async (rechnung: Rechnung) => {
    setzeFehler(null);
    setzeLaeuft(true);
    try {
      await rechnungLoeschen(rechnung.id);
      navigate('/rechnungen');
    } catch {
      setzeFehler(AUSFALL_LOESCHEN);
      setzeLaeuft(false);
    }
  };

  /** Die Aktionen im Kopf: „Speichern" als die eine Kupfertaste, das Loeschen im ⋯-Menue. */
  function aktionenZu(rechnung: Rechnung): ReactNode {
    return (
      <Box
        data-testid="rechnung-aktionen"
        sx={{ display: 'flex', alignItems: 'center', gap: '10px', flexWrap: 'wrap' }}
      >
        <AktionsMenue
          name="Aktionen für diese Rechnung"
          objekt={ueberschriftZu(rechnung)}
          eintraege={[
            {
              titel: 'Entwurf löschen',
              symbol: <IconTrash size={SYMBOL_TASTE} stroke={1.8} />,
              rueckfrage: LOESCHEN_FRAGE,
              onAuswahl: () => {
                void loeschen(rechnung);
              },
            },
          ]}
        />
        <KupferTaste
          disabled={laeuft}
          symbol={<IconDeviceFloppy size={SYMBOL_TASTE} stroke={1.8} />}
        >
          Speichern
        </KupferTaste>
      </Box>
    );
  }

  /** Die Maske des Entwurfs: Angaben, Positionstafel und die drei Summen. */
  function entwurfZu(rechnung: Rechnung, gerechnet: Gerechnetes | null): ReactNode {
    const summen = gerechnet?.summen ?? null;
    const wert = (cent: number | undefined) => (cent === undefined ? '—' : euro(cent));
    return (
      <>
        <Karte titel="Angaben zur Rechnung">
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
            {gespeichert ? (
              <Typography
                role="status"
                sx={(theme) => ({
                  fontSize: 12.5,
                  color: theme.vars.palette.kupferwolke.toenung.salbei.schrift,
                })}
              >
                {GESPEICHERT}
              </Typography>
            ) : null}
            <TextField
              label="Rechnungsdatum"
              type="date"
              value={texte.rechnungDatum}
              onChange={(ereignis) => {
                setzeTexte({ ...texte, rechnungDatum: ereignis.target.value });
              }}
              error={meldung('rechnungDatum') !== undefined}
              helperText={meldung('rechnungDatum')}
              required
              fullWidth
              // Ein `date`-Feld zeigt immer seine Maske, also darf die Beschriftung nie im Feld
              // liegen.
              slotProps={{ inputLabel: { shrink: true } }}
            />
            <TextField
              label="Leistungszeitraum"
              value={texte.leistungszeitraum}
              onChange={(ereignis) => {
                setzeTexte({ ...texte, leistungszeitraum: ereignis.target.value });
              }}
              error={meldung('leistungszeitraum') !== undefined}
              helperText={meldung('leistungszeitraum')}
              required
              fullWidth
              slotProps={{ htmlInput: { maxLength: ZEITRAUM_LAENGE } }}
            />
          </Box>
        </Karte>
        <Karte titel="Positionen" anzahl={zeilen.length}>
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
            <Tafel beschriftung="Positionen" spalten={SPALTEN}>
              {reihen.map((reihe, stelle) => (
                // Die Kennung der Angebotsposition ist der Schluessel: Sie bleibt ueber ein
                // Speichern hinweg dieselbe, und die Reihenfolge der Liste ist die des Angebots.
                <Positionszeile
                  key={reihe.zeile.angebotPositionId}
                  reihe={reihe}
                  stelle={stelle}
                  meldung={meldung}
                  aendere={aendere}
                />
              ))}
            </Tafel>
            <Box
              sx={(theme) => ({
                display: 'flex',
                flexDirection: 'column',
                gap: '6px',
                paddingTop: '14px',
                borderTop: `1px solid ${theme.vars.palette.kupferwolke.linie}`,
              })}
            >
              <Summenzeile
                testid="rechnung-netto"
                name="Summe (netto)"
                wert={wert(summen?.nettoInCent)}
              />
              <Summenzeile
                testid="rechnung-steuer"
                name={`Mehrwertsteuer ${dezimal(rechnung.steuersatzInHundertsteln, ',')} %`}
                wert={wert(summen?.steuerInCent)}
              />
              <Summenzeile
                testid="rechnung-brutto"
                name="Bruttobetrag"
                wert={wert(summen?.bruttoInCent)}
                stark
              />
            </Box>
          </Box>
        </Karte>
      </>
    );
  }

  /** Der Rahmen jeder Bereitschaft: der Abstand zwischen den Karten der Buehne. */
  const spalten = { display: 'flex', flexDirection: 'column', gap: '22px' } as const;

  if (stand.art === 'laedt') {
    return (
      <Box sx={spalten}>
        <Karte>
          <Typography
            sx={(theme) => ({ fontSize: 12.5, color: theme.vars.palette.kupferwolke.textSchwach })}
          >
            {LAEDT}
          </Typography>
        </Karte>
      </Box>
    );
  }

  if (stand.art !== 'daten') {
    return (
      <Box sx={spalten}>
        <Karte>
          <Alert severity="error">{stand.art === 'unbekannt' ? NICHT_GEFUNDEN : AUSFALL}</Alert>
        </Karte>
      </Box>
    );
  }

  const { rechnung } = stand;

  if (rechnung.zustand === 'GESTELLT') {
    return (
      <Box sx={spalten}>
        <Karte titel={ueberschriftZu(rechnung)} titelEbene={1}>
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
            <Angaben rechnung={rechnung} />
            <Typography
              role="status"
              sx={(theme) => ({ fontSize: 12.5, color: theme.vars.palette.kupferwolke.textMatt })}
            >
              {FOLGT}
            </Typography>
          </Box>
        </Karte>
      </Box>
    );
  }

  const gerechnet = gerechnetesAus(reihen, rechnung.steuersatzInHundertsteln);
  return (
    <Box
      component="form"
      noValidate
      onSubmit={(ereignis: FormEvent<HTMLFormElement>) => {
        ereignis.preventDefault();
        void speichern(rechnung, gerechnet);
      }}
      sx={spalten}
    >
      {fehler === null ? null : <Alert severity="error">{fehler}</Alert>}
      <Karte titel={ueberschriftZu(rechnung)} titelEbene={1} werkzeug={aktionenZu(rechnung)}>
        <Angaben rechnung={rechnung} />
      </Karte>
      {entwurfZu(rechnung, gerechnet)}
    </Box>
  );
}
