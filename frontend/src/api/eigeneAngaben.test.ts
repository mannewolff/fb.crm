import { afterEach, describe, expect, it, vi } from 'vitest';

import { eigeneAngabenLesen, eigeneAngabenPflegen, parseEigeneAngaben } from './eigeneAngaben';
import { fetchNachPfad, json, leer } from '../test/fetchNachPfad';

const ANGABEN = {
  name: 'Manfred Wolff',
  strasse: 'Hauptstraße 1',
  plz: '28195',
  ort: 'Bremen',
  land: 'Deutschland',
  email: 'info@mwolff.org',
  telefon: '0421/1234',
  steuernummer: '75/123/45678',
  umsatzsteuerId: 'DE123456789',
  bankverbindung: 'DE02120300000000202051',
  zahlungsbedingungen: 'Zahlbar innerhalb 14 Tagen ohne Abzug.',
};

/** Die frische Instanz: die Zeile steht, aber kein Feld ist gepflegt. */
const LEERE_ANGABEN = {
  name: null,
  strasse: null,
  plz: null,
  ort: null,
  land: null,
  email: null,
  telefon: null,
  steuernummer: null,
  umsatzsteuerId: null,
  bankverbindung: null,
  zahlungsbedingungen: null,
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

  it.each([
    ['kein Objekt', 42],
    ['null', null],
    // Ein fehlendes Feld ist nicht dasselbe wie ein leeres: `undefined` heisst, dass die
    // Gegenstelle das Feld nicht kennt — dann stimmt der Typ hier nicht mehr.
    ['ohne name', { ...ANGABEN, name: undefined }],
    ['ohne zahlungsbedingungen', { ...ANGABEN, zahlungsbedingungen: undefined }],
    ['name als Zahl', { ...ANGABEN, name: 7 }],
    ['plz als Zahl', { ...ANGABEN, plz: 28195 }],
    ['email als Objekt', { ...ANGABEN, email: {} }],
    ['bankverbindung als Wahrheitswert', { ...ANGABEN, bankverbindung: false }],
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
  it('schreibt die Angaben mit PUT fort und erwartet keinen Rumpf', async () => {
    const fetchMock = fetchNachPfad({ 'PUT /api/eigene-angaben': leer(204) });

    await expect(eigeneAngabenPflegen(ANGABEN)).resolves.toBeUndefined();

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/eigene-angaben',
      expect.objectContaining({ method: 'PUT', body: JSON.stringify(ANGABEN) }),
    );
  });
});
