import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import { IconChevronLeft, IconChevronRight, IconClockPlus } from '@tabler/icons-react';
import { Fragment, useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { useSearchParams } from 'react-router-dom';

import { arbeitszeitMonat } from '../api/arbeitszeit';
import type { Arbeitsmonat, Arbeitstag, Zeitzeile } from '../api/arbeitszeit';
import Karte from '../components/Karte';
import { useKopfPfad } from '../components/KopfPfad';
import type { PfadVerweis } from '../components/KopfPfad';
import KupferTaste from '../components/KupferTaste';
import Tafel from '../components/Tafel';
import {
  alsMonat,
  folgemonat,
  monatWort,
  stundenWort,
  vormonat,
  zeitspanneWort,
} from '../lib/arbeitszeit';
import { tagWort } from '../lib/tag';
import { RADIUS_RUND, ZAHLEN_KLASSE } from '../theme';

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
 * <b>„Zeit erfassen" ist die eine Kupfertaste</b> der Ansicht (A14). In diesem Stand ist sie
 * gesperrt: Der Dialog zum Erfassen und Aendern entsteht in Issue #202, und eine Taste, die nichts
 * oeffnet, waere keine Taste.
 */

/** Die Ansicht ist die erste Stufe des Pfades — ueber ihr steht nichts (E6). */
const KEIN_WEG: readonly PfadVerweis[] = [];

/** Der Name des Parameters, unter dem der Monat in der Adresse steht (A13). */
const PARAM_MONAT = 'monat';

const LAEDT = 'Die Arbeitszeit wird geladen …';
const AUSFALL = 'Die Arbeitszeit ist gerade nicht zu erreichen. Bitte später erneut versuchen.';
const LEER = 'Noch keine Arbeitszeit in diesem Monat. Erfassen Sie die erste über „Zeit erfassen".';

const SPALTEN: readonly string[] = ['Zeit', 'Dauer', 'Position'];

/** Die Symbolgroesse in den Tasten (wie in {@link RechnungenPage}). */
const SYMBOL_TASTE = 16;

/** Kantenlaenge der Icontaste (CLAUDE-design.md, „Tasten": Kreis 40 px). */
const ICONTASTE = 40;

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
      sx={(theme) => ({
        width: ICONTASTE,
        height: ICONTASTE,
        flex: 'none',
        borderRadius: `${RADIUS_RUND}px`,
        border: 0,
        cursor: 'pointer',
        display: 'grid',
        placeItems: 'center',
        color: theme.vars.palette.kupferwolke.textMatt,
        background: theme.vars.palette.kupferwolke.flaecheWeich,
        transition: 'background .15s ease, color .15s ease',
        '&:hover': {
          background: theme.vars.palette.kupferwolke.toenung.pfirsich.flaeche,
          color: theme.vars.palette.kupferwolke.toenung.pfirsich.schrift,
        },
      })}
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
    </Box>
  );
}

/** Ein Eintrag: Zeitspanne, Dauer, Position mit ihrer Firma. */
function Zeile({ zeile }: { readonly zeile: Zeitzeile }) {
  return (
    <Box component="tr">
      <Box
        component="td"
        className={ZAHLEN_KLASSE}
        sx={{ fontWeight: 500, whiteSpace: 'nowrap' }}
      >
        {zeitspanneWort(zeile.von, zeile.bis)}
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
        </Typography>
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
    </Box>
  );
}

/** Was in der Karte steht: Ladehinweis, Meldung, Einladung oder die Tafel. */
function inhaltZu(stand: Stand): ReactNode {
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
            <Zeile key={zeile.id} zeile={zeile} />
          ))}
        </Fragment>
      ))}
      <Monatssumme monatsliste={stand.monatsliste} />
    </Tafel>
  );
}

export default function ArbeitszeitPage() {
  useKopfPfad(KEIN_WEG, 'Arbeitszeit');
  const [parameter, setzeParameter] = useSearchParams();
  const monat = alsMonat(parameter.get(PARAM_MONAT));
  const [stand, setzeStand] = useState<Stand>({ art: 'laedt' });

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
  }, [monat]);

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
            {/* Gesperrt und ohne Rueckruf: Der Dialog zum Erfassen entsteht in Issue #202. Ein
                leerer Rueckruf waere Code, den nichts erreicht — eine gesperrte Taste loest
                keinen aus. */}
            <KupferTaste disabled symbol={<IconClockPlus size={SYMBOL_TASTE} stroke={1.8} />}>
              Zeit erfassen
            </KupferTaste>
          </>
        }
      >
        {inhaltZu(stand)}
      </Karte>
    </Box>
  );
}
