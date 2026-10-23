import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { IconChevronLeft, IconChevronRight, IconClockPlus, IconTrash } from '@tabler/icons-react';
import { Fragment, useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { useSearchParams } from 'react-router-dom';

import { ALLE, arbeitszeitMonat, personen, zeiteintragLoeschen } from '../api/arbeitszeit';
import type {
  Arbeitsmonat,
  Arbeitstag,
  Person,
  Personenauswahl,
  Zeitzeile,
} from '../api/arbeitszeit';
import { useRechte } from '../auth/rechte';
import AktionsMenue from '../components/AktionsMenue';
import InternChip from '../components/InternChip';
import Karte from '../components/Karte';
import { useKopfPfad } from '../components/KopfPfad';
import type { PfadVerweis } from '../components/KopfPfad';
import KupferTaste from '../components/KupferTaste';
import Tafel, { type TafelSpalte } from '../components/Tafel';
import { rundeIcontaste } from '../components/rundeIcontaste';
import ZeiteintragMaske from '../components/ZeiteintragMaske';
import {
  alsMonat,
  folgemonat,
  monatWort,
  stundenWort,
  vormonat,
  zeitspanneWort,
} from '../lib/arbeitszeit';
import { tagWort } from '../lib/tag';
import { ABSTAND_BUEHNE, ZAHLEN_KLASSE } from '../theme';

/**
 * Die Ansicht „Arbeitszeit": die Eintraege eines Monats nach Tagen, mit Summen (Issue #193,
 * Kriterium 5; Plan #194, A13, A20).
 *
 * Aufbau wie die Liste der Rechnungen: eine {@link Karte} mit der Hauptaktion im Kopf und einer
 * {@link Tafel} darunter. Sortiert und summiert wird am Server — die Tage stehen in der Reihenfolge
 * der Antwort, und keine Zahl dieser Ansicht entsteht hier.
 *
 * <b>Der Monat steht in der Adresse</b> (`?monat=JJJJ-MM`): Er ist teilbar, uebersteht das
 * Neuladen und ist der Zustand, den „zurueck" zuruecknehmen soll (CLAUDE-react.md, „State
 * Management"). <b>Fehlt er oder ist er keiner, fragt die Ansicht ohne Parameter</b> — welcher
 * Monat der laufende ist, entscheidet der Server an seiner Uhr in der Geschaeftszone (E4). Ein hier
 * gerechneter Monat waere ein zweiter Wahrheitsort daneben und in einem Browser mit anderer Zone der
 * falsche. Der Monat, den die Ansicht zeigt, kommt darum notfalls aus der Antwort; die Tasten fuer
 * Vor- und Folgemonat entstehen erst, wenn er bekannt ist — ohne ihn gibt es keinen Nachbarn.
 *
 * <b>Die Gruppenzeilen baut diese Ansicht aus Zeilen</b> und nicht die {@link Tafel}: Ein Tag ist
 * eine Zeile mit seinem Namen und seiner Summe, danach kommen seine Eintraege. Die Tafel bleibt
 * damit, was sie ist — eine Tabelle, die fertige Zeilen annimmt.
 *
 * <b>„Zeit erfassen" ist die eine Kupfertaste</b> der Ansicht (A14); sie oeffnet die
 * {@link ZeiteintragMaske}. Zum Aendern ist die Zeitspanne der Zeile eine <b>Textschaltflaeche</b>,
 * und Loeschen steht im ⋯-Menue der Zeile — <b>die Zeile selbst ist nicht klickbar</b> (Konvention
 * aus {@link RechnungenPage} und {@link FirmenPage}): Je Zeile gibt es genau diese zwei
 * fokussierbaren Elemente, und beide sind mit der Tastatur zu erreichen.
 *
 * <b>Wessen Zeiten die Ansicht zeigt, steht ebenso in der Adresse</b> (`?person=`, Plan #426):
 * die Kennung eines Kontos oder `alle`, aus demselben Grund wie der Monat. <b>Fehlt die Auswahl,
 * fragt die Ansicht ohne sie</b>, und die Vorauswahl aus der Rolle trifft der Server (E10) — ReadOnly
 * steht dann auf „Alle", C-Level und Employee auf den eigenen Zeiten. Der Umschalter zeigt die
 * Auswahl, die die Antwort nennt, und die Spalte „Person" erscheint nur bei „Alle" (E14). Einen
 * Umschalter gibt es nur, wo die Rolle die Zeiten aller sehen darf: Ein Employee sieht nur seine
 * eigenen, und eine Wahl, die der Server ohnehin uebergeht, waere ein leeres Versprechen.
 *
 * <b>Die Rolle fragt die Ansicht ueber {@link useRechte}</b> und damit ueber die Sitzung des
 * `AuthContext` (E17) — kein eigener Abruf von `GET /api/auth/me`. Die Sperre selbst liegt im
 * Server; die Ansicht zeigt nur, was die Rolle darf.
 *
 * <b>Das Aendern beruht auf dem Stand der Zeile</b> (#411, Kriterien 1 bis 3; Plan #415, E2): Die
 * Monatsliste traegt ihn je Eintrag, der Dialog legt ihn vor und zeigt eine Abweisung samt „Neu
 * laden" und den eigenen Eingaben selbst. Holt er dabei einen neuen Stand, laedt auch die Liste
 * dahinter neu.
 *
 * <b>Nach jeder Schreibung laedt der Monat neu</b> (`runde`), statt die Liste hier
 * fortzuschreiben: Tagessummen und Monatssumme rechnet der Server, und ein Eintrag kann den Monat
 * wechseln. Eine hier nachgezogene Liste waere eine zweite Wahrheit ueber dieselben Zahlen.
 */

/** Die Ansicht ist die erste Stufe des Pfades — ueber ihr steht nichts (E6). */
const KEIN_WEG: readonly PfadVerweis[] = [];

/** Der Name des Parameters, unter dem der Monat in der Adresse steht (A13). */
const PARAM_MONAT = 'monat';

/** Der Name des Parameters, unter dem die Auswahl der Person in der Adresse steht (E10). */
const PARAM_PERSON = 'person';

/** Der zugaengliche Name des Umschalters — und die Ueberschrift der Spalte bei „Alle". */
const PERSON = 'Person';

/** Was im Umschalter fuer alle Personen steht. */
const ALLE_WORT = 'Alle';

const LAEDT = 'Die Arbeitszeit wird geladen …';
const AUSFALL = 'Die Arbeitszeit ist gerade nicht zu erreichen. Bitte später erneut versuchen.';
const LEER = 'Noch keine Arbeitszeit in diesem Monat. Erfassen Sie die erste über „Zeit erfassen".';
/** Derselbe Satz ohne Einladung — wer nicht schreiben darf, findet „Zeit erfassen" nicht vor. */
const LEER_LESEND = 'Noch keine Arbeitszeit in diesem Monat.';

const LOESCHEN_FRAGE = 'Der Eintrag wird gelöscht. Das lässt sich nicht zurücknehmen.';
const AUSFALL_LOESCHEN = 'Der Eintrag wurde nicht gelöscht. Bitte später erneut versuchen.';

const SPALTEN: readonly TafelSpalte[] = [
  'Zeit',
  { beschriftung: 'Dauer', zahl: true },
  'Position',
  'Aktionen',
];

/** Dieselben Spalten bei „Alle" — die Person steht vor den Aktionen (E14). */
const SPALTEN_ALLE: readonly TafelSpalte[] = [
  'Zeit',
  { beschriftung: 'Dauer', zahl: true },
  'Position',
  PERSON,
  'Aktionen',
];

/** Die Symbolgroesse in den Tasten (wie in {@link RechnungenPage}). */
const SYMBOL_TASTE = 16;

/** Die Symbolgroesse in den Menueeintraegen (wie in {@link Kommentare}). */
const SYMBOL_MENUE = 17;

/**
 * Die Gestalt einer Zelle, die eine Gruppe oder die Summe benennt.
 *
 * Das Polster steht hier und nicht in der {@link Tafel}: Die polstert ihre `td` ueber einen
 * Nachfahren-Selektor, und eine Kopfzelle im Rumpf faellt nicht darunter.
 */
const GRUPPENZELLE = {
  textAlign: 'left',
  padding: '12px 14px',
  fontSize: 12.5,
  fontWeight: 600,
  whiteSpace: 'nowrap',
} as const;

/** Der offene Dialog: eine neue Buchung, oder die Zeile, die geaendert wird (A14). */
type Maske = { readonly art: 'neu' } | { readonly art: 'aendern'; readonly zeile: Zeitzeile };

/** Was die Ansicht gerade weiss. */
type Stand =
  | { readonly art: 'laedt' }
  | { readonly art: 'daten'; readonly monatsliste: Arbeitsmonat }
  | { readonly art: 'fehler' };

/**
 * Die Auswahl der Adresse, oder `null`, wo sie keine nennt — dann fragt die Ansicht ohne und der
 * Server waehlt aus der Rolle (E10). Dieselbe Form, die {@link arbeitszeitMonat} hinausschickt.
 */
function alsAuswahl(wert: string | null): Personenauswahl | null {
  if (wert === ALLE) {
    return ALLE;
  }
  return wert !== null && /^\d+$/.test(wert) ? Number(wert) : null;
}

/** Holt den Monat und macht auch aus dem Fehlschlag einen Stand. */
async function laden(monat: string | null, auswahl: Personenauswahl | null): Promise<Stand> {
  try {
    return {
      art: 'daten',
      monatsliste: await arbeitszeitMonat(monat ?? undefined, auswahl ?? undefined),
    };
  } catch {
    return { art: 'fehler' };
  }
}

/**
 * Eine Icontaste des Monatswechsels.
 *
 * Ein Schalter und kein Link: Der Wechsel schreibt denselben Weg mit anderem Parameter, und
 * `setzeParameter` schiebt ihn in die Geschichte — „zurueck" nimmt ihn damit zurueck.
 */
function Monatstaste({
  name,
  symbol,
  onKlick,
}: {
  readonly name: string;
  readonly symbol: ReactNode;
  readonly onKlick: () => void;
}) {
  return (
    <Box
      component="button"
      type="button"
      aria-label={name}
      onClick={onKlick}
      sx={rundeIcontaste}
    >
      {symbol}
    </Box>
  );
}

/** Der Waehler im Kartenkopf: zurueck, der Name des Monats, vor. */
function Monatswechsel({
  monat,
  waehlen,
}: {
  readonly monat: string;
  readonly waehlen: (neu: string) => void;
}) {
  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
      <Monatstaste
        name="Vorheriger Monat"
        symbol={<IconChevronLeft size={18} stroke={1.8} aria-hidden />}
        onKlick={() => {
          waehlen(vormonat(monat));
        }}
      />
      <Typography
        component="span"
        sx={{ fontSize: 13.5, fontWeight: 600, minWidth: 128, textAlign: 'center' }}
      >
        {monatWort(monat)}
      </Typography>
      <Monatstaste
        name="Nächster Monat"
        symbol={<IconChevronRight size={18} stroke={1.8} aria-hidden />}
        onKlick={() => {
          waehlen(folgemonat(monat));
        }}
      />
    </Box>
  );
}

