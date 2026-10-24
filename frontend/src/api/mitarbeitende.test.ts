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
import { alsJson, fetchNachPfad, json } from '../test/fetchNachPfad';

const ANNA = {
  id: 2,
  displayName: 'Anna Berg',
  email: 'anna@firma.de',
  role: 'EMPLOYEE',
  aktiv: true,
  version: 3,
};

const EINGABE: MitarbeiterEingabe = {
  email: 'anna@firma.de',
  displayName: 'Anna Berg',
  role: 'EMPLOYEE',
};

/** Die Bedingungskoepfe aller Aufrufe, in ihrer Reihenfolge. */
function vorgelegt(fetchMock: ReturnType<typeof fetchNachPfad>): (string | undefined)[] {
  return fetchMock.mock.calls.map(
    ([, init]) => (init?.headers as Record<string, string> | undefined)?.['If-Match'],
  );
}

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
    ['eine Version als Text', { ...ANNA, version: '3' }],
    ['ein Konto ohne Version', { ...ANNA, version: undefined }],
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

  it('aendert unter der Kennung auf dem vorgelegten Stand', async () => {
    let rumpf: unknown = null;
    const fetchMock = fetchNachPfad({
      'PUT /api/mitarbeitende/2': (gesendet) => {
        rumpf = alsJson(gesendet);
        return json(200, { ...ANNA, version: 4 })();
      },
    });

    await expect(mitarbeiterAendern(2, 3, EINGABE)).resolves.toEqual({
      ...ANNA,
      version: 4,
      begruessungMailVersandt: null,
    });
    expect(rumpf).toEqual(EINGABE);
    expect(vorgelegt(fetchMock)).toEqual(['"3"']);
  });

  it('legt still und aktiviert wieder, jeweils auf dem vorgelegten Stand', async () => {
    const fetchMock = fetchNachPfad({
      'POST /api/mitarbeitende/2/stilllegen': json(200, { ...ANNA, aktiv: false, version: 4 }),
      'POST /api/mitarbeitende/2/aktivieren': json(200, { ...ANNA, version: 5 }),
    });

    await expect(mitarbeiterStilllegen(2, 3)).resolves.toMatchObject({ aktiv: false, version: 4 });
    await expect(mitarbeiterAktivieren(2, 4)).resolves.toMatchObject({ aktiv: true, version: 5 });

    expect(vorgelegt(fetchMock)).toEqual(['"3"', '"4"']);
  });

  it('legt beim Anlegen keinen Stand vor (#411, Kriterium 7)', async () => {
    const fetchMock = fetchNachPfad({
      'POST /api/mitarbeitende': json(201, { ...ANNA, version: 0, begruessungMailVersandt: true }),
    });

    await mitarbeiterAnlegen(EINGABE);

    expect(vorgelegt(fetchMock)).toEqual([undefined]);
  });
});
