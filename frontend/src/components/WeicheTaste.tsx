import Button from '@mui/material/Button';
import type { Theme } from '@mui/material/styles';
import type { ReactNode } from 'react';
import { Link as RouterLink } from 'react-router-dom';

import { RADIUS_RUND } from '../theme';
import TastenSymbol from './TastenSymbol';

/**
 * Die zweitwichtigste Aktion einer Ansicht: eine Pille auf „Flaeche weich" mit einer Linie als
 * Innenring (Vorlage `.taste` Z. 63–64, `.taste.weich` Z. 66).
 *
 * Sie tritt neben der Kupfertaste zurueck, bleibt aber eine volle Taste — „Bearbeiten",
 * „Abbrechen", „Wieder oeffnen" (CLAUDE-design.md, „Tasten"). Der Innenring steht als
 * `box-shadow: inset` und nicht als `border`: So aendert er die Aussenmasse der Taste nicht, und
 * eine weiche neben einer Kupfertaste bleibt gleich hoch.
 *
 * Zwei Gestalten wie bei der {@link KupferTaste}: Mit `to` ein echter Link, sonst eine Taste mit
 * `type="button"` — **nicht** `submit`. Eine weiche Taste steht neben der Hauptaktion eines
 * Formulars; schickte sie es selbst ab, waere „Abbrechen" ein Speichern.
 */
export interface WeicheTasteProps {
  readonly children: ReactNode;
  /** Das Ziel eines Weges. Ohne `to` ist die Taste ein Schalter. */
  readonly to?: string;
  /** Was der Schalter tut. Ohne `to` der eigentliche Zweck der Taste. */
  readonly onClick?: () => void;
  /** Gesperrt, solange die Aktion laeuft. */
  readonly disabled?: boolean;
  /** Das stuetzende Symbol links der Aufschrift. */
  readonly symbol?: ReactNode;
}

/** Die Gestalt der weichen Taste, geteilt von beiden Varianten. */
function weichSx(theme: Theme) {
  return {
    borderRadius: `${RADIUS_RUND}px`,
    padding: '11px 20px',
    gap: '8px',
    color: theme.vars.palette.kupferwolke.text,
    background: theme.vars.palette.kupferwolke.flaecheWeich,
    border: 0,
    boxShadow: `inset 0 0 0 1px ${theme.vars.palette.kupferwolke.linie}`,
    '&:hover': {
      background: theme.vars.palette.kupferwolke.flaecheWeich,
      boxShadow: `inset 0 0 0 1px ${theme.vars.palette.kupferwolke.linie}`,
      // Tasten heben sich im Hover um 1 px (CLAUDE-design.md, „Tasten").
      transform: 'translateY(-1px)',
    },
    '&:active': { transform: 'none' },
  };
}

export default function WeicheTaste({
  children,
  to,
  onClick,
  disabled = false,
  symbol,
}: WeicheTasteProps) {
  const inhalt = (
    <>
      {symbol === undefined ? null : <TastenSymbol>{symbol}</TastenSymbol>}
      {children}
    </>
  );
  if (to !== undefined) {
    return (
      <Button component={RouterLink} to={to} sx={weichSx}>
        {inhalt}
      </Button>
    );
  }
  return (
    <Button type="button" onClick={onClick} disabled={disabled} sx={weichSx}>
      {inhalt}
    </Button>
  );
}
