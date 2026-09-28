import { IconCircleCheck, IconFileInvoice, IconProgress } from '@tabler/icons-react';
import type { TablerIcon } from '@tabler/icons-react';
import { describe, expect, it } from 'vitest';

import type { ToenungName } from '../theme';
import { auftragsstatusBild } from './auftragsstatus';
import type { Auftragsstatus } from './auftragsstatus';

/** Die Toene, die CLAUDE-design.md fuer Zustaende vorsieht — kein Status bringt einen neuen mit. */
const BEKANNTE_TOENE: readonly ToenungName[] = [
  'pfirsich',
  'salbei',
  'himmel',
  'bernstein',
  'rose',
  'flieder',
];

const ALLE: readonly Auftragsstatus[] = ['OFFEN', 'IN_ARBEIT', 'ABGESCHLOSSEN'];

describe('auftragsstatusBild (Plan E18)', () => {
  it.each<[Auftragsstatus, string, ToenungName, TablerIcon]>([
    ['OFFEN', 'Offen', 'flieder', IconFileInvoice],
    ['IN_ARBEIT', 'In Arbeit', 'himmel', IconProgress],
    ['ABGESCHLOSSEN', 'Abgeschlossen', 'salbei', IconCircleCheck],
  ])('gibt zu %s Wort, Toenung und Symbol', (status, wort, toenung, symbol) => {
    expect(auftragsstatusBild(status)).toEqual({ wort, toenung, symbol });
  });

  it('gibt jedem Status eine eigene Toenung, und keine davon ist neu', () => {
    const toene = ALLE.map((status) => auftragsstatusBild(status).toenung);

    expect(new Set(toene).size).toBe(ALLE.length);
    for (const ton of toene) {
      expect(BEKANNTE_TOENE).toContain(ton);
    }
  });

  it('gibt ein Bild heraus, das sich von aussen nicht veraendern laesst', () => {
    const bild = auftragsstatusBild('OFFEN');

    expect(Object.isFrozen(bild)).toBe(true);
    expect(() => {
      Object.assign(bild, { wort: 'Anders' });
    }).toThrow(TypeError);
    expect(auftragsstatusBild('OFFEN').wort).toBe('Offen');
  });
});
