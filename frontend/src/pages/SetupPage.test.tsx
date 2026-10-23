import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { AuthProvider } from '../auth/AuthContext';
import { alsJson, fetchNachPfad, json, leer, problem } from '../test/fetchNachPfad';
import type { Routen } from '../test/fetchNachPfad';
import { renderMitTheme } from '../test/render';
import SetupPage from './SetupPage';

const KONTO = { id: 1, displayName: 'Manfred Wolff', email: 'info@mwolff.org', role: 'C_LEVEL' };

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

async function angabenAbsenden({ email = 'info@mwolff.org' } = {}) {
  const nutzer = userEvent.setup();
  await nutzer.type(await screen.findByLabelText(/^Einmal-Schlüssel/), 'einmal');
  await nutzer.type(screen.getByLabelText(/^Anzeigename/), 'Manfred Wolff');
  await nutzer.type(screen.getByLabelText(/^E-Mail-Adresse/), email);
  await nutzer.click(screen.getByRole('button', { name: 'Code anfordern' }));
}

async function codeAbsenden(code = '123456') {
  const nutzer = userEvent.setup();
  await nutzer.type(await screen.findByLabelText(/^Code/), code);
  await nutzer.click(screen.getByRole('button', { name: 'Einrichten' }));
}

/** Beide Schritte in einem Zug: Angaben, dann Code. */
async function einrichtenMitCode() {
  await angabenAbsenden();
  await codeAbsenden();
}

/**
 * Kein Feld vom Typ `password` (Kriterium 1). Jedes Feld der Seite ist beschriftet (jsx-a11y), also
 * findet die Suche ueber die Beschriftung auch ein Passwortfeld, das keine Rolle `textbox` hat.
 */
function keinPasswortfeld() {
  const felder = screen.queryAllByLabelText(/./);
  expect(felder.length).toBeGreaterThan(0);
  for (const feld of felder) {
    expect(feld).not.toHaveAttribute('type', 'password');
  }
}

