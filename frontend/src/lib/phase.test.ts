import { describe, expect, it } from 'vitest';

import type { Phase } from '../api/vorgaenge';
import { phaseWort } from './phase';

describe('phaseWort', () => {
  it.each<[Phase, string]>([
    ['ANBAHNUNG', 'Anbahnung'],
    ['ANGEBOT', 'Angebot'],
    ['AUFTRAG', 'Auftrag'],
  ])('nennt %s beim Wort', (phase, wort) => {
    expect(phaseWort(phase)).toBe(wort);
  });
});
