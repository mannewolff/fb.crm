import { describe, expect, it } from 'vitest';

import { kennungAus } from './kennung';

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
