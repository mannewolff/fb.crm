import { IconHome } from '@tabler/icons-react';

import ZustandsChip from './ZustandsChip';

/**
 * Das Kennzeichen „Intern" als Chip — fuer ein Angebot, das die eigene Arbeit festhaelt und nicht
 * an einen Kunden geht (Plan #218, E13).
 *
 * **Das Wort traegt die Aussage, Toenung und Symbol stuetzen sie** (CLAUDE-design.md,
 * „Zustandsformen"). Darum kein Symbol ohne Wort: Ein Haus allein waere fuer jeden stumm, der die
 * Zeichen dieser Anwendung nicht kennt. `flieder` ist die neutrale Kategorie — eine eigene Toenung
 * gaebe es nur zu einer eigenen Bedeutung, und „intern" ist keine Bewertung.
 *
 * **Keine Eigenschaften.** Der Chip erscheint oder er erscheint nicht; ein `intern`-Schalter hiesse,
 * jeden Leser auch den leeren Fall zeichnen zu lassen.
 *
 * Die fuenfte Fundstelle des Kennzeichens verwendet ihn **nicht**: Die Positionswahl des
 * Zeitdialogs gruppiert nach Angebot, und das Label eines nativen `optgroup` nimmt nur Text —
 * dort steht das Wort als Teil der Beschriftung (E21).
 */

/** Dieselbe Groesse wie im `AngebotsstatusChip` — der Chip steht meist neben einem. */
const SYMBOL_CHIP = 13;

export default function InternChip() {
  return (
    <ZustandsChip
      wort="Intern"
      toenung="flieder"
      symbol={<IconHome size={SYMBOL_CHIP} stroke={1.8} />}
    />
  );
}
