import Box from '@mui/material/Box';

import { RADIUS_GROSS } from '../theme';

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
          borderRadius: `${RADIUS_GROSS}px`,
          background: theme.vars.palette.kupferwolke.flaeche,
          boxShadow: theme.vars.palette.kupferwolke.schatten.karte,
        })}
      />
    </Box>
  );
}
