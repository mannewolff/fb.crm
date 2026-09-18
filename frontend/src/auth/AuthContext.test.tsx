import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { renderMitTheme } from '../test/render';
import { AuthProvider, useAuth } from './AuthContext';

const KONTO = { id: 1, displayName: 'Manfred Wolff', email: 'info@mwolff.org' };

function kontoAntwort(): Response {
  return new Response(JSON.stringify(KONTO), {
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
  const { sitzung, anmelden, abmelden } = useAuth();
  return (
    <div>
      <p>Zustand: {sitzung.status}</p>
      <p>Konto: {sitzung.status === 'angemeldet' ? sitzung.konto.displayName : 'keines'}</p>
      <button
        type="button"
        onClick={() => {
          void anmelden('info@mwolff.org', 'geheim');
        }}
      >
        anmelden
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

describe('useAuth', () => {
  it('scheitert ausserhalb des AuthProvider', () => {
    const fehler = vi.spyOn(console, 'error').mockImplementation(() => undefined);

    expect(() => renderMitTheme(<Spiegel />)).toThrow(/AuthProvider/);
    expect(fehler).toHaveBeenCalled();
  });
});
