import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Link from '@mui/material/Link';
import TextField from '@mui/material/TextField';
import ToggleButton from '@mui/material/ToggleButton';
import Typography from '@mui/material/Typography';
import { useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { Link as RouterLink, useSearchParams } from 'react-router-dom';

import { firmenUebersicht } from '../api/firmen';
import type { FirmaZeile, FirmenUebersicht } from '../api/firmen';
import KopfAktion from '../components/KopfAktion';
import KupferTaste from '../components/KupferTaste';
import Platte from '../components/Platte';
import { CARD_RADIUS } from '../theme';

/**
 * Die Uebersicht der Firmen (Kriterien 2, 3, 6 und 13).
 *
 * Drei Zusagen tragen diese Ansicht, und jede hat einen Grund:
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
 * </ul>
 *
 * Die Vorlage gibt die Gestalt vor: Werkzeugleiste (`werkzeugleiste` Z. 799–806) mit dem Suchfeld
 * als Nut (`.suche` Z. 300–311) und dem Schalter als gedrueckter Filter (`.filter` Z. 561–576),
 * darunter die Platte mit den Zeilen (`.vorgang` Z. 580–605). Eine Ueberschrift traegt die Ansicht
 * nicht: Die Buehne der Vorlage beginnt mit der Werkzeugleiste, und eine Platte unter einer eigenen
 * Werkzeugleiste bleibt ohne Kopf (Issue #45). Die Liste ist stattdessen als benannte Liste
 * ausgezeichnet, damit sie mit dem Screenreader auffindbar bleibt.
 */

/** Wie lange der Suchtext ruhen muss, bevor er in Adresse und Aufruf geht. */
export const ENTPRELLUNG_MS = 300;

const PARAM_SUCHE = 'suche';
const PARAM_STILLGELEGTE = 'auchStillgelegte';

/** Wenn die Schnittstelle nicht antwortet — ohne technische Einzelheiten. */
const AUSFALL = 'Die Firmen sind gerade nicht zu erreichen. Bitte später erneut versuchen.';

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

/** „1 Ansprechpartner" statt „1 Ansprechpartners" — die Zahl steht immer davor. */
function ansprechpartnerText(anzahl: number): string {
  return anzahl === 1 ? '1 Ansprechpartner' : `${anzahl} Ansprechpartner`;
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

/** Eine Zeile der Uebersicht: ein Link auf die Detailansicht (Vorlage `.vorgang` Z. 580–605). */
function Zeile({ firma }: { readonly firma: FirmaZeile }) {
  return (
    <Box component="li">
      <Box
        component={RouterLink}
        to={`/firmen/${firma.id}`}
        sx={(theme) => ({
          display: 'flex',
          alignItems: 'center',
          gap: 1.5,
          padding: '11px 16px',
          textDecoration: 'none',
          color: 'inherit',
          borderBottom: `1px solid ${theme.vars.palette.kupferwarte.rand}`,
          transition: 'background .12s ease',
          '&:hover': { background: theme.vars.palette.kupferwarte.platteFuss },
          'li:last-of-type > &': { borderBottom: 0 },
        })}
      >
        <Box sx={{ minWidth: 0, flex: 1 }}>
          <Typography
            sx={{
              fontSize: 13.5,
              fontWeight: 500,
              whiteSpace: 'nowrap',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
            }}
          >
            {firma.name}
          </Typography>
          <Box
            sx={{
              display: 'flex',
              alignItems: 'center',
              gap: 1,
              marginTop: '3px',
              flexWrap: 'wrap',
            }}
          >
            {firma.ort === null ? null : (
              <Typography
                sx={(theme) => ({ fontSize: 11, color: theme.vars.palette.kupferwarte.textMatt })}
              >
                {firma.ort}
              </Typography>
            )}
            <Typography
              sx={(theme) => ({ fontSize: 11, color: theme.vars.palette.kupferwarte.textMatt })}
            >
              {ansprechpartnerText(firma.aktiveAnsprechpartner)}
            </Typography>
          </Box>
        </Box>
        {firma.aktiv ? null : (
          // Schild der Vorlage (Z. 780–790): der Stand steht als Wort da, nicht nur als Farbe
          // (E15) — Farbe allein traegt keine Information (CLAUDE-react.md, Accessibility).
          <Box
            component="span"
            sx={(theme) => ({
              flex: 'none',
              fontSize: 10,
              fontWeight: 500,
              padding: '1px 6px',
              borderRadius: '5px',
              color: theme.vars.palette.kupferwarte.grau,
              border: '1px solid currentColor',
              background: 'color-mix(in srgb, currentColor 13%, transparent)',
            })}
          >
            stillgelegt
          </Box>
        )}
      </Box>
    </Box>
  );
}

/** Was in der Platte steht: Ladehinweis, Meldung, Hinweis zur Leere oder die Liste. */
function inhaltZu(stand: Stand, suche: string): ReactNode {
  if (stand.art === 'laedt') {
    return (
      <Typography
        sx={(theme) => ({
          padding: '18px 16px',
          fontSize: 12.5,
          color: theme.vars.palette.kupferwarte.textSchwach,
        })}
      >
        Firmen werden geladen …
      </Typography>
    );
  }
  if (stand.art === 'fehler') {
    return (
      <Alert severity="error" sx={{ borderRadius: 0 }}>
        {AUSFALL}
      </Alert>
    );
  }
  if (stand.uebersicht.firmen.length === 0) {
    return (
      <Typography
        role="status"
        sx={(theme) => ({
          padding: '18px 16px',
          fontSize: 12.5,
          color: theme.vars.palette.kupferwarte.textMatt,
        })}
      >
        {hinweisZu(suche, stand.uebersicht.gesamt)}
      </Typography>
    );
  }
  return (
    <Box
      component="ul"
      aria-label="Firmen"
      sx={{ listStyle: 'none', margin: 0, padding: 0, display: 'flex', flexDirection: 'column' }}
    >
      {stand.uebersicht.firmen.map((firma) => (
        <Zeile key={firma.id} firma={firma} />
      ))}
    </Box>
  );
}

export default function FirmenPage() {
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
        padding: { xs: 2, sm: '22px 26px 44px' },
        display: 'flex',
        flexDirection: 'column',
        gap: '20px',
      }}
    >
      <KopfAktion>
        <KupferTaste to="/firmen/neu">Neue Firma</KupferTaste>
      </KopfAktion>
      <Box
        sx={(theme) => ({
          display: 'flex',
          alignItems: 'center',
          gap: '10px',
          flexWrap: 'wrap',
          padding: '9px 11px',
          borderRadius: `${CARD_RADIUS}px`,
          border: `1px solid ${theme.vars.palette.kupferwarte.rand}`,
          background: `linear-gradient(180deg, ${theme.vars.palette.kupferwarte.platteHoch}, ${theme.vars.palette.kupferwarte.platteFuss})`,
          boxShadow: theme.vars.palette.kupferwarte.schatten.platte,
        })}
      >
        <TextField
          label="Suche"
          type="search"
          size="small"
          value={eingabe}
          onChange={(ereignis) => {
            setzeEingabe(ereignis.target.value);
          }}
          sx={(theme) => ({
            minWidth: 190,
            '& .MuiOutlinedInput-root': {
              background: theme.vars.palette.kupferwarte.nute,
              boxShadow: theme.vars.palette.kupferwarte.schatten.nute,
              borderRadius: '9px',
              fontSize: 12.5,
            },
            '& .MuiOutlinedInput-notchedOutline': {
              borderColor: theme.vars.palette.kupferwarte.rand,
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
            padding: '4px 9px',
            borderRadius: '7px',
            color: theme.vars.palette.kupferwarte.textMatt,
            background: theme.vars.palette.kupferwarte.nute,
            border: `1px solid ${theme.vars.palette.kupferwarte.rand}`,
            boxShadow: theme.vars.palette.kupferwarte.schatten.nute,
            '&.Mui-selected': {
              color: theme.vars.palette.kupferwarte.text,
              background: `linear-gradient(180deg, ${theme.vars.palette.kupferwarte.platteHoch}, ${theme.vars.palette.kupferwarte.platte})`,
              boxShadow: theme.vars.palette.kupferwarte.schatten.taste,
            },
          })}
        >
          auch stillgelegte
        </ToggleButton>
      </Box>
      <Platte>{inhaltZu(stand, suche)}</Platte>
    </Box>
  );
}