/**
 * Der Umschalter im Kartenkopf: „Alle" und die Personen, die der Server nennt (E11).
 *
 * Ein natives Select wie {@link Monatswahl}. Stillgelegte Personen stehen mit zur Wahl — ihre
 * Zeiten bleiben sichtbar (Kriterium 9 von #410). Gibt es nur eine Person, steht nur sie da: „Alle"
 * hiesse dann dasselbe noch einmal. Nennt die Antwort trotzdem „Alle", steht es zur Wahl, damit der
 * Umschalter zeigt, was gilt.
 */
function Personenumschalter({
  liste,
  auswahl,
  waehlen,
}: {
  readonly liste: readonly Person[];
  readonly auswahl: Personenauswahl | null;
  readonly waehlen: (neu: string) => void;
}) {
  return (
    <TextField
      select
      size="small"
      label={PERSON}
      value={auswahl === null ? '' : String(auswahl)}
      onChange={(ereignis) => {
        waehlen(ereignis.target.value);
      }}
      slotProps={{ select: { native: true }, inputLabel: { shrink: true } }}
      sx={{ minWidth: 180 }}
    >
      {liste.length > 1 || auswahl === ALLE ? <option value={ALLE}>{ALLE_WORT}</option> : null}
      {liste.map((person) => (
        <option key={person.id} value={String(person.id)}>
          {person.name}
        </option>
      ))}
    </TextField>
  );
}

