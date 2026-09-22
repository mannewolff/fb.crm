import Box from '@mui/material/Box';

import UserMenu from './UserMenu';

/**
 * Der Kopf: klebend, getoent, mit Weichzeichner (Vorlage `.kopf` Z. 284–299).
 *
 * Links steht in diesem Stand nichts, rechts allein das Nutzer-Mal (K13). Pfad, Suche, `⌘K`,
 * Reiterleiste und Kennzahlen der Vorlage gehoeren zur Fachlichkeit von kanban-kit und werden
 * nicht uebernommen.
 */
export default function TopBar() {
  return (
    <Box
      component="header"
      sx={(theme) => ({
        display: 'flex',
        alignItems: 'center',
        gap: 2,
        padding: { xs: '12px 16px', sm: '14px 26px' },
        borderBottom: `1px solid ${theme.vars.palette.kupferwarte.rand}`,
        background: `color-mix(in srgb, ${theme.vars.palette.kupferwarte.grund} 86%, ${theme.vars.palette.kupferwarte.platte})`,
        position: 'sticky',
        top: 0,
        zIndex: 20,
        backdropFilter: 'blur(10px)',
      })}
    >
      <Box data-testid="kopf-links" sx={{ flex: 1, minWidth: 0 }} />
      <UserMenu />
    </Box>
  );
}
