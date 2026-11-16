import { describe, expect, it, vi } from 'vitest';

import { kennungAus, neuLadenFuer } from './kennung';

describe('kennungAus', () => {
  it('nimmt eine Folge aus Ziffern als Zahl', () => {
    expect(kennungAus('7')).toBe(7);
    expect(kennungAus('1024')).toBe(1024);
  });

  it('weist ab, was keine Kennung ist', () => {
    expect(kennungAus('sieben')).toBeNull();
    expect(kennungAus('7a')).toBeNull();
    expect(kennungAus('-7')).toBeNull();
    expect(kennungAus('7.5')).toBeNull();
    expect(kennungAus(' 7 ')).toBeNull();
    expect(kennungAus('')).toBeNull();
  });

  it('weist eine fehlende Kennung ab', () => {
    expect(kennungAus(undefined)).toBeNull();
  });
});

describe('neuLadenFuer', () => {
  it('gibt ohne Kennung nichts zum Neuladen', () => {
    expect(neuLadenFuer(null, vi.fn())).toBeNull();
  });

  it('laedt den Eintrag mit seiner Kennung neu', () => {
    const neuLaden = vi.fn(() => Promise.resolve());
    neuLadenFuer(7, neuLaden)?.();
    expect(neuLaden).toHaveBeenCalledWith(7);
  });
});
