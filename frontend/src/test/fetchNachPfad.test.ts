import { afterEach, describe, expect, it, vi } from 'vitest';

import { alsJson, fetchNachPfad, formularWeg, json, leer, problem } from './fetchNachPfad';

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

  it('reicht den Rumpf des Aufrufs an die Fabrik weiter', async () => {
    let gesehen: BodyInit | null = 'noch nichts';
    fetchNachPfad({
      'POST /e': (rumpf) => {
        gesehen = rumpf;
        return leer(204)();
      },
    });

    await fetch('/e', { method: 'POST', body: 'ein Rumpf' });

    expect(gesehen).toBe('ein Rumpf');
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

describe('formularWeg', () => {
  it('reicht das abgeschickte Formular heraus und antwortet', async () => {
    let gesehen = new FormData();
    const formular = new FormData();
    formular.append('art', 'KOMMENTAR');
    fetchNachPfad({
      'POST /f': formularWeg((abgeschickt) => {
        gesehen = abgeschickt;
      }, leer(201)),
    });

    const antwort = await fetch('/f', { method: 'POST', body: formular });

    expect(antwort.status).toBe(201);
    expect(gesehen.get('art')).toBe('KOMMENTAR');
  });

  it('scheitert laut, wenn der Rumpf kein Formular ist', () => {
    const merke = vi.fn();
    fetchNachPfad({ 'POST /f': formularWeg(merke, leer(201)) });

    expect(() => fetch('/f', { method: 'POST', body: 'kein Rumpf mit Feldern' })).toThrow(
      'kein Formular',
    );
    expect(merke).not.toHaveBeenCalled();
  });
});

describe('alsJson', () => {
  it('liest einen JSON-Rumpf', () => {
    expect(alsJson('{"platz":1}')).toEqual({ platz: 1 });
  });

  it.each([
    ['kein Rumpf', null],
    ['ein Formular', new FormData()],
  ])('scheitert laut bei %s', (_fall, rumpf) => {
    expect(() => alsJson(rumpf)).toThrow('Der Rumpf dieses Aufrufs ist kein JSON-Text.');
  });
});
