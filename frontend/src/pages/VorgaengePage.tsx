import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Link from '@mui/material/Link';
import TextField from '@mui/material/TextField';
import ToggleButton from '@mui/material/ToggleButton';
import Typography from '@mui/material/Typography';
import { IconPlus } from '@tabler/icons-react';
import { useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { Link as RouterLink, useSearchParams } from 'react-router-dom';

import { vorgaengeUebersicht } from '../api/vorgaenge';
import type { Phase, VorgaengeUebersicht, VorgangZeile } from '../api/vorgaenge';
import { useKopfPfad } from '../components/KopfPfad';
import type { PfadVerweis } from '../components/KopfPfad';
import KupferTaste from '../components/KupferTaste';
import Karte from '../components/Karte';
import Tafel from '../components/Tafel';
import { RADIUS_RUND, ZAHLEN_KLASSE } from '../theme';

/**
 * Die Uebersicht der Vorgaenge (Kriterien 1–4, 20, 25, 26).
 *
 * Sie folgt in allem dem Muster der Firmenuebersicht, weil beide dieselbe Zusage tragen: Der
 * Zustand — Suchtext und Schalter — steht in der Adresse, der Suchtext geht entprellt hinein, jeder
 * Lauf haengt an einem {@link AbortController}, und gefiltert wie sortiert wird auf dem Server. Die
 * Begruendungen im Einzelnen stehen an {@link FirmenPage}; hier wiederholt sie diese Ansicht nicht.
 *
 * Gestalt wie die Firmenuebersicht: eine {@link Karte}, deren Kopf den Titel und rechts das
 * Werkzeug traegt — Suchfeld, Schalter und als Letztes die Kupfertaste „Neuer Vorgang"
 * (Vorlage `.kopfzeile` Z. 79–80, CLAUDE-design.md, „Tasten": „rechts im Kartenkopf der Liste").
 * Die Hauptaktion steht damit <b>in der Ansicht</b> und nicht mehr im Kopf der Anwendung: Der Kopf
 * traegt nur noch den Pfad. Ohne Auswahlhaken, Massenleiste, Gruppenzeilen, Spaltenwahl und Export
 * (E22) — das sind Funktionen, die fb.crm nicht hat.
 *
 * **Der Link steht in der Titelspalte, nicht um die Zeile.** Ein `a` kann keine `td`-Elemente
 * umschliessen; eine Zeile mit `role="link"` verlöre ihre Rolle als Tabellenzeile und damit die
 * Zuordnung zu den Spalten. Der Weg zum Vorgang haengt darum am Titel — ein echtes `href`, im
 * Tabulatorweg, in einem neuen Reiter zu oeffnen.
 */

/** Wie lange der Suchtext ruhen muss, bevor er in Adresse und Aufruf geht. */
export const ENTPRELLUNG_MS = 300;

/** Die Uebersicht ist die erste Stufe des Pfades — ueber ihr steht nichts (E6). */
const KEIN_WEG: readonly PfadVerweis[] = [];

const PARAM_SUCHE = 'suche';
const PARAM_ABGESCHLOSSENE = 'auchAbgeschlossene';

/** Wenn die Schnittstelle nicht antwortet — ohne technische Einzelheiten. */
const AUSFALL = 'Die Vorgänge sind gerade nicht zu erreichen. Bitte später erneut versuchen.';

const SPALTEN = ['Nr.', 'Vorgang', 'Firma', 'Phase', 'Letzte Aktivität'] as const;

/** Die Phase als Wort. Heute kennt das Backend genau eine (`Phase` in `api/vorgaenge.ts`). */
const PHASE_TEXT: Readonly<Record<Phase, string>> = { ANBAHNUNG: 'Anbahnung' };

/** Was die Ansicht gerade weiss. */
type Stand =
  | { readonly art: 'laedt' }
  | { readonly art: 'daten'; readonly uebersicht: VorgaengeUebersicht }
  | { readonly art: 'fehler' };

/** Holt die Uebersicht und macht auch aus dem Fehlschlag einen Stand. */
async function laden(
  suche: string,
  auchAbgeschlossene: boolean,
  signal: AbortSignal,
): Promise<Stand> {
  try {
    return {
      art: 'daten',
      uebersicht: await vorgaengeUebersicht(suche, auchAbgeschlossene, signal),
    };
  } catch {
    // Jeder Grund fuehrt zur selben Meldung; welcher es war, hilft dem Benutzer nicht.
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
 * Der Zeitpunkt der letzten Aktivitaet als Tag.
 *
 * Nur der Tag, nicht die Uhrzeit: In einer Spalte neben vier anderen ist die Minute kein Wert,
 * sondern Breite. Wer sie braucht, findet sie in der Historie des Vorgangs.
 */
function alsTag(zeitstempel: string): string {
  return new Date(zeitstempel).toLocaleDateString('de-DE', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  });
}

/**
 * Der Hinweis zur leeren Tafel.
 *
 * Drei Lagen, die eine leere Liste sonst zusammenwirft (E5, Feld `gesamt`). Die dritte steht als
 * Restfall: Ohne Suchtext und mit `gesamt` groesser null kann die Tafel nur leer sein, weil alles
 * abgeschlossen ist — mit eingeschaltetem Schalter kaeme dieselbe Anfrage mit Zeilen zurueck.
 */
function hinweisZu(suche: string, gesamt: number): ReactNode {
  if (gesamt === 0) {
    return (
      <>
        Es ist noch kein Vorgang angelegt.{' '}
        <Link component={RouterLink} to="/vorgaenge/neu" underline="hover">
          Ersten Vorgang anlegen
        </Link>
      </>
    );
  }
  if (suche !== '') {
    return <>Zu diesem Suchtext wurde nichts gefunden.</>;
  }
  return <>Alle Vorgänge sind abgeschlossen. Der Schalter „auch abgeschlossene“ zeigt sie an.</>;
}

/** Eine Zeile der Tafel. Der Weg zum Vorgang haengt am Titel (siehe Klassenkommentar). */
function Zeile({ vorgang }: { readonly vorgang: VorgangZeile }) {
  return (
    <Box component="tr">
      <Box
        component="td"
        className={ZAHLEN_KLASSE}
        sx={(theme) => ({
          color: theme.vars.palette.kupferwolke.textSchwach,
          width: 58,
        })}
      >
        {`#${vorgang.nummer}`}
      </Box>
      <Box component="td" sx={{ fontWeight: 500 }}>
        <Box
          component={RouterLink}
          to={`/vorgaenge/${vorgang.id}`}
          sx={(theme) => ({
            display: 'inline-flex',
            alignItems: 'center',
            gap: 1,
            color: 'inherit',
            textDecoration: 'none',
            '&:hover': { color: theme.vars.palette.kupferwolke.kupfer },
          })}
        >
          {vorgang.titel}
          {vorgang.abgeschlossen ? (
            // Schild der Vorlage (Z. 780–790), im Link: Der Stand steht als Wort da und gehoert
            // zum Namen des Weges, damit der Screenreader ihn mit der Zeile vorliest (E15).
            <Box
              component="span"
              sx={(theme) => ({
                flex: 'none',
                fontSize: 10,
                fontWeight: 500,
                padding: '1px 6px',
                borderRadius: '5px',
                color: theme.vars.palette.kupferwolke.melder.grau,
                border: '1px solid currentColor',
                background: 'color-mix(in srgb, currentColor 13%, transparent)',
              })}
            >
              abgeschlossen
            </Box>
          ) : null}
        </Box>
      </Box>
      <Box component="td">{vorgang.firma}</Box>
      <Box component="td">{PHASE_TEXT[vorgang.phase]}</Box>
      <Box
        component="td"
        className={ZAHLEN_KLASSE}
        sx={(theme) => ({
          color: theme.vars.palette.kupferwolke.textMatt,
          whiteSpace: 'nowrap',
        })}
      >
        {alsTag(vorgang.letzteAktivitaet)}
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
        Vorgänge werden geladen …
      </Typography>
    );
  }
  if (stand.art === 'fehler') {
    return <Alert severity="error">{AUSFALL}</Alert>;
  }
  if (stand.uebersicht.vorgaenge.length === 0) {
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
    <Tafel beschriftung="Vorgänge" spalten={[...SPALTEN]}>
      {stand.uebersicht.vorgaenge.map((vorgang) => (
        <Zeile key={vorgang.id} vorgang={vorgang} />
      ))}
    </Tafel>
  );
}

export default function VorgaengePage() {
  useKopfPfad(KEIN_WEG, 'Vorgänge');
  const [parameter, setzeParameter] = useSearchParams();
  const suche = parameter.get(PARAM_SUCHE) ?? '';
  const auchAbgeschlossene = parameter.get(PARAM_ABGESCHLOSSENE) === 'true';
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
      setzeParameter((alt) => mitParameter(alt, PARAM_SUCHE, eingabe === '' ? null : eingabe), {
        replace: true,
      });
    }, ENTPRELLUNG_MS);
    return () => {
      clearTimeout(uhr);
    };
  }, [eingabe, suche, setzeParameter]);

  useEffect(() => {
    const steuerung = new AbortController();
    setzeStand({ art: 'laedt' });
    void laden(suche, auchAbgeschlossene, steuerung.signal).then((neu) => {
      // Abgebrochen heisst: Es gibt bereits eine juengere Anfrage. Ihre Antwort ist die
      // richtige, auch wenn diese hier zuerst eintrifft.
      if (!steuerung.signal.aborted) {
        setzeStand(neu);
      }
    });
    return () => {
      steuerung.abort();
    };
  }, [suche, auchAbgeschlossene]);

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
        titel="Vorgänge"
        werkzeug={
          <>
            <TextField
              type="search"
              size="small"
              value={eingabe}
              onChange={(ereignis) => {
                setzeEingabe(ereignis.target.value);
              }}
              placeholder="Vorgänge durchsuchen …"
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
              value={PARAM_ABGESCHLOSSENE}
              selected={auchAbgeschlossene}
              onChange={() => {
                // Geschoben statt ersetzt: Der Schalter ist eine Handlung, die „zurueck"
                // zuruecknehmen koennen soll.
                setzeParameter((alt) =>
                  mitParameter(alt, PARAM_ABGESCHLOSSENE, auchAbgeschlossene ? null : 'true'),
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
              auch abgeschlossene
            </ToggleButton>
            <KupferTaste to="/vorgaenge/neu" symbol={<IconPlus size={16} stroke={1.8} />}>
              Neuer Vorgang
            </KupferTaste>
          </>
        }
      >
        {inhaltZu(stand, suche)}
      </Karte>
    </Box>
  );
}
