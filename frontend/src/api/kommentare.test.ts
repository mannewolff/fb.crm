import { afterEach, describe, expect, it, vi } from 'vitest';

import {
  kommentarAendern,
  kommentarLoeschen,
  kommentarSchreiben,
  kommentareLesen,
  parseKommentar,
  parseKommentare,
} from './kommentare';
import { alsJson, fetchNachPfad, json, leer } from '../test/fetchNachPfad';

const KOMMENTAR = { id: 3, text: 'Kunde ruft zurueck.', createdAt: '2026-09-30T12:05:00Z' };

afterEach(() => {
  vi.restoreAllMocks();
});

describe('parseKommentar', () => {
  it('verengt einen Kommentar', () => {
    expect(parseKommentar(KOMMENTAR)).toEqual(KOMMENTAR);
  });

  it.each([
    ['kein Objekt', 42],
    ['null', null],
    ['id als Text', { ...KOMMENTAR, id: '3' }],
    ['ohne Text', { ...KOMMENTAR, text: null }],
    ['createdAt als Zahl', { ...KOMMENTAR, createdAt: 17590000 }],
    ['ohne createdAt', { ...KOMMENTAR, createdAt: undefined }],
  ])('weist eine Antwort ab: %s', (_fall, rumpf) => {
    expect(() => parseKommentar(rumpf)).toThrow(TypeError);
  });
});

describe('parseKommentare', () => {
  it('verengt die Liste', () => {
    expect(parseKommentare({ kommentare: [KOMMENTAR] })).toEqual({ kommentare: [KOMMENTAR] });
  });

  it('verengt die leere Liste', () => {
    expect(parseKommentare({ kommentare: [] })).toEqual({ kommentare: [] });
  });

  it.each([
    ['kein Objekt', 'x'],
    ['ohne kommentare', {}],
    ['kommentare ist kein Array', { kommentare: 3 }],
    ['Kommentar ohne id', { kommentare: [{ ...KOMMENTAR, id: null }] }],
  ])('weist eine Antwort ab: %s', (_fall, rumpf) => {
    expect(() => parseKommentare(rumpf)).toThrow(TypeError);
  });
});

describe('kommentareLesen', () => {
  it('fragt die Kommentare des Angebots ab', async () => {
    fetchNachPfad({
      'GET /api/angebote/9/kommentare': json(200, { kommentare: [KOMMENTAR] }),
    });

    await expect(kommentareLesen(9)).resolves.toEqual({ kommentare: [KOMMENTAR] });
  });
});

describe('kommentarSchreiben', () => {
  it('schickt den Text und gibt den neuen Kommentar heraus', async () => {
    let gesendet: unknown = null;
    fetchNachPfad({
      'POST /api/angebote/9/kommentare': (rumpf) => {
        gesendet = alsJson(rumpf);
        return json(201, KOMMENTAR)();
      },
    });

    await expect(kommentarSchreiben(9, 'Kunde ruft zurueck.')).resolves.toEqual(KOMMENTAR);
    expect(gesendet).toEqual({ text: 'Kunde ruft zurueck.' });
  });
});

describe('kommentarAendern', () => {
  it('schickt den Text an den Kommentar und gibt ihn geaendert heraus', async () => {
    let gesendet: unknown = null;
    fetchNachPfad({
      'PUT /api/angebote/9/kommentare/3': (rumpf) => {
        gesendet = alsJson(rumpf);
        return json(200, { ...KOMMENTAR, text: 'Neuer Text' })();
      },
    });

    await expect(kommentarAendern(9, 3, 'Neuer Text')).resolves.toEqual({
      ...KOMMENTAR,
      text: 'Neuer Text',
    });
    expect(gesendet).toEqual({ text: 'Neuer Text' });
  });
});

describe('kommentarLoeschen', () => {
  it('loescht den Kommentar ohne Rumpf', async () => {
    const abruf = fetchNachPfad({
      'DELETE /api/angebote/9/kommentare/3': leer(204),
    });

    await expect(kommentarLoeschen(9, 3)).resolves.toBeUndefined();
    expect(abruf.mock.calls[0][1]?.body).toBeUndefined();
  });
});
