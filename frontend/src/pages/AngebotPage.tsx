import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import Link from '@mui/material/Link';
import Typography from '@mui/material/Typography';
import { IconArrowLeft, IconArrowRight, IconFilePlus, IconPencil } from '@tabler/icons-react';
import { Fragment, useEffect, useId, useState } from 'react';
import type { ReactNode } from 'react';
import { Link as RouterLink, useNavigate, useParams } from 'react-router-dom';

import { angebotLesen, angebotStatusWeiter, angebotStatusZurueck } from '../api/angebote';
import type { Angebot, AngebotPosition } from '../api/angebote';
import { angebotAbrechnung, rechnungAnlegen } from '../api/rechnungen';
import type {
  Abrechnungsposition,
  AngebotRechnungZeile,
  Angebotsabrechnung,
} from '../api/rechnungen';
import AngebotsstatusChip from '../components/AngebotsstatusChip';
import Anlagen from '../components/Anlagen';
import Karte from '../components/Karte';
import Kommentare from '../components/Kommentare';
import { useKopfPfad } from '../components/KopfPfad';
import type { PfadVerweis } from '../components/KopfPfad';
import KupferTaste from '../components/KupferTaste';
import Monatswahl, { monatOderKeiner, monatswahlWert } from '../components/Monatswahl';
import { EINHEIT_WORT, MODUS_WORT } from '../components/Positionsmaske';
import RechnungszustandChip from '../components/RechnungszustandChip';
import Tafel from '../components/Tafel';
import Ueberschreitungshinweis from '../components/Ueberschreitungshinweis';
import WeicheTaste from '../components/WeicheTaste';
import { nichtGefunden, serverMeldung } from '../lib/apifehler';
import { dezimal, euro } from '../lib/geld';
import { kennungAus } from '../lib/kennung';
import { tagWort } from '../lib/tag';
import { ZAHLEN_KLASSE } from '../theme';

/**
 * Die Ansicht eines Angebots (Issue #127).
 *
 * Oben die Karte mit der Ueberschrift, den Angaben und den Aktionen; darunter die Beschreibung,
 * sofern es eine gibt, die Positionen mit Summe, die Anlagen und zuletzt die Kommentare (Vorlage
 * `.karte` Z. 53, `.kopfzeile` Z. 79–80, `.stamm` Z. 100–102).
 *
 * <b>Anlagen und Kommentare laden sich selbst</b> ({@link Anlagen}, Issue #148; {@link Kommentare},
 * Issue #146): Die Seite gibt nur die Kennung weiter. Ein Ausfall eines der beiden Wege steht darum
 * in dessen Karte und laesst Ueberschrift, Angaben und Positionen stehen.
 *
 * <b>Die Aktionen.</b> „Status weiter" ist die eine Kupfertaste — der gewoehnliche naechste Schritt.
 * „Status zurueck" und „Bearbeiten" stehen weich daneben (CLAUDE-design.md, Leitgedanke 2). An den
 * Enden der Reihe fehlt die Taste, die ins Leere fuehrte: bei „angelegt" die zurueck, bei
 * „abgerechnet" die weiter. Bearbeiten laesst sich in jedem Status (Kriterium 5).
 *
 * <b>Nach jedem Statuswechsel gilt die Antwort</b>, nicht ein selbst umgeschalteter Status: Was der
 * Server fuehrt, ist die Wahrheit.
 *
 * <b>Der Abrechnungsstand ist ein eigener Weg</b> (#160, Kriterien 3 und 26; Issue #187): Er traegt
 * je Position, was abgerechnet und was offen ist, und dazu die Rechnungen dieses Angebots. Er faellt
 * fuer sich aus, genau wie Anlagen und Kommentare — dann steht seine Meldung in der Karte
 * „Rechnungen", und Ueberschrift, Angaben und Positionen bleiben stehen. Was er nicht weiss, zeigt
 * die Ansicht nicht: Ohne ihn fehlen die zwei Spalten und „Rechnung schreiben".
 *
 * <b>Die zwei Spalten stehen erst, wenn es eine Rechnung gibt</b>: Solange keine existiert, sagten
 * „Abgerechnet" mit 0,00 und „Offen" mit der vollen Menge nichts, was die Spalte „Menge" nicht schon
 * sagt — zwei Spalten Rauschen in jeder Angebotsansicht der Anwendung.
 *
 * <b>„Rechnung schreiben" ist weich</b>: Die eine Kupfertaste der Ansicht bleibt „Status weiter"
 * (CLAUDE-design.md, Leitgedanke 2). Sie steht nur, wo sie etwas bewirkt — ab „bestellt" und solange
 * an einer Position etwas offen ist. <b>Die Taste fuehrt, der Server entscheidet</b>, wie in
 * {@link RechnungenPage}. <b>Sie legt nicht selbst an</b>, sondern oeffnet einen kleinen Dialog mit
 * der einen Frage, die vorher zu beantworten ist: welchen Monat der Arbeitszeit der Entwurf
 * vorbelegen soll ({@link Monatswahl}, Issue #203). Weist das Anlegen mit 409 ab, steht die Meldung
 * des Servers in diesem Dialog — dort, wo die Wahl steht, die der Betrachter aendern kann.
 */

