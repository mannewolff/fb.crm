import { screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { STORNIERT_WORT } from '../lib/angebotsstatus';
import { theme } from '../theme';
import { ohneRueckfall } from '../test/cssvar';
import { renderMitTheme } from '../test/render';
import StorniertChip from './StorniertChip';

describe('StorniertChip (Plan #332, E13)', () => {
  it('sagt das Storno mit dem Wort, das auch der Filter zeigt', () => {
    renderMitTheme(<StorniertChip />);

    expect(screen.getByTestId('chip')).toHaveTextContent(STORNIERT_WORT);
  });

  it('traegt die Toenung Rosé', () => {
    renderMitTheme(<StorniertChip />);

    expect(screen.getByTestId('chip')).toHaveStyle({
      color: ohneRueckfall(theme.vars.palette.kupferwolke.toenung.rose.schrift),
    });
  });

  it('haelt das stuetzende Symbol aus dem Vorgelesenen heraus', () => {
    renderMitTheme(<StorniertChip />);

    expect(screen.getByTestId('chip-symbol')).toHaveAttribute('aria-hidden', 'true');
  });
});
