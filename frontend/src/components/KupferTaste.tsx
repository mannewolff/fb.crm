import Button from '@mui/material/Button';
import type { Theme } from '@mui/material/styles';
import type { ReactNode } from 'react';
import { Link as RouterLink } from 'react-router-dom';

import { CARD_RADIUS } from '../theme';

/**
 * Die Haupttaste einer Ansicht: erhaben, in Kupfer (Vorlage `.taste-kupfer` Z. 322–345).
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
    borderRadius: `${CARD_RADIUS}px`,
    paddingBlock: '9px',
    color: theme.vars.palette.kupferwarte.kupferSchrift,
    background: `linear-gradient(180deg, ${theme.vars.palette.kupferwarte.kupferHell}, ${theme.vars.palette.kupferwarte.kupfer})`,
    border: `1px solid ${theme.vars.palette.kupferwarte.kupferTief}`,
    boxShadow: theme.vars.palette.kupferwarte.schatten.taste,
    '&:hover': {
      background: `linear-gradient(180deg, ${theme.vars.palette.kupferwarte.kupferHell}, ${theme.vars.palette.kupferwarte.kupfer})`,
      boxShadow: theme.vars.palette.kupferwarte.schatten.platte,
    },
    '&:active': { transform: 'translateY(1px)' },
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
