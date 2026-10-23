import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { AuthProvider } from '../auth/AuthContext';
import { alsJson, fetchNachPfad, json, leer } from '../test/fetchNachPfad';
import { renderMitTheme } from '../test/render';
import LoginPage from './LoginPage';

const KONTO = { id: 1, displayName: 'Manfred Wolff', email: 'info@mwolff.org', role: 'C_LEVEL' };

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

function angenommen(): Response {
  return new Response(null, { status: 202 });
}

type Antwort = () => Response | Promise<Response>;

interface Ablauf {
  readonly anforderungen?: readonly Antwort[];
  readonly anmeldungen?: readonly Antwort[];
}

/**
 * Keine Sitzung, eingerichtete Instanz — und fuer jede Anforderung und jeden Anmeldeversuch die
 * naechste Antwort. Ohne Angabe nimmt der Server jede Anforderung mit 202 an.
 *
 * Nach Pfad statt nach Reihenfolge: Beim Aufbau fragen Provider und Seite gleichzeitig, und
 * welche Anfrage zuerst hinausgeht, ist kein Teil der Zusage.
 */
function ohneSitzung({ anforderungen, anmeldungen = [] }: Ablauf = {}) {
  return aufInstanz(true, { anforderungen, anmeldungen });
}

function aufInstanz(eingerichtet: boolean, { anforderungen, anmeldungen = [] }: Ablauf = {}) {
  let naechsteAnforderung = 0;
  let naechsteAnmeldung = 0;
  return fetchNachPfad({
    'GET /api/auth/me': leer(401),
    'GET /api/setup/status': json(200, { initialized: eingerichtet }),
    'POST /api/auth/code': () =>
      anforderungen === undefined ? angenommen() : anforderungen[naechsteAnforderung++](),
    'POST /api/auth/login': () => anmeldungen[naechsteAnmeldung++](),
  });
}

