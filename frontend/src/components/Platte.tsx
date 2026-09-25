import Box from '@mui/material/Box';
import Paper from '@mui/material/Paper';
import Typography from '@mui/material/Typography';
import type { ReactNode } from 'react';

import { PANEL_RADIUS } from '../theme';

/**
 * Die Platte: die tragende Flaeche des Inhaltsbereichs (Vorlage `.platte` Z. 543–549,
 * `.platte-kopf` Z. 550–559).
 *
 * Der Kopf entsteht nur mit einem Titel. Eine Platte ohne Titel — etwa die Liste unter einer
 * eigenen Werkzeugleiste — traegt sonst eine leere Leiste mit einer Trennlinie darunter.
 *
 * Der Titel ist eine Ueberschrift der zweiten Ebene: Die Seiten tragen genau eine `h1`, und die
 * Platten darunter gliedern sie. Wer mit dem Screenreader durch die Ueberschriften springt, findet
 * damit die Abschnitte einer Ansicht.
 */
export interface PlatteProps {
  readonly children: ReactNode;
  /** Ohne Titel entsteht kein Kopf. */
  readonly titel?: string;
  /** Beiwerk neben dem Titel, etwa eine Zahl. */
  readonly notiz?: string;
  /** Rechts im Kopf: Schalter, Filter, Nebenwege. */
  readonly werkzeug?: ReactNode;
}

export default function Platte({ children, titel, notiz, werkzeug }: PlatteProps) {
  return (
    <Paper
      elevation={0}
      sx={(theme) => ({
        borderRadius: `${PANEL_RADIUS}px`,
        border: `1px solid ${theme.vars.palette.kupferwarte.rand}`,
        background: theme.vars.palette.kupferwarte.platte,
        boxShadow: theme.vars.palette.kupferwarte.schatten.platte,
        overflow: 'hidden',
      })}
    >
      {titel === undefined ? null : (
        <Box
          sx={(theme) => ({
            display: 'flex',
            alignItems: 'center',
            gap: 1.5,
            flexWrap: 'wrap',
            padding: '13px 16px',
            borderBottom: `1px solid ${theme.vars.palette.kupferwarte.rand}`,
            background: `linear-gradient(180deg, ${theme.vars.palette.kupferwarte.platteHoch}, ${theme.vars.palette.kupferwarte.platte})`,
          })}
        >
          <Typography variant="h2" sx={{ fontSize: 13.5, fontWeight: 600 }}>
            {titel}
          </Typography>
          {notiz === undefined ? null : (
            <Typography
              sx={(theme) => ({
                fontSize: 11.5,
                color: theme.vars.palette.kupferwarte.textSchwach,
              })}
            >
              {notiz}
            </Typography>
          )}
          {werkzeug === undefined ? null : (
            <Box sx={{ marginLeft: 'auto', display: 'flex', alignItems: 'center', gap: 1 }}>
              {werkzeug}
            </Box>
          )}
        </Box>
      )}
      {children}
    </Paper>
  );
}
