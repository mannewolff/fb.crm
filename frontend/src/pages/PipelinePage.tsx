import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import { IconCoinEuro, IconTrendingUp } from '@tabler/icons-react';
import { useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { Link as RouterLink } from 'react-router-dom';

import { pipeline } from '../api/pipeline';
import type { Pipeline, PipelineZeile } from '../api/pipeline';
import Karte from '../components/Karte';
import Kennzahl from '../components/Kennzahl';
import { useKopfPfad } from '../components/KopfPfad';
import type { PfadVerweis } from '../components/KopfPfad';
import Tafel from '../components/Tafel';
import { euro } from '../lib/geld';
import { tagWort } from '../lib/tag';
import { ZAHLEN_KLASSE } from '../theme';

/**
 * Die Auswertung „Pipeline" (Kriterien 23, 24, 27, 28).
 *
 * Oben zwei {@link Kennzahl}-Kacheln, darunter die offenen Angebote als {@link Tafel} — die Gestalt
 * der Vorlage, in der `.zahlen` ueber dem Inhalt steht (Z. 152–154). Die ungewichtete Summe traegt
 * Salbei („Umsatz" in der Bedeutungstabelle), die gewichtete Flieder („neutrale Kategorie"); beide
 * Toene und beide Symbole stehen in der Vorlage, es entsteht kein neuer Ton.
 *
 * **Beide Summen kommen aus der Antwort**, nicht aus den Zeilen: Die Rundungsregel steht im
 * Backend (E20). Die leere Pipeline zeigt darum zwei Nullsummen und einen Hinweis — sie ist eine
 * Aussage („keine offene Chance") und nicht ein Mangel, und eine Tabelle mit Kopfzeile ohne Zeilen
 * waere keine.
 *
 * **„nicht eingeschätzt" steht als Wort in der Spalte der Wahrscheinlichkeit**, nicht als Farbe und
 * nicht als Symbol: Jeder Zustand muss lesbar sein (CLAUDE-react.md, Accessibility), und eine
 * eigene Spalte fuer eine Ausnahme kostete Breite ohne Aussage.
 *
 * **Der Link steht in der Vorgangsspalte, nicht um die Zeile** — dieselbe Ueberlegung wie in
 * {@link VorgaengePage}: Ein `a` kann keine `td`-Elemente umschliessen, und eine Zeile mit
 * `role="link"` verlöre ihre Zuordnung zu den Spalten.
 */

/** Die Auswertung ist die erste Stufe des Pfades — ueber ihr steht nichts (E6). */
const KEIN_WEG: readonly PfadVerweis[] = [];

/** Wenn die Schnittstelle nicht antwortet — ohne technische Einzelheiten. */
const AUSFALL = 'Die Pipeline ist gerade nicht zu erreichen. Bitte später erneut versuchen.';

/** Was in der leeren Pipeline steht: eine Aussage, kein Mangel. */
const LEER =
  'Es ist kein Angebot offen. Versendete Angebote erscheinen hier, solange der Kunde nicht reagiert hat.';

const SPALTEN = [
  'Vorgang',
  'Firma',
  'Summe',
  'Wahrscheinlichkeit',
  'Gewichtete Summe',
  'Entscheidung erwartet',
] as const;

/** Was die Ansicht gerade weiss. */
type Stand =
  | { readonly art: 'laedt' }
  | { readonly art: 'daten'; readonly auswertung: Pipeline }
  | { readonly art: 'fehler' };

/** Holt die Auswertung und macht auch aus dem Fehlschlag einen Stand. */
async function laden(signal: AbortSignal): Promise<Stand> {
  try {
    return { art: 'daten', auswertung: await pipeline(signal) };
  } catch {
    // Jeder Grund fuehrt zur selben Meldung; welcher es war, hilft dem Benutzer nicht.
    return { art: 'fehler' };
  }
}

/**
 * Die Abschlusswahrscheinlichkeit als Wort.
 *
 * `null` **ist** die Kennzeichnung „nicht eingeschaetzt" (F2) — sie steht hier als Wort da und
 * nicht als Strich: Ein Strich saehe aus wie eine fehlende Angabe, und genau das ist es nicht.
 */
function wahrscheinlichkeitWort(prozent: number | null): string {
  return prozent === null ? 'nicht eingeschätzt' : `${String(prozent)} %`;
}

/** Der erwartete Entscheidungszeitpunkt als Wort — auch sein Fehlen ist eines. */
function entscheidungWort(tag: string | null): string {
  return tag === null ? 'nicht angegeben' : tagWort(tag);
}

/** Eine Zeile der Tafel. Der Weg zum Vorgang haengt an der Vorgangsspalte (Klassenkommentar). */
function Zeile({ zeile }: { readonly zeile: PipelineZeile }) {
  return (
    <Box component="tr">
      <Box component="td" sx={{ fontWeight: 500 }}>
        <Box
          component={RouterLink}
          to={`/vorgaenge/${String(zeile.vorgangId)}`}
          sx={(theme) => ({
            color: 'inherit',
            textDecoration: 'none',
            '&:hover': { color: theme.vars.palette.kupferwolke.kupfer },
          })}
        >
          {`#${String(zeile.vorgangNummer)} ${zeile.vorgangTitel}`}
        </Box>
      </Box>
      <Box component="td">{zeile.firma}</Box>
      <Box component="td" className={ZAHLEN_KLASSE} sx={{ whiteSpace: 'nowrap' }}>
        {euro(zeile.summeInCent)}
      </Box>
      <Box
        component="td"
        className={ZAHLEN_KLASSE}
        sx={(theme) => ({
          whiteSpace: 'nowrap',
          // Die Ausnahme steht matter als die Zahl, bleibt aber ein Wort in Volltext.
          color:
            zeile.wahrscheinlichkeit === null
              ? theme.vars.palette.kupferwolke.textMatt
              : 'inherit',
        })}
      >
        {wahrscheinlichkeitWort(zeile.wahrscheinlichkeit)}
      </Box>
      <Box component="td" className={ZAHLEN_KLASSE} sx={{ whiteSpace: 'nowrap' }}>
        {euro(zeile.gewichteteSummeInCent)}
      </Box>
      <Box
        component="td"
        className={ZAHLEN_KLASSE}
        sx={(theme) => ({
          whiteSpace: 'nowrap',
          color: theme.vars.palette.kupferwolke.textMatt,
        })}
      >
        {entscheidungWort(zeile.entscheidungErwartetAm)}
      </Box>
    </Box>
  );
}

/** Die beiden Kacheln ueber der Tafel (Vorlage `.zahlen` Z. 151, `.zahl-karte` Z. 71–74). */
function Kacheln({ auswertung }: { readonly auswertung: Pipeline }) {
  return (
    <Box
      sx={{
        display: 'grid',
        // Zwei Kacheln nebeneinander, auf schmalem Raum untereinander; die Vorlage setzt drei
        // gleiche Spalten (`.zahlen` Z. 151), hier sind es zwei.
        gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, 1fr)' },
        gap: '18px',
      }}
    >
      <Kennzahl
        beschriftung="Pipeline netto"
        zahl={euro(auswertung.summeInCent)}
        toenung="salbei"
        symbol={<IconCoinEuro size={22} stroke={1.7} />}
      />
      <Kennzahl
        beschriftung="Gewichtete Pipeline"
        zahl={euro(auswertung.gewichteteSummeInCent)}
        toenung="flieder"
        symbol={<IconTrendingUp size={22} stroke={1.7} />}
      />
    </Box>
  );
}

