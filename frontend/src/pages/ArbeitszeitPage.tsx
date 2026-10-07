import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import { IconChevronLeft, IconChevronRight, IconClockPlus, IconTrash } from '@tabler/icons-react';
import { Fragment, useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { useSearchParams } from 'react-router-dom';

import { arbeitszeitMonat, zeiteintragLoeschen } from '../api/arbeitszeit';
import type { Arbeitsmonat, Arbeitstag, Zeitzeile } from '../api/arbeitszeit';
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
import { ZAHLEN_KLASSE } from '../theme';

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
 * <b>Nach jeder Schreibung laedt der Monat neu</b> (`runde`), statt die Liste hier
 * fortzuschreiben: Tagessummen und Monatssumme rechnet der Server, und ein Eintrag kann den Monat
 * wechseln. Eine hier nachgezogene Liste waere eine zweite Wahrheit ueber dieselben Zahlen.
 */

/** Die Ansicht ist die erste Stufe des Pfades — ueber ihr steht nichts (E6). */
const KEIN_WEG: readonly PfadVerweis[] = [];

/** Der Name des Parameters, unter dem der Monat in der Adresse steht (A13). */
const PARAM_MONAT = 'monat';

const LAEDT = 'Die Arbeitszeit wird geladen …';
const AUSFALL = 'Die Arbeitszeit ist gerade nicht zu erreichen. Bitte später erneut versuchen.';
const LEER = 'Noch keine Arbeitszeit in diesem Monat. Erfassen Sie die erste über „Zeit erfassen".';

const LOESCHEN_FRAGE = 'Der Eintrag wird gelöscht. Das lässt sich nicht zurücknehmen.';
const AUSFALL_LOESCHEN = 'Der Eintrag wurde nicht gelöscht. Bitte später erneut versuchen.';

const SPALTEN: readonly TafelSpalte[] = [
  'Zeit',
  { beschriftung: 'Dauer', zahl: true },
  'Position',
  'Aktionen',
];

/** Die Symbolgroesse in den Tasten (wie in {@link RechnungenPage}). */
const SYMBOL_TASTE = 16;

/** Kantenlaenge der Icontaste (CLAUDE-design.md, „Tasten": Kreis 40 px). */

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

/** Holt den Monat und macht auch aus dem Fehlschlag einen Stand. */
async function laden(monat: string | null): Promise<Stand> {
  try {
    return {
      art: 'daten',
      monatsliste: await arbeitszeitMonat(monat ?? undefined),
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
 * Die Zeile, die einen Tag eroeffnet: sein Datum und seine Summe (A20).
 *
 * Das Datum ist eine <b>Kopfzelle der Zeile</b> (`th scope="row"`) und keine Kopfzelle einer
 * Gruppe: Es benennt die Summe in derselben Zeile, nicht die Eintraege darunter. Der Screenreader
 * liest damit „12.11.2026 — 3,75 Std." und nicht einen Tag, der fuer alles Folgende gelten soll.
 * Das Polster steht hier, weil die {@link Tafel} nur ihre `td` polstert.
 */
function Tagesgruppe({ arbeitstag }: { readonly arbeitstag: Arbeitstag }) {
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
function Aufteilung({ monatsliste }: { readonly monatsliste: Arbeitsmonat }) {
  return (
    <Box component="tr">
      <Box
        component="td"
        colSpan={SPALTEN.length}
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

/**
 * Ein Eintrag: Zeitspanne, Dauer, Position mit ihrer Firma, ⋯-Menue (A14).
 *
 * Die Zeitspanne ist eine <b>Textschaltflaeche</b> und kein Link: Sie oeffnet einen Dialog und
 * wechselt keinen Weg — ein `a` ohne Ziel waere ein Versprechen, das es nicht haelt. Ihre Gestalt
 * ist die der Zeilenverweise der anderen Tafeln (Kupfer im Hover), ihre Rolle die eines Schalters.
 */
function Zeile({
  zeile,
  onAendern,
  onLoeschen,
}: {
  readonly zeile: Zeitzeile;
  readonly onAendern: () => void;
  readonly onLoeschen: () => void;
}) {
  const wort = `${tagWort(zeile.tag)}, ${zeitspanneWort(zeile.von, zeile.bis)}`;
  return (
    <Box component="tr">
      <Box
        component="td"
        className={ZAHLEN_KLASSE}
        sx={{ fontWeight: 500, whiteSpace: 'nowrap' }}
      >
        <Box
          component="button"
          type="button"
          onClick={onAendern}
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
      <Box component="td" sx={{ textAlign: 'right' }}>
        <AktionsMenue
          name={`Aktionen für ${wort}`}
          objekt={wort}
          eintraege={[
            {
              titel: 'Löschen',
              symbol: <IconTrash size={SYMBOL_MENUE} stroke={1.8} />,
              rueckfrage: LOESCHEN_FRAGE,
              onAuswahl: onLoeschen,
            },
          ]}
        />
      </Box>
    </Box>
  );
}

/** Die abschliessende Zeile der Tafel: die Summe des Monats (A20). */
function Monatssumme({ monatsliste }: { readonly monatsliste: Arbeitsmonat }) {
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
      <Box component="td" />
    </Box>
  );
}

/** Was in der Karte steht: Ladehinweis, Meldung, Einladung oder die Tafel. */
function inhaltZu(
  stand: Stand,
  oeffneMaske: (maske: Maske) => void,
  loesche: (zeile: Zeitzeile) => void,
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
        {LEER}
      </Typography>
    );
  }
  return (
    <Tafel beschriftung={`Arbeitszeit ${monatWort(stand.monatsliste.monat)}`} spalten={SPALTEN}>
      {stand.monatsliste.tage.map((arbeitstag) => (
        <Fragment key={arbeitstag.tag}>
          <Tagesgruppe arbeitstag={arbeitstag} />
          {arbeitstag.eintraege.map((zeile) => (
            <Zeile
              key={zeile.id}
              zeile={zeile}
              onAendern={() => {
                oeffneMaske({ art: 'aendern', zeile });
              }}
              onLoeschen={() => {
                loesche(zeile);
              }}
            />
          ))}
        </Fragment>
      ))}
      <Monatssumme monatsliste={stand.monatsliste} />
      <Aufteilung monatsliste={stand.monatsliste} />
    </Tafel>
  );
}

export default function ArbeitszeitPage() {
  useKopfPfad(KEIN_WEG, 'Arbeitszeit');
  const [parameter, setzeParameter] = useSearchParams();
  const monat = alsMonat(parameter.get(PARAM_MONAT));
  const [stand, setzeStand] = useState<Stand>({ art: 'laedt' });
  const [maske, setzeMaske] = useState<Maske | null>(null);
  const [loeschfehler, setzeLoeschfehler] = useState<string | null>(null);
  /** Jede Schreibung zaehlt eine Runde weiter — der Effekt holt den Monat dann erneut. */
  const [runde, setzeRunde] = useState(0);

  useEffect(() => {
    let gueltig = true;
    setzeStand({ art: 'laedt' });
    void laden(monat).then((neu) => {
      // Ein juengerer Monat hat diesen Lauf abgeloest; seine Antwort ist die richtige.
      if (gueltig) {
        setzeStand(neu);
      }
    });
    return () => {
      gueltig = false;
    };
  }, [monat, runde]);

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

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: '22px' }}>
      <Karte
        titel="Arbeitszeit"
        titelEbene={1}
        werkzeug={
          <>
            {gezeigterMonat === null ? null : (
              <Monatswechsel
                monat={gezeigterMonat}
                waehlen={(neu) => {
                  // Geschoben statt ersetzt: Der Monatswechsel ist eine Handlung, die „zurueck"
                  // zuruecknehmen koennen soll.
                  setzeParameter({ [PARAM_MONAT]: neu });
                }}
              />
            )}
            <KupferTaste
              onClick={() => {
                setzeMaske({ art: 'neu' });
              }}
              symbol={<IconClockPlus size={SYMBOL_TASTE} stroke={1.8} />}
            >
              Zeit erfassen
            </KupferTaste>
          </>
        }
      >
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
          {loeschfehler === null ? null : <Alert severity="error">{loeschfehler}</Alert>}
          {inhaltZu(stand, setzeMaske, (zeile) => {
            void loeschen(zeile);
          })}
        </Box>
      </Karte>
      {maske === null ? null : (
        <ZeiteintragMaske
          zeile={maske.art === 'neu' ? null : maske.zeile}
          onGespeichert={() => {
            setzeMaske(null);
            neuLaden();
          }}
          onSchliessen={() => {
            setzeMaske(null);
          }}
        />
      )}
    </Box>
  );
}
