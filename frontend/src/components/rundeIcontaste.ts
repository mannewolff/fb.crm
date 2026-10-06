import type { Theme } from '@mui/material/styles';

import { RADIUS_RUND } from '../theme';

/** Durchmesser einer runden Icontaste (Vorlage: Kreis 40 px). */
export const ICONTASTE = 40;

/**
 * Das Aussehen einer runden Icontaste als `sx`: Kreis auf „Flaeche weich", Symbol in „Text matt",
 * Hover in der Toenung Pfirsich (CLAUDE-design.md, „Tasten").
 *
 * Nur das Aussehen ist gemeinsam — Element, Name und Ereignisse traegt jeder Aufrufer selbst
 * (Aktionsmenue, Monatswechsel der Arbeitszeit).
 */
export function rundeIcontaste(theme: Theme) {
  const farben = theme.vars.palette.kupferwolke;
  return {
    width: ICONTASTE,
    height: ICONTASTE,
    flex: 'none',
    borderRadius: `${String(RADIUS_RUND)}px`,
    border: 0,
    cursor: 'pointer',
    display: 'grid',
    placeItems: 'center',
    color: farben.textMatt,
    background: farben.flaecheWeich,
    transition: 'background .15s ease, color .15s ease',
    '&:hover': {
      background: farben.toenung.pfirsich.flaeche,
      color: farben.toenung.pfirsich.schrift,
    },
  };
}
