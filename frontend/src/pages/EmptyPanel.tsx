import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import { IconSparkles } from '@tabler/icons-react';

import Karte from '../components/Karte';
import { RADIUS_MITTEL } from '../theme';

/**
 * Der leere Zustand der Bereiche, in die noch keine Fachlichkeit eingezogen ist.
 *
 * Er dient `/`, `/administration` und `/dokumentation`. **Eine Einladung, keine Entschuldigung**
 * (CLAUDE-design.md, „Zustandsformen"): Symbol auf einer Toenung und ein Satz — keine Meldung ueber
 * fehlende Daten und keine Stoerung, denn es ist keine.
 *
 * Eine Taste traegt er nicht, obwohl die Designquelle sie beim leeren Zustand nennt: Es gibt hier
 * nichts anzulegen. Eine Taste ohne Ziel waere ein Bedienelement ohne Anlass (Plan A3).
 *
 * Eine Ueberschrift traegt er auch nicht — „Administration" stuende fuer etwas, das es noch nicht
 * gibt.
 */

/** Kantenlaenge des Symbolfeldes (CLAUDE-design.md, „Bausteine": Symbolfelder 36–48 px). */
const SYMBOLFELD = 48;

export default function EmptyPanel() {
  return (
    <Karte>
      <Box
        data-testid="leeres-panel"
        sx={{
          minHeight: 'calc(100vh - 220px)',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          gap: '14px',
          textAlign: 'center',
        }}
      >
        <Box
          data-testid="leeres-panel-symbol"
          aria-hidden
          sx={(theme) => ({
            width: SYMBOLFELD,
            height: SYMBOLFELD,
            borderRadius: `${RADIUS_MITTEL}px`,
            display: 'grid',
            placeItems: 'center',
            color: theme.vars.palette.kupferwolke.toenung.flieder.schrift,
            backgroundColor: theme.vars.palette.kupferwolke.toenung.flieder.flaeche,
          })}
        >
          <IconSparkles size={24} stroke={1.9} aria-hidden />
        </Box>
        <Typography sx={(theme) => ({ color: theme.vars.palette.kupferwolke.textMatt })}>
          Dieser Bereich entsteht noch.
        </Typography>
      </Box>
    </Karte>
  );
}
