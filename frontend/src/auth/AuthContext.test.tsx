import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { apiOhneInhalt } from '../api/client';
import { renderMitTheme } from '../test/render';
import { AuthProvider, useAuth } from './AuthContext';
import { useRechte } from './rechte';

const KONTO = { id: 1, displayName: 'Manfred Wolff', email: 'info@mwolff.org', role: 'C_LEVEL' };

function kontoAntwort(konto: unknown = KONTO): Response {
  return new Response(JSON.stringify(konto), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  });
}

function fetchLiefert(bauen: () => Response) {
  return vi.spyOn(globalThis, 'fetch').mockImplementation(() => Promise.resolve(bauen()));
}

/** Loest erst im naechsten Durchlauf auf — so laesst sich der Ausbau davor setzen. */
function fetchLiefertVerzoegert(bauen: () => Response) {
  return vi.spyOn(globalThis, 'fetch').mockImplementation(
    () =>
      new Promise<Response>((aufloesen) => {
        setTimeout(() => {
          aufloesen(bauen());
        }, 0);
      }),
  );
}

/** Spiegelt den Sitzungszustand und bietet die beiden Wege daraus an. */
function Spiegel() {
  const { sitzung, codeAnfordern, anmelden, einrichten, abmelden, rolleNeuLesen } = useAuth();
  const { darfKontenVerwalten } = useRechte();
  return (
    <div>
      <p>Zustand: {sitzung.status}</p>
      <p>Konto: {sitzung.status === 'angemeldet' ? sitzung.konto.displayName : 'keines'}</p>
      <p>Rolle: {sitzung.status === 'angemeldet' ? sitzung.konto.role : 'keine'}</p>
      <p>Konten verwalten: {darfKontenVerwalten ? 'ja' : 'nein'}</p>
      <button
        type="button"
        onClick={() => {
          void rolleNeuLesen();
        }}
      >
        Rolle neu lesen
      </button>
      <button
        type="button"
        onClick={() => {
          apiOhneInhalt('/api/firmen', { methode: 'POST' }).catch(() => undefined);
        }}
      >
        verbotene Anfrage
      </button>
      <button
        type="button"
        onClick={() => {
          void codeAnfordern('info@mwolff.org');
        }}
      >
        Code anfordern
      </button>
      <button
        type="button"
        onClick={() => {
          void anmelden('info@mwolff.org', '123456');
        }}
      >
        anmelden
      </button>
      <button
        type="button"
        onClick={() => {
          void einrichten({
            bootstrapToken: 'einmal',
            displayName: 'Manfred Wolff',
            email: 'info@mwolff.org',
            code: '123456',
          });
        }}
      >
        einrichten
      </button>
      <button
        type="button"
        onClick={() => {
          void abmelden();
        }}
      >
        abmelden
      </button>
    </div>
  );
}

function renderSpiegel() {
  return renderMitTheme(
    <AuthProvider>
      <Spiegel />
    </AuthProvider>,
  );
}

/**
 * Ein Speicher-Doppel auf `globalThis`.
 *
 * Node bringt in dieser Umgebung ein `localStorage` mit, das ohne `--localstorage-file`
 * nichts liefert — der Zustand liesse sich also nicht nachlesen. Das Doppel dreht die Frage
 * um: Nicht „was liegt drin", sondern „wurde ueberhaupt etwas abgelegt".
 */
function speicherDoppel(name: 'localStorage' | 'sessionStorage') {
  const setItem = vi.fn();
  Object.defineProperty(globalThis, name, {
    value: { setItem, getItem: vi.fn(), removeItem: vi.fn(), clear: vi.fn(), key: vi.fn(), length: 0 },
    configurable: true,
  });
  return setItem;
}

afterEach(() => {
  vi.restoreAllMocks();
  Reflect.deleteProperty(globalThis, 'localStorage');
  Reflect.deleteProperty(globalThis, 'sessionStorage');
});

