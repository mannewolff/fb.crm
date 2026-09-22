import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { AuthProvider } from '../auth/AuthContext';
import { fetchNachPfad, json, leer } from '../test/fetchNachPfad';
import type { Routen } from '../test/fetchNachPfad';
import { renderMitTheme } from '../test/render';
import UserMenu from './UserMenu';

const KONTO = { id: 1, displayName: 'Manfred Wolff', email: 'info@mwolff.org' };

/** Ein Speicher, der mitzaehlt, ob ueberhaupt etwas abgelegt wurde. */
function speicherDoppel(name: 'localStorage' | 'sessionStorage') {
  const setItem = vi.fn();
  Object.defineProperty(globalThis, name, {
    value: { setItem, getItem: vi.fn(() => null) },
    configurable: true,
  });
  return setItem;
}

function angemeldet(weitere: Routen = {}) {
  return fetchNachPfad({ 'GET /api/auth/me': json(200, KONTO), ...weitere });
}

function renderMenue() {
  return renderMitTheme(
    <MemoryRouter initialEntries={['/']}>
      <AuthProvider>
        <Routes>
          <Route path="/" element={<UserMenu />} />
          <Route path="/anmelden" element={<p>Anmeldeseite</p>} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('UserMenu', () => {
  it('zeigt die Initialen aus dem Anzeigenamen und nennt das Konto (K13, E12)', async () => {
    angemeldet();

    renderMenue();

    const mal = await screen.findByRole('button', { name: 'Nutzermenü: Manfred Wolff' });
    expect(mal).toHaveTextContent('MW');
    expect(mal).toHaveAttribute('aria-haspopup', 'menu');
  });

  it('zeigt ohne Sitzung nichts', () => {
    fetchNachPfad({ 'GET /api/auth/me': leer(401) });

    renderMenue();

    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });

  it('enthaelt genau einen Eintrag: Abmelden (K14)', async () => {
    angemeldet();
    const nutzer = userEvent.setup();

    renderMenue();
    await nutzer.click(await screen.findByRole('button', { name: /Nutzermenü/ }));

    const eintraege = within(screen.getByRole('menu')).getAllByRole('menuitem');
    expect(eintraege.map((eintrag) => eintrag.textContent)).toEqual(['Abmelden']);
  });

  it('fuehrt nach dem Abmelden auf die Anmeldeseite und legt nichts im Speicher ab (K9, K14)', async () => {
    const lokal = speicherDoppel('localStorage');
    const sitzungsweit = speicherDoppel('sessionStorage');
    const fetchMock = angemeldet({ 'POST /api/auth/logout': leer(204) });
    const nutzer = userEvent.setup();

    renderMenue();
    await nutzer.click(await screen.findByRole('button', { name: /Nutzermenü/ }));
    await nutzer.click(screen.getByRole('menuitem', { name: 'Abmelden' }));

    expect(await screen.findByText('Anmeldeseite')).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith('/api/auth/logout', expect.objectContaining({ method: 'POST' }));
    expect(lokal).not.toHaveBeenCalled();
    expect(sitzungsweit).not.toHaveBeenCalled();
  });

  it('bleibt angemeldet stehen, wenn das Abmelden nicht ankommt', async () => {
    angemeldet();
    const nutzer = userEvent.setup();

    renderMenue();
    await nutzer.click(await screen.findByRole('button', { name: /Nutzermenü/ }));
    await nutzer.click(screen.getByRole('menuitem', { name: 'Abmelden' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Die Abmeldung ist gerade nicht möglich. Bitte erneut versuchen.',
    );
    expect(screen.queryByText('Anmeldeseite')).not.toBeInTheDocument();
  });

  it('schliesst das Menue mit Escape und gibt den Fokus an das Mal zurueck', async () => {
    angemeldet();
    const nutzer = userEvent.setup();

    renderMenue();
    const mal = await screen.findByRole('button', { name: /Nutzermenü/ });
    await nutzer.click(mal);
    await nutzer.keyboard('{Escape}');

    await vi.waitFor(() => {
      expect(screen.queryByRole('menu')).not.toBeInTheDocument();
    });
    expect(mal).toHaveFocus();
  });
});
