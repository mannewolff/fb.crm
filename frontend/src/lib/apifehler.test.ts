import { describe, expect, it } from 'vitest';

import { ApiError } from '../api/client';
import {
  feldMeldungen,
  istBearbeitungskonflikt,
  konflikt,
  konfliktAngaben,
  nichtGefunden,
  serverMeldung,
  verboten,
} from './apifehler';

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

describe('konflikt', () => {
  it('erkennt die Antwort mit Status 409', () => {
    expect(konflikt(new ApiError(409, 'Widerspricht dem Stand', {}))).toBe(true);
  });

  it.each([400, 403, 500])('haelt den Status %i fuer keinen Konflikt', (status) => {
    expect(konflikt(new ApiError(status, 'Anders', {}))).toBe(false);
  });

  it('haelt einen Fehlschlag ohne Antwort fuer keinen Konflikt', () => {
    expect(konflikt(new TypeError('Netz weg'))).toBe(false);
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

/** Eine Abweisung des Servers mit der Erweiterung `konflikt` (Plan #415, E4). */
function abweisung(konfliktFelder: Record<string, unknown>): ApiError {
  return new ApiError(409, 'Inzwischen geaendert.', {}, { konflikt: konfliktFelder });
}

// 30.09.2026, 18:00 in Berlin — der Augenblick, in dem die Meldung erscheint.
const JETZT = new Date('2026-09-30T16:00:00Z');

describe('istBearbeitungskonflikt', () => {
  it('erkennt die Abweisung an der Erweiterung konflikt', () => {
    expect(istBearbeitungskonflikt(abweisung({ art: 'veraltet', eigenesFenster: false }))).toBe(
      true,
    );
  });

  it('haelt einen 409 ohne konflikt fuer keinen Bearbeitungskonflikt', () => {
    // Die acht anderen fachlichen 409-Lagen tragen keine Erweiterung (E4).
    const fehler = new ApiError(409, 'Der Zustand passt nicht.', {}, { detail: 'passt nicht' });
    expect(istBearbeitungskonflikt(fehler)).toBe(false);
  });

  it('haelt einen 409 ohne Rumpf fuer keinen Bearbeitungskonflikt', () => {
    expect(istBearbeitungskonflikt(new ApiError(409, 'Anders', {}))).toBe(false);
  });

  it('haelt eine Erweiterung, die kein Objekt ist, fuer keinen Bearbeitungskonflikt', () => {
    expect(istBearbeitungskonflikt(new ApiError(409, 'Anders', {}, { konflikt: 'ja' }))).toBe(
      false,
    );
  });

  it('haelt einen Fehlschlag ohne Antwort fuer keinen Bearbeitungskonflikt', () => {
    expect(istBearbeitungskonflikt(new TypeError('Netz weg'))).toBe(false);
  });
});

describe('konfliktAngaben (#411, Kriterien 2 und 6)', () => {
  it('nennt Urheber und Zeitpunkt', () => {
    const angaben = konfliktAngaben(
      abweisung({
        art: 'veraltet',
        geaendertAm: '2026-09-30T12:05:00Z',
        geaendertVon: 'Anna Muster',
        eigenesFenster: false,
      }),
      JETZT,
    );
    expect(angaben).toEqual({
      art: 'veraltet',
      satz: 'Anna Muster hat diesen Eintrag inzwischen geändert (14:05).',
    });
  });

  it('nennt an einem frueheren Tag Datum und Uhrzeit', () => {
    const angaben = konfliktAngaben(
      abweisung({
        art: 'veraltet',
        geaendertAm: '2026-09-29T12:05:00Z',
        geaendertVon: 'Anna Muster',
        eigenesFenster: false,
      }),
      JETZT,
    );
    expect(angaben?.satz).toBe(
      'Anna Muster hat diesen Eintrag inzwischen geändert (29.09.2026, 14:05).',
    );
  });

  it('sagt bei der eigenen Aenderung aus einem anderen Fenster du, mit Zeitpunkt', () => {
    const angaben = konfliktAngaben(
      abweisung({
        art: 'veraltet',
        geaendertAm: '2026-09-30T12:05:00Z',
        geaendertVon: 'Manfred Wolff',
        eigenesFenster: true,
      }),
      JETZT,
    );
    expect(angaben?.satz).toBe(
      'Du hast diesen Eintrag inzwischen in einem anderen Fenster geändert (14:05).',
    );
  });

  it('sagt ohne Urheber jemand', () => {
    const angaben = konfliktAngaben(abweisung({ art: 'veraltet', eigenesFenster: false }), JETZT);
    expect(angaben?.satz).toBe('Inzwischen hat jemand diesen Eintrag geändert.');
  });

  it('nennt ohne Urheber den Zeitpunkt, wenn er bekannt ist', () => {
    const angaben = konfliktAngaben(
      abweisung({ art: 'veraltet', geaendertAm: '2026-09-30T12:05:00Z', eigenesFenster: false }),
      JETZT,
    );
    expect(angaben?.satz).toBe('Inzwischen hat jemand diesen Eintrag geändert (14:05).');
  });

  it('nennt den Urheber auch ohne Zeitpunkt', () => {
    const angaben = konfliktAngaben(
      abweisung({ art: 'veraltet', geaendertVon: 'Anna Muster', eigenesFenster: false }),
      JETZT,
    );
    expect(angaben?.satz).toBe('Anna Muster hat diesen Eintrag inzwischen geändert.');
  });

  it.each([
    [
      { geaendertVon: 'Anna Muster', geaendertAm: '2026-09-30T12:05:00Z', eigenesFenster: false },
      'Anna Muster hat diesen Eintrag inzwischen gelöscht (14:05).',
    ],
    [
      { geaendertVon: 'Manfred Wolff', geaendertAm: '2026-09-30T12:05:00Z', eigenesFenster: true },
      'Du hast diesen Eintrag inzwischen in einem anderen Fenster gelöscht (14:05).',
    ],
    [{ eigenesFenster: false }, 'Inzwischen hat jemand diesen Eintrag gelöscht.'],
  ])('sagt fuer einen geloeschten Eintrag geloescht: %o', (felder, satz) => {
    const angaben = konfliktAngaben(abweisung({ art: 'gelöscht', ...felder }), JETZT);
    expect(angaben).toEqual({ art: 'gelöscht', satz });
  });

  it('nimmt eine unbekannte Art als veraltet — der Weg ist dann Neu laden', () => {
    const angaben = konfliktAngaben(abweisung({ art: 'anders' }), JETZT);
    expect(angaben).toEqual({
      art: 'veraltet',
      satz: 'Inzwischen hat jemand diesen Eintrag geändert.',
    });
  });

  it('gibt nichts heraus, wo kein Bearbeitungskonflikt vorliegt', () => {
    expect(konfliktAngaben(new ApiError(409, 'Der Zustand passt nicht.', {}), JETZT)).toBeNull();
    expect(konfliktAngaben(new TypeError('Netz weg'), JETZT)).toBeNull();
  });

  it('rechnet ohne Augenblick mit der Uhr des Rechners', () => {
    const angaben = konfliktAngaben(
      abweisung({ art: 'veraltet', geaendertAm: new Date().toISOString(), eigenesFenster: false }),
    );
    expect(angaben?.satz).toMatch(/^Inzwischen hat jemand diesen Eintrag geändert \(\d\d:\d\d\)\.$/);
  });
});
