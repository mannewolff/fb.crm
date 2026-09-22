import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Menu from '@mui/material/Menu';
import MenuItem from '@mui/material/MenuItem';
import { useId, useState } from 'react';
import { useNavigate } from 'react-router-dom';

import { useAuth } from '../auth/AuthContext';
import { initialen } from '../lib/initials';

/**
 * Das runde Nutzer-Mal im Kopf und sein Menue (K13, K14; Vorlage `.nutzer` Z. 347–356).
 *
 * Das Menue hat genau einen Eintrag: „Abmelden". Eine Profilseite, Einstellungen oder ein
 * Wechsel des Erscheinungsbilds gehoeren nicht in diesen Stand — ein Eintrag, der ins Leere
 * fuehrt, waere schlechter als keiner.
 */

/** Kantenlaenge des Mals (Vorlage Z. 348). */
const MAL = 30;

/** Lichtkante und Schatten des Mals (Vorlage Z. 353) — wie beim Markenmal am Bauteil. */
const MAL_SCHATTEN = '0 1px 0 rgba(255,255,255,.18) inset, 0 2px 5px rgba(0,0,0,.35)';

const ABMELDEN_GESCHEITERT = 'Die Abmeldung ist gerade nicht möglich. Bitte erneut versuchen.';

export default function UserMenu() {
  const { sitzung, abmelden } = useAuth();
  const navigate = useNavigate();
  const menueId = useId();
  const [anker, setAnker] = useState<HTMLElement | null>(null);
  const [fehler, setFehler] = useState<string | null>(null);

  if (sitzung.status !== 'angemeldet') {
    return null;
  }
  const { displayName } = sitzung.konto;

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
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
      {fehler === null ? null : (
        <Alert severity="error" sx={{ paddingBlock: 0 }}>
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
          width: MAL,
          height: MAL,
          borderRadius: '50%',
          border: 'none',
          padding: 0,
          cursor: 'pointer',
          flex: 'none',
          display: 'grid',
          placeItems: 'center',
          fontFamily: 'inherit',
          fontSize: 11,
          fontWeight: 600,
          color: theme.vars.palette.kupferwarte.nutzerSchrift,
          background: `linear-gradient(160deg, ${theme.vars.palette.kupferwarte.nutzerHell}, ${theme.vars.palette.kupferwarte.nutzerTief})`,
          boxShadow: MAL_SCHATTEN,
        })}
      >
        {initialen(displayName)}
      </Box>
      <Menu
        id={menueId}
        anchorEl={anker}
        open={anker !== null}
        onClose={() => {
          setAnker(null);
        }}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
        transformOrigin={{ vertical: 'top', horizontal: 'right' }}
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
