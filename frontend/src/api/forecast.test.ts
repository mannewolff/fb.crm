import { afterEach, describe, expect, it, vi } from 'vitest';

import { forecast, parseForecast } from './forecast';
import { fetchNachPfad, json } from '../test/fetchNachPfad';

/**
 * Ein Forecast, wie `ForecastResponse` ihn schreibt — das Jahr als `JJJJ`, Geld als Dezimalzahl.
 * 2027 traegt das Angebot an IT Bildungshaus aus #302 mit zwei seiner Positionen.
 */
const ANTWORT = {
  jahre: [
    {
      jahr: '2026',
      zugesagtNetto: 1200,
      angebotenNetto: 0,
      summeNetto: 1200,
      positionen: [
        {
          angebotId: 9,
          firmaName: 'Werkstatt Nord',
          angebotDatum: '2026-08-03',
          bezeichnung: 'Schulung',
          netto: 1200,
        },
      ],
    },
    {
      jahr: '2027',
      zugesagtNetto: 0,
      angebotenNetto: 16200.5,
      summeNetto: 16200.5,
      positionen: [
        {
          angebotId: 14,
          firmaName: 'IT Bildungshaus',
          angebotDatum: '2026-10-15',
          bezeichnung: 'Aufwände Mai 2027',
          netto: 8100.25,
        },
        {
          angebotId: 14,
          firmaName: 'IT Bildungshaus',
          angebotDatum: '2026-10-15',
          bezeichnung: 'Aufwände Juni 2027',
          netto: 8100.25,
        },
      ],
    },
  ],
};

afterEach(() => {
  vi.restoreAllMocks();
});

describe('parseForecast', () => {
  it('verengt die Jahre samt Positionen, die Betraege in ganzen Cent', () => {
    const gelesen = parseForecast(ANTWORT);

    expect(gelesen.jahre).toHaveLength(2);
    expect(gelesen.jahre[1]).toEqual({
      jahr: '2027',
      zugesagtInCent: 0,
      angebotenInCent: 1620050,
      summeInCent: 1620050,
      positionen: [
        {
          angebotId: 14,
          firmaName: 'IT Bildungshaus',
          angebotDatum: '2026-10-15',
          bezeichnung: 'Aufwände Mai 2027',
          nettoInCent: 810025,
        },
        {
          angebotId: 14,
          firmaName: 'IT Bildungshaus',
          angebotDatum: '2026-10-15',
          bezeichnung: 'Aufwände Juni 2027',
          nettoInCent: 810025,
        },
      ],
    });
    expect(gelesen.jahre[0]?.zugesagtInCent).toBe(120000);
  });

  it('liest „gar nichts offen" als leere Liste (Kriterium 6)', () => {
    expect(parseForecast({ jahre: [] })).toEqual({ jahre: [] });
  });

  it('scheitert, wo die Antwort kein Objekt ist', () => {
    expect(() => parseForecast(null)).toThrow(TypeError);
  });

  it('scheitert, wo die Jahre keine Liste sind', () => {
    expect(() => parseForecast({ jahre: {} })).toThrow(TypeError);
  });

  it('scheitert, wo das Jahr als Zahl kommt — Jahre stehen als Text (E9)', () => {
    const kaputt = { jahre: [{ ...ANTWORT.jahre[0], jahr: 2026 }] };

    expect(() => parseForecast(kaputt)).toThrow(TypeError);
  });

  it('scheitert, wo ein Betrag des Jahres drei Nachkommastellen traegt', () => {
    const kaputt = { jahre: [{ ...ANTWORT.jahre[0], summeNetto: 1.005 }] };

    expect(() => parseForecast(kaputt)).toThrow(TypeError);
  });

  it('scheitert, wo „zugesagt" fehlt — ein halb gelesenes Jahr geht nicht weiter', () => {
    const kaputt = { jahre: [{ ...ANTWORT.jahre[0], zugesagtNetto: undefined }] };

    expect(() => parseForecast(kaputt)).toThrow(TypeError);
  });

  it('scheitert, wo „angeboten" keine Zahl ist', () => {
    const kaputt = { jahre: [{ ...ANTWORT.jahre[0], angebotenNetto: '0' }] };

    expect(() => parseForecast(kaputt)).toThrow(TypeError);
  });

  it('scheitert, wo die Positionen eines Jahres fehlen', () => {
    const kaputt = { jahre: [{ ...ANTWORT.jahre[0], positionen: null }] };

    expect(() => parseForecast(kaputt)).toThrow(TypeError);
  });

  it.each([
    ['die Kennung des Angebots keine Zahl ist', { angebotId: '9' }],
    ['der Name der Firma fehlt', { firmaName: null }],
    ['das Angebotsdatum keine Zeichenkette ist', { angebotDatum: 20260803 }],
    ['die Bezeichnung fehlt', { bezeichnung: undefined }],
    ['der Restbetrag drei Nachkommastellen traegt', { netto: 1.005 }],
  ])('scheitert, wo an einer Position %s', (_, aenderung) => {
    const jahr = ANTWORT.jahre[0];
    const kaputt = {
      jahre: [{ ...jahr, positionen: [{ ...jahr?.positionen[0], ...aenderung }] }],
    };

    expect(() => parseForecast(kaputt)).toThrow(TypeError);
  });
});

describe('forecast', () => {
  it('fragt GET /api/startseite/forecast ohne Parameter und liefert den verengten Forecast', async () => {
    const aufruf = fetchNachPfad({ 'GET /api/startseite/forecast': json(200, ANTWORT) });

    await expect(forecast()).resolves.toEqual(parseForecast(ANTWORT));
    expect(aufruf.mock.calls.map(([ziel]) => ziel)).toEqual(['/api/startseite/forecast']);
  });

  it('scheitert an einer Antwort mit fremder Form', async () => {
    fetchNachPfad({ 'GET /api/startseite/forecast': json(200, { jahr: '2027' }) });

    await expect(forecast()).rejects.toThrow();
  });
});
