import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';

import { renderMitTheme } from '../test/render';
import WeicheTaste from './WeicheTaste';

describe('WeicheTaste', () => {
  it('ist als Weg ein echter Link mit Ziel', () => {
    renderMitTheme(
      <MemoryRouter>
        <WeicheTaste to="/firmen/7">Bearbeiten</WeicheTaste>
      </MemoryRouter>,
    );

    const taste = screen.getByRole('link', { name: 'Bearbeiten' });
    expect(taste).toHaveAttribute('href', '/firmen/7');
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });

  it('ist ohne Ziel eine Taste, die ihre Aktion ausfuehrt', async () => {
    const abbrechen = vi.fn();
    renderMitTheme(<WeicheTaste onClick={abbrechen}>Abbrechen</WeicheTaste>);

    const taste = screen.getByRole('button', { name: 'Abbrechen' });
    // Keine absendende Taste: Eine weiche Taste steht neben der Hauptaktion eines Formulars und
    // darf es nicht selbst abschicken.
    expect(taste).toHaveAttribute('type', 'button');
    await userEvent.click(taste);
    expect(abbrechen).toHaveBeenCalledTimes(1);
  });

  it('nimmt ihren zugaenglichen Namen aus ihrer Aufschrift', () => {
    renderMitTheme(
      <WeicheTaste symbol={<svg data-testid="zeichen" />}>Wieder öffnen</WeicheTaste>,
    );

    expect(screen.getByRole('button', { name: 'Wieder öffnen' })).toBeInTheDocument();
    expect(screen.getByTestId('taste-symbol')).toHaveAttribute('aria-hidden', 'true');
    expect(screen.getByTestId('zeichen')).toBeInTheDocument();
  });

  it('traegt ohne Symbol keinen leeren Symbolplatz', () => {
    renderMitTheme(<WeicheTaste>Abbrechen</WeicheTaste>);

    expect(screen.queryByTestId('taste-symbol')).not.toBeInTheDocument();
  });

  it('laesst sich sperren', () => {
    renderMitTheme(<WeicheTaste disabled>Abbrechen</WeicheTaste>);

    expect(screen.getByRole('button', { name: 'Abbrechen' })).toBeDisabled();
  });
});
