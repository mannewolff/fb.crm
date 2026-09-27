import {
  IconArchive,
  IconCircleCheck,
  IconCircleX,
  IconClockExclamation,
  IconPencil,
  IconSend,
} from '@tabler/icons-react';
import type { TablerIcon } from '@tabler/icons-react';
import { describe, expect, it } from 'vitest';

import type { ToenungName } from '../theme';
import { angebotsstandBild } from './angebotsstand';
import type { Angebotsstand } from './angebotsstand';

describe('angebotsstandBild', () => {
  it.each<[Angebotsstand, string, ToenungName, TablerIcon]>([
    ['ENTWURF', 'Entwurf', 'flieder', IconPencil],
    ['VERSENDET', 'Versendet', 'himmel', IconSend],
    ['ABGELAUFEN', 'Abgelaufen', 'bernstein', IconClockExclamation],
    ['ANGENOMMEN', 'Angenommen', 'salbei', IconCircleCheck],
    ['ABGELEHNT', 'Abgelehnt', 'rose', IconCircleX],
    ['ABGELOEST', 'Abgelöst', 'rose', IconArchive],
  ])('gibt zu %s Wort, Toenung und Symbol', (stand, wort, toenung, symbol) => {
    expect(angebotsstandBild(stand)).toEqual({ wort, toenung, symbol });
  });
});
