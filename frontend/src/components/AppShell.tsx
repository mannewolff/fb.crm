import Box from '@mui/material/Box';
import Link from '@mui/material/Link';
import { useTheme } from '@mui/material/styles';
import useMediaQuery from '@mui/material/useMediaQuery';
import { useRef } from 'react';
import type { ReactNode } from 'react';

import { KopfAktionProvider } from './KopfAktion';
import NavRail from './NavRail';
import TopBar from './TopBar';

/**
 * Der Rahmen der angemeldeten Oberflaeche: Schiene links, rechts Kopf und Inhalt (Vorlage
 * `.warte` Z. 199–204).
 *
 * Unterhalb von 760 px entfaellt die Schiene (E14, Vorlage Z. 1084–1090). Das steht hier im
 * Code und nicht nur im Stylesheet: Eine per CSS versteckte Schiene bliebe im Baum und haette
 * im Tabulatorweg keinen Ort. **Abweichung von CLAUDE-design.md, die mit dem GO vorliegt:**
 * Zwischen 768 und 900 px steht die Schiene offen; die Schaltflaeche, hinter der sie dort liegen
 * soll, kommt, sobald die Schiene fachliche Ziele traegt.
 *
 * Der Rahmen spannt ausserdem den Kontext der Kopfaktion auf ({@link KopfAktionProvider}): Er muss
 * Kopf und Inhalt gemeinsam umschliessen, damit eine Ansicht ihre Hauptaktion in den Kopf legen
 * kann, der ueber ihr steht (E11).
 *
 * Der Skip-Link steht als erstes im Tabulatorweg und setzt den Fokus auf den Inhalt — ohne ihn
 * muesste jeder Tastaturnutzer vor jedem Inhalt durch Schiene und Kopf.
 */
export default function AppShell({ children }: { readonly children: ReactNode }) {
  const theme = useTheme();
  const mitSchiene = useMediaQuery(theme.breakpoints.up('sm'), { noSsr: true });
  const inhalt = useRef<HTMLElement>(null);

  return (
    <KopfAktionProvider>
      <Box
        sx={{
          display: 'grid',
          gridTemplateColumns: mitSchiene ? 'auto minmax(0, 1fr)' : 'minmax(0, 1fr)',
          minHeight: '100vh',
        }}
      >
        <Link
          href="#inhalt"
          onClick={(ereignis) => {
            ereignis.preventDefault();
            inhalt.current?.focus();
          }}
          sx={(t) => ({
            position: 'absolute',
            left: 8,
            top: -48,
            zIndex: 30,
            padding: '6px 12px',
            borderRadius: '6px',
            background: t.vars.palette.kupferwarte.platte,
            boxShadow: t.vars.palette.kupferwarte.schatten.hoch,
            '&:focus': { top: 8 },
          })}
        >
          Zum Inhalt springen
        </Link>
        {mitSchiene ? <NavRail /> : null}
        <Box sx={{ minWidth: 0, display: 'flex', flexDirection: 'column' }}>
          <TopBar />
          <Box component="main" id="inhalt" ref={inhalt} tabIndex={-1} sx={{ outline: 'none' }}>
            {children}
          </Box>
        </Box>
      </Box>
    </KopfAktionProvider>
  );
}