describe('AuthProvider', () => {
  it('startet im Zustand unbekannt, solange die Sitzung nicht beantwortet ist', () => {
    fetchLiefertVerzoegert(kontoAntwort);

    renderSpiegel();

    expect(screen.getByText('Zustand: unbekannt')).toBeInTheDocument();
  });

  it('gilt als abgemeldet, wenn die Sitzungspruefung 401 antwortet', async () => {
    fetchLiefert(() => new Response(null, { status: 401 }));

    renderSpiegel();

    expect(await screen.findByText('Zustand: abgemeldet')).toBeInTheDocument();
    expect(screen.getByText('Konto: keines')).toBeInTheDocument();
  });

  it('gilt als angemeldet, wenn die Sitzungspruefung ein Konto liefert', async () => {
    fetchLiefert(kontoAntwort);

    renderSpiegel();

    expect(await screen.findByText('Zustand: angemeldet')).toBeInTheDocument();
    expect(screen.getByText('Konto: Manfred Wolff')).toBeInTheDocument();
  });

  it('wechselt nach dem Anmelden in den angemeldeten Zustand', async () => {
    const fetchMock = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(new Response(null, { status: 401 }))
      .mockResolvedValueOnce(kontoAntwort());
    const nutzer = userEvent.setup();

    renderSpiegel();
    await screen.findByText('Zustand: abgemeldet');
    await nutzer.click(screen.getByRole('button', { name: 'anmelden' }));

    expect(await screen.findByText('Zustand: angemeldet')).toBeInTheDocument();
    expect(screen.getByText('Konto: Manfred Wolff')).toBeInTheDocument();
    expect(fetchMock).toHaveBeenLastCalledWith('/api/auth/login', expect.anything());
  });

  it('bleibt beim Anfordern eines Codes abgemeldet', async () => {
    const fetchMock = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(new Response(null, { status: 401 }))
      .mockResolvedValueOnce(new Response(null, { status: 202 }));
    const nutzer = userEvent.setup();

    renderSpiegel();
    await screen.findByText('Zustand: abgemeldet');
    await nutzer.click(screen.getByRole('button', { name: 'Code anfordern' }));

    await waitFor(() => {
      expect(fetchMock).toHaveBeenLastCalledWith('/api/auth/code', expect.anything());
    });
    expect(screen.getByText('Zustand: abgemeldet')).toBeInTheDocument();
  });

  it('ist nach dem Einrichten angemeldet — die Einrichtung meldet den Betreiber an (K3)', async () => {
    const fetchMock = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(new Response(null, { status: 401 }))
      .mockResolvedValueOnce(kontoAntwort());
    const nutzer = userEvent.setup();

    renderSpiegel();
    await screen.findByText('Zustand: abgemeldet');
    await nutzer.click(screen.getByRole('button', { name: 'einrichten' }));

    expect(await screen.findByText('Zustand: angemeldet')).toBeInTheDocument();
    expect(fetchMock).toHaveBeenLastCalledWith('/api/setup', expect.anything());
  });

  it('legt auf keinem Weg etwas im Speicher des Browsers ab', async () => {
    const lokal = speicherDoppel('localStorage');
    const sitzungsweit = speicherDoppel('sessionStorage');
    vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(new Response(null, { status: 401 }))
      .mockResolvedValueOnce(kontoAntwort())
      .mockResolvedValueOnce(new Response(null, { status: 204 }));
    const nutzer = userEvent.setup();

    renderSpiegel();
    await screen.findByText('Zustand: abgemeldet');
    await nutzer.click(screen.getByRole('button', { name: 'anmelden' }));
    await screen.findByText('Zustand: angemeldet');
    await nutzer.click(screen.getByRole('button', { name: 'abmelden' }));
    await screen.findByText('Zustand: abgemeldet');

    // Die Sitzung steckt im HttpOnly-Cookie; im JS-Speicher darf nichts davon liegen
    // (CLAUDE-security.md).
    expect(lokal).not.toHaveBeenCalled();
    expect(sitzungsweit).not.toHaveBeenCalled();
  });

  it('leert den Zustand nach dem Abmelden', async () => {
    vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(kontoAntwort())
      .mockResolvedValueOnce(new Response(null, { status: 204 }));
    const nutzer = userEvent.setup();

    renderSpiegel();
    await screen.findByText('Zustand: angemeldet');
    await nutzer.click(screen.getByRole('button', { name: 'abmelden' }));

    expect(await screen.findByText('Zustand: abgemeldet')).toBeInTheDocument();
    expect(screen.getByText('Konto: keines')).toBeInTheDocument();
  });

  it('schreibt nach dem Ausbau keinen Zustand mehr, wenn das Konto noch eintrifft', async () => {
    const fehler = vi.spyOn(console, 'error').mockImplementation(() => undefined);
    fetchLiefertVerzoegert(kontoAntwort);

    const { unmount } = renderSpiegel();
    unmount();
    await new Promise((weiter) => setTimeout(weiter, 10));

    expect(fehler).not.toHaveBeenCalled();
  });

  it('schreibt nach dem Ausbau keinen Zustand mehr, wenn die Pruefung scheitert', async () => {
    const fehler = vi.spyOn(console, 'error').mockImplementation(() => undefined);
    fetchLiefertVerzoegert(() => new Response(null, { status: 401 }));

    const { unmount } = renderSpiegel();
    unmount();
    await new Promise((weiter) => setTimeout(weiter, 10));

    expect(fehler).not.toHaveBeenCalled();
  });
});

