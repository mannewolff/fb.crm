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
 * `(min-width:<n>px)`. Eine Abfrage ohne Zahl ergibt `false`.
 */
function fensterbreite(breite: number) {
  Object.defineProperty(window, 'matchMedia', {
    configurable: true,
    value: (abfrage: string) => ({
      matches: breite >= Number.parseInt(abfrage.replace(/\D+/g, ' ').trim(), 10),
      media: abfrage,
      // Beide Formen, neue und alte: MUI fragt je nach Stelle die eine oder die andere.
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

const SCHALTER = 'Navigation öffnen';

describe('AppShell', () => {
  it('legt die Schiene bei 800 px hinter die Schaltflaeche im Kopf (E18)', () => {
    fensterbreite(800);

    renderRahmen();

    expect(screen.queryByRole('navigation', { name: 'Hauptnavigation' })).not.toBeInTheDocument();
    const schalter = screen.getByRole('button', { name: SCHALTER });
    expect(schalter).toHaveAttribute('aria-expanded', 'false');
    // Die Schaltflaeche steht links im Kopf.
    expect(screen.getByTestId('kopf-links')).toContainElement(schalter);
  });

  it('oeffnet die Schiene ueber dem Inhalt und schliesst sie mit Escape (E18)', async () => {
    fensterbreite(800);
    const nutzer = userEvent.setup();

    renderRahmen();
    const schalter = screen.getByRole('button', { name: SCHALTER });
    await nutzer.click(schalter);

    expect(screen.getByRole('navigation', { name: 'Hauptnavigation' })).toBeInTheDocument();
    // Der Kopf liegt jetzt hinter dem Dialog und ist damit `aria-hidden` — die Schaltflaeche ist
    // nur noch ueber ihre Referenz zu fassen, und genau das ist der gewollte Zustand.
    expect(schalter).toHaveAttribute('aria-expanded', 'true');

    await nutzer.keyboard('{Escape}');

    await vi.waitFor(() => {
      expect(screen.queryByRole('navigation', { name: 'Hauptnavigation' })).not.toBeInTheDocument();
    });
    expect(screen.getByRole('button', { name: SCHALTER })).toHaveFocus();
  });

  it('schliesst die Schiene, wenn „Firmen" gewaehlt wird, und gibt den Fokus zurueck (E18)', async () => {
    fensterbreite(800);
    const nutzer = userEvent.setup();

    renderRahmen();
    await nutzer.click(screen.getByRole('button', { name: SCHALTER }));
    await nutzer.click(screen.getByRole('link', { name: 'Firmen' }));

    await vi.waitFor(() => {
      expect(screen.queryByRole('navigation', { name: 'Hauptnavigation' })).not.toBeInTheDocument();
    });
    expect(screen.getByRole('button', { name: SCHALTER })).toHaveFocus();
  });

  it('haelt die Schiene bei 1024 px offen und ohne Schaltflaeche (E18)', () => {
    fensterbreite(1024);

    renderRahmen();

    expect(screen.getByRole('navigation', { name: 'Hauptnavigation' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: SCHALTER })).not.toBeInTheDocument();
    expect(screen.getByTestId('kopf-links')).toBeEmptyDOMElement();
  });

  it('laesst Schiene und Schaltflaeche bei 700 px weg (E14)', () => {
    fensterbreite(700);

    renderRahmen();

    expect(screen.queryByRole('navigation')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: SCHALTER })).not.toBeInTheDocument();
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
