import Box from '@mui/material/Box';
import Drawer from '@mui/material/Drawer';
import Link from '@mui/material/Link';
import { useTheme } from '@mui/material/styles';
import useMediaQuery from '@mui/material/useMediaQuery';
import { useRef, useState } from 'react';
import type { ReactNode } from 'react';

import { CONTROL_RADIUS } from '../theme';
import { KopfAktionProvider } from './KopfAktion';
import NavRail from './NavRail';
import TopBar from './TopBar';

/**
 * Der Rahmen der angemeldeten Oberflaeche: Schiene links, rechts Kopf und Inhalt (Vorlage
 * `.warte` Z. 199–204).
 *
 * Drei Betriebsarten, und jede steht hier im Code und nicht nur im Stylesheet — eine per CSS
 * versteckte Schiene bliebe im Baum und haette im Tabulatorweg keinen Ort:
 *
 * <ul>
 *   <li><b>Ab 900 px</b> steht die Schiene offen als erste Spalte.</li>
 *   <li><b>Zwischen 760 und 900 px</b> liegt sie hinter der Schaltflaeche links im Kopf
 *       (CLAUDE-design.md, „Mindestbreite"; E18, Entscheid Manne 2026-09-24). Damit ist die
 *       Abweichung abgeloest, die hier galt, solange die Schiene keine fachlichen Ziele trug.</li>
 *   <li><b>Unterhalb von 760 px</b> entfaellt sie ganz (E14, Vorlage Z. 1084–1090) — dort fehlt
 *       auch der Platz fuer eine Schaltflaeche neben dem Nutzer-Mal.</li>
 * </ul>
 *
 * Die geoeffnete Schiene laeuft als {@link Drawer} ueber dem Inhalt. Der Drawer ist ein Dialog:
 * Escape und der Klick daneben schliessen ihn, der Fokus bleibt drin, und beim Schliessen kehrt er
 * dorthin zurueck, wo er beim Oeffnen stand — an die Schaltflaeche. Das gilt auch, wenn ein
 * gewaehlter Eintrag ihn schliesst; deshalb genuegt `setSchieneOffen(false)` und es braucht kein
 * eigenes Fokus-Kommando, das mit dem Fokusfang des Dialogs streiten wuerde.
 *
 * Der Rahmen spannt ausserdem den Kontext der Kopfaktion auf ({@link KopfAktionProvider}): Er muss
 * Kopf und Inhalt gemeinsam umschliessen, damit eine Ansicht ihre Hauptaktion in den Kopf legen
 * kann, der ueber ihr steht (E11).
 *
 * Der Skip-Link steht als erstes im Tabulatorweg und setzt den Fokus auf den Inhalt — ohne ihn
 * muesste jeder Tastaturnutzer vor jedem Inhalt durch Schiene und Kopf.
 */

/**
 * Die Schaltflaeche, hinter der die Schiene liegt: erhabene Platte mit drei Balken, links im Kopf
 * (Vorlage `.taste` CSS Z. 315–330).
 *
 * Sie traegt keinen sichtbaren Text — das Symbol allein steht im Kopf, wie das Nutzer-Mal auf der
 * anderen Seite. Den zugaenglichen Namen traegt deshalb `aria-label`, und `aria-expanded` sagt,
 * ob die Schiene gerade offen ist.
 */
function SchieneSchalter({
  offen,
  oeffnen,
}: {
  readonly offen: boolean;
  readonly oeffnen: () => void;
}) {
  return (
    <Box
      component="button"
      type="button"
      aria-label="Navigation öffnen"
      aria-expanded={offen}
      onClick={oeffnen}
      sx={(t) => ({
        display: 'grid',
        placeItems: 'center',
        width: 32,
        height: 32,
        flex: 'none',
        padding: 0,
        cursor: 'pointer',
        borderRadius: `${CONTROL_RADIUS}px`,
        border: `1px solid ${t.vars.palette.kupferwarte.rand}`,
        background: `linear-gradient(180deg, ${t.vars.palette.kupferwarte.platteHoch}, ${t.vars.palette.kupferwarte.platte})`,
        boxShadow: t.vars.palette.kupferwarte.schatten.taste,
        color: t.vars.palette.kupferwarte.textMatt,
        '&:hover': { color: t.vars.palette.kupferwarte.text },
      })}
    >
      <Box component="svg" viewBox="0 0 16 16" fill="none" aria-hidden sx={{ width: 16, height: 16 }}>
        <path
          d="M2.5 4h11M2.5 8h11M2.5 12h11"
          stroke="currentColor"
          strokeWidth="1.5"
          strokeLinecap="round"
        />
      </Box>
    </Box>
  );
}
export default function AppShell({ children }: { readonly children: ReactNode }) {
  const theme = useTheme();
  const mitSchiene = useMediaQuery(theme.breakpoints.up('sm'), { noSsr: true });
  const offeneSchiene = useMediaQuery(theme.breakpoints.up('md'), { noSsr: true });
  const [schieneOffen, setSchieneOffen] = useState(false);
  const inhalt = useRef<HTMLElement>(null);
  const hinterSchaltflaeche = mitSchiene && !offeneSchiene;

  return (
    <KopfAktionProvider>
      <Box
        sx={{
          display: 'grid',
          gridTemplateColumns: offeneSchiene ? 'auto minmax(0, 1fr)' : 'minmax(0, 1fr)',
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
        {offeneSchiene ? <NavRail /> : null}
        {hinterSchaltflaeche ? (
          <Drawer
            anchor="left"
            open={schieneOffen}
            onClose={() => {
              setSchieneOffen(false);
            }}
            // Die Schiene bringt Flaeche, Rand und Tiefe selbst mit; das Papier des Drawers
            // wuerde sie sonst mit einer zweiten weissen Flaeche unterlegen.
            slotProps={{ paper: { sx: { background: 'none', border: 0, boxShadow: 'none' } } }}
          >
            <NavRail
              onWahl={() => {
                setSchieneOffen(false);
              }}
            />
          </Drawer>
        ) : null}
        <Box sx={{ minWidth: 0, display: 'flex', flexDirection: 'column' }}>
          <TopBar
            schalter={
              hinterSchaltflaeche ? (
                <SchieneSchalter
                  offen={schieneOffen}
                  oeffnen={() => {
                    setSchieneOffen(true);
                  }}
                />
              ) : undefined
            }
          />
          <Box component="main" id="inhalt" ref={inhalt} tabIndex={-1} sx={{ outline: 'none' }}>
            {children}
          </Box>
        </Box>
      </Box>
    </KopfAktionProvider>
  );
}
