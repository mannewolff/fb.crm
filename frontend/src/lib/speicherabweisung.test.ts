import { describe, expect, it } from 'vitest';

import { ApiError } from '../api/client';
import { speicherabweisung } from './speicherabweisung';

const AUSFALL = 'Speichern fehlgeschlagen.';

/** Ein Bearbeitungskonflikt, wie ihn der Server meldet: 409 mit der Erweiterung `konflikt`. */
function bearbeitungskonflikt(felder: Record<string, string[]> = {}): ApiError {
  return new ApiError(409, 'Veraltet', felder, { konflikt: { art: 'veraltet' } });
}

describe('speicherabweisung', () => {
  it('reicht einen Bearbeitungskonflikt als Abweisung weiter, ohne Feldfehler und Ausfall', () => {
    const ursache = bearbeitungskonflikt();

    expect(speicherabweisung(ursache, AUSFALL)).toEqual({
      abweisung: ursache,
      feldFehler: {},
      fehler: null,
    });
  });

  it('laesst beim Bearbeitungskonflikt auch mitgeschickte Feldmeldungen weg', () => {
    // Der Konflikt geht vor: Die Eingaben bleiben stehen, den Weg weiter zeigt die Konfliktmeldung.
    const ursache = bearbeitungskonflikt({ name: ['Pflichtangabe'] });

    expect(speicherabweisung(ursache, AUSFALL).feldFehler).toEqual({});
  });

  it('stellt die Feldmeldungen des Servers an die Felder', () => {
    const ursache = new ApiError(400, 'Ungültig', { name: ['Pflichtangabe'] });

    expect(speicherabweisung(ursache, AUSFALL)).toEqual({
      abweisung: null,
      feldFehler: { name: ['Pflichtangabe'] },
      fehler: null,
    });
  });

  it('meldet eine Antwort ohne Feldmeldungen als Ausfall', () => {
    expect(speicherabweisung(new ApiError(500, 'Kaputt', {}), AUSFALL)).toEqual({
      abweisung: null,
      feldFehler: {},
      fehler: AUSFALL,
    });
  });

  it('meldet einen Fehlschlag ohne Antwort als Ausfall', () => {
    expect(speicherabweisung(new TypeError('Netz weg'), AUSFALL).fehler).toBe(AUSFALL);
  });
});
