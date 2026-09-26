import Box from '@mui/material/Box';
import type { ReactNode } from 'react';

import type { ToenungName } from '../theme';
import { RADIUS_RUND } from '../theme';

/**
 * Der Zustands-Chip: eine Pille aus Wort und stuetzendem Symbol auf einer Toenung
 * (Vorlage `.chip` Z. 61, `.chips` Z. 60).
 *
 * **Das Wort sagt den Zustand, die Farbe stuetzt ihn** (CLAUDE-design.md, „Zustandsformen"): Ein
 * Chip, der „Stillgelegt" nur in Rose zeigte, waere fuer jeden unlesbar, der die Toenungen nicht
 * auseinanderhaelt. Das Symbol bleibt darum aus dem Vorgelesenen heraus — es traegt keine eigene
 * Aussage.
 *
 * Die Toenung kommt als **Name** herein, nicht als Farbpaar: Jede Toenung hat eine feste Bedeutung
 * und wird nicht der Reihe nach durchgefaerbt.
 */
export interface ZustandsChipProps {
  /** Der Zustand als Wort — „Aktiv", „Stillgelegt", „Überfällig". */
  readonly wort: string;
  /** Die Toenung nach Bedeutung (CLAUDE-design.md, „Toenungen"). */
  readonly toenung: ToenungName;
  /** Das stuetzende Symbol links des Wortes. */
  readonly symbol?: ReactNode;
}

export default function ZustandsChip({ wort, toenung, symbol }: ZustandsChipProps) {
  return (
    <Box
      component="span"
      data-testid="chip"
      sx={(theme) => ({
        display: 'inline-flex',
        alignItems: 'center',
        gap: '6px',
        fontSize: 12.5,
        fontWeight: 600,
        borderRadius: `${RADIUS_RUND}px`,
        padding: '5px 12px',
        whiteSpace: 'nowrap',
        color: theme.vars.palette.kupferwolke.toenung[toenung].schrift,
        backgroundColor: theme.vars.palette.kupferwolke.toenung[toenung].flaeche,
      })}
    >
      {symbol === undefined ? null : (
        <Box
          component="span"
          data-testid="chip-symbol"
          aria-hidden
          sx={{ display: 'flex', flex: 'none' }}
        >
          {symbol}
        </Box>
      )}
      {wort}
    </Box>
  );
}