describe('Rolle der Sitzung (Plan #414, A8, E5)', () => {
  const EMPLOYEE = { ...KONTO, role: 'EMPLOYEE' };

  function verboteneAntwort(): Response {
    return new Response(JSON.stringify({ detail: 'Nicht erlaubt' }), {
      status: 403,
      headers: { 'Content-Type': 'application/problem+json' },
    });
  }

  it('traegt die Rolle im Zustand angemeldet', async () => {
    fetchLiefert(() => kontoAntwort(EMPLOYEE));

    renderSpiegel();

    expect(await screen.findByText('Rolle: EMPLOYEE')).toBeInTheDocument();
    expect(screen.getByText('Konten verwalten: nein')).toBeInTheDocument();
  });

  it('liest die Rolle nach einem 403 neu', async () => {
    const fetchMock = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(kontoAntwort())
      .mockResolvedValueOnce(verboteneAntwort())
      .mockResolvedValueOnce(kontoAntwort(EMPLOYEE));
    const nutzer = userEvent.setup();

    renderSpiegel();
    await screen.findByText('Rolle: C_LEVEL');
    expect(screen.getByText('Konten verwalten: ja')).toBeInTheDocument();
    await nutzer.click(screen.getByRole('button', { name: 'verbotene Anfrage' }));

    expect(await screen.findByText('Rolle: EMPLOYEE')).toBeInTheDocument();
    expect(screen.getByText('Konten verwalten: nein')).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(fetchMock).toHaveBeenLastCalledWith('/api/auth/me', expect.anything());
  });

  it('liest ohne Sitzung nach einem 403 nichts nach', async () => {
    const fetchMock = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(new Response(null, { status: 401 }))
      .mockResolvedValueOnce(verboteneAntwort());
    const nutzer = userEvent.setup();

    renderSpiegel();
    await screen.findByText('Zustand: abgemeldet');
    await nutzer.click(screen.getByRole('button', { name: 'verbotene Anfrage' }));

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledTimes(2);
    });
    expect(fetchMock).toHaveBeenLastCalledWith('/api/firmen', expect.anything());
    expect(screen.getByText('Zustand: abgemeldet')).toBeInTheDocument();
  });

  it('ruft sich nicht ohne Ende neu auf, wenn das Nachlesen selbst mit 403 antwortet', async () => {
    const fetchMock = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(kontoAntwort())
      .mockResolvedValueOnce(verboteneAntwort())
      .mockResolvedValueOnce(verboteneAntwort())
      .mockResolvedValue(kontoAntwort(EMPLOYEE));
    const nutzer = userEvent.setup();

    renderSpiegel();
    await screen.findByText('Rolle: C_LEVEL');
    await nutzer.click(screen.getByRole('button', { name: 'verbotene Anfrage' }));
    await new Promise((weiter) => setTimeout(weiter, 20));

    expect(fetchMock).toHaveBeenCalledTimes(3);
    // Ein Nachlesen, das selbst scheitert, laesst die Sitzung stehen.
    expect(screen.getByText('Rolle: C_LEVEL')).toBeInTheDocument();
    expect(screen.getByText('Zustand: angemeldet')).toBeInTheDocument();
  });

  it('gilt als abgemeldet, wenn das Nachlesen mit 401 antwortet', async () => {
    vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(kontoAntwort())
      .mockResolvedValueOnce(new Response(null, { status: 401 }));
    const nutzer = userEvent.setup();

    renderSpiegel();
    await screen.findByText('Zustand: angemeldet');
    await nutzer.click(screen.getByRole('button', { name: 'Rolle neu lesen' }));

    expect(await screen.findByText('Zustand: abgemeldet')).toBeInTheDocument();
  });

  it('behaelt die Sitzung, wenn das Nachlesen am Netz scheitert', async () => {
    vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(kontoAntwort())
      .mockRejectedValueOnce(new TypeError('Netz weg'));
    const nutzer = userEvent.setup();

    renderSpiegel();
    await screen.findByText('Zustand: angemeldet');
    await nutzer.click(screen.getByRole('button', { name: 'Rolle neu lesen' }));
    await new Promise((weiter) => setTimeout(weiter, 10));

    expect(screen.getByText('Zustand: angemeldet')).toBeInTheDocument();
    expect(screen.getByText('Rolle: C_LEVEL')).toBeInTheDocument();
  });

  it('nimmt den Rueckruf beim Ausbau wieder heraus', async () => {
    const fetchMock = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(kontoAntwort())
      .mockResolvedValueOnce(verboteneAntwort());

    const { unmount } = renderSpiegel();
    await screen.findByText('Zustand: angemeldet');
    unmount();
    await apiOhneInhalt('/api/firmen', { methode: 'POST' }).catch(() => undefined);

    expect(fetchMock).toHaveBeenCalledTimes(2);
  });
});

describe('useAuth', () => {
  it('scheitert ausserhalb des AuthProvider', () => {
    const fehler = vi.spyOn(console, 'error').mockImplementation(() => undefined);

    expect(() => renderMitTheme(<Spiegel />)).toThrow(/AuthProvider/);
    expect(fehler).toHaveBeenCalled();
  });
});
