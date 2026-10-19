/**
 * Die Teile der Dokumentation (Issue #386; Plan #378, E1, E10).
 *
 * Der Text kommt zur Bauzeit als Rohtext-Import aus `docs/` an der Projektwurzel (E1) — die eine
 * Fassung aus Kriterium 2: Wer dort einen Satz aendert und neu baut, sieht ihn in fb.crm. Eine
 * Kopie nach `frontend/public/` waere die zweite Stelle; `dokumentation.test.ts` haelt das fest.
 * Der Image-Bau kopiert die Dateien dafuer eigens in seine Frontend-Stufe (`Dockerfile`, E2).
 */
import bedienung from '../../../docs/bedienung.md?raw';
import betrieb from '../../../docs/betrieb.md?raw';

import { leseMarkdown, type Dokument } from './markdown';

export type Kennung = 'bedienung' | 'betrieb';

export interface Teil {
  kennung: Kennung;
  name: string;
  dokument: Dokument;
}

const BEDIENUNG: Teil = { kennung: 'bedienung', name: 'Bedienung', dokument: leseMarkdown(bedienung) };
const BETRIEB: Teil = { kennung: 'betrieb', name: 'Betrieb', dokument: leseMarkdown(betrieb) };

/** „Bedienung" zuerst: die Reihenfolge im Umschalter und der Teil, den der Aufruf zeigt (Kriterium 1). */
export const TEILE: readonly Teil[] = [BEDIENUNG, BETRIEB];

const NACH_KENNUNG: Record<Kennung, Teil> = { bedienung: BEDIENUNG, betrieb: BETRIEB };

export function teilNach(kennung: Kennung): Teil {
  return NACH_KENNUNG[kennung];
}
