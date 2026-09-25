import { describe, expect, it } from 'vitest';

import { ApiError } from '../api/client';
import { feldMeldungen, nichtGefunden } from './apifehler';

describe('nichtGefunden', () => {
  it('erkennt die Antwort mit Status 404', () => {
    expect(nichtGefunden(new ApiError(404, 'Weg', {}))).toBe(true);
  });

  it('haelt jeden anderen Status fuer einen Ausfall', () => {
    expect(nichtGefunden(new ApiError(500, 'Kaputt', {}))).toBe(false);
  });

  it('haelt einen Fehlschlag ohne Antwort fuer einen Ausfall', () => {
    expect(nichtGefunden(new TypeError('Netz weg'))).toBe(false);
  });
});

describe('feldMeldungen', () => {
  it('reicht die Meldungen des Servers durch', () => {
    const fehler = new ApiError(400, 'Ungültig', { name: ['Pflichtangabe'] });
    expect(feldMeldungen(fehler)).toEqual({ name: ['Pflichtangabe'] });
  });

  it('gibt nichts zurueck, wo der Fehlschlag keine Antwort traegt', () => {
    expect(feldMeldungen(new TypeError('Netz weg'))).toEqual({});
  });
});
