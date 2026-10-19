import { IconBan } from '@tabler/icons-react';

import { STORNIERT_WORT } from '../lib/angebotsstatus';
import ZustandsChip from './ZustandsChip';

/**
 * Das Storno als Chip — **neben** dem Statuschip, nicht an seiner Stelle (Plan #332, E13): Ein
 * storniertes Angebot behaelt seinen Status, und beide Aussagen sollen auf einen Blick zu lesen
 * sein (Kriterium 4).
 *
 * **Das Wort traegt die Aussage, Toenung und Symbol stuetzen sie** (CLAUDE-design.md,
 * „Zustandsformen"). Rosé traegt dort schon „gescheitert, ueberfaellig, stillgelegt" — es wird
 * keine Toenung neu belegt. Das Wort ist dieselbe Konstante, die der Filter der Uebersicht zeigt
 * (E19).
 *
 * **Keine Eigenschaften**, wie beim `InternChip`: Der Chip erscheint oder er erscheint nicht.
 */

/** Dieselbe Groesse wie im `AngebotsstatusChip` — der Chip steht neben einem. */
const SYMBOL_CHIP = 13;

export default function StorniertChip() {
  return (
    <ZustandsChip
      wort={STORNIERT_WORT}
      toenung="rose"
      symbol={<IconBan size={SYMBOL_CHIP} stroke={1.8} />}
    />
  );
}
