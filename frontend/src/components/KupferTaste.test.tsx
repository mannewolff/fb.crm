import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';

import { renderMitTheme } from '../test/render';
import { TASTE_INNENABSTAND, theme } from '../theme';
import KupferTaste, { kupferSx } from './KupferTaste';

describe('KupferTaste', () => {
  it('traegt das dichtere Pillenmass 7 px auf 14 px (Issue #299)', () => {
    expect(kupferSx(theme).padding).toBe(TASTE_INNENABSTAND);
    expect(TASTE_INNENABSTAND).toBe('7px 14px');
  });

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

  it('wird mit einer Handlung eine Taste, die ihr Formular nicht absendet', async () => {
    const nutzer = userEvent.setup();
    const handlung = vi.fn();
    renderMitTheme(<KupferTaste onClick={handlung}>Abschließen</KupferTaste>);

    const taste = screen.getByRole('button', { name: 'Abschließen' });
    // `type="button"`: Eine Hauptaktion steht nicht immer in einem Formular, und eine
    // `submit`-Taste ausserhalb eines Formulars tut beim Klick nichts.
    expect(taste).toHaveAttribute('type', 'button');

    await nutzer.click(taste);

    expect(handlung).toHaveBeenCalledTimes(1);
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
