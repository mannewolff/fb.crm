import { afterEach, describe, expect, it, vi } from 'vitest';

import { fetchNachPfad, json, leer, problem } from './fetchNachPfad';

afterEach(() => {
  vi.restoreAllMocks();
});

describe('fetchNachPfad', () => {
  it('antwortet nach Methode und Pfad, mit frischem Rumpf je Aufruf', async () => {
    fetchNachPfad({ 'POST /a': json(201, { ok: true }) });

    const erste = await fetch('/a', { method: 'POST' });
    const zweite = await fetch('/a', { method: 'POST' });

    expect(erste.status).toBe(201);
    expect(await erste.json()).toEqual({ ok: true });
    expect(await zweite.json()).toEqual({ ok: true });
  });

  it('nimmt GET an, wenn keine Methode genannt ist', async () => {
    fetchNachPfad({ 'GET /b': leer(204) });

    expect((await fetch('/b')).status).toBe(204);
  });

  it('liest den Pfad auch aus einem Request-Objekt', async () => {
    fetchNachPfad({ 'GET http://localhost/d': leer(204) });

    expect((await fetch(new Request('http://localhost/d'))).status).toBe(204);
  });

  it('scheitert laut bei einem Aufruf ohne Eintrag', async () => {
    fetchNachPfad({});

    await expect(fetch('/nirgends', { method: 'GET' })).rejects.toThrow('Unerwarteter Aufruf: GET /nirgends');
  });

  it('baut Problem Details mit Feldfehlern', async () => {
    fetchNachPfad({ 'GET /c': problem(400, 'ungueltig', { email: ['falsch'] }) });

    expect(await (await fetch('/c')).json()).toEqual({
      title: 'Fehler',
      detail: 'ungueltig',
      fieldErrors: { email: ['falsch'] },
    });
  });
});