/** Was in der Karte steht: Ladehinweis, Meldung, Hinweis zur Leere oder die Tafel. */
function inhaltZu(stand: Stand): ReactNode {
  if (stand.art === 'laedt') {
    return (
      <Typography
        sx={(theme) => ({
          fontSize: 12.5,
          color: theme.vars.palette.kupferwolke.textSchwach,
        })}
      >
        Pipeline wird geladen …
      </Typography>
    );
  }
  if (stand.art === 'fehler') {
    return <Alert severity="error">{AUSFALL}</Alert>;
  }
  if (stand.auswertung.zeilen.length === 0) {
    return (
      <Typography
        role="status"
        sx={(theme) => ({
          fontSize: 12.5,
          color: theme.vars.palette.kupferwolke.textMatt,
        })}
      >
        {LEER}
      </Typography>
    );
  }
  return (
    <Tafel beschriftung="Offene Angebote" spalten={[...SPALTEN]}>
      {stand.auswertung.zeilen.map((zeile) => (
        <Zeile key={zeile.angebotId} zeile={zeile} />
      ))}
    </Tafel>
  );
}

export default function PipelinePage() {
  useKopfPfad(KEIN_WEG, 'Pipeline');
  const [stand, setzeStand] = useState<Stand>({ art: 'laedt' });

  useEffect(() => {
    const steuerung = new AbortController();
    void laden(steuerung.signal).then((neu) => {
      // Abgebrochen heisst: Die Ansicht ist gegangen. Ihr Stand interessiert dann niemanden mehr.
      if (!steuerung.signal.aborted) {
        setzeStand(neu);
      }
    });
    return () => {
      steuerung.abort();
    };
  }, []);

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
      {stand.art === 'daten' ? <Kacheln auswertung={stand.auswertung} /> : null}
      <Karte titel="Pipeline" titelEbene={1}>
        {inhaltZu(stand)}
      </Karte>
    </Box>
  );
}