const NICHT_GEFUNDEN = 'Dieses Angebot gibt es nicht.';
const AUSFALL = 'Das Angebot ist gerade nicht zu erreichen. Bitte später erneut versuchen.';
const AUSFALL_AKTION = 'Der Status wurde nicht geändert. Bitte später erneut versuchen.';
const LAEDT = 'Das Angebot wird geladen …';
const OHNE_POSITION = 'Noch keine Position.';
const NETTO = 'Alle Beträge netto, zzgl. gesetzlicher Umsatzsteuer';

/** Die Symbolgroesse in den Tasten (wie in {@link FirmaPage}). */
const SYMBOL_TASTE = 16;

const OHNE_RECHNUNG = 'Noch keine Rechnung.';
const LAEDT_ABRECHNUNG = 'Der Abrechnungsstand wird geladen …';
const AUSFALL_ABRECHNUNG =
  'Der Abrechnungsstand ist gerade nicht zu erreichen. Bitte später erneut versuchen.';
const AUSFALL_ENTWURF = 'Der Rechnungsentwurf wurde nicht angelegt. Bitte später erneut versuchen.';

/** Die Ueberschrift des Dialogs, in dem der Monat gewaehlt wird (Issue #203). */
const WAHL_TITEL = 'Rechnung schreiben';

/** Was an der Stelle der Nummer steht, solange die Rechnung keine hat (wie in {@link RechnungenPage}). */
const OHNE_NUMMER = 'Entwurf';

/** Die Spalten der Positionstafel ohne den Abrechnungsstand. */
const SPALTEN = ['Bezeichnung', 'Abrechnung', 'Menge', 'Einheit', 'Einzelpreis', 'Betrag'] as const;

/**
 * Dieselben Spalten mit dem Stand — „Abgerechnet" und „Offen" stehen bei der Menge.
 *
 * Dort und nicht am Ende der Zeile: Alle drei sind Mengen in derselben Einheit, und die Spalte
 * „Einheit" dahinter gilt damit fuer alle drei.
 */
const SPALTEN_MIT_STAND = [
  'Bezeichnung',
  'Abrechnung',
  'Menge',
  'Abgerechnet',
  'Offen',
  'Einheit',
  'Einzelpreis',
  'Betrag',
] as const;

/** Die Spalten der Rechnungstafel — ohne Firma, die steht schon im Kopf des Angebots. */
const SPALTEN_RECHNUNGEN: readonly string[] = ['Nummer', 'Rechnungsdatum', 'Betrag', 'Zustand'];

/** Ab diesen Staenden laesst sich zu einem Angebot eine Rechnung schreiben (Kriterium 3). */
const ABRECHENBAR: readonly Angebot['status'][] = ['BESTELLT', 'ERLEDIGT', 'ABGERECHNET'];

/** Was die Ansicht gerade weiss. */
type Stand =
  | { readonly art: 'laedt' }
  | { readonly art: 'daten'; readonly angebot: Angebot }
  | { readonly art: 'unbekannt' }
  | { readonly art: 'ausfall' };

