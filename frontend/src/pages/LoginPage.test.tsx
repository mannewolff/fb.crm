import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { AuthProvider } from '../auth/AuthContext';
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

/** Die erste Antwort gehoert immer der Sitzungspruefung des Providers. */
function ohneSitzung() {
  return vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(new Response(null, { status: 401 }));
}

function renderAnmeldeseite() {
  return renderMitTheme(
    <MemoryRouter initialEntries={['/anmelden']}>
      <AuthProvider>
        <Routes>
          <Route path="/anmelden" element={<LoginPage />} />
          <Route path="/" element={<p>Leitstand</p>} />
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
    ohneSitzung().mockResolvedValueOnce(kontoAntwort());

    renderAnmeldeseite();
    await screen.findByLabelText(/^E-Mail-Adresse/);
    await anmeldenMit('geheim');

    expect(await screen.findByText('Leitstand')).toBeInTheDocument();
  });

  it('zeigt fuer unbekannte Adresse und falsches Passwort denselben Text', async () => {
    const fetchMock = ohneSitzung()
      .mockResolvedValueOnce(problemAntwort(401, 'Es gibt kein Konto mit dieser Adresse.'))
      .mockResolvedValueOnce(problemAntwort(401, 'Das Passwort ist falsch.'));

    renderAnmeldeseite();
    await screen.findByLabelText(/^E-Mail-Adresse/);
    await anmeldenMit('falsch');
    const ersteMeldung = (await screen.findByRole('alert')).textContent;

    await anmeldenMit('auch-falsch');
    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledTimes(3);
    });
    const zweiteMeldung = screen.getByRole('alert').textContent;

    expect(zweiteMeldung).toBe(ersteMeldung);
    expect(ersteMeldung).not.toMatch(/kein Konto/);
    expect(ersteMeldung).not.toMatch(/Das Passwort ist falsch/);
  });

  it('reicht die Meldung des Servers durch, wenn sie nichts ueber das Konto verraet', async () => {
    ohneSitzung().mockResolvedValueOnce(
      problemAntwort(429, 'Zu viele Anmeldeversuche. Bitte spaeter erneut versuchen.'),
    );

    renderAnmeldeseite();
    await screen.findByLabelText(/^E-Mail-Adresse/);
    await anmeldenMit('geheim');

    expect(await screen.findByRole('alert')).toHaveTextContent(/Zu viele Anmeldeversuche/);
  });

  it('meldet einen Ausfall der Schnittstelle ohne technische Einzelheiten', async () => {
    ohneSitzung().mockRejectedValueOnce(new TypeError('Failed to fetch'));

    renderAnmeldeseite();
    await screen.findByLabelText(/^E-Mail-Adresse/);
    await anmeldenMit('geheim');

    const meldung = await screen.findByRole('alert');
    expect(meldung).toBeInTheDocument();
    expect(meldung).not.toHaveTextContent(/fetch/i);
  });

  it('deaktiviert den Absendeknopf waehrend der Anfrage', async () => {
    ohneSitzung().mockImplementationOnce(
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
    ohneSitzung().mockResolvedValueOnce(problemAntwort(401, 'Anmeldung gescheitert.'));

    renderAnmeldeseite();
    await screen.findByLabelText(/^E-Mail-Adresse/);
    await anmeldenMit('falsch');
    await screen.findByRole('alert');

    expect(screen.getByRole('button', { name: 'Anmelden' })).toBeEnabled();
  });
});
