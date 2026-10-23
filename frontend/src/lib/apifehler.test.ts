import { describe, expect, it } from 'vitest';

import { ApiError } from '../api/client';
import { feldMeldungen, nichtGefunden, serverMeldung, verboten } from './apifehler';

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

describe('verboten', () => {
  it('erkennt die Antwort mit Status 403', () => {
    expect(verboten(new ApiError(403, 'Nicht erlaubt', {}))).toBe(true);
  });

  it.each([401, 404, 500])('haelt den Status %i fuer kein Verbot', (status) => {
    expect(verboten(new ApiError(status, 'Anders', {}))).toBe(false);
  });

  it('haelt einen Fehlschlag ohne Antwort fuer kein Verbot', () => {
    expect(verboten(new TypeError('Netz weg'))).toBe(false);
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

describe('serverMeldung', () => {
  it('reicht die Meldung der Antwort durch', () => {
    const fehler = new ApiError(409, 'Der Zustand passt nicht.', {});
    expect(serverMeldung(fehler, 'Ersatz')).toBe('Der Zustand passt nicht.');
  });

  it('nimmt den Ersatz, wo gar keine Antwort ankam', () => {
    expect(serverMeldung(new TypeError('Netz weg'), 'Ersatz')).toBe('Ersatz');
  });
});