/**
 * Was die Ansicht ueber den Abrechnungsstand weiss.
 *
 * Ein eigener Stand neben {@link Stand}: Der Weg faellt fuer sich aus, und ein Ausfall dort darf das
 * Angebot nicht mitnehmen.
 */
type Abrechnungsstand =
  | { readonly art: 'laedt' }
  | { readonly art: 'daten'; readonly abrechnung: Angebotsabrechnung }
  | { readonly art: 'ausfall' };

/** Die Ueberschrift: ein Angebot hat keine Nummer, es heisst nach seinem Datum. */
function ueberschriftZu(angebot: Angebot): string {
  return `Angebot vom ${tagWort(angebot.angebotDatum)}`;
}

/**
 * Eine Zeile der Positionstafel (Kriterien 4, 5, 26).
 *
 * `stand` ist der Abrechnungsstand dieser Position, oder `undefined` — dann fehlen die zwei Spalten.
 * Eine Position ohne Stand bei vorhandenen Rechnungen gibt es nicht; die Zellen stuenden sonst leer
 * da, und das ist hier die ehrlichere Zelle als eine erfundene Null.
 */
function Positionszeile({
  position,
  stand,
}: {
  readonly position: AngebotPosition;
  readonly stand: Abrechnungsposition | undefined;
}) {
  return (
    <Box component="tr">
      <Box component="td" sx={{ fontWeight: 500 }}>
        {position.bezeichnung}
      </Box>
      <Box component="td">{MODUS_WORT[position.abrechnungsmodus]}</Box>
      <Box
        component="td"
        className={ZAHLEN_KLASSE}
        sx={{ textAlign: 'right', whiteSpace: 'nowrap' }}
      >
        {dezimal(position.mengeInHundertsteln, ',')}
      </Box>
      {stand === undefined ? null : (
        <>
          <Box
            component="td"
            className={ZAHLEN_KLASSE}
            sx={{ textAlign: 'right', whiteSpace: 'nowrap' }}
          >
            {dezimal(stand.abgerechnetInHundertsteln, ',')}
          </Box>
          <Box component="td" sx={{ textAlign: 'right' }}>
            <Box className={ZAHLEN_KLASSE} sx={{ whiteSpace: 'nowrap' }}>
              {dezimal(stand.offenInHundertsteln, ',')}
            </Box>
            {stand.ueberschreitungInHundertsteln > 0 ? (
              // Der Hinweis steht bei „Offen": Dort steht 0,00, und er sagt, warum das zu wenig ist.
              <Box sx={{ fontSize: 12.5 }}>
                <Ueberschreitungshinweis
                  mengeInHundertsteln={stand.ueberschreitungInHundertsteln}
                  angebotPositionId={stand.angebotPositionId}
                />
              </Box>
            ) : null}
          </Box>
        </>
      )}
      <Box component="td">{EINHEIT_WORT[position.einheit]}</Box>
      <Box
        component="td"
        className={ZAHLEN_KLASSE}
        sx={{ textAlign: 'right', whiteSpace: 'nowrap' }}
      >
        {euro(position.einzelpreisInCent)}
      </Box>
      <Box
        component="td"
        className={ZAHLEN_KLASSE}
        sx={{ textAlign: 'right', whiteSpace: 'nowrap', fontWeight: 600 }}
      >
        {euro(position.betragInCent)}
      </Box>
    </Box>
  );
}

