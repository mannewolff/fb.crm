/**
 * Ein Tag aus dem Backend als deutsche Schreibweise — „2026-09-24" wird „24.09.2026".
 *
 * Gesetzt wird aus den Ziffern und nicht ueber `Date`: Ein `LocalDate` traegt keine Zeitzone,
 * `new Date('2026-09-24')` liest ihn aber als Mitternacht UTC. Westlich von Greenwich zeigte
 * `toLocaleDateString` danach den Vortag — ein Angebotsdatum, das um einen Tag neben dem steht,
 * was auf dem Beleg gedruckt ist.
 *
 * Was nicht die Form `YYYY-MM-DD` hat, geht unveraendert heraus. Der Wert hat den Parser der
 * Schnittstelle als Zeichenkette passiert; ihn hier zu verschweigen oder durch einen Platzhalter
 * zu ersetzen, waere eine erfundene Angabe an der Stelle einer vorhandenen.
 */

const TAG = /^(\d{4})-(\d{2})-(\d{2})$/u;

/** Der Tag in deutscher Schreibweise, oder der rohe Wert, wenn er kein Tag ist. */
export function tagWort(tag: string): string {
  const treffer = TAG.exec(tag);
  return treffer === null ? tag : `${treffer[3]}.${treffer[2]}.${treffer[1]}`;
}

/** Was an der Stelle eines Leistungszeitraums steht, der nicht angegeben ist. */
export const KEIN_ZEITRAUM = 'nicht angegeben';

/**
 * Ein Leistungszeitraum als Wort — „01.10.2026 – 31.12.2026" (Kriterium 3).
 *
 * Der Zeitraum steht ganz oder gar nicht: Das Backend nimmt nur beide Tage oder keinen an
 * (`LeistungszeitraumConstraint`). Fehlt einer, gibt es also keinen Zeitraum, und dann steht dort
 * ein Wort und kein leeres Feld — eine leere Zelle liest sich wie ein Ladefehler.
 */
export function zeitraumWort(ab: string | null, bis: string | null): string {
  return ab === null || bis === null ? KEIN_ZEITRAUM : `${tagWort(ab)} – ${tagWort(bis)}`;
}
