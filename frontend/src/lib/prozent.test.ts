import { describe, expect, it } from 'vitest';

import { prozentWort } from './prozent';

describe('prozentWort', () => {
  it.each([
    [6670, '66,7 %'],
    [50, '0,5 %'],
    [950, '9,5 %'],
    [0, '0,0 %'],
    [10000, '100,0 %'],
    [12345670, '123.456,7 %'],
  ])('setzt %i Hundertstel-Prozent als „%s"', (wert, wort) => {
    expect(prozentWort(wert)).toBe(wort);
  });
});
