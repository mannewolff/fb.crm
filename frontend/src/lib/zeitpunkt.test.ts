import { describe, expect, it } from 'vitest';

import { zeitpunktWort } from './zeitpunkt';

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
