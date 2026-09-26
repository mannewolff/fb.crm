import { screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';

import { renderMitTheme } from '../test/render';
import KupferTaste from './KupferTaste';

describe('KupferTaste', () => {
  it('ist als Weg ein echter Link mit Ziel', () => {
    renderMitTheme(
      <MemoryRouter>
        <KupferTaste to="/firmen/neu">Neue Firma</KupferTaste>
      </MemoryRouter>,
    );

    const taste = screen.getByRole('link', { name: 'Neue Firma' });
    expect(taste).toHaveAttribute('href', '/firmen/neu');
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });

  it('bleibt ohne Ziel die absendende Taste eines Formulars', () => {
    renderMitTheme(<KupferTaste>Speichern</KupferTaste>);

    const taste = screen.getByRole('button', { name: 'Speichern' });
    expect(taste).toHaveAttribute('type', 'submit');
    expect(screen.queryByRole('link')).not.toBeInTheDocument();
  });

  it('laesst sich sperren, solange gesendet wird', () => {
    renderMitTheme(<KupferTaste disabled>Speichern</KupferTaste>);

    expect(screen.getByRole('button', { name: 'Speichern' })).toBeDisabled();
  });

  it('haengt ein Symbol vor die Aufschrift und haelt es aus dem Namen heraus', () => {
    renderMitTheme(<KupferTaste symbol={<svg data-testid="zeichen" />}>Speichern</KupferTaste>);

    // Das Symbol stuetzt das Wort, es ersetzt es nicht: Der zugaengliche Name bleibt die
    // Aufschrift (CLAUDE-design.md, „Zustandsformen").
    expect(screen.getByRole('button', { name: 'Speichern' })).toBeInTheDocument();
    expect(screen.getByTestId('taste-symbol')).toHaveAttribute('aria-hidden', 'true');
    expect(screen.getByTestId('zeichen')).toBeInTheDocument();
  });

  it('traegt ohne Symbol keinen leeren Symbolplatz', () => {
    renderMitTheme(<KupferTaste>Speichern</KupferTaste>);

    expect(screen.queryByTestId('taste-symbol')).not.toBeInTheDocument();
  });
});
