import Box from '@mui/material/Box';
import type { ReactNode } from 'react';

/**
 * Das Symbol links in einer Taste (Vorlage `.taste` Z. 63: `gap:8px`).
 *
 * Es **stuetzt** das Wort und ersetzt es nie (CLAUDE-design.md, „Zustandsformen"). Darum bleibt es
 * aus dem Baum der Hilfsmittel heraus: Der zugaengliche Name einer Taste ist ihre Aufschrift, und
 * ein mitgelesenes Symbol haengte ihm einen zweiten, stummen Namensteil an.
 *
 * Ein eigener Baustein, weil Kupfertaste und weiche Taste ihn gleich tragen — zwei Abschriften
 * liefen beim naechsten Nachziehen der Vorlage auseinander.
 */
export default function TastenSymbol({ children }: { readonly children: ReactNode }) {
  return (
    <Box
      component="span"
      data-testid="taste-symbol"
      aria-hidden
      sx={{ display: 'flex', flex: 'none' }}
    >
      {children}
    </Box>
  );
}
