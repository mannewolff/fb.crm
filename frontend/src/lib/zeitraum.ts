import { alsMonat, monatWort } from './arbeitszeit';

/**
 * Der Zeitraum der Startseite: ein Monat oder ein Jahr (Issue #280; Plan #274, E1, E10, E15).
 *
 * Reine Funktionen und kein React, wie `lib/arbeitszeit.ts`. Der Zeitraum ist ein Begriff der
 * Startseite und nicht der Zeiterfassung — darum steht er in einer eigenen Datei, und die Monats-
 * regeln aus `lib/arbeitszeit.ts` werden hier verwendet und nicht abgeschrieben.
 *
 * <b>Der Server entscheidet, welcher Zeitraum gilt</b> (E15): Er kennt die Uhr in der Geschaefts-
 * zone und die waehlbaren Zeitraeume. {@link alsZeitraum} prueft nur, ob die Ansicht ueberhaupt
 * einen Parameter mitschickt — dieselbe Doppelung wie bei `alsMonat` gegenueber der Wandlung des
 * Servers. Darum entsteht hier kein Zeitraum aus der Browser-Uhr und kein Ersatzwert.
 */

/** Die Art des Zeitraums, wie die Antwort sie schreibt — das Gegenstueck zu `Zeitraum` im Backend. */
export type Zeitraumart = 'MONAT' | 'JAHR';

/** Die beiden Arten in der Reihenfolge des Backends. */
const ZEITRAUMARTEN: readonly Zeitraumart[] = ['MONAT', 'JAHR'];

/** Ein Jahr, wie der Adressparameter und die Antwort es schreiben — „2026". */
const JAHR = /^\d{4}$/u;

/**
 * Ein Wert aus der Adresse als Zeitraum, oder `null` (E1, E15).
 *
 * Nimmt einen Monat `JJJJ-MM` mit Monat 1 bis 12 oder ein Jahr `JJJJ`. Was keines von beiden ist,
 * ist hier kein Zeitraum — dann fragt die Ansicht ohne Parameter, und der Server nimmt den
 * laufenden Monat. Ein stiller Ersatzwert waere ein Zeitraum, den niemand gewaehlt hat.
 */
export function alsZeitraum(wert: string | null): string | null {
  if (wert !== null && JAHR.test(wert)) {
    return wert;
  }
  return alsMonat(wert);
}

/** Verengt einen Wert auf eine der beiden Arten, oder `null`, wenn er keine ist. */
export function alsZeitraumart(wert: unknown): Zeitraumart | null {
  return ZEITRAUMARTEN.find((art) => art === wert) ?? null;
}

/**
 * Der Zeitraum als Wort — ein Monat als „Oktober 2026", ein Jahr als „2026".
 *
 * Ein Jahr ist schon ein Wort; was weder Monat noch Jahr ist, geht wie bei {@link monatWort}
 * unveraendert heraus.
 */
export function zeitraumWort(wert: string): string {
  return monatWort(wert);
}