/** Die Angaben des Angebots als Stammdaten-Liste (Vorlage `.stamm` Z. 100–102). */
function Angaben({ angebot }: { readonly angebot: Angebot }) {
  const zeilen: readonly { name: string; wert: ReactNode }[] = [
    {
      name: 'Firma',
      wert: (
        <Link
          component={RouterLink}
          to={`/firmen/${String(angebot.firmaId)}`}
          underline="hover"
          sx={{ fontSize: 13.5, fontWeight: 500 }}
        >
          {angebot.firmaName}
        </Link>
      ),
    },
    // Der Ansprechpartner ist optional (Issue #126); ohne ihn steht die Zeile gar nicht da.
    ...(angebot.ansprechpartnerName === null
      ? []
      : [{ name: 'Ansprechpartner', wert: angebot.ansprechpartnerName }]),
    { name: 'Angebotsdatum', wert: tagWort(angebot.angebotDatum) },
    { name: 'Status', wert: <AngebotsstatusChip status={angebot.status} /> },
  ];
  return (
    <Box
      component="dl"
      data-testid="angebot-angaben"
      sx={{
        display: 'grid',
        gridTemplateColumns: { xs: 'minmax(0, 1fr)', sm: 'auto minmax(0, 1fr)' },
        alignItems: 'center',
        gap: '10px 20px',
        margin: 0,
        fontSize: 13.5,
      }}
    >
      {zeilen.map((zeile) => (
        <Fragment key={zeile.name}>
          <Box
            component="dt"
            sx={(theme) => ({ color: theme.vars.palette.kupferwolke.textSchwach })}
          >
            {zeile.name}
          </Box>
          <Box component="dd" sx={{ margin: 0, fontWeight: 500 }}>
            {zeile.wert}
          </Box>
        </Fragment>
      ))}
    </Box>
  );
}

/**
 * Eine Zeile der Rechnungstafel; die Nummernspalte traegt den Weg zur Rechnung.
 *
 * Dieselbe Form wie in {@link RechnungenPage}: ein Link je Zeile, und der Entwurf heisst „Entwurf",
 * weil er noch keine Nummer hat (Kriterium 15).
 */
function Rechnungszeile({ rechnung }: { readonly rechnung: AngebotRechnungZeile }) {
  return (
    <Box component="tr">
      <Box component="td" sx={{ fontWeight: 500, whiteSpace: 'nowrap' }}>
        <Box
          component={RouterLink}
          to={`/rechnungen/${String(rechnung.id)}`}
          className={ZAHLEN_KLASSE}
          sx={(theme) => ({
            color: 'inherit',
            textDecoration: 'none',
            '&:hover': { color: theme.vars.palette.kupferwolke.kupfer },
          })}
        >
          {rechnung.nummer ?? OHNE_NUMMER}
        </Box>
      </Box>
      <Box component="td" className={ZAHLEN_KLASSE} sx={{ whiteSpace: 'nowrap' }}>
        {tagWort(rechnung.rechnungDatum)}
      </Box>
      <Box
        component="td"
        className={ZAHLEN_KLASSE}
        sx={{ fontWeight: 600, whiteSpace: 'nowrap', textAlign: 'right' }}
      >
        {euro(rechnung.bruttoInCent)}
      </Box>
      <Box component="td">
        <RechnungszustandChip zustand={rechnung.zustand} />
      </Box>
    </Box>
  );
}

/** Was in der Karte „Rechnungen" steht: Ladehinweis, Meldung, Satz zur Leere oder die Tafel. */
function rechnungenInhalt(stand: Abrechnungsstand): ReactNode {
  if (stand.art === 'ausfall') {
    return <Alert severity="error">{AUSFALL_ABRECHNUNG}</Alert>;
  }
  if (stand.art === 'laedt') {
    return (
      <Typography
        sx={(theme) => ({ fontSize: 12.5, color: theme.vars.palette.kupferwolke.textSchwach })}
      >
        {LAEDT_ABRECHNUNG}
      </Typography>
    );
  }
  if (stand.abrechnung.rechnungen.length === 0) {
    return (
      <Typography
        role="status"
        sx={(theme) => ({ fontSize: 12.5, color: theme.vars.palette.kupferwolke.textMatt })}
      >
        {OHNE_RECHNUNG}
      </Typography>
    );
  }
  return (
    <Tafel beschriftung="Rechnungen" spalten={SPALTEN_RECHNUNGEN}>
      {stand.abrechnung.rechnungen.map((rechnung) => (
        <Rechnungszeile key={rechnung.id} rechnung={rechnung} />
      ))}
    </Tafel>
  );
}

