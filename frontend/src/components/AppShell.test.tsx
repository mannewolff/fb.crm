import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { AuthProvider } from '../auth/AuthContext';
import { fetchNachPfad, json } from '../test/fetchNachPfad';
import { renderMitTheme } from '../test/render';
import AppShell from './AppShell';

const KONTO = { id: 1, displayName: 'Manfred Wolff', email: 'info@mwolff.org' };

/**
 * Stellt die Fensterbreite ein, gegen die `matchMedia` auswertet.
 *
 * jsdom kennt kein `matchMedia`; MUI fragt es fuer `useMediaQuery`. Das Doppel liest die erste
 * Zahl der Abfrage als Mindestbreite — die Form, die MUI fuer `breakpoints.up(...)` erzeugt:
 * `(min-width:<n>px)`. Die Abfrage des Themes nach `prefers-color-scheme` traegt keine Zahl und
 * ergibt damit `false`, also das helle Erscheinungsbild.
 */
function fensterbreite(breite: number) {
  Object.defineProperty(window, 'matchMedia', {
    configurable: true,
    value: (abfrage: string) => ({
      matches: breite >= Number.parseInt(abfrage.replace(/\D+/g, ' ').trim(), 10),
      media: abfrage,
      // Beide Formen: useMediaQuery nimmt die neue, das Erscheinungsbild des Themes die alte.
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      addListener: vi.fn(),
      removeListener: vi.fn(),
    }),
  });
}

function renderRahmen() {
  fetchNachPfad({
    'GET /api/auth/me': json(200, KONTO),
    'GET /api/instance': json(200, { version: '0.1.1' }),
  });
  return renderMitTheme(
    <MemoryRouter>
      <AuthProvider>
        <AppShell>
          <p>Panel</p>
        </AppShell>
      </AuthProvider>
    </MemoryRouter>,
  );
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('AppShell', () => {
  it('zeigt die Schiene bei 800 px Fensterbreite (E14)', () => {
    fensterbreite(800);

    renderRahmen();

    expect(screen.getByRole('navigation', { name: 'Hauptnavigation' })).toBeInTheDocument();
  });

  it('laesst die Schiene bei 700 px Fensterbreite weg (E14)', () => {
    fensterbreite(700);

    renderRahmen();

    expect(screen.queryByRole('navigation')).not.toBeInTheDocument();
    expect(screen.getByRole('banner')).toBeInTheDocument();
    expect(screen.getByText('Panel')).toBeInTheDocument();
  });

  it('traegt Kopf und Inhalt, den Inhalt als main', () => {
    fensterbreite(1440);

    renderRahmen();

    expect(screen.getByRole('banner')).toBeInTheDocument();
    expect(screen.getByRole('main')).toHaveTextContent('Panel');
  });

  it('fuehrt ueber den Skip-Link auf den Inhaltsbereich', async () => {
    fensterbreite(1440);
    const nutzer = userEvent.setup();

    renderRahmen();
    await nutzer.tab();
    const sprung = screen.getByRole('link', { name: 'Zum Inhalt springen' });
    expect(sprung).toHaveFocus();
    await nutzer.keyboard('{Enter}');

    expect(screen.getByRole('main')).toHaveFocus();
  });
});