/**
 * Die Zeile, die einen Tag eroeffnet: sein Datum und seine Summe (A20).
 *
 * Das Datum ist eine <b>Kopfzelle der Zeile</b> (`th scope="row"`) und keine Kopfzelle einer
 * Gruppe: Es benennt die Summe in derselben Zeile, nicht die Eintraege darunter. Der Screenreader
 * liest damit „12.11.2026 — 3,75 Std." und nicht einen Tag, der fuer alles Folgende gelten soll.
 * Das Polster steht hier, weil die {@link Tafel} nur ihre `td` polstert.
 */
function Tagesgruppe({
  arbeitstag,
  mitPerson,
}: {
  readonly arbeitstag: Arbeitstag;
  readonly mitPerson: boolean;
}) {
  return (
    <Box component="tr">
      <Box
        component="th"
        scope="row"
        className={ZAHLEN_KLASSE}
        sx={(theme) => ({
          ...GRUPPENZELLE,
          color: theme.vars.palette.kupferwolke.textSchwach,
        })}
      >
        {tagWort(arbeitstag.tag)}
      </Box>
      <Box
        component="td"
        className={ZAHLEN_KLASSE}
        sx={(theme) => ({
          ...GRUPPENZELLE,
          textAlign: 'right',
          color: theme.vars.palette.kupferwolke.textSchwach,
        })}
      >
        {stundenWort(arbeitstag.stundenInHundertsteln)}
      </Box>
      <Box component="td" />
      {mitPerson ? <Box component="td" /> : null}
      <Box component="td" />
    </Box>
  );
}

