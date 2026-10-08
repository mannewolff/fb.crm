import { screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { renderMitTheme } from '../test/render';
import EmptyPanel from './EmptyPanel';

describe('EmptyPanel', () => {
  it('laedt mit einem Satz ein, statt sich zu entschuldigen', () => {
    renderMitTheme(<EmptyPanel />);

    // Leere Zustaende sind eine Einladung, keine Entschuldigung (CLAUDE-design.md,
    // „Zustandsformen") — und der Satz nennt keinen Fehler und keine Stoerung.
    const satz = screen.getByText('Dieser Bereich entsteht noch.');
    expect(satz).toBeInTheDocument();
    expect(satz.textContent).not.toMatch(/leider|Fehler|keine Daten/i);
  });

  it('traegt ein Symbol auf einer Toenung, das nichts vorliest', () => {
    renderMitTheme(<EmptyPanel />);

    expect(screen.getByTestId('leeres-panel-symbol')).toHaveAttribute('aria-hidden', 'true');
  });

  it('traegt keine fachliche Ueberschrift', () => {
    renderMitTheme(<EmptyPanel />);

    // Eine Ueberschrift wie „Administration" stuende hier fuer etwas, das es noch nicht gibt.
    expect(screen.queryByRole('heading')).not.toBeInTheDocument();
  });
});
