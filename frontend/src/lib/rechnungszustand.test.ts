import { IconFileInvoice, IconPencil } from '@tabler/icons-react';
import type { TablerIcon } from '@tabler/icons-react';
import { describe, expect, it } from 'vitest';

import type { ToenungName } from '../theme';
import {
  RECHNUNGSZUSTAENDE,
  alsRechnungszustand,
  rechnungszustandBild,
} from './rechnungszustand';
import type { Rechnungszustand } from './rechnungszustand';

describe('rechnungszustandBild', () => {
  it.each<[Rechnungszustand, string, ToenungName, TablerIcon]>([
    ['ENTWURF', 'Entwurf', 'flieder', IconPencil],
    ['GESTELLT', 'Gestellt', 'himmel', IconFileInvoice],
  ])('gibt zu %s Wort, Toenung und Symbol', (zustand, wort, toenung, symbol) => {
    expect(rechnungszustandBild(zustand)).toEqual({ wort, toenung, symbol });
  });
});

describe('RECHNUNGSZUSTAENDE', () => {
  it('nennt die zwei Zustaende in der Reihenfolge des Backends', () => {
    expect(RECHNUNGSZUSTAENDE).toEqual(['ENTWURF', 'GESTELLT']);
  });
});

describe('alsRechnungszustand', () => {
  it('nimmt einen bekannten Zustand', () => {
    expect(alsRechnungszustand('GESTELLT')).toBe('GESTELLT');
  });

  it.each([['BEZAHLT'], [''], [null], [3]])('weist %s ab', (wert) => {
    expect(alsRechnungszustand(wert)).toBeNull();
  });
});
