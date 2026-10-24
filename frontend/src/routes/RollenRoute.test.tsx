import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { AuthProvider } from '../auth/AuthContext';
import type { Recht } from '../auth/rechte';
import { fetchNachPfad, json, leer } from '../test/fetchNachPfad';
import { renderMitTheme } from '../test/render';
import ProtectedRoute from './ProtectedRoute';
import RollenRoute, { NICHT_OFFEN } from './RollenRoute';

const KONTO = { id: 1, displayName: 'Anna Berg', email: 'anna@firma.de', role: 'EMPLOYEE' };

function sitzungAls(rolle: string) {
  return fetchNachPfad({ 'GET /api/auth/me': json(200, { ...KONTO, role: rolle }) });
}

/** Der Routenbaum wie in `App`: die Sitzung zuerst, dahinter das Recht. */
function renderRoute(adresse: string, recht: Recht = 'darfEinstellungenSehen') {
  return renderMitTheme(
    <MemoryRouter initialEntries={[adresse]}>
      <AuthProvider>
        <Routes>
          <Route path="/anmelden" element={<p>Anmeldeseite</p>} />
          <Route
            path="/"
            element={
              <ProtectedRoute>
                <p>Startseite</p>
              </ProtectedRoute>
            }
          />
          <Route
            path="/administration"
            element={
              <ProtectedRoute>
                <RollenRoute recht={recht}>
                  <p>Einstellungen zur Rechnung</p>
                </RollenRoute>
              </ProtectedRoute>
            }
          />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('RollenRoute (Plan #414, A11)', () => {
  it('Szenario: Employee kommt nicht an die Einstellungen', async () => {
    sitzungAls('EMPLOYEE');
    const nutzer = userEvent.setup();

    renderRoute('/administration');

    expect(await screen.findByText(/Diese Ansicht steht deiner Rolle nicht offen/)).toBeInTheDocument();
    expect(screen.queryByText('Einstellungen zur Rechnung')).not.toBeInTheDocument();
    const weg = screen.getByRole('link', { name: 'Zur Startseite' });
    expect(weg).toHaveAttribute('href', '/');

    await nutzer.click(weg);

    expect(await screen.findByText('Startseite')).toBeInTheDocument();
  });

  it('sagt es in genau dem Satz aus Kriterium 9', () => {
    expect(NICHT_OFFEN).toBe('Diese Ansicht steht deiner Rolle nicht offen.');
  });

  it.each(['C_LEVEL', 'READ_ONLY'])('zeigt %s die Ansicht, denn die Rolle traegt das Recht', async (rolle) => {
    sitzungAls(rolle);

    renderRoute('/administration');

    expect(await screen.findByText('Einstellungen zur Rechnung')).toBeInTheDocument();
    expect(screen.queryByText(NICHT_OFFEN, { exact: false })).not.toBeInTheDocument();
  });

  it('nimmt das verlangte Recht aus der Prop', async () => {
    sitzungAls('READ_ONLY');

    renderRoute('/administration', 'darfEinstellungenAendern');

    expect(await screen.findByText(/Diese Ansicht steht deiner Rolle nicht offen/)).toBeInTheDocument();
  });

  it('fuehrt ohne Sitzung weiterhin auf die Anmeldeseite, nicht zum Satz', async () => {
    fetchNachPfad({ 'GET /api/auth/me': leer(401) });

    renderRoute('/administration');

    expect(await screen.findByText('Anmeldeseite')).toBeInTheDocument();
    expect(screen.queryByText(NICHT_OFFEN, { exact: false })).not.toBeInTheDocument();
  });
});