/**
 * Die Aufteilung der Monatssumme in Kundenzeit und eigene Zeit (Issue #236, Kriterium 10).
 *
 * <b>Eine Zeile, nicht zwei weitere Summen</b>: Das Kriterium nennt die Aufteilung als eine Angabe
 * unter der Summe. Zwei eigene Summenzeilen liessen den Fuss der Tafel nach drei Summen aussehen.
 *
 * <b>Hier entsteht keine Zahl.</b> Beide Werte kommen vom Server ({@code Arbeitsmonat}, Issue #230)
 * — die Ansicht rechnet weder die Teile aus den Zeilen noch den einen Teil aus dem anderen.
 */
function Aufteilung({
  monatsliste,
  spalten,
}: {
  readonly monatsliste: Arbeitsmonat;
  readonly spalten: number;
}) {
  return (
    <Box component="tr">
      <Box
        component="td"
        colSpan={spalten}
        sx={(theme) => ({
          ...GRUPPENZELLE,
          fontWeight: 400,
          color: theme.vars.palette.kupferwolke.textSchwach,
        })}
      >
        {`davon für Kunden ${stundenWort(monatsliste.stundenFuerKundenInHundertsteln)}, intern ${stundenWort(monatsliste.stundenInternInHundertsteln)}`}
      </Box>
    </Box>
  );
}

