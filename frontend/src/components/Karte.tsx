import Box from '@mui/material/Box';
import Paper from '@mui/material/Paper';
import Typography from '@mui/material/Typography';
import type { ReactNode } from 'react';

import { RADIUS_GROSS, RADIUS_RUND } from '../theme';

/**
 * Die Karte: die tragende, schwebende Flaeche der Buehne (Vorlage `.karte` Z. 53, `.kopfzeile`
 * Z. 79–80, `.karte h2 .anzahl` Z. 78).
 *
 * Sie traegt den Namen der Designquelle (E4): Die Kupferwarte kannte an dieser Stelle einen anderen
 * Baustein, und ein alter Name im Code fuehrt kuenftige Pakete in die alte Designsprache zurueck.
 *
 * **Karten statt Linien** (CLAUDE-design.md, Leitgedanke): Die Karte gliedert durch Flaeche, Radius
 * und weichen Schatten. Unter dem Kartenkopf steht darum **keine** Trennlinie mehr; den Abstand
 * traegt der Kopf selbst.
 *
 * Der Kopf entsteht nur mit einem Titel. Eine Karte ohne Titel — etwa eine Meldung oder eine Liste
 * unter einer eigenen Werkzeugleiste — truege sonst eine leere Leiste.
 *
 * Der Titel ist eine Ueberschrift der zweiten Ebene: Die Seiten tragen genau eine `h1`, und die
 * Karten darunter gliedern sie. Wer mit dem Screenreader durch die Ueberschriften springt, findet
 * damit die Abschnitte einer Ansicht.
 */
export interface KarteProps {
  readonly children: ReactNode;
  /** Ohne Titel entsteht kein Kopf. */
  readonly titel?: string;
  /** Die Zahl der Eintraege, als runde Plakette neben dem Titel. */
  readonly anzahl?: number;
  /** Beiwerk neben dem Titel, etwa ein Zusatz in Worten. */
  readonly notiz?: string;
  /** Rechts im Kopf: Filter, Waehler, die Hauptaktion einer Liste. */
  readonly werkzeug?: ReactNode;
}

/** Innenabstand einer Karte (CLAUDE-design.md, „Rahmen"; Vorlage `.karte` Z. 53). */
const INNENABSTAND = 28;

/** Abstand zwischen Kartenkopf und Inhalt (Vorlage `.kopfzeile` Z. 79). */
const KOPF_ABSTAND = 18;

export default function Karte({ children, titel, anzahl, notiz, werkzeug }: KarteProps) {
  return (
    <Paper
      elevation={0}
      sx={(theme) => ({
        borderRadius: `${RADIUS_GROSS}px`,
        background: theme.vars.palette.kupferwolke.flaeche,
        boxShadow: theme.vars.palette.kupferwolke.schatten.karte,
        padding: `${String(INNENABSTAND)}px`,
        overflow: 'hidden',
      })}
    >
      {titel === undefined ? null : (
        <Box
          data-testid="karte-kopf"
          sx={{
            display: 'flex',
            alignItems: 'center',
            gap: 1.25,
            flexWrap: 'wrap',
            marginBottom: `${String(KOPF_ABSTAND)}px`,
          }}
        >
          <Typography variant="h2">{titel}</Typography>
          {anzahl === undefined ? null : (
            <Box
              component="span"
              data-testid="karte-anzahl"
              sx={(theme) => ({
                fontSize: 12,
                fontWeight: 600,
                borderRadius: `${RADIUS_RUND}px`,
                padding: '2px 9px',
                color: theme.vars.palette.kupferwolke.textMatt,
                background: theme.vars.palette.kupferwolke.flaecheWeich,
              })}
            >
              {anzahl}
            </Box>
          )}
          {notiz === undefined ? null : (
            <Typography
              sx={(theme) => ({
                fontSize: 12.5,
                color: theme.vars.palette.kupferwolke.textSchwach,
              })}
            >
              {notiz}
            </Typography>
          )}
          {werkzeug === undefined ? null : (
            <Box
              sx={{
                marginLeft: 'auto',
                display: 'flex',
                alignItems: 'center',
                gap: 1.25,
                flexWrap: 'wrap',
              }}
            >
              {werkzeug}
            </Box>
          )}
        </Box>
      )}
      {children}
    </Paper>
  );
}
