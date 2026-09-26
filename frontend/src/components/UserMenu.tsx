import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Menu from '@mui/material/Menu';
import MenuItem from '@mui/material/MenuItem';
import { IconSelector } from '@tabler/icons-react';
import { useId, useState } from 'react';
import { useNavigate } from 'react-router-dom';

import { useAuth } from '../auth/AuthContext';
import { initialen } from '../lib/initials';
import { RADIUS_KACHEL, RADIUS_RUND } from '../theme';

/**
 * Die Nutzerkarte im Fuss der Schiene und ihr Menue (K13, K14, E7).
 *
 * Sie stand bis zur Kupferwolke als rundes Mal im Kopf; die Vorlage zeigt sie als Kachel „Flaeche
 * weich" unten in der Schiene (`.schiene .fuss` Z. 42, HTML Z. 118–122). Sichtbar sind Kuerzel,
 * `displayName` und E-Mail. Eine Rolle steht nicht dabei: `Konto` kennt im Frontend keine, und
 * sie wird dafuer nicht eingefuehrt.
 *
 * Das Kuerzel liegt auf der Toenung Flieder — der neutralen Kategorie fuer Personen
 * (CLAUDE-design.md, „Toenungen"). Neben dem Namen sagt es nichts Eigenes und ist darum
 * `aria-hidden`; den Namen der Taste traegt `aria-label`, damit er auch eingeklappt steht, wo
 * nur noch das Kuerzel zu sehen ist.
 *
 * Das Menue hat genau einen Eintrag: „Abmelden". Eine Profilseite, Einstellungen oder ein
 * Wechsel des Erscheinungsbilds gehoeren nicht in diesen Stand — ein Eintrag, der ins Leere
 * fuehrt, waere schlechter als keiner.
 */

/** Kantenlaenge des Kuerzels (Vorlage HTML Z. 119). */
const MAL = 38;

const ABMELDEN_GESCHEITERT = 'Die Abmeldung ist gerade nicht möglich. Bitte erneut versuchen.';

/** Eine Zeile der Karte: schneidet ab, statt die Schiene zu verbreitern. */
const zeileStil = {
  display: 'block',
  minWidth: 0,
  whiteSpace: 'nowrap',
  overflow: 'hidden',
  textOverflow: 'ellipsis',
} as const;

export default function UserMenu({ kompakt = false }: { readonly kompakt?: boolean }) {
  const { sitzung, abmelden } = useAuth();
  const navigate = useNavigate();
  const menueId = useId();
  const [anker, setAnker] = useState<HTMLElement | null>(null);
  const [fehler, setFehler] = useState<string | null>(null);

  if (sitzung.status !== 'angemeldet') {
    return null;
  }
  const { displayName, email } = sitzung.konto;

  const abmeldenUndGehen = async () => {
    setAnker(null);
    setFehler(null);
    try {
      await abmelden();
      navigate('/anmelden', { replace: true });
    } catch {
      setFehler(ABMELDEN_GESCHEITERT);
    }
  };

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
      {fehler === null ? null : (
        <Alert severity="error" sx={{ fontSize: 12 }}>
          {fehler}
        </Alert>
      )}
      <Box
        component="button"
        type="button"
        aria-label={`Nutzermenü: ${displayName}`}
        aria-haspopup="menu"
        aria-expanded={anker !== null}
        aria-controls={anker === null ? undefined : menueId}
        onClick={(ereignis) => {
          setAnker(ereignis.currentTarget);
        }}
        sx={(theme) => ({
          display: 'flex',
          alignItems: 'center',
          gap: '10px',
          width: '100%',
          minWidth: 0,
          padding: kompakt ? '7px' : '12px 14px',
          borderRadius: `${RADIUS_KACHEL}px`,
          border: 0,
          cursor: 'pointer',
          fontFamily: 'inherit',
          textAlign: 'left',
          color: theme.vars.palette.kupferwolke.text,
          background: theme.vars.palette.kupferwolke.flaecheWeich,
          transition: 'background .15s ease',
          '&:hover': { background: theme.vars.palette.kupferwolke.toenung.pfirsich.flaeche },
        })}
      >
        <Box
          data-testid="nutzer-mal"
          aria-hidden
          sx={(theme) => ({
            width: MAL,
            height: MAL,
            borderRadius: `${RADIUS_RUND}px`,
            flex: 'none',
            display: 'grid',
            placeItems: 'center',
            fontSize: 13.5,
            fontWeight: 700,
            color: theme.vars.palette.kupferwolke.toenung.flieder.schrift,
            background: theme.vars.palette.kupferwolke.toenung.flieder.flaeche,
          })}
        >
          {initialen(displayName)}
        </Box>
        {kompakt ? null : (
          <>
            <Box sx={{ minWidth: 0 }}>
              <Box component="span" sx={{ ...zeileStil, fontSize: 13.5, fontWeight: 700 }}>
                {displayName}
              </Box>
              <Box
                component="span"
                sx={(theme) => ({
                  ...zeileStil,
                  fontSize: 12,
                  fontWeight: 500,
                  color: theme.vars.palette.kupferwolke.textSchwach,
                })}
              >
                {email}
              </Box>
            </Box>
            <Box
              component="span"
              sx={(theme) => ({
                marginLeft: 'auto',
                flex: 'none',
                display: 'grid',
                color: theme.vars.palette.kupferwolke.textSchwach,
              })}
            >
              <IconSelector size={18} stroke={1.8} aria-hidden />
            </Box>
          </>
        )}
      </Box>
      <Menu
        id={menueId}
        anchorEl={anker}
        open={anker !== null}
        onClose={() => {
          setAnker(null);
        }}
        // Die Karte steht unten in der Schiene — das Menue oeffnet darum nach oben.
        anchorOrigin={{ vertical: 'top', horizontal: 'left' }}
        transformOrigin={{ vertical: 'bottom', horizontal: 'left' }}
      >
        <MenuItem
          onClick={() => {
            void abmeldenUndGehen();
          }}
        >
          Abmelden
        </MenuItem>
      </Menu>
    </Box>
  );
}