/** Die zwei Wege einer Zeile, die etwas aendern. */
interface ZeilenAktionen {
  readonly onAendern: () => void;
  readonly onLoeschen: () => void;
}

/**
 * Ein Eintrag: Zeitspanne, Dauer, Position mit ihrer Firma, ⋯-Menue (A14).
 *
 * Die Zeitspanne ist eine <b>Textschaltflaeche</b> und kein Link: Sie oeffnet einen Dialog und
 * wechselt keinen Weg — ein `a` ohne Ziel waere ein Versprechen, das es nicht haelt. Ihre Gestalt
 * ist die der Zeilenverweise der anderen Tafeln (Kupfer im Hover), ihre Rolle die eines Schalters.
 *
 * Ohne Schreibrecht ({@link ZeilenAktionen} fehlt) steht die Zeitspanne als Text da und das
 * ⋯-Menue fehlt (Kriterium 10 von #409): Beide fuehrten nur zu einer Aenderung.
 */
function Zeile({
  zeile,
  mitPerson,
  aktionen,
}: {
  readonly zeile: Zeitzeile;
  readonly mitPerson: boolean;
  readonly aktionen: ZeilenAktionen | null;
}) {
  const wort = `${tagWort(zeile.tag)}, ${zeitspanneWort(zeile.von, zeile.bis)}`;
  return (
    <Box component="tr">
      <Box
        component="td"
        className={ZAHLEN_KLASSE}
        sx={{ fontWeight: 500, whiteSpace: 'nowrap' }}
      >
        {aktionen === null ? (
          zeitspanneWort(zeile.von, zeile.bis)
        ) : (
          <Box
            component="button"
            type="button"
            onClick={aktionen.onAendern}
            sx={(theme) => ({
              padding: 0,
              border: 0,
              background: 'transparent',
              cursor: 'pointer',
              font: 'inherit',
              color: 'inherit',
              '&:hover': { color: theme.vars.palette.kupferwolke.kupfer },
            })}
          >
            {zeitspanneWort(zeile.von, zeile.bis)}
          </Box>
        )}
      </Box>
      <Box
        component="td"
        className={ZAHLEN_KLASSE}
        sx={{ fontWeight: 600, whiteSpace: 'nowrap', textAlign: 'right' }}
      >
        {stundenWort(zeile.stundenInHundertsteln)}
      </Box>
      <Box component="td">
        {zeile.position.bezeichnung}
        <Typography
          component="span"
          sx={(theme) => ({
            display: 'block',
            fontSize: 12.5,
            color: theme.vars.palette.kupferwolke.textSchwach,
          })}
        >
          {zeile.position.firmaName}
          {zeile.position.intern ? <InternChip /> : null}
        </Typography>
      </Box>
      {mitPerson ? <Box component="td">{zeile.person.name}</Box> : null}
      <Box component="td" sx={{ textAlign: 'right' }}>
        {aktionen === null ? null : (
          <AktionsMenue
            name={`Aktionen für ${wort}`}
            objekt={wort}
            eintraege={[
              {
                titel: 'Löschen',
                symbol: <IconTrash size={SYMBOL_MENUE} stroke={1.8} />,
                rueckfrage: LOESCHEN_FRAGE,
                onAuswahl: aktionen.onLoeschen,
              },
            ]}
          />
        )}
      </Box>
    </Box>
  );
}

/** Die abschliessende Zeile der Tafel: die Summe des Monats (A20). */
function Monatssumme({
  monatsliste,
  mitPerson,
}: {
  readonly monatsliste: Arbeitsmonat;
  readonly mitPerson: boolean;
}) {
  return (
    <Box component="tr">
      <Box component="th" scope="row" sx={{ ...GRUPPENZELLE, fontWeight: 700 }}>
        {`Summe ${monatWort(monatsliste.monat)}`}
      </Box>
      <Box
        component="td"
        className={ZAHLEN_KLASSE}
        sx={{ fontWeight: 700, whiteSpace: 'nowrap', textAlign: 'right' }}
      >
        {stundenWort(monatsliste.stundenInHundertsteln)}
      </Box>
      <Box component="td" />
      {mitPerson ? <Box component="td" /> : null}
      <Box component="td" />
    </Box>
  );
}

