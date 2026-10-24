import { describe, expect, it } from 'vitest';

import { zeitpunktKurz, zeitpunktWort } from './zeitpunkt';

describe('zeitpunktWort', () => {
  it('setzt einen Zeitstempel in der Ortszeit des Betrachters — Sommerzeit', () => {
    // 12:05 UTC sind in Berlin 14:05 MESZ. Die Zone des Testlaufs steht in vite.config.ts.
    expect(zeitpunktWort('2026-09-30T12:05:00Z')).toBe('30.09.2026, 14:05');
  });

  it('setzt einen Zeitstempel in der Ortszeit des Betrachters — Winterzeit', () => {
    expect(zeitpunktWort('2026-01-15T12:05:00Z')).toBe('15.01.2026, 13:05');
  });

  it('setzt einstellige Werte zweistellig', () => {
    expect(zeitpunktWort('2026-01-05T07:03:09Z')).toBe('05.01.2026, 08:03');
  });

  it('nimmt einen Zeitstempel mit Millisekunden an', () => {
    expect(zeitpunktWort('2026-09-30T12:05:00.123456Z')).toBe('30.09.2026, 14:05');
  });

  it.each([['kein Zeitpunkt'], [''], ['2026-13-45T99:99:99Z']])(
    'gibt heraus, was hereinkam, wenn es kein Zeitstempel ist: %s',
    (wert) => {
      // Wie bei tagWort: lieber der rohe Wert als eine erfundene Angabe.
      expect(zeitpunktWort(wert)).toBe(wert);
    },
  );
});

describe('zeitpunktKurz (#411, Kriterium 2)', () => {
  // 30.09.2026, 18:00 in Berlin — der Augenblick, in dem die Meldung erscheint.
  const jetzt = new Date('2026-09-30T16:00:00Z');

  it('nennt am selben Tag nur die Uhrzeit', () => {
    expect(zeitpunktKurz('2026-09-30T12:05:00Z', jetzt)).toBe('14:05');
  });

  it('nennt an einem frueheren Tag Datum und Uhrzeit', () => {
    expect(zeitpunktKurz('2026-09-29T12:05:00Z', jetzt)).toBe('29.09.2026, 14:05');
  });

  it('vergleicht den Tag in der Ortszeit, nicht in UTC', () => {
    // 22:30 UTC am 29. sind 00:30 am 30. in Berlin — derselbe Tag wie jetzt.
    expect(zeitpunktKurz('2026-09-29T22:30:00Z', jetzt)).toBe('00:30');
  });

  it('nennt denselben Kalendertag eines anderen Monats mit Datum', () => {
    expect(zeitpunktKurz('2026-08-30T12:05:00Z', jetzt)).toBe('30.08.2026, 14:05');
  });

  it('nennt denselben Kalendertag eines anderen Jahres mit Datum', () => {
    expect(zeitpunktKurz('2025-09-30T12:05:00Z', jetzt)).toBe('30.09.2025, 14:05');
  });

  it('gibt heraus, was hereinkam, wenn es kein Zeitstempel ist', () => {
    expect(zeitpunktKurz('kein Zeitpunkt', jetzt)).toBe('kein Zeitpunkt');
  });
});
