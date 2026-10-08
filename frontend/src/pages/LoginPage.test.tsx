import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { AuthProvider } from '../auth/AuthContext';
import { fetchNachPfad, json, leer } from '../test/fetchNachPfad';
import { renderMitTheme } from '../test/render';
import LoginPage from './LoginPage';

const KONTO = { id: 1, displayName: 'Manfred Wolff', email: 'info@mwolff.org' };

function problemAntwort(status: number, detail: string): Response {
  return new Response(JSON.stringify({ title: 'Nicht angemeldet', detail }), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  });
}

function kontoAntwort(): Response {
  return new Response(JSON.stringify(KONTO), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  });
}

type Antwort = () => Response | Promise<Response>;

/**
 * Keine Sitzung, eingerichtete Instanz — und fuer jeden Anmeldeversuch die naechste Antwort.
 *
 * Nach Pfad statt nach Reihenfolge: Beim Aufbau fragen Provider und Seite gleichzeitig, und
 * welche Anfrage zuerst hinausgeht, ist kein Teil der Zusage.
 */
function ohneSitzung(...anmeldungen: Antwort[]) {
  return aufInstanz(true, ...anmeldungen);
}

function aufInstanz(eingerichtet: boolean, ...anmeldungen: Antwort[]) {
  let naechste = 0;
  return fetchNachPfad({
    'GET /api/auth/me': leer(401),
    'GET /api/setup/status': json(200, { initialized: eingerichtet }),
    'POST /api/auth/login': () => anmeldungen[naechste++](),
  });
}

function anmeldeAufrufe(fetchMock: ReturnType<typeof ohneSitzung>) {
  return fetchMock.mock.calls.filter(([ziel]) => ziel === '/api/auth/login');
}

