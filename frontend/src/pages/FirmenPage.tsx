import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Link from '@mui/material/Link';
import TextField from '@mui/material/TextField';
import ToggleButton from '@mui/material/ToggleButton';
import Typography from '@mui/material/Typography';
import { IconArchive, IconPlus } from '@tabler/icons-react';
import { useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { Link as RouterLink, useSearchParams } from 'react-router-dom';

import { firmenUebersicht } from '../api/firmen';
import type { FirmaZeile, FirmenUebersicht } from '../api/firmen';
import Karte from '../components/Karte';
import { useKopfPfad } from '../components/KopfPfad';
import type { PfadVerweis } from '../components/KopfPfad';
import KupferTaste from '../components/KupferTaste';
import Tafel from '../components/Tafel';
import ZustandsChip from '../components/ZustandsChip';
import { RADIUS_RUND, ZAHLEN_KLASSE } from '../theme';

/**
 * Die Uebersicht der Firmen (Kriterien 2, 3, 6 und 13) in der Kupferwolke.
 *
 * Die Zeilen stehen in einer {@link Tafel} innerhalb einer {@link Karte}; im Kartenkopf steht die
 * Hauptaktion als Kupfertaste, davor Filter und Schalter (Plan E5, CLAUDE-design.md, „Tasten":
 * „rechts im Kartenkopf der Liste"). Vier Zusagen tragen diese Ansicht, und jede hat einen Grund:
 *
 * <ul>
 *   <li><b>Der Zustand steht in der Adresse</b> — Suchtext und Schalter sind ueber
 *       `useSearchParams` teilbar und ueberleben das Neuladen. Der Suchtext geht dabei
 *       <i>entprellt</i> hinein: Jeder Tastendruck als eigener Verlaufseintrag waere ein
 *       Zurueck-Knopf, der Buchstabe fuer Buchstabe rueckwaerts tippt. Der Schalter dagegen ist
 *       eine bewusste Handlung und wird geschoben, damit „zurueck" ihn zuruecknimmt.</li>
 *   <li><b>Veraltete Antworten fallen weg</b> — jeder Lauf haengt an einem
 *       {@link AbortController}. Beim naechsten Suchtext bricht der vorige ab; was danach noch
 *       eintrifft, wird verworfen statt angezeigt (CLAUDE-react.md, Hooks).</li>
 *   <li><b>Filtern und Sortieren macht der Server</b> (E5) — hier wird nichts nachsortiert. Die
 *       Zeilen stehen in der Reihenfolge der Antwort.</li>
 *   <li><b>Der Stilllegungsstand steht als Wort da</b> — als Chip in der Zeile, nicht als zweite
 *       Farbe (CLAUDE-design.md, „Zustandsformen").</li>
 * </ul>
 */

/** Wie lange der Suchtext ruhen muss, bevor er in Adresse und Aufruf geht. */
export const ENTPRELLUNG_MS = 300;

/** Die Uebersicht ist die erste Stufe des Pfades — ueber ihr steht nichts (E6). */
const KEIN_WEG: readonly PfadVerweis[] = [];

const PARAM_SUCHE = 'suche';
const PARAM_STILLGELEGTE = 'auchStillgelegte';

/** Wenn die Schnittstelle nicht antwortet — ohne technische Einzelheiten. */
const AUSFALL = 'Die Firmen sind gerade nicht zu erreichen. Bitte später erneut versuchen.';

/** Die Spalten der Tafel, in der Reihenfolge der Zellen. */
const SPALTEN: readonly string[] = ['Firma', 'Ort', 'Ansprechpartner'];

/** Was die Ansicht gerade weiss. */
type Stand =
  | { readonly art: 'laedt' }
  | { readonly art: 'daten'; readonly uebersicht: FirmenUebersicht }
  | { readonly art: 'fehler' };

/**
 * Holt die Uebersicht und macht auch aus dem Fehlschlag einen Stand.
 *
 * Der Fehler wird hier abgefangen und nicht in der Ansicht: So gibt es genau eine Stelle, an der
 * ein Ergebnis entsteht — und damit auch nur eine, an der geprueft wird, ob es noch gebraucht wird.
 */
async function laden(
  suche: string,
  auchStillgelegte: boolean,
  signal: AbortSignal,
): Promise<Stand> {
  try {
    return { art: 'daten', uebersicht: await firmenUebersicht(suche, auchStillgelegte, signal) };
  } catch {
    // Jeder Grund — Netz, Status, unerwartete Form — fuehrt zur selben Meldung. Welcher es war,
    // hilft dem Benutzer nicht und gehoert nicht in die Oberflaeche (CLAUDE-react.md).
    return { art: 'fehler' };
  }
}

/** Setzt oder entfernt einen Parameter, ohne die uebrigen anzutasten. */
function mitParameter(alt: URLSearchParams, name: string, wert: string | null): URLSearchParams {
  const neu = new URLSearchParams(alt);
  if (wert === null) {
    neu.delete(name);
  } else {
    neu.set(name, wert);
  }
  return neu;
}

/**
 * Der Hinweis zur leeren Liste.
 *
 * Drei Lagen, die eine leere Liste sonst nicht auseinanderhaelt (E5). Die dritte steht als
 * Restfall: Ohne Suchtext und mit `gesamt` groesser null kann die Liste nur leer sein, weil alles
 * stillgelegt ist — bei eingeschaltetem Schalter kaeme dieselbe Anfrage mit Zeilen zurueck.
 */
function hinweisZu(suche: string, gesamt: number): ReactNode {
  if (gesamt === 0) {
    return (
      <>
        Es ist noch keine Firma angelegt.{' '}
        <Link component={RouterLink} to="/firmen/neu" underline="hover">
          Erste Firma anlegen
        </Link>
      </>
    );
  }
  if (suche !== '') {
    return <>Zu diesem Suchtext wurde nichts gefunden.</>;
  }
  return <>Alle Firmen sind stillgelegt. Der Schalter „auch stillgelegte“ zeigt sie an.</>;
}

/**
 * Eine Zeile der Tafel.
 *
 * Der **Name** traegt den Weg zum Objekt: Eine Tabellenzeile kann kein Link sein, und ein Weg
 * gehoert in ein `a` mit `href`. Der Chip steht daneben, ausserhalb des Weges — er ist kein Teil
 * seines Ziels. Fehlt der Ort, bleibt die Zelle leer; ein „—" waere ein Wort ohne Aussage.
 */
function Zeile({ firma }: { readonly firma: FirmaZeile }) {
  return (
    <Box component="tr">
      <Box component="td">
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.25, flexWrap: 'wrap' }}>
          <Box
            component={RouterLink}
            to={`/firmen/${String(firma.id)}`}
            sx={(theme) => ({
              fontWeight: 500,
              color: 'inherit',
              textDecoration: 'none',
              '&:hover': { color: theme.vars.palette.kupferwolke.kupfer },
            })}
          >
            {firma.name}
          </Box>
          {firma.aktiv ? null : (
            <ZustandsChip wort="Stillgelegt" toenung="rose" symbol={<IconArchive size={13} />} />
          )}
        </Box>
      </Box>
      <Box component="td" sx={(theme) => ({ color: theme.vars.palette.kupferwolke.textMatt })}>
        {firma.ort}
      </Box>
      <Box component="td" className={ZAHLEN_KLASSE}>
        {firma.aktiveAnsprechpartner}
      </Box>
    </Box>
  );
}

