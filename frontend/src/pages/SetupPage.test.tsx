import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { AuthProvider } from '../auth/AuthContext';
import { fetchNachPfad, json, leer, problem } from '../test/fetchNachPfad';
import type { Routen } from '../test/fetchNachPfad';
import { renderMitTheme } from '../test/render';
import SetupPage from './SetupPage';

const KONTO = { id: 1, displayName: 'Manfred Wolff', email: 'info@mwolff.org' };

/** Frische Instanz ohne Sitzung — dazu, was der jeweilige Fall braucht. */
function frisch(weitere: Routen = {}) {
  return fetchNachPfad({
    'GET /api/auth/me': leer(401),
    'GET /api/setup/status': json(200, { initialized: false }),
    ...weitere,
  });
}

function renderEinrichtung() {
  return renderMitTheme(
    <MemoryRouter initialEntries={['/einrichten']}>
      <AuthProvider>
        <Routes>
          <Route path="/einrichten" element={<SetupPage />} />
          <Route path="/" element={<p>Leitstand</p>} />
          <Route path="/anmelden" element={<p>Anmeldeseite</p>} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
}

async function ausfuellen({
  email = 'info@mwolff.org',
  wiederholung = 'info@mwolff.org',
  passwort = 'geheim-genug',
} = {}) {
  const nutzer = userEvent.setup();
  await nutzer.type(await screen.findByLabelText(/^E-Mail-Adresse/), email);
  await nutzer.type(screen.getByLabelText(/^Wiederholung der E-Mail-Adresse/), wiederholung);
  await nutzer.type(screen.getByLabelText(/^Anzeigename/), 'Manfred Wolff');
  await nutzer.type(screen.getByLabelText(/^Passwort/), passwort);
  await nutzer.type(screen.getByLabelText(/^Einmal-Schlüssel/), 'einmal');
  await nutzer.click(screen.getByRole('button', { name: 'Einrichten' }));
}

function setupAufrufe(fetchMock: ReturnType<typeof frisch>) {
  return fetchMock.mock.calls.filter(([ziel]) => ziel === '/api/setup');
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('Einrichtungsseite', () => {
  it('beschriftet jedes Feld sichtbar und zuordenbar', async () => {
    frisch();

    renderEinrichtung();

    expect(await screen.findByLabelText(/^E-Mail-Adresse/)).toHaveAttribute('type', 'email');
    expect(screen.getByLabelText(/^Wiederholung der E-Mail-Adresse/)).toHaveAttribute('type', 'email');
    expect(screen.getByLabelText(/^Anzeigename/)).toBeInTheDocument();
    expect(screen.getByLabelText(/^Passwort/)).toHaveAttribute('type', 'password');
    expect(screen.getByLabelText(/^Einmal-Schlüssel/)).toBeInTheDocument();
  });

  it('meldet eine abweichende Wiederholung am Feld und schickt nichts ab (E6)', async () => {
    const fetchMock = frisch();

    renderEinrichtung();
    await ausfuellen({ wiederholung: 'info@mwolf.org' });

    const feld = screen.getByLabelText(/^Wiederholung der E-Mail-Adresse/);
    expect(feld).toHaveAccessibleDescription(/stimmt nicht mit der E-Mail-Adresse überein/);
    expect(feld).toHaveAttribute('aria-invalid', 'true');
    expect(setupAufrufe(fetchMock)).toHaveLength(0);
  });

  it('nimmt eine Wiederholung in anderer Gross- und Kleinschreibung an, wie der Server', async () => {
    const fetchMock = frisch({ 'POST /api/setup': json(200, KONTO) });

    renderEinrichtung();
    await ausfuellen({ wiederholung: 'Info@MWolff.org' });

    expect(await screen.findByText('Leitstand')).toBeInTheDocument();
    expect(setupAufrufe(fetchMock)).toHaveLength(1);
  });

  it('zeigt die Meldung des Servers zu einem kurzen Passwort am Feld (K6, E26)', async () => {
    frisch({
      'POST /api/setup': problem(400, 'Ungueltige Eingabe', {
        password: ['muss mindestens 8 Zeichen lang sein'],
      }),
    });

    renderEinrichtung();
    await ausfuellen({ passwort: 'sieben7' });

    expect(await screen.findByLabelText(/^Passwort/)).toHaveAccessibleDescription(
      'muss mindestens 8 Zeichen lang sein',
    );
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('zeigt bei eingerichteter Instanz eine Meldung statt des Formulars (K3)', async () => {
    fetchNachPfad({
      'GET /api/auth/me': leer(401),
      'GET /api/setup/status': json(200, { initialized: true }),
    });

    renderEinrichtung();

    expect(await screen.findByText(/bereits eingerichtet/)).toBeInTheDocument();
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Zur Anmeldung/ })).toHaveAttribute('href', '/anmelden');
  });

  it('zeigt das Formular, wenn der Einrichtungsstand nicht zu erfahren ist', async () => {
    fetchNachPfad({
      'GET /api/auth/me': leer(401),
      'GET /api/setup/status': leer(503),
    });

    renderEinrichtung();

    expect(await screen.findByLabelText(/^Einmal-Schlüssel/)).toBeInTheDocument();
  });

  it('fuehrt nach der Einrichtung angemeldet auf die Startadresse (K3)', async () => {
    frisch({ 'POST /api/setup': json(200, KONTO) });

    renderEinrichtung();
    await ausfuellen();

    expect(await screen.findByText('Leitstand')).toBeInTheDocument();
  });

  it('sagt bei abgewiesenem Schluessel nicht, woran es lag', async () => {
    frisch({ 'POST /api/setup': leer(403) });

    renderEinrichtung();
    await ausfuellen();

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Die Einrichtung wurde abgelehnt. Bitte den Einmal-Schlüssel prüfen.',
    );
  });

  it('reicht eine andere Meldung des Servers ohne Feldbezug durch', async () => {
    frisch({ 'POST /api/setup': problem(500, 'Gerade nicht moeglich.') });

    renderEinrichtung();
    await ausfuellen();

    expect(await screen.findByRole('alert')).toHaveTextContent('Gerade nicht moeglich.');
  });

  it('sagt ohne technische Einzelheiten, wenn die Schnittstelle nicht antwortet', async () => {
    frisch();

    renderEinrichtung();
    await ausfuellen();

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Die Einrichtung ist gerade nicht möglich. Bitte später erneut versuchen.',
    );
  });
});