function aufrufe(fetchMock: ReturnType<typeof ohneSitzung>, pfad: string) {
  return fetchMock.mock.calls.filter(([ziel]) => ziel === pfad);
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

async function adresseAbsenden(adresse = 'info@mwolff.org') {
  const nutzer = userEvent.setup();
  const feld = await screen.findByLabelText(/^E-Mail-Adresse/);
  await nutzer.clear(feld);
  await nutzer.type(feld, adresse);
  await nutzer.click(screen.getByRole('button', { name: 'Code anfordern' }));
}

async function codeAbsenden(code: string) {
  const nutzer = userEvent.setup();
  const feld = await screen.findByLabelText(/^Code/);
  await nutzer.clear(feld);
  await nutzer.type(feld, code);
  await nutzer.click(screen.getByRole('button', { name: 'Anmelden' }));
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

afterEach(() => {
  vi.restoreAllMocks();
});

describe('Anmeldeseite, Schritt Adresse', () => {
  it('fragt nur nach der E-Mail-Adresse, sichtbar beschriftet', async () => {
    ohneSitzung();

    renderAnmeldeseite();

    expect(await screen.findByLabelText(/^E-Mail-Adresse/)).toHaveAttribute('type', 'email');
    expect(screen.queryByLabelText(/^Code/)).not.toBeInTheDocument();
    expect(screen.getAllByRole('textbox')).toHaveLength(1);
    keinPasswortfeld();
  });

  it('bietet weder Registrierung noch „Passwort vergessen" an', async () => {
    ohneSitzung();

    renderAnmeldeseite();
    await screen.findByLabelText(/^E-Mail-Adresse/);

    expect(screen.queryByText(/registr/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/konto anlegen/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/passwort/i)).not.toBeInTheDocument();
    expect(screen.queryAllByRole('link')).toHaveLength(0);
  });

  it('schickt die Adresse an die Anforderung des Codes', async () => {
    let rumpf: unknown = null;
    fetchNachPfad({
      'GET /api/auth/me': leer(401),
      'GET /api/setup/status': json(200, { initialized: true }),
      'POST /api/auth/code': (gesendet) => {
        rumpf = alsJson(gesendet);
        return angenommen();
      },
    });

    renderAnmeldeseite();
    await adresseAbsenden();
    await screen.findByLabelText(/^Code/);

    expect(rumpf).toEqual({ email: 'info@mwolff.org' });
  });

  it('zeigt nach dem Absenden Codefeld und Hinweis, ohne Passwortfeld', async () => {
    ohneSitzung();

    renderAnmeldeseite();
    await adresseAbsenden();

    const codefeld = await screen.findByLabelText(/^Code/);
    expect(codefeld).toHaveAttribute('inputmode', 'numeric');
    expect(codefeld).toHaveAttribute('autocomplete', 'one-time-code');
    expect(codefeld).toHaveAttribute('maxlength', '6');
    expect(screen.getByRole('status')).toHaveTextContent(/Code ist unterwegs/);
    expect(screen.getByRole('status')).toHaveTextContent(/falls es zu dieser Adresse ein Konto gibt/);
    keinPasswortfeld();
  });

  it('zeigt denselben Hinweis, ob es zu der Adresse ein Konto gibt oder nicht (E17)', async () => {
    // Beide Anforderungen antworten 202: so, wie der Server fuer jede Adresse antwortet (E13).
    const fetchMock = ohneSitzung({ anforderungen: [angenommen, angenommen] });
    const nutzer = userEvent.setup();

    renderAnmeldeseite();
    await adresseAbsenden('info@mwolff.org');
    const zumKonto = (await screen.findByRole('status')).textContent;

    await nutzer.click(screen.getByRole('button', { name: 'Andere Adresse' }));
    await adresseAbsenden('niemand@example.org');
    await waitFor(() => {
      expect(aufrufe(fetchMock, '/api/auth/code')).toHaveLength(2);
    });
    const ohneKonto = (await screen.findByRole('status')).textContent;

    expect(ohneKonto).toBe(zumKonto);
  });

  it('bleibt bei einer abgewiesenen Anforderung beim Schritt Adresse und nennt die Meldung', async () => {
    ohneSitzung({
      anforderungen: [() => problemAntwort(429, 'Zu viele Anforderungen. Bitte in 12 Minuten erneut versuchen.')],
    });

    renderAnmeldeseite();
    await adresseAbsenden();

    expect(await screen.findByRole('alert')).toHaveTextContent(/in 12 Minuten/);
    expect(screen.queryByLabelText(/^Code/)).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Code anfordern' })).toBeEnabled();
  });

  it('meldet einen Ausfall der Anforderung ohne technische Einzelheiten', async () => {
    ohneSitzung({ anforderungen: [() => Promise.reject(new TypeError('Failed to fetch'))] });

    renderAnmeldeseite();
    await adresseAbsenden();

    const meldung = await screen.findByRole('alert');
    expect(meldung).toHaveTextContent('Die Anmeldung ist gerade nicht möglich.');
    expect(meldung).not.toHaveTextContent(/fetch/i);
  });

  it('deaktiviert den Absendeknopf waehrend der Anforderung', async () => {
    let aufloesen!: (antwort: Response) => void;
    const antwort = new Promise<Response>((weiter) => {
      aufloesen = weiter;
    });
    ohneSitzung({ anforderungen: [() => antwort] });

    renderAnmeldeseite();
    await adresseAbsenden();

    expect(screen.getByRole('button', { name: 'Code anfordern' })).toBeDisabled();
    aufloesen(angenommen());
    expect(await screen.findByLabelText(/^Code/)).toBeInTheDocument();
  });
});

describe('Anmeldeseite, Schritt Code', () => {
  it('fuehrt mit dem richtigen Code in die Anwendung', async () => {
    let rumpf: unknown = null;
    fetchNachPfad({
      'GET /api/auth/me': leer(401),
      'GET /api/setup/status': json(200, { initialized: true }),
      'POST /api/auth/code': angenommen,
      'POST /api/auth/login': (gesendet) => {
        rumpf = alsJson(gesendet);
        return kontoAntwort();
      },
    });

    renderAnmeldeseite();
    await adresseAbsenden();
    await codeAbsenden('123456');

    expect(await screen.findByText('Leitstand')).toBeInTheDocument();
    expect(rumpf).toEqual({ email: 'info@mwolff.org', code: '123456' });
  });

  it('setzt den Fokus nach dem Absenden der Adresse in das Codefeld', async () => {
    ohneSitzung();

    renderAnmeldeseite();
    await adresseAbsenden();

    expect(await screen.findByLabelText(/^Code/)).toHaveFocus();
  });

  it('zeigt fuer jede gescheiterte Eingabe denselben Satz, gleich was der Server schreibt', async () => {
    const fetchMock = ohneSitzung({
      anmeldungen: [
        () => problemAntwort(401, 'Es gibt kein Konto mit dieser Adresse.'),
        () => problemAntwort(401, 'Der Code ist abgelaufen.'),
      ],
    });

    renderAnmeldeseite();
    await adresseAbsenden();
    await codeAbsenden('111111');
    const ersteMeldung = (await screen.findByRole('alert')).textContent;

    await codeAbsenden('222222');
    await waitFor(() => {
      expect(aufrufe(fetchMock, '/api/auth/login')).toHaveLength(2);
    });
    const zweiteMeldung = screen.getByRole('alert').textContent;

    expect(zweiteMeldung).toBe(ersteMeldung);
    expect(ersteMeldung).toBe(
      'Der Code ist falsch, abgelaufen oder schon benutzt. Bitte einen neuen Code anfordern.',
    );
  });

  it('reicht die Meldung des Servers durch, wenn sie nichts ueber das Konto verraet', async () => {
    ohneSitzung({
      anmeldungen: [() => problemAntwort(429, 'Zu viele Versuche. Bitte in 9 Minuten erneut versuchen.')],
    });

    renderAnmeldeseite();
    await adresseAbsenden();
    await codeAbsenden('123456');

    expect(await screen.findByRole('alert')).toHaveTextContent(/in 9 Minuten/);
  });

  it('meldet einen Ausfall der Anmeldung ohne technische Einzelheiten', async () => {
    ohneSitzung({ anmeldungen: [() => Promise.reject(new TypeError('Failed to fetch'))] });

    renderAnmeldeseite();
    await adresseAbsenden();
    await codeAbsenden('123456');

    const meldung = await screen.findByRole('alert');
    expect(meldung).toBeInTheDocument();
    expect(meldung).not.toHaveTextContent(/fetch/i);
  });

  it('deaktiviert den Absendeknopf waehrend der Anmeldung', async () => {
    let aufloesen!: (antwort: Response) => void;
    const antwort = new Promise<Response>((weiter) => {
      aufloesen = weiter;
    });
    ohneSitzung({ anmeldungen: [() => antwort] });

    renderAnmeldeseite();
    await adresseAbsenden();
    await codeAbsenden('123456');

    expect(screen.getByRole('button', { name: 'Anmelden' })).toBeDisabled();
    aufloesen(kontoAntwort());
    expect(await screen.findByText('Leitstand')).toBeInTheDocument();
  });

  it('gibt den Absendeknopf nach einem Fehlschlag wieder frei', async () => {
    ohneSitzung({ anmeldungen: [() => problemAntwort(401, 'Anmeldung gescheitert.')] });

    renderAnmeldeseite();
    await adresseAbsenden();
    await codeAbsenden('000000');
    await screen.findByRole('alert');

    expect(screen.getByRole('button', { name: 'Anmelden' })).toBeEnabled();
  });

  it('fuehrt mit „Andere Adresse" zurueck zum ersten Schritt und behaelt die Adresse', async () => {
    ohneSitzung({ anmeldungen: [() => problemAntwort(401, 'Anmeldung gescheitert.')] });
    const nutzer = userEvent.setup();

    renderAnmeldeseite();
    await adresseAbsenden();
    await codeAbsenden('000000');
    await screen.findByRole('alert');
    await nutzer.click(screen.getByRole('button', { name: 'Andere Adresse' }));

    expect(screen.getByLabelText(/^E-Mail-Adresse/)).toHaveValue('info@mwolff.org');
    expect(screen.queryByLabelText(/^Code/)).not.toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });
});

describe('Anmeldeseite, Einrichtungsstand', () => {
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

    renderAnmeldeseite({ hinweis: 'Die Sitzung ist abgelaufen.' });

    expect(await screen.findByRole('status')).toHaveTextContent('Die Sitzung ist abgelaufen.');
  });
});
