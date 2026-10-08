import { describe, expect, it } from 'vitest';

import { contrastRatio, relativeLuminance } from './contrast';

describe('relativeLuminance', () => {
  it('ist 0 fuer Schwarz und 1 fuer Weiss', () => {
    expect(relativeLuminance('#000000')).toBe(0);
    expect(relativeLuminance('#FFFFFF')).toBeCloseTo(1, 12);
  });

  it('liest die Kurzform mit drei Ziffern wie die Langform', () => {
    expect(relativeLuminance('#fff')).toBe(relativeLuminance('#FFFFFF'));
    expect(relativeLuminance('#08a')).toBe(relativeLuminance('#0088AA'));
  });

  it('nimmt die Schreibweise ohne Raute und ohne Rücksicht auf Gross- und Kleinschreibung', () => {
    expect(relativeLuminance('e7e9ed')).toBe(relativeLuminance('#E7E9ED'));
  });

  it('wendet den flachen Zweig der Kurve unterhalb der Schwelle an', () => {
    // #0A0A0A liegt mit 10/255 unter 0,03928 und wird linear geteilt, nicht potenziert.
    expect(relativeLuminance('#0A0A0A')).toBeCloseTo(10 / 255 / 12.92, 12);
  });

  it('weist eine Angabe zurueck, die keine Farbe ist', () => {
    expect(() => relativeLuminance('#12345')).toThrow(/Farbwert/);
    expect(() => relativeLuminance('#ZZZZZZ')).toThrow(/Farbwert/);
  });
});

describe('contrastRatio', () => {
  it('meldet fuer Schwarz auf Weiss 21:1', () => {
    expect(contrastRatio('#000000', '#FFFFFF')).toBeCloseTo(21, 10);
  });

  it('meldet fuer dieselbe Farbe 1:1', () => {
    expect(contrastRatio('#A85F2C', '#A85F2C')).toBeCloseTo(1, 10);
  });

  it('ist unabhaengig von der Reihenfolge der Argumente', () => {
    expect(contrastRatio('#14181E', '#FDFDFE')).toBeCloseTo(contrastRatio('#FDFDFE', '#14181E'), 12);
  });
});
