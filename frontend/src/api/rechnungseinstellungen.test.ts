import { afterEach, describe, expect, it, vi } from 'vitest';

import {
  parseRechnungseinstellungen,
  rechnungseinstellungenLesen,
  rechnungseinstellungenPflegen,
} from './rechnungseinstellungen';
import { fetchNachPfad, json, leer } from '../test/fetchNachPfad';

/** Die Antwort des Backends: der Steuersatz als Dezimalzahl. */
const ANTWORT = {
  nummerMuster: '{NNNN}-{JJJJ}',
  naechsteNummer: 3,
  steuersatz: 19.5,
  zahlungszielTage: 14,
};

/** Dieselben Einstellungen, wie die Oberflaeche sie sieht: der Steuersatz in Hundertstel-Prozent. */
const EINSTELLUNGEN = {
  nummerMuster: '{NNNN}-{JJJJ}',
  naechsteNummer: 3,
  steuersatzInHundertsteln: 1950,
  zahlungszielTage: 14,
};

afterEach(() => {
  vi.restoreAllMocks();
});

describe('parseRechnungseinstellungen', () => {
  it('verengt die Antwort und rechnet den Steuersatz in Hundertstel-Prozent', () => {
    expect(parseRechnungseinstellungen(ANTWORT)).toEqual(EINSTELLUNGEN);
  });

  it.each([
    ['kein Objekt', 42],
    ['null', null],
    ['steuersatz als Zeichenkette', { ...ANTWORT, steuersatz: '19.5' }],
    ['ohne steuersatz', { ...ANTWORT, steuersatz: undefined }],
    ['steuersatz mit drei Nachkommastellen', { ...ANTWORT, steuersatz: 19.555 }],
    ['ohne nummerMuster', { ...ANTWORT, nummerMuster: undefined }],
    ['nummerMuster als Zahl', { ...ANTWORT, nummerMuster: 7 }],
    ['ohne naechsteNummer', { ...ANTWORT, naechsteNummer: undefined }],
    ['naechsteNummer als Zeichenkette', { ...ANTWORT, naechsteNummer: '3' }],
    ['ohne zahlungszielTage', { ...ANTWORT, zahlungszielTage: undefined }],
    ['zahlungszielTage als Wahrheitswert', { ...ANTWORT, zahlungszielTage: false }],
  ])('weist eine Antwort mit %s ab', (_fall, wert) => {
    expect(() => parseRechnungseinstellungen(wert)).toThrow(TypeError);
  });
});

describe('rechnungseinstellungenLesen', () => {
  it('liest die Einstellungen mit GET', async () => {
    const fetchMock = fetchNachPfad({
      'GET /api/rechnung/einstellungen': json(200, ANTWORT),
    });

    await expect(rechnungseinstellungenLesen()).resolves.toEqual(EINSTELLUNGEN);

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/rechnung/einstellungen',
      expect.objectContaining({ method: 'GET' }),
    );
  });
});

describe('rechnungseinstellungenPflegen', () => {
  it('schickt den Steuersatz mit PUT als Dezimaltext mit Punkt', async () => {
    const fetchMock = fetchNachPfad({ 'PUT /api/rechnung/einstellungen': leer(204) });

    await expect(rechnungseinstellungenPflegen(EINSTELLUNGEN)).resolves.toBeUndefined();

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/rechnung/einstellungen',
      expect.objectContaining({
        method: 'PUT',
        body: JSON.stringify({
          nummerMuster: '{NNNN}-{JJJJ}',
          naechsteNummer: 3,
          steuersatz: '19.50',
          zahlungszielTage: 14,
        }),
      }),
    );
  });
});
