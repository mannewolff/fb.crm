import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import type { ReactNode } from 'react';
import { Link as RouterLink } from 'react-router-dom';

import type { ToenungName } from '../theme';
import { RADIUS_MITTEL } from '../theme';
import Mal from './Mal';
import TastenSymbol from './TastenSymbol';

/**
 * Die Innenkarte: ein gleichrangiges Objekt **innerhalb** einer Karte (Vorlage `.personen`/`.person`
 * Z. 81–90, `.person .mehr` Z. 89–90, `.neu` Z. 91–92, HTML Z. 158–186).
 *
 * Sie ist die Form fuer **wenige** Objekte — Ansprechpartner einer Firma. Viele Eintraege sind
 * Zeilen in einer `Tafel`, kein Kartenraster (CLAUDE-design.md, „Bausteine").
 *
 * Die Innenkarte liegt als „Flaeche weich" ohne eigenen Schatten in der Karte und geht im Hover auf
 * Weiss mit `schatten-hoch` (CLAUDE-design.md, „Tiefe").
 *
 * **Die Aktion bleibt erreichbar** (E12): Sie steht immer im DOM und wird nur durchsichtig
 * gestellt. Sichtbar wird sie bei `:hover`, bei `:focus-within` — also sobald sie den
 * Tastaturfokus traegt — und auf Geraeten ohne Hover grundsaetzlich. Sie aus dem Dokument zu
 * nehmen, haette sie ohne Maus unerreichbar gemacht; Accessibility steht ueber der visuellen
 * Praeferenz (CLAUDE.md, Prioritaeten).
 */

/** Die Klasse, ueber die die Innenkarte ihre Aktion ein- und ausblendet. */
const AKTION_KLASSE = 'innenkarte-aktion';

/** Kantenlaenge des Kuerzels in einer Innenkarte (CLAUDE-design.md, „Bausteine": 48 px Kreis). */
const MAL = 48;

export interface InnenkarteProps {
  /** Der Name des Objekts — und die Quelle des Kuerzels im Mal. */
  readonly name: string;
  /** Die Zeile unter dem Namen, etwa die Rolle. */
  readonly unterzeile?: string;
  /** Die Toenung des Mals. Vorgabe Flieder — die neutrale Kategorie fuer Personen. */
  readonly toenung?: ToenungName;
  /** Die Kontaktwege: Links und Angaben mit Symbol. */
  readonly children?: ReactNode;
  /** Die Aktion rechts, meist ein {@link AktionsMenue}. Erscheint im Hover und im Fokus. */
  readonly aktion?: ReactNode;
}

export default function Innenkarte({
  name,
  unterzeile,
  toenung = 'flieder',
  children,
  aktion,
}: InnenkarteProps) {
  return (
    <Box
      sx={(theme) => ({
        display: 'flex',
        alignItems: 'flex-start',
        gap: '14px',
        padding: '18px',
        borderRadius: `${RADIUS_MITTEL}px`,
        background: theme.vars.palette.kupferwolke.flaecheWeich,
        transition: 'background .15s ease, box-shadow .15s ease',
        '&:hover': {
          background: theme.vars.palette.kupferwolke.flaeche,
          boxShadow: theme.vars.palette.kupferwolke.schatten.hoch,
        },
        [`& .${AKTION_KLASSE}`]: { opacity: 0, transition: 'opacity .15s ease' },
        [`&:hover .${AKTION_KLASSE}`]: { opacity: 1 },
        [`&:focus-within .${AKTION_KLASSE}`]: { opacity: 1 },
        '@media (hover: none)': {
          [`& .${AKTION_KLASSE}`]: { opacity: 1 },
        },
      })}
    >
      <Mal name={name} groesse={MAL} toenung={toenung} />
      <Box sx={{ minWidth: 0 }}>
        <Typography variant="h4" component="div">
          {name}
        </Typography>
        {unterzeile === undefined ? null : (
          <Typography
            data-testid="innenkarte-unterzeile"
            sx={(theme) => ({
              fontSize: 13,
              color: theme.vars.palette.kupferwolke.textMatt,
              margin: '2px 0 10px',
            })}
          >
            {unterzeile}
          </Typography>
        )}
        {children === undefined ? null : (
          <Box
            data-testid="innenkarte-kontakt"
            sx={(theme) => ({
              display: 'flex',
              flexDirection: 'column',
              gap: '6px',
              fontSize: 13,
              color: theme.vars.palette.kupferwolke.textMatt,
              // Die Kontaktwege bringen keine eigenen Farben mit: Ein Aufrufer uebergibt ein
              // schlichtes `a`, und die Gestalt steht hier (wie in der `Tafel`).
              '& a': {
                display: 'flex',
                alignItems: 'center',
                gap: '6px',
                fontWeight: 500,
                textDecoration: 'none',
                color: theme.vars.palette.kupferwolke.kupfer,
              },
              '& span': { display: 'flex', alignItems: 'center', gap: '6px' },
            })}
          >
            {children}
          </Box>
        )}
      </Box>
      {aktion === undefined ? null : (
        <Box className={AKTION_KLASSE} sx={{ marginLeft: 'auto', flex: 'none' }}>
          {aktion}
        </Box>
      )}
    </Box>
  );
}

