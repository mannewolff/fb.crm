import { afterEach, describe, expect, it, vi } from 'vitest';

import { parsePipeline, pipeline } from './pipeline';
import { fetchNachPfad, json, leer } from '../test/fetchNachPfad';

const ZEILE = {
  angebotId: 9,
  nummer: 'A-2026-001',
  vorgangId: 12,
  vorgangNummer: 2026001,
  vorgangTitel: 'Neue Website',
  firma: 'Beispiel GmbH',
  summe: 2500.03,
  wahrscheinlichkeit: 60,
  gewichteteSumme: 1500.02,
  entscheidungErwartetAm: '2026-10-15',
};

const ZEILE_VERENGT = {
  angebotId: 9,
  nummer: 'A-2026-001',
  vorgangId: 12,
  vorgangNummer: 2026001,
  vorgangTitel: 'Neue Website',
  firma: 'Beispiel GmbH',
  summeInCent: 250003,
  wahrscheinlichkeit: 60,
  gewichteteSummeInCent: 150002,
  entscheidungErwartetAm: '2026-10-15',
};

const ANTWORT = { zeilen: [ZEILE], summe: 2500.03, gewichteteSumme: 1500.02 };

afterEach(() => {
  vi.restoreAllMocks();
});

describe('parsePipeline', () => {
  it('verengt die Auswertung und rechnet Geld in ganze Cent', () => {
    expect(parsePipeline(ANTWORT)).toEqual({
      zeilen: [ZEILE_VERENGT],
      summeInCent: 250003,
      gewichteteSummeInCent: 150002,
    });
  });

  it('nimmt eine Zeile ohne Einschaetzung an — `null` ist die Kennzeichnung (F2)', () => {
    const ohneEinschaetzung = {
      ...ANTWORT,
      zeilen: [{ ...ZEILE, wahrscheinlichkeit: null, gewichteteSumme: 0, entscheidungErwartetAm: null }],
    };

    expect(parsePipeline(ohneEinschaetzung).zeilen[0]).toEqual({
      ...ZEILE_VERENGT,
      wahrscheinlichkeit: null,
      gewichteteSummeInCent: 0,
      entscheidungErwartetAm: null,
    });
  });

  it('nimmt die leere Pipeline mit zwei Nullsummen an', () => {
    expect(parsePipeline({ zeilen: [], summe: 0, gewichteteSumme: 0 })).toEqual({
      zeilen: [],
      summeInCent: 0,
      gewichteteSummeInCent: 0,
    });
  });

  it.each([
    ['kein Objekt', 42],
    ['null', null],
    ['ohne zeilen', { summe: 0, gewichteteSumme: 0 }],
    ['zeilen ist kein Array', { ...ANTWORT, zeilen: {} }],
    ['ohne Summe', { ...ANTWORT, summe: undefined }],
    ['Summe als Zeichenkette', { ...ANTWORT, summe: '2500.03' }],
    ['Summe mit drei Nachkommastellen', { ...ANTWORT, summe: 2500.031 }],
    ['ohne gewichtete Summe', { ...ANTWORT, gewichteteSumme: null }],
    ['Zeile ist kein Objekt', { ...ANTWORT, zeilen: ['x'] }],
    ['Zeile ohne angebotId', { ...ANTWORT, zeilen: [{ ...ZEILE, angebotId: '9' }] }],
    ['Zeile ohne Nummer', { ...ANTWORT, zeilen: [{ ...ZEILE, nummer: null }] }],
    ['Zeile ohne vorgangId', { ...ANTWORT, zeilen: [{ ...ZEILE, vorgangId: undefined }] }],
    ['Zeile mit Vorgangsnummer als Text', { ...ANTWORT, zeilen: [{ ...ZEILE, vorgangNummer: '1' }] }],
    ['Zeile ohne Titel', { ...ANTWORT, zeilen: [{ ...ZEILE, vorgangTitel: 7 }] }],
    ['Zeile ohne Firma', { ...ANTWORT, zeilen: [{ ...ZEILE, firma: null }] }],
    ['Zeile ohne Summe', { ...ANTWORT, zeilen: [{ ...ZEILE, summe: undefined }] }],
    ['Zeile mit Wahrscheinlichkeit als Text', { ...ANTWORT, zeilen: [{ ...ZEILE, wahrscheinlichkeit: '60' }] }],
    ['Zeile ohne gewichtete Summe', { ...ANTWORT, zeilen: [{ ...ZEILE, gewichteteSumme: null }] }],
    [
      'Zeile mit Entscheidungszeitpunkt als Zahl',
      { ...ANTWORT, zeilen: [{ ...ZEILE, entscheidungErwartetAm: 20261015 }] },
    ],
  ])('weist eine Antwort ab: %s', (_fall, rumpf) => {
    expect(() => parsePipeline(rumpf)).toThrow(TypeError);
  });
});

describe('pipeline', () => {
  it('holt die Auswertung ohne Parameter', async () => {
    const fetchMock = fetchNachPfad({ 'GET /api/pipeline': json(200, ANTWORT) });

    await expect(pipeline()).resolves.toEqual({
      zeilen: [ZEILE_VERENGT],
      summeInCent: 250003,
      gewichteteSummeInCent: 150002,
    });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('reicht das Abbruchsignal weiter', async () => {
    const fetchMock = fetchNachPfad({ 'GET /api/pipeline': json(200, ANTWORT) });
    const steuerung = new AbortController();

    await pipeline(steuerung.signal);

    expect(fetchMock.mock.calls[0][1]?.signal).toBe(steuerung.signal);
  });

  it('scheitert, wenn die Schnittstelle nicht antwortet', async () => {
    fetchNachPfad({ 'GET /api/pipeline': leer(503) });

    await expect(pipeline()).rejects.toThrow();
  });
});
