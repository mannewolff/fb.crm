import { IconCircleCheck, IconPencil, IconReceipt, IconSend, IconTool } from '@tabler/icons-react';
import type { TablerIcon } from '@tabler/icons-react';
import { describe, expect, it } from 'vitest';

import type { ToenungName } from '../theme';
import {
  ANGEBOTSSTATUS,
  FILTERWERTE,
  STORNIERT_WORT,
  alsAngebotsstatus,
  alsFilterwert,
  angebotsstatusBild,
  filterwortZu,
} from './angebotsstatus';
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

describe('angebotsstatusBild — kein achter Status (Plan #332, E1, E19)', () => {
  it('kennt weiter genau die sieben Status', () => {
    expect(ANGEBOTSSTATUS).toHaveLength(7);
    expect(ANGEBOTSSTATUS.map((status) => angebotsstatusBild(status).wort)).toHaveLength(7);
    // „Storniert" ist kein Status: Ein unbekannter Schluessel hat kein Bild.
    expect(angebotsstatusBild('STORNIERT' as Angebotsstatus)).toBeUndefined();
  });
});

describe('FILTERWERTE (Plan #332, E19)', () => {
  it('nennt die sieben Status in ihrer Reihenfolge und „Storniert" am Ende', () => {
    expect(FILTERWERTE).toHaveLength(8);
    expect(FILTERWERTE).toEqual([...ANGEBOTSSTATUS, 'STORNIERT']);
  });
});

describe('alsFilterwert (Plan #332, E20)', () => {
  it.each([
    ['ANGELEGT'],
    ['ABGEGEBEN'],
    ['BESTELLT'],
    ['ERLEDIGT'],
    ['ABGERECHNET'],
    ['LAEUFT'],
    ['ABGESCHLOSSEN'],
    ['STORNIERT'],
  ])('nimmt den Filterwert %s', (wert) => {
    expect(alsFilterwert(wert)).toBe(wert);
  });

  it.each([['VERHANDELT'], ['storniert'], [''], [null], [3]])(
    'liest den unbekannten Wert %s als „alle"',
    (wert) => {
      expect(alsFilterwert(wert)).toBeNull();
    },
  );
});

describe('filterwortZu (Plan #332, E19)', () => {
  it.each(ANGEBOTSSTATUS.map((status) => [status]))(
    'nennt zu %s das Wort des Statusbilds',
    (status) => {
      expect(filterwortZu(status)).toBe(angebotsstatusBild(status).wort);
    },
  );

  it('nennt zu STORNIERT die geteilte Wortkonstante', () => {
    expect(STORNIERT_WORT).toBe('Storniert');
    expect(filterwortZu('STORNIERT')).toBe(STORNIERT_WORT);
  });
});