/**
 * Was in der Karte steht: Ladehinweis, Meldung, Einladung oder die Tafel.
 *
 * `schreibend` ist `null`, wo die Rolle nicht schreiben darf — dann gibt es weder Einladung noch
 * einen Weg zum Aendern oder Loeschen.
 */
function inhaltZu(
  stand: Stand,
  schreibend: {
    readonly oeffneMaske: (maske: Maske) => void;
    readonly loesche: (zeile: Zeitzeile) => void;
  } | null,
): ReactNode {
  if (stand.art === 'laedt') {
    return (
      <Typography
        sx={(theme) => ({ fontSize: 12.5, color: theme.vars.palette.kupferwolke.textSchwach })}
      >
        {LAEDT}
      </Typography>
    );
  }
  if (stand.art === 'fehler') {
    return <Alert severity="error">{AUSFALL}</Alert>;
  }
  if (stand.monatsliste.tage.length === 0) {
    return (
      <Typography
        role="status"
        sx={(theme) => ({ fontSize: 12.5, color: theme.vars.palette.kupferwolke.textMatt })}
      >
        {schreibend === null ? LEER_LESEND : LEER}
      </Typography>
    );
  }
  // Die Person je Zeile nur bei „Alle" — sonst ist es in jeder Zeile dieselbe (E14).
  const mitPerson = stand.monatsliste.auswahl === ALLE;
  const spalten = mitPerson ? SPALTEN_ALLE : SPALTEN;
  return (
    <Tafel beschriftung={`Arbeitszeit ${monatWort(stand.monatsliste.monat)}`} spalten={spalten}>
      {stand.monatsliste.tage.map((arbeitstag) => (
        <Fragment key={arbeitstag.tag}>
          <Tagesgruppe arbeitstag={arbeitstag} mitPerson={mitPerson} />
          {arbeitstag.eintraege.map((zeile) => (
            <Zeile
              key={zeile.id}
              zeile={zeile}
              mitPerson={mitPerson}
              aktionen={
                schreibend === null
                  ? null
                  : {
                      onAendern: () => {
                        schreibend.oeffneMaske({ art: 'aendern', zeile });
                      },
                      onLoeschen: () => {
                        schreibend.loesche(zeile);
                      },
                    }
              }
            />
          ))}
        </Fragment>
      ))}
      <Monatssumme monatsliste={stand.monatsliste} mitPerson={mitPerson} />
      <Aufteilung monatsliste={stand.monatsliste} spalten={spalten.length} />
    </Tafel>
  );
}

