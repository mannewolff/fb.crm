import { IconCircleCheck, IconPencil, IconReceipt, IconSend, IconTool } from '@tabler/icons-react';
import type { TablerIcon } from '@tabler/icons-react';
import { describe, expect, it } from 'vitest';

import type { ToenungName } from '../theme';
import { ANGEBOTSSTATUS, alsAngebotsstatus, angebotsstatusBild } from './angebotsstatus';
import type { Angebotsstatus } from './angebotsstatus';

describe('angebotsstatusBild', () => {
  it.each<[Angebotsstatus, string, ToenungName, TablerIcon]>([
    ['ANGELEGT', 'Angelegt', 'flieder', IconPencil],
    ['ABGEGEBEN', 'Abgegeben', 'himmel', IconSend],
    ['BESTELLT', 'Bestellt', 'pfirsich', IconTool],
    ['ERLEDIGT', 'Erledigt', 'bernstein', IconCircleCheck],
    ['ABGERECHNET', 'Abgerechnet', 'salbei', IconReceipt],
    ['LAEUFT', 'Läuft', 'pfirsich', IconTool],
    ['ABGESCHLOSSEN', 'Abgeschlossen', 'salbei', IconCircleCheck],
  ])('gibt zu %s Wort, Toenung und Symbol', (status, wort, toenung, symbol) => {
    expect(angebotsstatusBild(status)).toEqual({ wort, toenung, symbol });
  });
});

describe('ANGEBOTSSTATUS', () => {
  it('nennt die sieben Status in der Reihenfolge des Backends', () => {
    expect(ANGEBOTSSTATUS).toEqual([
      'ANGELEGT',
      'ABGEGEBEN',
      'BESTELLT',
      'ERLEDIGT',
      'ABGERECHNET',
      'LAEUFT',
      'ABGESCHLOSSEN',
    ]);
  });
});

describe('alsAngebotsstatus', () => {
  it.each([['BESTELLT'], ['LAEUFT'], ['ABGESCHLOSSEN']])('nimmt den bekannten Status %s', (wert) => {
    expect(alsAngebotsstatus(wert)).toBe(wert);
  });

  it.each([['VERHANDELT'], ['XY'], [''], [null], [3]])('weist %s ab', (wert) => {
    expect(alsAngebotsstatus(wert)).toBeNull();
  });
});
