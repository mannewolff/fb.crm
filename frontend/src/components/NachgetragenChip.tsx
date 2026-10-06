import { IconFileImport } from '@tabler/icons-react';

import ZustandsChip from './ZustandsChip';

/**
 * Das Kennzeichen „nachgetragen" als Chip — fuer eine Rechnung, die fb.crm nicht geschrieben,
 * sondern nur mit ihren Eckdaten erfasst hat (#254, Kriterium 6; Plan #259, E22).
 *
 * Nach dem Muster von {@link InternChip}: **Das Wort traegt die Aussage, Toenung und Symbol stuetzen
 * sie** (CLAUDE-design.md, „Zustandsformen"). Er steht **neben** dem Zustands-Chip und ersetzt ihn
 * nicht — eine nachgetragene Rechnung hat ihren Zustand wie jede gestellte (Kriterium 8).
 *
 * `flieder` ist die neutrale Kategorie und die Toenung der „Rechnungen als Menge": „nachgetragen"
 * sagt, woher eine Rechnung kommt, und bewertet nichts. Die vier Toenungen des Zustands sind damit
 * nicht doppelt belegt — ein Chip in Himmel neben „Gestellt" laese sich als zweiter Zustand.
 *
 * **Keine Eigenschaften.** Der Chip erscheint oder er erscheint nicht.
 */

/** Dieselbe Groesse wie im `RechnungszustandChip` — der Chip steht immer neben einem. */
const SYMBOL_CHIP = 13;

export default function NachgetragenChip() {
  return (
    <ZustandsChip
      wort="nachgetragen"
      toenung="flieder"
      symbol={<IconFileImport size={SYMBOL_CHIP} stroke={1.8} />}
    />
  );
}