function aufrufe(fetchMock: ReturnType<typeof frisch>, pfad: string) {
  return fetchMock.mock.calls.filter(([ziel]) => ziel === pfad);
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('Einrichtungsseite, Schritt Angaben', () => {
  it('fragt Schluessel, Anzeigename und Adresse, jedes Feld sichtbar beschriftet', async () => {
    frisch();

    renderEinrichtung();

    expect(await screen.findByLabelText(/^Einmal-Schlüssel/)).toBeInTheDocument();
    expect(screen.getByLabelText(/^Anzeigename/)).toBeInTheDocument();
    expect(screen.getByLabelText(/^E-Mail-Adresse/)).toHaveAttribute('type', 'email');
    expect(screen.getAllByRole('textbox')).toHaveLength(3);
  });

  it('hat kein Passwortfeld und keine Wiederholung der Adresse (E15)', async () => {
    frisch();

    renderEinrichtung();
    await screen.findByLabelText(/^Einmal-Schlüssel/);

    keinPasswortfeld();
    expect(screen.queryByLabelText(/Wiederholung/)).not.toBeInTheDocument();
    expect(screen.queryByText(/passwort/i)).not.toBeInTheDocument();
  });

  it('fordert den Code mit den drei Angaben an und zeigt dann Codefeld und Hinweis', async () => {
    let rumpf: unknown = null;
    const fetchMock = frisch({
      'POST /api/setup/code': (gesendet) => {
        rumpf = alsJson(gesendet);
        return leer(202)();
      },
    });

    renderEinrichtung();
    await angabenAbsenden();

    const codefeld = await screen.findByLabelText(/^Code/);
    expect(codefeld).toHaveAttribute('inputmode', 'numeric');
    expect(codefeld).toHaveAttribute('autocomplete', 'one-time-code');
    expect(codefeld).toHaveAttribute('maxlength', '6');
    expect(codefeld).toHaveFocus();
    expect(screen.getByRole('status')).toHaveTextContent(/Code ist unterwegs/);
    expect(rumpf).toEqual({
      email: 'info@mwolff.org',
      displayName: 'Manfred Wolff',
      bootstrapToken: 'einmal',
    });
    expect(aufrufe(fetchMock, '/api/setup')).toHaveLength(0);
    keinPasswortfeld();
  });

  it('sagt bei abgewiesenem Schluessel nicht, woran es lag, und bleibt beim ersten Schritt', async () => {
    frisch({ 'POST /api/setup/code': leer(403) });

    renderEinrichtung();
    await angabenAbsenden();

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Die Einrichtung wurde abgelehnt. Bitte den Einmal-Schlüssel prüfen.',
    );
    expect(screen.queryByLabelText(/^Code/)).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Code anfordern' })).toBeEnabled();
  });

  it('zeigt die Meldung des Servers zu einem Feld am Feld', async () => {
    frisch({
      'POST /api/setup/code': problem(400, 'Ungueltige Eingabe', {
        email: ['muss eine gueltige E-Mail-Adresse sein'],
      }),
    });

    renderEinrichtung();
    await angabenAbsenden({ email: 'info@mwolff' });

    expect(await screen.findByLabelText(/^E-Mail-Adresse/)).toHaveAccessibleDescription(
      'muss eine gueltige E-Mail-Adresse sein',
    );
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('sagt ohne technische Einzelheiten, wenn die Schnittstelle nicht antwortet', async () => {
    frisch();

    renderEinrichtung();
    await angabenAbsenden();

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Die Einrichtung ist gerade nicht möglich. Bitte später erneut versuchen.',
    );
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
});

describe('Einrichtungsseite, Schritt Code', () => {
  it('legt mit dem Code das Konto an und fuehrt angemeldet auf die Startadresse (K3)', async () => {
    let rumpf: unknown = null;
    frisch({
      'POST /api/setup/code': leer(202),
      'POST /api/setup': (gesendet) => {
        rumpf = alsJson(gesendet);
        return json(200, KONTO)();
      },
    });

    renderEinrichtung();
    await einrichtenMitCode();

    expect(await screen.findByText('Leitstand')).toBeInTheDocument();
    expect(rumpf).toEqual({
      bootstrapToken: 'einmal',
      displayName: 'Manfred Wolff',
      email: 'info@mwolff.org',
      code: '123456',
    });
  });

  it('nennt die Meldung des Servers zu einem falschen Code und gibt den Knopf wieder frei', async () => {
    frisch({
      'POST /api/setup/code': leer(202),
      'POST /api/setup': problem(
        401,
        'Der Code ist falsch, abgelaufen oder schon benutzt. Bitte einen neuen Code anfordern.',
      ),
    });

    renderEinrichtung();
    await einrichtenMitCode();

    expect(await screen.findByRole('alert')).toHaveTextContent(/Bitte einen neuen Code anfordern/);
    expect(screen.getByRole('button', { name: 'Einrichten' })).toBeEnabled();
  });

  it('sagt auch im zweiten Schritt bei abgewiesenem Schluessel nicht, woran es lag', async () => {
    frisch({ 'POST /api/setup/code': leer(202), 'POST /api/setup': leer(403) });

    renderEinrichtung();
    await einrichtenMitCode();

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Die Einrichtung wurde abgelehnt. Bitte den Einmal-Schlüssel prüfen.',
    );
  });

  it('zeigt die Meldung des Servers zum Code am Codefeld', async () => {
    frisch({
      'POST /api/setup/code': leer(202),
      'POST /api/setup': problem(400, 'Ungueltige Eingabe', { code: ['darf nicht leer sein'] }),
    });

    renderEinrichtung();
    await einrichtenMitCode();

    expect(await screen.findByLabelText(/^Code/)).toHaveAccessibleDescription(
      'darf nicht leer sein',
    );
  });

  it('fuehrt mit „Angaben ändern" zurueck und behaelt die Eingaben', async () => {
    frisch({ 'POST /api/setup/code': leer(202) });
    const nutzer = userEvent.setup();

    renderEinrichtung();
    await angabenAbsenden();
    await screen.findByLabelText(/^Code/);
    await nutzer.click(screen.getByRole('button', { name: 'Angaben ändern' }));

    expect(screen.getByLabelText(/^E-Mail-Adresse/)).toHaveValue('info@mwolff.org');
    expect(screen.getByLabelText(/^Anzeigename/)).toHaveValue('Manfred Wolff');
    expect(screen.queryByLabelText(/^Code/)).not.toBeInTheDocument();
  });
});
