import { afterEach, describe, expect, it, vi } from 'vitest';

import { login, logout, me, parseMe, parseSetupStatus, setupStatus } from './auth';

const KONTO = { id: 1, displayName: 'Manfred Wolff', email: 'info@mwolff.org' };

function fetchLiefert(bauen: () => Response) {
  return vi.spyOn(globalThis, 'fetch').mockImplementation(() => Promise.resolve(bauen()));
}

function jsonAntwort(rumpf: unknown): Response {
  return new Response(JSON.stringify(rumpf), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  });
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('parseMe', () => {
  it('nimmt eine vollstaendige Antwort an', () => {
    expect(parseMe(KONTO)).toEqual(KONTO);
  });

  it.each([
    ['ohne Objekt', 'kein Konto'],
    ['bei null', null],
    ['ohne id', { displayName: 'Manfred Wolff', email: 'info@mwolff.org' }],
    ['mit falsch typisierter id', { ...KONTO, id: '1' }],
    ['ohne Anzeigename', { id: 1, email: 'info@mwolff.org' }],
    ['mit falsch typisiertem Anzeigenamen', { ...KONTO, displayName: 42 }],
    ['ohne Adresse', { id: 1, displayName: 'Manfred Wolff' }],
    ['mit falsch typisierter Adresse', { ...KONTO, email: null }],
  ])('weist eine Antwort %s zurueck', (_fall, antwort) => {
    expect(() => parseMe(antwort)).toThrow(TypeError);
  });
});

describe('parseSetupStatus', () => {
  it('nimmt den Schalter an', () => {
    expect(parseSetupStatus({ initialized: true })).toEqual({ initialized: true });
  });

  it.each([
    ['ohne Objekt', 42],
    ['mit falsch typisiertem Schalter', { initialized: 'ja' }],
  ])('weist eine Antwort %s zurueck', (_fall, antwort) => {
    expect(() => parseSetupStatus(antwort)).toThrow(TypeError);
  });
});

describe('Aufrufe', () => {
  it('meldet mit Adresse und Passwort an', async () => {
    const fetchMock = fetchLiefert(() => jsonAntwort(KONTO));

    await expect(login('info@mwolff.org', 'geheim')).resolves.toEqual(KONTO);
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/auth/login',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({ email: 'info@mwolff.org', password: 'geheim' }),
      }),
    );
  });

  it('meldet ab', async () => {
    const fetchMock = fetchLiefert(() => new Response(null, { status: 204 }));

    await expect(logout()).resolves.toBeUndefined();
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/auth/logout',
      expect.objectContaining({ method: 'POST' }),
    );
  });

  it('liest das eigene Konto', async () => {
    const fetchMock = fetchLiefert(() => jsonAntwort(KONTO));

    await expect(me()).resolves.toEqual(KONTO);
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/auth/me',
      expect.objectContaining({ method: 'GET' }),
    );
  });

  it('fragt, ob die Instanz eingerichtet ist', async () => {
    const fetchMock = fetchLiefert(() => jsonAntwort({ initialized: false }));

    await expect(setupStatus()).resolves.toEqual({ initialized: false });
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/setup/status',
      expect.objectContaining({ method: 'GET' }),
    );
  });
});
