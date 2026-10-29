import { afterEach, describe, expect, it, vi } from 'vitest';

import { eigeneAngabenLesen, eigeneAngabenPflegen, parseEigeneAngaben } from './eigeneAngaben';
import { fetchNachPfad, json } from '../test/fetchNachPfad';

const EINGABE = {
  name: 'Manfred Wolff',
  berufsbezeichnung: 'Freiberuflicher Softwareentwickler',
  strasse: 'Hauptstraße 1',
  plz: '28195',
  ort: 'Bremen',
  land: 'Deutschland',
  email: 'info@mwolff.org',
  telefon: '0421/1234',
  webadresse: 'https://mwolff.org',
  steuernummer: '75/123/45678',
  umsatzsteuerId: 'DE123456789',
  iban: 'DE02120300000000202051',
  bic: 'BYLADEM1001',
  kontoinhaber: 'Manfred Wolff',
};

const ANGABEN = { ...EINGABE, bisherigeBankverbindung: null, version: 3 };

/** Die frische Instanz: die Zeile steht, aber kein Feld ist gepflegt. */
const LEERE_ANGABEN = {
  name: null,
  berufsbezeichnung: null,
  strasse: null,
  plz: null,
  ort: null,
  land: null,
  email: null,
  telefon: null,
  webadresse: null,
  steuernummer: null,
  umsatzsteuerId: null,
  iban: null,
  bic: null,
  kontoinhaber: null,
  bisherigeBankverbindung: null,
  version: 0,
};

afterEach(() => {
  vi.restoreAllMocks();
});

describe('parseEigeneAngaben', () => {
  it('verengt eine vollstaendige Antwort', () => {
    expect(parseEigeneAngaben(ANGABEN)).toEqual(ANGABEN);
  });

  it('laesst null in jedem Feld zu — auf einer frischen Instanz ist kein Feld gepflegt', () => {
    expect(parseEigeneAngaben(LEERE_ANGABEN)).toEqual(LEERE_ANGABEN);
  });

  it('liest die bisherige Bankverbindung mit, solange der Server sie fuehrt', () => {
    const umgestellt = { ...LEERE_ANGABEN, bisherigeBankverbindung: 'Sparkasse Bremen' };

    expect(parseEigeneAngaben(umgestellt)).toEqual(umgestellt);
  });

  it.each([
    ['kein Objekt', 42],
    ['null', null],
    // Ein fehlendes Feld ist nicht dasselbe wie ein leeres: `undefined` heisst, dass die
    // Gegenstelle das Feld nicht kennt — dann stimmt der Typ hier nicht mehr.
    ['ohne name', { ...ANGABEN, name: undefined }],
    ['ohne berufsbezeichnung', { ...ANGABEN, berufsbezeichnung: undefined }],
    ['ohne webadresse', { ...ANGABEN, webadresse: undefined }],
    ['webadresse als Zahl', { ...ANGABEN, webadresse: 7 }],
    ['name als Zahl', { ...ANGABEN, name: 7 }],
    ['plz als Zahl', { ...ANGABEN, plz: 28195 }],
    ['email als Objekt', { ...ANGABEN, email: {} }],
    ['iban als Wahrheitswert', { ...ANGABEN, iban: false }],
    ['ohne bic', { ...ANGABEN, bic: undefined }],
    ['kontoinhaber als Zahl', { ...ANGABEN, kontoinhaber: 7 }],
    ['ohne bisherigeBankverbindung', { ...ANGABEN, bisherigeBankverbindung: undefined }],
    ['ohne version', { ...ANGABEN, version: undefined }],
    ['version als Text', { ...ANGABEN, version: '3' }],
  ])('weist %s ab', (_fall, wert) => {
    expect(() => parseEigeneAngaben(wert)).toThrow(TypeError);
  });
});

describe('eigeneAngabenLesen', () => {
  it('liest die eigenen Angaben', async () => {
    const fetchMock = fetchNachPfad({ 'GET /api/eigene-angaben': json(200, ANGABEN) });

    await expect(eigeneAngabenLesen()).resolves.toEqual(ANGABEN);

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/eigene-angaben',
      expect.objectContaining({ method: 'GET' }),
    );
  });
});

describe('eigeneAngabenPflegen', () => {
  it('schreibt die Angaben mit PUT auf dem vorgelegten Stand fort und liest den neuen', async () => {
    const fetchMock = fetchNachPfad({
      'PUT /api/eigene-angaben': json(200, { ...ANGABEN, version: 4 }),
    });

    await expect(eigeneAngabenPflegen(3, EINGABE)).resolves.toEqual({ ...ANGABEN, version: 4 });

    // Plan #415, E2: der Stand geht als If-Match hinaus, nicht im Rumpf.
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/eigene-angaben',
      expect.objectContaining({
        method: 'PUT',
        // Die bisherige Bankverbindung ist nur zum Lesen da (Plan #461, E13).
        body: JSON.stringify(EINGABE),
        headers: expect.objectContaining({ 'If-Match': '"3"' }) as unknown,
      }),
    );
  });
});
