import { screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import type { ToenungName } from '../theme';
import { theme } from '../theme';
import { ohneRueckfall } from '../test/cssvar';
import { renderMitTheme } from '../test/render';
import ZustandsChip from './ZustandsChip';

const TOENUNGEN: readonly ToenungName[] = [
  'pfirsich',
  'salbei',
  'himmel',
  'bernstein',
  'rose',
  'flieder',
];

describe('ZustandsChip', () => {
  it('sagt den Zustand als Wort', () => {
    renderMitTheme(<ZustandsChip wort="Aktiv" toenung="salbei" />);

    // Farbe allein traegt nie eine Aussage (CLAUDE-design.md, „Zustandsformen").
    expect(screen.getByText('Aktiv')).toBeInTheDocument();
  });

  it('haelt das stuetzende Symbol aus dem Vorgelesenen heraus', () => {
    renderMitTheme(
      <ZustandsChip wort="Stillgelegt" toenung="rose" symbol={<svg data-testid="zeichen" />} />,
    );

    expect(screen.getByTestId('chip-symbol')).toHaveAttribute('aria-hidden', 'true');
    expect(screen.getByTestId('zeichen')).toBeInTheDocument();
  });

  it('bleibt ohne Symbol ein Chip mit Wort', () => {
    renderMitTheme(<ZustandsChip wort="Aktiv" toenung="salbei" />);

    expect(screen.queryByTestId('chip-symbol')).not.toBeInTheDocument();
    expect(screen.getByTestId('chip')).toHaveTextContent('Aktiv');
  });

  it.each(TOENUNGEN)('traegt die Toenung %s mit ihrer Schrift', (name) => {
    renderMitTheme(<ZustandsChip wort="Zustand" toenung={name} />);

    // Geprueft wird die Schrift der Toenung: Flaeche und Schrift kommen aus **einem** Zugriff auf
    // `toenung[name]`, und `background-color` mit `var(…)` laesst jsdom nicht durch seinen
    // Farbpruefer — die Flaeche waere dort immer leer.
    expect(screen.getByTestId('chip')).toHaveStyle({
      color: ohneRueckfall(theme.vars.palette.kupferwolke.toenung[name].schrift),
    });
  });
});