/** Was in der Karte steht: Ladehinweis, Meldung, Hinweis zur Leere oder die Tafel. */
function inhaltZu(stand: Stand, suche: string): ReactNode {
  if (stand.art === 'laedt') {
    return (
      <Typography
        sx={(theme) => ({
          fontSize: 12.5,
          color: theme.vars.palette.kupferwolke.textSchwach,
        })}
      >
        Firmen werden geladen …
      </Typography>
    );
  }
  if (stand.art === 'fehler') {
    return <Alert severity="error">{AUSFALL}</Alert>;
  }
  if (stand.uebersicht.firmen.length === 0) {
    return (
      <Typography
        role="status"
        sx={(theme) => ({
          fontSize: 12.5,
          color: theme.vars.palette.kupferwolke.textMatt,
        })}
      >
        {hinweisZu(suche, stand.uebersicht.gesamt)}
      </Typography>
    );
  }
  return (
    <Tafel beschriftung="Firmen" spalten={SPALTEN}>
      {stand.uebersicht.firmen.map((firma) => (
        <Zeile key={firma.id} firma={firma} />
      ))}
    </Tafel>
  );
}

export default function FirmenPage() {
  useKopfPfad(KEIN_WEG, 'Firmen');
  const [parameter, setzeParameter] = useSearchParams();
  const suche = parameter.get(PARAM_SUCHE) ?? '';
  const auchStillgelegte = parameter.get(PARAM_STILLGELEGTE) === 'true';
  const [eingabe, setzeEingabe] = useState(suche);
  const [stand, setzeStand] = useState<Stand>({ art: 'laedt' });

  // Entprellung: Der Suchtext wandert erst in die Adresse, wenn die Tastatur ruht. Solange
  // Eingabe und Adresse uebereinstimmen, gibt es nichts zu tun — sonst liefe der Effekt im
  // Kreis, weil das Setzen der Adresse ihn erneut anstoesst.
  useEffect(() => {
    if (eingabe === suche) {
      return;
    }
    const uhr = setTimeout(() => {
      setzeParameter(
        (alt) => mitParameter(alt, PARAM_SUCHE, eingabe === '' ? null : eingabe),
        { replace: true },
      );
    }, ENTPRELLUNG_MS);
    return () => {
      clearTimeout(uhr);
    };
  }, [eingabe, suche, setzeParameter]);

  useEffect(() => {
    const steuerung = new AbortController();
    setzeStand({ art: 'laedt' });
    void laden(suche, auchStillgelegte, steuerung.signal).then((neu) => {
      // Abgebrochen heisst: Es gibt bereits eine juengere Anfrage. Ihre Antwort ist die
      // richtige, auch wenn diese hier zuerst eintrifft.
      if (!steuerung.signal.aborted) {
        setzeStand(neu);
      }
    });
    return () => {
      steuerung.abort();
    };
  }, [suche, auchStillgelegte]);

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
      <Karte
        titel="Firmen"
        werkzeug={
          <>
            <TextField
              type="search"
              size="small"
              value={eingabe}
              onChange={(ereignis) => {
                setzeEingabe(ereignis.target.value);
              }}
              placeholder="Firmen durchsuchen …"
              // Keine Beschriftung ueber dem Feld: In der Werkzeugleiste traegt der Wert die
              // Benennung, den zugaenglichen Namen behaelt das Feld (CLAUDE-design.md,
              // „Zustandsformen"). Die Pille steht in einer Linie mit Schalter und Taste.
              slotProps={{ htmlInput: { 'aria-label': 'Suche' } }}
              sx={(theme) => ({
                minWidth: 220,
                '& .MuiOutlinedInput-root': {
                  fontSize: 12.5,
                  borderRadius: `${RADIUS_RUND}px`,
                  background: theme.vars.palette.kupferwolke.flaecheWeich,
                },
              })}
            />
            <ToggleButton
              value={PARAM_STILLGELEGTE}
              selected={auchStillgelegte}
              onChange={() => {
                // Geschoben statt ersetzt: Der Schalter ist eine Handlung, die „zurueck"
                // zuruecknehmen koennen soll.
                setzeParameter((alt) =>
                  mitParameter(alt, PARAM_STILLGELEGTE, auchStillgelegte ? null : 'true'),
                );
              }}
              sx={(theme) => ({
                fontSize: 11.5,
                fontWeight: 500,
                textTransform: 'none',
                padding: '4px 12px',
                borderRadius: `${RADIUS_RUND}px`,
                color: theme.vars.palette.kupferwolke.textMatt,
                background: theme.vars.palette.kupferwolke.flaecheWeich,
                border: 0,
                boxShadow: `inset 0 0 0 1px ${theme.vars.palette.kupferwolke.linie}`,
                // Gewaehlt: die Toenung Pfirsich, nicht bloss eine zweite Tiefe — Form und Farbe
                // sagen dasselbe (CLAUDE-design.md, „Zustandsformen").
                '&.Mui-selected': {
                  color: theme.vars.palette.kupferwolke.toenung.pfirsich.schrift,
                  background: theme.vars.palette.kupferwolke.toenung.pfirsich.flaeche,
                },
              })}
            >
              auch stillgelegte
            </ToggleButton>
            <KupferTaste to="/firmen/neu" symbol={<IconPlus size={16} stroke={1.8} />}>
              Neue Firma
            </KupferTaste>
          </>
        }
      >
        {inhaltZu(stand, suche)}
      </Karte>
    </Box>
  );
}
