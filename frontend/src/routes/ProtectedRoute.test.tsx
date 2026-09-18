import { screen } from '@testing-library/react';
import type { ReactNode } from 'react';
import { MemoryRouter, Navigate, Route, Routes } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { AuthProvider } from '../auth/AuthContext';
import { renderMitTheme } from '../test/render';
import ProtectedRoute from './ProtectedRoute';

const KONTO = { id: 1, displayName: 'Manfred Wolff', email: 'info@mwolff.org' };

function fetchLiefert(bauen: () => Response) {
  return vi.spyOn(globalThis, 'fetch').mockImplementation(() => Promise.resolve(bauen()));
}

function kontoAntwort(): Response {
  return new Response(JSON.stringify(KONTO), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  });
}

function geschuetzt(inhalt: ReactNode) {
  return <ProtectedRoute>{inhalt}</ProtectedRoute>;
}

/** Der Routenbaum dieses Stands: die Anmeldeseite offen, alles andere hinter der Sitzung. */
function renderRoute(adresse: string) {
  return renderMitTheme(
    <MemoryRouter initialEntries={[adresse]}>
      <AuthProvider>
        <Routes>
          <Route path="/anmelden" element={<p>Anmeldeseite</p>} />
          <Route path="/" element={geschuetzt(<p>Geschuetzter Inhalt</p>)} />
          <Route path="*" element={geschuetzt(<Navigate to="/" replace />)} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('ProtectedRoute ohne Sitzung', () => {
  it('fuehrt eine geschuetzte Adresse auf die Anmeldeseite', async () => {
    fetchLiefert(() => new Response(null, { status: 401 }));

    renderRoute('/');

    expect(await screen.findByText('Anmeldeseite')).toBeInTheDocument();
    expect(screen.queryByText('Geschuetzter Inhalt')).not.toBeInTheDocument();
  });

  it('fuehrt auch eine unbekannte Adresse auf die Anmeldeseite', async () => {
    fetchLiefert(() => new Response(null, { status: 401 }));

    renderRoute('/gibt-es-nicht');

    expect(await screen.findByText('Anmeldeseite')).toBeInTheDocument();
  });
});

describe('ProtectedRoute mit Sitzung', () => {
  it('zeigt den geschuetzten Inhalt', async () => {
    fetchLiefert(kontoAntwort);

    renderRoute('/');

    expect(await screen.findByText('Geschuetzter Inhalt')).toBeInTheDocument();
  });

  it('fuehrt eine unbekannte Adresse auf die Startadresse', async () => {
    fetchLiefert(kontoAntwort);

    renderRoute('/gibt-es-nicht');

    expect(await screen.findByText('Geschuetzter Inhalt')).toBeInTheDocument();
    expect(screen.queryByText('Anmeldeseite')).not.toBeInTheDocument();
  });
});

describe('ProtectedRoute waehrend der Sitzungspruefung', () => {
  it('zeigt einen Ladezustand statt Inhalt oder Anmeldeseite', () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(() => new Promise<Response>(() => undefined));

    renderRoute('/');

    expect(screen.getByRole('status')).toBeInTheDocument();
    expect(screen.queryByText('Geschuetzter Inhalt')).not.toBeInTheDocument();
    expect(screen.queryByText('Anmeldeseite')).not.toBeInTheDocument();
  });
});
