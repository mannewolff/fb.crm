import {
  IconCircleCheck,
  IconCircleX,
  IconFileInvoice,
  IconPencil,
} from '@tabler/icons-react';
import type { TablerIcon } from '@tabler/icons-react';
import { describe, expect, it } from 'vitest';

import type { ToenungName } from '../theme';
import {
  RECHNUNGSZUSTAENDE,
  alsRechnungszustand,
  istGestellt,
  rechnungszustandBild,
} from './rechnungszustand';
import type { Rechnungszustand } from './rechnungszustand';

describe('rechnungszustandBild', () => {
  it.each<[Rechnungszustand, string, ToenungName, TablerIcon]>([
    ['ENTWURF', 'Entwurf', 'flieder', IconPencil],
    ['GESTELLT', 'Gestellt', 'himmel', IconFileInvoice],
    ['BEZAHLT', 'Bezahlt', 'salbei', IconCircleCheck],
    ['ABGESCHRIEBEN', 'Abgeschrieben', 'rose', IconCircleX],
  ])('gibt zu %s Wort, Toenung und Symbol', (zustand, wort, toenung, symbol) => {
    expect(rechnungszustandBild(zustand)).toEqual({ wort, toenung, symbol });
  });
});

describe('RECHNUNGSZUSTAENDE', () => {
  it('nennt die vier Zustaende in der Reihenfolge des Backends', () => {
    expect(RECHNUNGSZUSTAENDE).toEqual(['ENTWURF', 'GESTELLT', 'BEZAHLT', 'ABGESCHRIEBEN']);
  });
});

describe('istGestellt', () => {
  it.each<[Rechnungszustand]>([['GESTELLT'], ['BEZAHLT'], ['ABGESCHRIEBEN']])(
    'haelt %s fuer gestellt',
    (zustand) => {
      expect(istGestellt(zustand)).toBe(true);
    },
  );

  it('haelt einen Entwurf nicht fuer gestellt', () => {
    expect(istGestellt('ENTWURF')).toBe(false);
  });
});

describe('alsRechnungszustand', () => {
  it('nimmt einen bekannten Zustand', () => {
    expect(alsRechnungszustand('GESTELLT')).toBe('GESTELLT');
  });

  it('nimmt auch die beiden Ausgaenge', () => {
    expect(alsRechnungszustand('BEZAHLT')).toBe('BEZAHLT');
    expect(alsRechnungszustand('ABGESCHRIEBEN')).toBe('ABGESCHRIEBEN');
  });

  it.each([['MAHNUNG'], [''], [null], [3]])('weist %s ab', (wert) => {
    expect(alsRechnungszustand(wert)).toBeNull();
  });
});