/**
 * Das Raster der Innenkarten: zwei Spalten, unter 900 px eine (Vorlage `.personen` Z. 81).
 *
 * Kein `role="list"`: Die Innenkarten tragen Namen, Kontaktwege und eine Aktion — als
 * Listeneintraege angesagt, verloere jede von ihnen die eigene Struktur.
 */
export function InnenkartenRaster({ children }: { readonly children: ReactNode }) {
  return (
    <Box
      data-testid="innenkarten-raster"
      sx={(theme) => ({
        display: 'grid',
        gap: '14px',
        gridTemplateColumns: 'repeat(2, 1fr)',
        [theme.breakpoints.down('md')]: { gridTemplateColumns: '1fr' },
      })}
    >
      {children}
    </Box>
  );
}

export interface HinzufuegenKachelProps {
  /** Das Ziel — die Maske, in der das neue Objekt entsteht. */
  readonly to: string;
  readonly children: ReactNode;
  readonly symbol?: ReactNode;
}

/**
 * Die Hinzufuegen-Kachel: die **letzte** Kachel eines Innenkarten-Rasters (Vorlage `.neu` Z. 91–92,
 * HTML Z. 185).
 *
 * Ein echter Link und kein Knopf: Sie fuehrt auf eine Maske, und ein Weg gehoert in ein `a` mit
 * `href` — sonst laesst er sich nicht in einem neuen Reiter oeffnen und wird als Schalter
 * angesagt, obwohl er die Seite wechselt (dieselbe Begruendung wie bei `KupferTaste`).
 *
 * Der gestrichelte Rand steht in „Linie" und traegt damit keine Aussage; im Hover wechselt er auf
 * Kupfer-Glanz und die Schrift auf Kupfer. Erkennbar ist die Kachel an ihrer Aufschrift, nicht am
 * Rand (CLAUDE-design.md, „Bekannte Grenzen").
 */
export function HinzufuegenKachel({ to, children, symbol }: HinzufuegenKachelProps) {
  return (
    <Box
      component={RouterLink}
      to={to}
      sx={(theme) => ({
        display: 'grid',
        placeItems: 'center',
        gap: '8px',
        gridAutoFlow: 'column',
        minHeight: 120,
        padding: '18px',
        borderRadius: `${RADIUS_MITTEL}px`,
        border: `2px dashed ${theme.vars.palette.kupferwolke.linie}`,
        background: 'transparent',
        fontSize: 14,
        fontWeight: 600,
        textDecoration: 'none',
        color: theme.vars.palette.kupferwolke.textSchwach,
        transition: 'border-color .15s ease, color .15s ease',
        '&:hover': {
          borderColor: theme.vars.palette.kupferwolke.kupferGlanz,
          color: theme.vars.palette.kupferwolke.kupfer,
        },
      })}
    >
      {symbol === undefined ? null : <TastenSymbol>{symbol}</TastenSymbol>}
      {children}
    </Box>
  );
}