export default function ArbeitszeitPage() {
  const { darfFachlichSchreiben, darfZeitenAllerSehen } = useRechte();
  useKopfPfad(KEIN_WEG, 'Arbeitszeit');
  const [parameter, setzeParameter] = useSearchParams();
  const monat = alsMonat(parameter.get(PARAM_MONAT));
  const auswahl = alsAuswahl(parameter.get(PARAM_PERSON));
  const [stand, setzeStand] = useState<Stand>({ art: 'laedt' });
  /** Die Personen des Umschalters; `null`, solange sie fehlen oder nicht zu erreichen sind. */
  const [personenliste, setzePersonenliste] = useState<readonly Person[] | null>(null);
  const [maske, setzeMaske] = useState<Maske | null>(null);
  const [loeschfehler, setzeLoeschfehler] = useState<string | null>(null);
  /** Jede Schreibung zaehlt eine Runde weiter — der Effekt holt den Monat dann erneut. */
  const [runde, setzeRunde] = useState(0);

  useEffect(() => {
    let gueltig = true;
    setzeStand({ art: 'laedt' });
    void laden(monat, auswahl).then((neu) => {
      // Ein juengerer Monat hat diesen Lauf abgeloest; seine Antwort ist die richtige.
      if (gueltig) {
        setzeStand(neu);
      }
    });
    return () => {
      gueltig = false;
    };
  }, [monat, auswahl, runde]);

  useEffect(() => {
    // Ein Employee bekommt keinen Umschalter und fragt darum auch keine Personen.
    if (!darfZeitenAllerSehen) {
      return undefined;
    }
    let gueltig = true;
    personen().then(
      (liste) => {
        if (gueltig) {
          setzePersonenliste(liste);
        }
      },
      () => {
        // Ohne Personen kein Umschalter — die Liste der gewaehlten Auswahl steht trotzdem da.
      },
    );
    return () => {
      gueltig = false;
    };
  }, [darfZeitenAllerSehen]);

  /**
   * Schreibt einen Parameter in die Adresse und laesst den anderen stehen. Geschoben statt
   * ersetzt: Monat und Person zu wechseln sind Handlungen, die „zurueck" zuruecknehmen soll.
   */
  const setze = (name: string, wert: string) => {
    const neu = new URLSearchParams(parameter);
    neu.set(name, wert);
    setzeParameter(neu);
  };
  const neuLaden = () => {
    setzeLoeschfehler(null);
    setzeRunde((alt) => alt + 1);
  };

  const loeschen = async (zeile: Zeitzeile) => {
    try {
      await zeiteintragLoeschen(zeile.id);
      neuLaden();
    } catch {
      // Ein eigener Satz und nicht die Meldung des Servers: Das Loeschen hat keine fachliche
      // Abweisung, die er begruenden koennte — was hier ankommt, ist ein Ausfall (wie
      // {@link Kommentare}).
      setzeLoeschfehler(AUSFALL_LOESCHEN);
    }
  };

  // Der Monat der Adresse, solange sie einen nennt; sonst der, den die Antwort nennt (E4).
  const gezeigterMonat = monat ?? (stand.art === 'daten' ? stand.monatsliste.monat : null);
  // Die Auswahl, die die Antwort nennt — sie gilt, auch wo der Server sie getroffen hat (E10).
  const gezeigteAuswahl = stand.art === 'daten' ? stand.monatsliste.auswahl : auswahl;

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: ABSTAND_BUEHNE }}>
      <Karte
        titel="Arbeitszeit"
        titelEbene={1}
        werkzeug={
          <>
            {!darfZeitenAllerSehen || personenliste === null ? null : (
              <Personenumschalter
                liste={personenliste}
                auswahl={gezeigteAuswahl}
                waehlen={(neu) => {
                  setze(PARAM_PERSON, neu);
                }}
              />
            )}
            {gezeigterMonat === null ? null : (
              <Monatswechsel
                monat={gezeigterMonat}
                waehlen={(neu) => {
                  setze(PARAM_MONAT, neu);
                }}
              />
            )}
            {darfFachlichSchreiben ? (
              <KupferTaste
                onClick={() => {
                  setzeMaske({ art: 'neu' });
                }}
                symbol={<IconClockPlus size={SYMBOL_TASTE} stroke={1.8} />}
              >
                Zeit erfassen
              </KupferTaste>
            ) : null}
          </>
        }
      >
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
          {loeschfehler === null ? null : <Alert severity="error">{loeschfehler}</Alert>}
          {inhaltZu(
            stand,
            darfFachlichSchreiben
              ? {
                  oeffneMaske: setzeMaske,
                  loesche: (zeile) => {
                    void loeschen(zeile);
                  },
                }
              : null,
          )}
        </Box>
      </Karte>
      {maske === null ? null : (
        <ZeiteintragMaske
          zeile={maske.art === 'neu' ? null : maske.zeile}
          auswahl={gezeigteAuswahl}
          onGespeichert={() => {
            setzeMaske(null);
            neuLaden();
          }}
          onNeuGeladen={neuLaden}
          onSchliessen={() => {
            setzeMaske(null);
          }}
        />
      )}
    </Box>
  );
}