/** Ueber jeder Angebotsansicht stehen die Firmen und — sobald bekannt — die Firma (E6). */
const ZU_FIRMEN: PfadVerweis = { titel: 'Firmen', ziel: '/firmen' };

export default function AngebotPage() {
  const { angebotId } = useParams();
  const kennung = kennungAus(angebotId);
  const navigate = useNavigate();
  const titelId = useId();
  const [stand, setzeStand] = useState<Stand>({ art: 'laedt' });
  const [abrechnung, setzeAbrechnung] = useState<Abrechnungsstand>({ art: 'laedt' });
  const [meldung, setzeMeldung] = useState<string | null>(null);
  const [laeuft, setzeLaeuft] = useState(false);
  const [wahlOffen, setzeWahlOffen] = useState(false);
  const [wahlmeldung, setzeWahlmeldung] = useState<string | null>(null);
  const [monat, setzeMonat] = useState(monatswahlWert);
  // Solange das Angebot nicht gelesen ist, traegt die Endstufe das Wort „Angebot"; die Firma kommt
  // mit dem Angebot, denn erst das Angebot weiss, an wen es geht.
  useKopfPfad(
    stand.art === 'daten'
      ? [
          ZU_FIRMEN,
          {
            titel: stand.angebot.firmaName,
            ziel: `/firmen/${String(stand.angebot.firmaId)}`,
          },
        ]
      : [ZU_FIRMEN],
    stand.art === 'daten' ? ueberschriftZu(stand.angebot) : 'Angebot',
  );

  useEffect(() => {
    if (kennung === null) {
      // Eine Kennung, die keine ist, geht gar nicht erst ans Netz (kennung.ts).
      setzeStand({ art: 'unbekannt' });
      return;
    }
    void angebotLesen(kennung)
      .then((angebot) => {
        setzeStand({ art: 'daten', angebot });
      })
      .catch((ursache: unknown) => {
        setzeStand(nichtGefunden(ursache) ? { art: 'unbekannt' } : { art: 'ausfall' });
      });
  }, [kennung]);

  useEffect(() => {
    if (kennung === null) {
      // Wie beim Angebot: Eine Kennung, die keine ist, geht gar nicht erst ans Netz.
      return;
    }
    let gueltig = true;
    void angebotAbrechnung(kennung)
      .then((gelesen) => {
        if (gueltig) {
          setzeAbrechnung({ art: 'daten', abrechnung: gelesen });
        }
      })
      .catch(() => {
        if (gueltig) {
          setzeAbrechnung({ art: 'ausfall' });
        }
      });
    return () => {
      gueltig = false;
    };
  }, [kennung]);

  /** Ein Statuswechsel: Der neue Status kommt aus der Antwort, nicht aus der Oberflaeche. */
  const schalten = async (weg: (id: number) => Promise<Angebot>, angebot: Angebot) => {
    setzeMeldung(null);
    setzeLaeuft(true);
    try {
      setzeStand({ art: 'daten', angebot: await weg(angebot.id) });
    } catch {
      setzeMeldung(AUSFALL_AKTION);
    } finally {
      setzeLaeuft(false);
    }
  };

  /**
   * „Anlegen" im Dialog: Der Entwurf entsteht am Server, die Antwort nennt seine Kennung.
   *
   * Nach dem Fehlschlag bleibt der Dialog stehen — die Wahl des Monats ist dann noch da, und der
   * Betrachter kann eine andere treffen, ohne von vorn zu beginnen.
   */
  const entwurfAnlegen = async (angebot: Angebot) => {
    setzeWahlmeldung(null);
    setzeLaeuft(true);
    try {
      const entwurf = await rechnungAnlegen(angebot.id, monatOderKeiner(monat));
      navigate(`/rechnungen/${String(entwurf.id)}`);
    } catch (ursache: unknown) {
      // Die Meldung des Servers, wo er eine schickt — er allein weiss, warum er abgewiesen hat.
      setzeWahlmeldung(serverMeldung(ursache, AUSFALL_ENTWURF));
      setzeLaeuft(false);
    }
  };

  /** Die Wahl schliessen — ohne etwas anzulegen; die Meldung des letzten Versuchs geht mit. */
  const wahlSchliessen = () => {
    setzeWahlOffen(false);
    setzeWahlmeldung(null);
  };

  /** Der Dialog mit der einen Frage: welcher Monat die Mengen vorbelegt (Issue #203). */
  function wahlZu(angebot: Angebot): ReactNode {
    return (
      <Dialog
        open
        onClose={wahlSchliessen}
        fullWidth
        maxWidth="xs"
        aria-labelledby={titelId}
      >
        <DialogTitle id={titelId} sx={{ fontSize: 16 }}>
          {WAHL_TITEL}
        </DialogTitle>
        <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
          {wahlmeldung === null ? null : <Alert severity="error">{wahlmeldung}</Alert>}
          <Monatswahl monat={monat} setzeMonat={setzeMonat} disabled={laeuft} />
        </DialogContent>
        <DialogActions sx={{ padding: '4px 24px 20px', gap: '10px' }}>
          <WeicheTaste onClick={wahlSchliessen} disabled={laeuft}>
            Abbrechen
          </WeicheTaste>
          <KupferTaste
            onClick={() => {
              void entwurfAnlegen(angebot);
            }}
            disabled={laeuft}
          >
            Anlegen
          </KupferTaste>
        </DialogActions>
      </Dialog>
    );
  }

  /**
   * Steht „Rechnung schreiben" da? Nur ab „bestellt" und nur mit etwas Offenem (Kriterium 3).
   *
   * Ohne gelesenen Abrechnungsstand steht sie nicht: Was offen ist, weiss nur der Server, und eine
   * Taste auf Verdacht fuehrte in eine Abweisung.
   */
  function schreibbar(angebot: Angebot): boolean {
    return (
      ABRECHENBAR.includes(angebot.status) &&
      abrechnung.art === 'daten' &&
      abrechnung.abrechnung.positionen.some((position) => position.offenInHundertsteln > 0)
    );
  }

  /** Die Aktionen neben der Ueberschrift; an den Enden der Reihe fehlt die jeweilige Taste. */
  function aktionenZu(angebot: Angebot): ReactNode {
    return (
      <Box
        data-testid="angebot-aktionen"
        sx={{ display: 'flex', alignItems: 'center', gap: '10px', flexWrap: 'wrap' }}
      >
        <WeicheTaste
          to={`/angebote/${String(angebot.id)}/bearbeiten`}
          symbol={<IconPencil size={SYMBOL_TASTE} stroke={1.8} />}
        >
          Bearbeiten
        </WeicheTaste>
        {schreibbar(angebot) ? (
          <WeicheTaste
            onClick={() => {
              setzeWahlOffen(true);
            }}
            disabled={laeuft}
            symbol={<IconFilePlus size={SYMBOL_TASTE} stroke={1.8} />}
          >
            Rechnung schreiben
          </WeicheTaste>
        ) : null}
        {angebot.status === 'ANGELEGT' ? null : (
          <WeicheTaste
            onClick={() => {
              void schalten(angebotStatusZurueck, angebot);
            }}
            disabled={laeuft}
            symbol={<IconArrowLeft size={SYMBOL_TASTE} stroke={1.8} />}
          >
            Status zurück
          </WeicheTaste>
        )}
        {angebot.status === 'ABGERECHNET' ? null : (
          <KupferTaste
            onClick={() => {
              void schalten(angebotStatusWeiter, angebot);
            }}
            disabled={laeuft}
            symbol={<IconArrowRight size={SYMBOL_TASTE} stroke={1.8} />}
          >
            Status weiter
          </KupferTaste>
        )}
      </Box>
    );
  }

  /** Die Karte mit Ueberschrift, Angaben und Aktionen, darunter Beschreibung und Positionen. */
  function inhaltZu(angebot: Angebot): ReactNode {
    // Der Stand je Position, aber nur wo es Rechnungen gibt: Ohne sie fehlen die zwei Spalten.
    const staende =
      abrechnung.art === 'daten' && abrechnung.abrechnung.rechnungen.length > 0
        ? new Map(
            abrechnung.abrechnung.positionen.map((position) => [
              position.angebotPositionId,
              position,
            ]),
          )
        : null;
    return (
      <>
        <Karte titel={ueberschriftZu(angebot)} titelEbene={1} werkzeug={aktionenZu(angebot)}>
          <Angaben angebot={angebot} />
        </Karte>
        {wahlOffen ? wahlZu(angebot) : null}
        {angebot.beschreibung === null ? null : (
          <Karte titel="Beschreibung">
            <Typography sx={{ fontSize: 13.5, whiteSpace: 'pre-wrap' }}>
              {angebot.beschreibung}
            </Typography>
          </Karte>
        )}
        <Karte titel="Positionen" anzahl={angebot.positionen.length}>
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
            {angebot.positionen.length === 0 ? (
              <Typography
                role="status"
                sx={(theme) => ({
                  fontSize: 12.5,
                  color: theme.vars.palette.kupferwolke.textMatt,
                })}
              >
                {OHNE_POSITION}
              </Typography>
            ) : (
              <Tafel
                beschriftung="Positionen"
                spalten={staende === null ? [...SPALTEN] : [...SPALTEN_MIT_STAND]}
              >
                {angebot.positionen.map((position) => (
                  // Die Kennung ist der Schluessel: Seit Issue #171 traegt jede Position eine
                  // eigene und bleibt ueber ein Speichern hinweg dieselbe. Die Reihenfolge der
                  // Liste bleibt die gezeigte (E24) — sie ordnet, sie benennt nicht mehr.
                  <Positionszeile
                    key={position.id}
                    position={position}
                    stand={staende?.get(position.id)}
                  />
                ))}
              </Tafel>
            )}
            <Box
              sx={(theme) => ({
                display: 'flex',
                alignItems: 'baseline',
                gap: '10px',
                flexWrap: 'wrap',
                paddingTop: '14px',
                borderTop: `1px solid ${theme.vars.palette.kupferwolke.linie}`,
              })}
            >
              <Typography sx={{ fontSize: 13.5, fontWeight: 600 }}>Summe</Typography>
              <Typography
                data-testid="angebot-summe"
                className={ZAHLEN_KLASSE}
                sx={{ fontSize: 17, fontWeight: 800 }}
              >
                {euro(angebot.summeInCent)}
              </Typography>
              <Typography
                sx={(theme) => ({
                  fontSize: 12.5,
                  marginLeft: 'auto',
                  color: theme.vars.palette.kupferwolke.textSchwach,
                })}
              >
                {NETTO}
              </Typography>
            </Box>
          </Box>
        </Karte>
        <Karte
          titel="Rechnungen"
          anzahl={abrechnung.art === 'daten' ? abrechnung.abrechnung.rechnungen.length : undefined}
        >
          {rechnungenInhalt(abrechnung)}
        </Karte>
        <Anlagen angebotId={angebot.id} />
        <Kommentare angebotId={angebot.id} />
      </>
    );
  }

  let inhalt: ReactNode;
  if (stand.art === 'daten') {
    inhalt = inhaltZu(stand.angebot);
  } else if (stand.art === 'laedt') {
    inhalt = (
      <Karte>
        <Typography
          sx={(theme) => ({
            fontSize: 12.5,
            color: theme.vars.palette.kupferwolke.textSchwach,
          })}
        >
          {LAEDT}
        </Typography>
      </Karte>
    );
  } else {
    inhalt = (
      <Karte>
        <Alert severity="error">{stand.art === 'unbekannt' ? NICHT_GEFUNDEN : AUSFALL}</Alert>
      </Karte>
    );
  }

  return (
    <Box
      sx={{
        display: 'flex',
        flexDirection: 'column',
        // Aussenabstand und Spalt bringt der Rahmen mit; hier bleibt nur der Abstand zwischen
        // den Bereichen der Buehne (CLAUDE-design.md, „Rahmen").
        gap: '22px',
      }}
    >
      {meldung === null ? null : <Alert severity="error">{meldung}</Alert>}
      {inhalt}
    </Box>
  );
}
