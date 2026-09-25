import Box from '@mui/material/Box';

import { useKopfAktionPlatz } from './KopfAktion';
import UserMenu from './UserMenu';

/**
 * Der Kopf: klebend, getoent, mit Weichzeichner (Vorlage `.kopf` Z. 284–299).
 *
 * Links steht nichts, rechts der Platz fuer die Hauptaktion der Ansicht und dahinter das
 * Nutzer-Mal (K13, Vorlage Z. 1192–1196). Welche Aktion dort steht, bringt die Ansicht selbst mit
 * ({@link KopfAktion}); ohne Aktion bleibt der Platz leer. Pfad, Suche, `⌘K`, Reiterleiste und
 * Kennzahlen der Vorlage gehoeren zur Fachlichkeit von kanban-kit und werden nicht uebernommen.
 */
export default function TopBar() {
  const meldePlatz = useKopfAktionPlatz();

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
      <Box
        data-testid="kopf-aktion"
        ref={meldePlatz}
        sx={{ display: 'flex', alignItems: 'center', gap: 1 }}
      />
      <UserMenu />
    </Box>
  );
}
