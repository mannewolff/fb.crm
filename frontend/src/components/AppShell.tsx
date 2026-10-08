import Box from '@mui/material/Box';
import Drawer from '@mui/material/Drawer';
import Link from '@mui/material/Link';
import { useTheme } from '@mui/material/styles';
import useMediaQuery from '@mui/material/useMediaQuery';
import { IconMenu2 } from '@tabler/icons-react';
import { useRef, useState } from 'react';
import type { ReactNode } from 'react';

import { ABSTAND_BUEHNE, KOPFTASTE, RADIUS_KLEIN, RADIUS_RUND } from '../theme';
import { KopfPfadProvider } from './KopfPfad';
import NavRail from './NavRail';
import TopBar from './TopBar';

/**
 * Der Rahmen der angemeldeten Oberflaeche: Schiene links, rechts die Buehne mit Kopf und Inhalt
 * (Vorlage `.app` Z. 28, `main` Z. 45).
 *
 * Die Masse stehen in der Designquelle („Rahmen"): 20 px Aussenabstand, 24 px Spalt zwischen
 * Schiene und Buehne, die Buehne hoechstens 1180 px breit, 16 px zwischen ihren Bereichen.
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
 * Der Rahmen spannt ausserdem den Kontext des Kopfes auf ({@link KopfPfadProvider}): Er muss Kopf
 * und Inhalt gemeinsam umschliessen, damit eine Ansicht ihren Pfad in den Kopf legen kann, der
 * ueber ihr steht (E6).
 *
 * Der Skip-Link steht als erstes im Tabulatorweg und setzt den Fokus auf den Inhalt — ohne ihn
 * muesste jeder Tastaturnutzer vor jedem Inhalt durch Schiene und Kopf.
 */

/**
 * Die Schaltflaeche, hinter der die Schiene liegt: Icontaste links im Kopf, vor dem Pfad
 * (Vorlage `.icontaste` Z. 67–68).
 *
 * Sie traegt keinen sichtbaren Text — das Symbol allein steht im Kopf. Den zugaenglichen Namen
 * traegt deshalb `aria-label`, und `aria-expanded` sagt, ob die Schiene gerade offen ist.
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
        width: KOPFTASTE,
        height: KOPFTASTE,
        flex: 'none',
        padding: 0,
        cursor: 'pointer',
        // Runde Kopftaste: Kreis 36 px auf „Flaeche weich", Symbol in Text matt, Hover Pfirsich
        // (CLAUDE-design.md, „Tasten").
        borderRadius: `${RADIUS_RUND}px`,
        border: 0,
        background: t.vars.palette.kupferwolke.flaecheWeich,
        color: t.vars.palette.kupferwolke.textMatt,
        '&:hover': {
          background: t.vars.palette.kupferwolke.toenung.pfirsich.flaeche,
          color: t.vars.palette.kupferwolke.toenung.pfirsich.schrift,
        },
      })}
    >
      <IconMenu2 size={20} stroke={1.7} aria-hidden />
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
    <KopfPfadProvider>
      <Box
        sx={{
          display: 'grid',
          gridTemplateColumns: offeneSchiene ? 'auto minmax(0, 1fr)' : 'minmax(0, 1fr)',
          gap: '24px',
          padding: '20px',
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
            borderRadius: `${RADIUS_KLEIN}px`,
            background: t.vars.palette.kupferwolke.flaeche,
            boxShadow: t.vars.palette.kupferwolke.schatten.hoch,
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
            // wuerde sie sonst mit einer zweiten weissen Flaeche unterlegen. Der Aussenabstand
            // des Rahmens steht hier am Papier, damit die Karte auch hier frei schwebt.
            slotProps={{
              paper: { sx: { background: 'none', border: 0, boxShadow: 'none', padding: '20px' } },
            }}
          >
            <NavRail
              onWahl={() => {
                setSchieneOffen(false);
              }}
            />
          </Drawer>
        ) : null}
        <Box
          data-testid="buehne"
          sx={{
            minWidth: 0,
            width: '100%',
            maxWidth: 1180,
            display: 'flex',
            flexDirection: 'column',
            gap: ABSTAND_BUEHNE,
          }}
        >
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
    </KopfPfadProvider>
  );
}
