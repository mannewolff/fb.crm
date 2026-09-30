import { afterEach, describe, expect, it, vi } from 'vitest';

import {
  anlageHochladen,
  anlageInhalt,
  anlageInhaltPfad,
  anlageLoeschen,
  anlagenLesen,
  parseAnlage,
  parseAnlagen,
} from './anlagen';
import { blobText } from '../test/blobText';
import { fetchNachPfad, formularWeg, json, leer } from '../test/fetchNachPfad';

const ANLAGE = {
  id: 3,
  dateiName: 'anfrage.pdf',
  groesse: 2048,
  vorschauArt: 'PDF',
  createdAt: '2026-09-30T12:05:00Z',
};

const OHNE_VORSCHAU = { ...ANLAGE, id: 4, dateiName: 'liste.xlsx', vorschauArt: null };

afterEach(() => {
  vi.restoreAllMocks();
});

describe('parseAnlage', () => {
  it('verengt eine Anlage', () => {
    expect(parseAnlage(ANLAGE)).toEqual(ANLAGE);
  });

  it('verengt eine Anlage ohne Vorschauart', () => {
    expect(parseAnlage(OHNE_VORSCHAU)).toEqual(OHNE_VORSCHAU);
  });

  it.each([['PNG'], ['JPEG'], ['GIF'], ['WEBP'], ['PDF']])('nimmt die Vorschauart %s', (art) => {
    expect(parseAnlage({ ...ANLAGE, vorschauArt: art }).vorschauArt).toBe(art);
  });

  it.each([
    ['kein Objekt', 42],
    ['null', null],
    ['id als Text', { ...ANLAGE, id: '3' }],
    ['ohne Dateiname', { ...ANLAGE, dateiName: null }],
    ['Groesse als Text', { ...ANLAGE, groesse: '2048' }],
    ['ohne createdAt', { ...ANLAGE, createdAt: undefined }],
    ['unbekannte Vorschauart', { ...ANLAGE, vorschauArt: 'TIFF' }],
    ['Vorschauart in Kleinschrift', { ...ANLAGE, vorschauArt: 'pdf' }],
    ['Vorschauart als Zahl', { ...ANLAGE, vorschauArt: 7 }],
    ['Vorschauart fehlt ganz', { ...ANLAGE, vorschauArt: undefined }],
  ])('weist eine Antwort ab: %s', (_fall, rumpf) => {
    expect(() => parseAnlage(rumpf)).toThrow(TypeError);
  });
});

describe('parseAnlagen', () => {
  it('verengt die Liste', () => {
    expect(parseAnlagen({ anlagen: [ANLAGE, OHNE_VORSCHAU] })).toEqual({
      anlagen: [ANLAGE, OHNE_VORSCHAU],
    });
  });

  it('verengt die leere Liste', () => {
    expect(parseAnlagen({ anlagen: [] })).toEqual({ anlagen: [] });
  });

  it.each([
    ['kein Objekt', 'x'],
    ['ohne anlagen', {}],
    ['anlagen ist kein Array', { anlagen: 3 }],
    ['Anlage mit unbekannter Vorschauart', { anlagen: [{ ...ANLAGE, vorschauArt: 'TIFF' }] }],
  ])('weist eine Antwort ab: %s', (_fall, rumpf) => {
    expect(() => parseAnlagen(rumpf)).toThrow(TypeError);
  });
});

describe('anlagenLesen', () => {
  it('fragt die Anlagen des Angebots ab', async () => {
    const fetchMock = fetchNachPfad({
      'GET /api/angebote/9/anlagen': json(200, { anlagen: [ANLAGE] }),
    });

    await expect(anlagenLesen(9)).resolves.toEqual({ anlagen: [ANLAGE] });
    expect(fetchMock.mock.calls[0][1]).toMatchObject({ method: 'GET' });
  });
});

describe('anlageHochladen', () => {
  it('schickt die Datei im Teil „datei" und gibt die neue Anlage heraus', async () => {
    const gesendet: FormData[] = [];
    fetchNachPfad({
      'POST /api/angebote/9/anlagen': formularWeg((formular) => {
        gesendet.push(formular);
      }, json(201, ANLAGE)),
    });
    const datei = new File(['inhalt'], 'anfrage.pdf', { type: 'application/pdf' });

    await expect(anlageHochladen(9, datei)).resolves.toEqual(ANLAGE);
    expect(gesendet).toHaveLength(1);
    expect([...gesendet[0].keys()]).toEqual(['datei']);
    expect(gesendet[0].get('datei')).toBe(datei);
  });

  it('weist eine Antwort mit unbekannter Vorschauart ab', async () => {
    fetchNachPfad({
      'POST /api/angebote/9/anlagen': formularWeg(
        () => undefined,
        json(201, { ...ANLAGE, vorschauArt: 'TIFF' }),
      ),
    });

    await expect(anlageHochladen(9, new File(['x'], 'a.tif'))).rejects.toThrow(TypeError);
  });
});

describe('anlageInhalt', () => {
  it('holt den Inhalt als Blob', async () => {
    const fetchMock = fetchNachPfad({
      'GET /api/angebote/9/anlagen/3/inhalt': () => new Response('inhalt', { status: 200 }),
    });

    const blob = await anlageInhalt(9, 3);

    await expect(blobText(blob)).resolves.toBe('inhalt');
    expect(fetchMock.mock.calls[0][1]).toMatchObject({ method: 'GET' });
  });
});

describe('anlageLoeschen', () => {
  it('loescht die Anlage', async () => {
    const fetchMock = fetchNachPfad({
      'DELETE /api/angebote/9/anlagen/3': leer(204),
    });

    await expect(anlageLoeschen(9, 3)).resolves.toBeUndefined();
    expect(fetchMock.mock.calls[0][1]).toMatchObject({ method: 'DELETE' });
  });
});

describe('anlageInhaltPfad', () => {
  it('nennt den Weg zum Inhalt, den der Verweis „Herunterladen" braucht', () => {
    expect(anlageInhaltPfad(9, 3)).toBe('/api/angebote/9/anlagen/3/inhalt');
  });
});
