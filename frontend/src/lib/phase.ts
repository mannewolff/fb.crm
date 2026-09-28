import type { Phase } from '../api/vorgaenge';

/**
 * Die Phase eines Vorgangs als Wort — an genau einer Stelle (E30).
 *
 * Bis zu diesem Paket stand dieselbe Zuordnung dreimal da: in `VorgangPage`, `VorgaengePage` und
 * `FirmaPage`. Solange es eine einzige Phase gab, war das tragbar; mit der zweiten waere es eine
 * Angabe an drei Orten, die beim naechsten Wert auseinanderlaeuft — `AUFTRAG` ist der dritte, und
 * Rechnung und Zahlung bringen weitere mit.
 *
 * Eine Funktion und keine ausgelieferte Karte: So kann kein Aufrufer die Zuordnung erweitern oder
 * an einem Schluessel vorbeigreifen, den es nicht gibt.
 */

const PHASE_WORT: Readonly<Record<Phase, string>> = {
  ANBAHNUNG: 'Anbahnung',
  ANGEBOT: 'Angebot',
  AUFTRAG: 'Auftrag',
};

/** Das Wort zur Phase (Kriterien 10, 11, 22). */
export function phaseWort(phase: Phase): string {
  return PHASE_WORT[phase];
}
