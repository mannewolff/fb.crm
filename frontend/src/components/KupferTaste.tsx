import Button from '@mui/material/Button';
import type { ReactNode } from 'react';

import { CARD_RADIUS } from '../theme';

/**
 * Die Haupttaste eines Formulars: erhaben, in Kupfer (Vorlage `.taste-kupfer` Z. 322–345).
 *
 * Eine eigene Komponente, weil jede Auth-Seite genau eine davon traegt — vier Abschriften
 * desselben `sx`-Blocks liefen beim ersten Nachziehen der Vorlage auseinander.
 */
export interface KupferTasteProps {
  readonly children: ReactNode;
  readonly disabled?: boolean;
}

export default function KupferTaste({ children, disabled = false }: KupferTasteProps) {
  return (
    <Button
      type="submit"
      disabled={disabled}
      sx={(theme) => ({
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
      })}
    >
      {children}
    </Button>
  );
}