function renderAnmeldeseite(zustand: unknown = null) {
  return renderMitTheme(
    <MemoryRouter initialEntries={[{ pathname: '/anmelden', state: zustand }]}>
      <AuthProvider>
        <Routes>
          <Route path="/anmelden" element={<LoginPage />} />
          <Route path="/" element={<p>Leitstand</p>} />
          <Route path="/einrichten" element={<p>Einrichtungsseite</p>} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
}

async function anmeldenMit(passwort: string) {
  const nutzer = userEvent.setup();
  await nutzer.clear(screen.getByLabelText(/^E-Mail-Adresse/));
  await nutzer.type(screen.getByLabelText(/^E-Mail-Adresse/), 'info@mwolff.org');
  await nutzer.clear(screen.getByLabelText(/^Passwort/));
  await nutzer.type(screen.getByLabelText(/^Passwort/), passwort);
  await nutzer.click(screen.getByRole('button', { name: 'Anmelden' }));
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('Anmeldeseite', () => {
  it('beschriftet beide Felder sichtbar und zuordenbar', async () => {
    ohneSitzung();

    renderAnmeldeseite();

    expect(await screen.findByLabelText(/^E-Mail-Adresse/)).toHaveAttribute('type', 'email');
    expect(screen.getByLabelText(/^Passwort/)).toHaveAttribute('type', 'password');
  });

  it('bietet keine Registrierung an', async () => {
    ohneSitzung();

    renderAnmeldeseite();
    await screen.findByLabelText(/^E-Mail-Adresse/);

    expect(screen.queryByText(/registr/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/konto anlegen/i)).not.toBeInTheDocument();
    const nebenwege = screen.getAllByRole('link');
    expect(nebenwege).toHaveLength(1);
    expect(nebenwege[0]).toHaveAttribute('href', '/passwort-vergessen');
  });

  it('fuehrt nach erfolgreicher Anmeldung in die Anwendung', async () => {
    ohneSitzung(kontoAntwort);

    renderAnmeldeseite();
    await screen.findByLabelText(/^E-Mail-Adresse/);
    await anmeldenMit('geheim');

    expect(await screen.findByText('Leitstand')).toBeInTheDocument();
  });

  it('zeigt fuer unbekannte Adresse und falsches Passwort denselben Text', async () => {
    const fetchMock = ohneSitzung(
      () => problemAntwort(401, 'Es gibt kein Konto mit dieser Adresse.'),
      () => problemAntwort(401, 'Das Passwort ist falsch.'),
    );

    renderAnmeldeseite();
    await screen.findByLabelText(/^E-Mail-Adresse/);
    await anmeldenMit('falsch');
    const ersteMeldung = (await screen.findByRole('alert')).textContent;

    await anmeldenMit('auch-falsch');
    await waitFor(() => {
      expect(anmeldeAufrufe(fetchMock)).toHaveLength(2);
    });
    const zweiteMeldung = screen.getByRole('alert').textContent;

    expect(zweiteMeldung).toBe(ersteMeldung);
    expect(ersteMeldung).not.toMatch(/kein Konto/);
    expect(ersteMeldung).not.toMatch(/Das Passwort ist falsch/);
  });

  it('reicht die Meldung des Servers durch, wenn sie nichts ueber das Konto verraet', async () => {
    ohneSitzung(() =>
      problemAntwort(429, 'Zu viele Anmeldeversuche. Bitte spaeter erneut versuchen.'),
    );

    renderAnmeldeseite();
    await screen.findByLabelText(/^E-Mail-Adresse/);
    await anmeldenMit('geheim');

    expect(await screen.findByRole('alert')).toHaveTextContent(/Zu viele Anmeldeversuche/);
  });

  it('meldet einen Ausfall der Schnittstelle ohne technische Einzelheiten', async () => {
    ohneSitzung(() => Promise.reject(new TypeError('Failed to fetch')));

    renderAnmeldeseite();
    await screen.findByLabelText(/^E-Mail-Adresse/);
    await anmeldenMit('geheim');

    const meldung = await screen.findByRole('alert');
    expect(meldung).toBeInTheDocument();
    expect(meldung).not.toHaveTextContent(/fetch/i);
  });

  it('deaktiviert den Absendeknopf waehrend der Anfrage', async () => {
    ohneSitzung(
      () =>
        new Promise<Response>((aufloesen) => {
          setTimeout(() => {
            aufloesen(kontoAntwort());
          }, 20);
        }),
    );

    renderAnmeldeseite();
    await screen.findByLabelText(/^E-Mail-Adresse/);
    await anmeldenMit('geheim');

    expect(screen.getByRole('button', { name: 'Anmelden' })).toBeDisabled();
    expect(await screen.findByText('Leitstand')).toBeInTheDocument();
  });

  it('gibt den Absendeknopf nach einem Fehlschlag wieder frei', async () => {
    ohneSitzung(() => problemAntwort(401, 'Anmeldung gescheitert.'));

    renderAnmeldeseite();
    await screen.findByLabelText(/^E-Mail-Adresse/);
    await anmeldenMit('falsch');
    await screen.findByRole('alert');

    expect(screen.getByRole('button', { name: 'Anmelden' })).toBeEnabled();
  });

  it('fuehrt eine nicht eingerichtete Instanz auf die Einrichtungsseite', async () => {
    aufInstanz(false);

    renderAnmeldeseite();

    expect(await screen.findByText('Einrichtungsseite')).toBeInTheDocument();
  });

  it('bleibt auf der Anmeldeseite, wenn der Einrichtungsstand nicht zu erfahren ist', async () => {
    fetchNachPfad({ 'GET /api/auth/me': leer(401), 'GET /api/setup/status': leer(503) });

    renderAnmeldeseite();

    expect(await screen.findByLabelText(/^E-Mail-Adresse/)).toBeInTheDocument();
    expect(screen.queryByText('Einrichtungsseite')).not.toBeInTheDocument();
  });

  it('zeigt den Hinweis, mit dem ein anderer Weg hierher gefuehrt hat', async () => {
    ohneSitzung();

    renderAnmeldeseite({ hinweis: 'Das neue Passwort ist gesetzt.' });

    expect(await screen.findByRole('status')).toHaveTextContent('Das neue Passwort ist gesetzt.');
  });
});
