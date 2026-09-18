import { ThemeProvider } from '@mui/material/styles';
import { render } from '@testing-library/react';
import type { ReactNode } from 'react';

import { theme } from '../theme';

/**
 * Rendert mit dem Theme der Kupferwarte.
 *
 * Ohne den Provider traegt MUI sein Standard-Theme, und `theme.vars.palette.kupferwarte.*`
 * — die Wertequelle aller Flaechen, Raender und Tiefen — faellt weg. Jede Ansicht, die
 * darauf zugreift, bricht dann im Test an einer Stelle, die mit dem Testfall nichts zu tun
 * hat.
 */
export function renderMitTheme(ui: ReactNode) {
  return render(<ThemeProvider theme={theme}>{ui}</ThemeProvider>);
}
