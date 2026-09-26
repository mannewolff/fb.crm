import Button from '@mui/material/Button';
import type { Theme } from '@mui/material/styles';
import type { ReactNode } from 'react';
import { Link as RouterLink } from 'react-router-dom';

import { RADIUS_RUND } from '../theme';

/**
 * Die Haupttaste einer Ansicht: eine Pille im Kupferverlauf mit farbigem Schatten
 * (Vorlage `.taste.primaer` Z. 63–65).
 *
 * Eine eigene Komponente, weil jede Auth-Seite genau eine davon traegt — vier Abschriften
 * desselben `sx`-Blocks liefen beim ersten Nachziehen der Vorlage auseinander.
 *
 * Zwei Gestalten, eine Optik: Mit `to` ist die Taste ein **echter Link** und kein Knopf mit
 * `onClick`. Ein Weg gehoert in ein `a` mit `href` — sonst faellt er aus dem Tabulatorweg der
 * Links, laesst sich nicht in einem neuen Reiter oeffnen und wird vom Screenreader als Schalter
 * angesagt, obwohl er die Seite wechselt (E11).
 */
export interface KupferTasteProps {
  readonly children: ReactNode;
  /** Das Ziel eines Weges. Ohne `to` ist die Taste der Absender ihres Formulars. */
  readonly to?: string;
  /** Gesperrt, solange gesendet wird — nur fuer die absendende Gestalt. */
  readonly disabled?: boolean;
}

/** Die Gestalt der Kupfertaste, geteilt von beiden Varianten. */
function kupferSx(theme: Theme) {
  return {
    borderRadius: `${RADIUS_RUND}px`,
    paddingBlock: '9px',
    color: theme.vars.palette.kupferwolke.kupferSchrift,
    background: `linear-gradient(135deg, ${theme.vars.palette.kupferwolke.kupferTaste}, ${theme.vars.palette.kupferwolke.kupferTief})`,
    border: 0,
    boxShadow: theme.vars.palette.kupferwolke.schatten.kupfer,
    '&:hover': {
      background: `linear-gradient(135deg, ${theme.vars.palette.kupferwolke.kupferTaste}, ${theme.vars.palette.kupferwolke.kupferTief})`,
      boxShadow: theme.vars.palette.kupferwolke.schatten.kupfer,
      // Tasten heben sich im Hover um 1 px (CLAUDE-design.md, „Tasten").
      transform: 'translateY(-1px)',
    },
    '&:active': { transform: 'none' },
  };
}

export default function KupferTaste({ children, to, disabled = false }: KupferTasteProps) {
  if (to !== undefined) {
    return (
      <Button component={RouterLink} to={to} sx={kupferSx}>
        {children}
      </Button>
    );
  }
  return (
    <Button type="submit" disabled={disabled} sx={kupferSx}>
      {children}
    </Button>
  );
}
