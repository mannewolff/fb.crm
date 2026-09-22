import Box from '@mui/material/Box';

import { PANEL_RADIUS } from '../theme';

/**
 * Das leere Inhaltspanel: eine Platte auf der Buehne, ohne Ueberschrift und ohne Inhalt.
 *
 * Es dient `/`, `/administration` und `/dokumentation`, bis dort Fachlichkeit einzieht. Eine
 * Ueberschrift wie „Administration" stuende hier fuer etwas, das es noch nicht gibt.
 */
export default function EmptyPanel() {
  return (
    <Box sx={{ padding: { xs: 2, sm: '22px 26px' } }}>
      <Box
        data-testid="leeres-panel"
        sx={(theme) => ({
          minHeight: 'calc(100vh - 140px)',
          borderRadius: `${PANEL_RADIUS}px`,
          border: `1px solid ${theme.vars.palette.kupferwarte.rand}`,
          background: theme.vars.palette.kupferwarte.platte,
          boxShadow: theme.vars.palette.kupferwarte.schatten.platte,
        })}
      />
    </Box>
  );
}
