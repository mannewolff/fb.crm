import Box from '@mui/material/Box';
import Paper from '@mui/material/Paper';
import Typography from '@mui/material/Typography';
import type { ReactNode } from 'react';

import { PANEL_RADIUS } from '../theme';
import BrandMark from './BrandMark';

/**
 * Die Platte der Auth-Seiten: mittig auf dem Grund, mit Marke, Titel, Inhalt und Fuss
 * (Vorlage `.platte` Z. 543–549, `body`-Grund Z. 152–162).
 *
 * Die Marke traegt hier <b>keine</b> Versionsnummer (K15, E10) — siehe {@link BrandMark}.
 */

/** Breite der Platte; darueber hinaus wuerde ein Formular mit zwei Feldern auseinanderlaufen. */
const PLATTE_BREITE = 380;

export interface AuthCardProps {
  readonly titel: string;
  readonly children: ReactNode;
  /** Nebenwege unter dem Formular, etwa „Passwort vergessen?". */
  readonly fuss?: ReactNode;
}

export default function AuthCard({ titel, children, fuss }: AuthCardProps) {
  return (
    <Box
      component="main"
      sx={{ minHeight: '100vh', display: 'grid', placeItems: 'center', padding: 2 }}
    >
      <Paper
        elevation={0}
        sx={(theme) => ({
          width: '100%',
          maxWidth: PLATTE_BREITE,
          padding: 3,
          borderRadius: `${PANEL_RADIUS}px`,
          border: `1px solid ${theme.vars.palette.kupferwarte.rand}`,
          background: theme.vars.palette.kupferwarte.platte,
          boxShadow: theme.vars.palette.kupferwarte.schatten.platte,
          display: 'flex',
          flexDirection: 'column',
          gap: 2.5,
        })}
      >
        <BrandMark />
        <Typography variant="h1" sx={{ fontSize: 20, fontWeight: 600 }}>
          {titel}
        </Typography>
        {children}
        {fuss === undefined ? null : (
          <Box
            sx={(theme) => ({
              paddingTop: 1,
              borderTop: `1px solid ${theme.vars.palette.kupferwarte.rand}`,
              fontSize: 13,
              color: theme.vars.palette.kupferwarte.textMatt,
            })}
          >
            {fuss}
          </Box>
        )}
      </Paper>
    </Box>
  );
}
