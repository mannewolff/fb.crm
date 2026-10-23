import { afterEach, describe, expect, it, vi } from 'vitest';

import {
  mitarbeiterAendern,
  mitarbeiterAktivieren,
  mitarbeiterAnlegen,
  mitarbeiterStilllegen,
  mitarbeitendeListe,
  parseMitarbeitende,
  parseMitarbeiter,
} from './mitarbeitende';
import type { MitarbeiterEingabe } from './mitarbeitende';
import { alsJson, fetchNachPfad, json, leer } from '../test/fetchNachPfad';

const ANNA = {
  id: 2,
  displayName: 'Anna Berg',
  email: 'anna@firma.de',
  role: 'EMPLOYEE',
  aktiv: true,
};

const EINGABE: MitarbeiterEingabe = {
  email: 'anna@firma.de',
  displayName: 'Anna Berg',
  role: 'EMPLOYEE',
};

afterEach(() => {
  vi.restoreAllMocks();
});

describe('parseMitarbeiter', () => {
  it('liest ein Konto der Liste ohne Auskunft ueber die Begruessung', () => {
    expect(parseMitarbeiter(ANNA)).toEqual({ ...ANNA, begruessungMailVersandt: null });
  });

  it('liest die Auskunft ueber die Begruessung, wo sie steht', () => {
    expect(parseMitarbeiter({ ...ANNA, begruessungMailVersandt: false })).toEqual({
      ...ANNA,
      begruessungMailVersandt: false,
    });
  });

  it.each([
    ['eine unbekannte Rolle', { ...ANNA, role: 'ADMIN' }],
    ['einen Zustand als Text', { ...ANNA, aktiv: 'ja' }],
    ['eine Kennung als Text', { ...ANNA, id: '2' }],
    ['eine Begruessung als Text', { ...ANNA, begruessungMailVersandt: 'nein' }],
    ['kein Objekt', 'Anna'],
  ])('weist %s ab', (_fall, wert) => {
    expect(() => parseMitarbeiter(wert)).toThrow(TypeError);
  });
});

describe('parseMitarbeitende', () => {
  it('liest die Liste', () => {
    expect(parseMitarbeitende({ mitarbeitende: [ANNA] })).toEqual([
      { ...ANNA, begruessungMailVersandt: null },
    ]);
  });

  it('weist eine nackte Liste ab', () => {
    expect(() => parseMitarbeitende([ANNA])).toThrow(TypeError);
  });
});

describe('Wege der Kontenverwaltung', () => {
  it('liest die Liste', async () => {
    fetchNachPfad({ 'GET /api/mitarbeitende': json(200, { mitarbeitende: [ANNA] }) });

    await expect(mitarbeitendeListe()).resolves.toEqual([
      { ...ANNA, begruessungMailVersandt: null },
    ]);
  });

  it('legt an und schickt Adresse, Name und Rolle', async () => {
    let rumpf: unknown = null;
    fetchNachPfad({
      'POST /api/mitarbeitende': (gesendet) => {
        rumpf = alsJson(gesendet);
        return json(201, { ...ANNA, begruessungMailVersandt: true })();
      },
    });

    await expect(mitarbeiterAnlegen(EINGABE)).resolves.toEqual({
      ...ANNA,
      begruessungMailVersandt: true,
    });
    expect(rumpf).toEqual(EINGABE);
  });

  it('aendert unter der Kennung', async () => {
    let rumpf: unknown = null;
    fetchNachPfad({
      'PUT /api/mitarbeitende/2': (gesendet) => {
        rumpf = alsJson(gesendet);
        return json(200, ANNA)();
      },
    });

    await expect(mitarbeiterAendern(2, EINGABE)).resolves.toEqual({
      ...ANNA,
      begruessungMailVersandt: null,
    });
    expect(rumpf).toEqual(EINGABE);
  });

  it('legt still und aktiviert wieder', async () => {
    const fetchMock = fetchNachPfad({
      'POST /api/mitarbeitende/2/stilllegen': leer(204),
      'POST /api/mitarbeitende/2/aktivieren': leer(204),
    });

    await mitarbeiterStilllegen(2);
    await mitarbeiterAktivieren(2);

    expect(fetchMock).toHaveBeenCalledTimes(2);
  });
});
