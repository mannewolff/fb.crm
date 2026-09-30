/**
 * Ein Zeitstempel aus dem Backend als deutsche Schreibweise mit Uhrzeit — „30.09.2026, 14:05".
 *
 * Der Zeitpunkt kommt als `Instant` und damit in UTC; gezeigt wird er in der <b>Ortszeit des
 * Betrachters</b> (Plan #141, E9). Anders als bei einem Tag ({@link tagWort} in `lib/tag.ts`) ist
 * `Date` hier also nicht der Fehler, sondern der Weg: Ein Zeitstempel traegt seine Zone mit, und
 * genau die Umrechnung in die Zone des Rechners ist gewollt.
 *
 * Gesetzt wird aus den Feldern von `Date` und nicht ueber `toLocaleString`: Dessen Ausgabe haengt
 * an der Sprachliste des Browsers — dieselbe Ansicht las sich je nach Rechner „9/30/2026, 2:05 PM".
 * Ein fest gesetztes Gebietsschema waere der zweite Weg zur selben Form, und die Trennzeichen
 * stehen ohnehin schon in `lib/tag.ts`.
 *
 * Sekunden stehen nicht dort: Der Kommentarbereich zeigt, wann etwas geschrieben wurde, und dafuer
 * genuegt die Minute.
 *
 * Was kein Zeitstempel ist, geht unveraendert heraus — dieselbe Regel wie bei {@link tagWort}: Der
 * Wert hat den Parser der Schnittstelle als Zeichenkette passiert, und ein Platzhalter an seiner
 * Stelle waere eine erfundene Angabe.
 */

function zweistellig(wert: number): string {
  return String(wert).padStart(2, '0');
}

/** Der Zeitpunkt in deutscher Schreibweise mit Uhrzeit, oder der rohe Wert, wenn er keiner ist. */
export function zeitpunktWort(zeitstempel: string): string {
  const zeit = new Date(zeitstempel);
  if (Number.isNaN(zeit.getTime())) {
    return zeitstempel;
  }
  const tag = `${zweistellig(zeit.getDate())}.${zweistellig(zeit.getMonth() + 1)}.${String(zeit.getFullYear())}`;
  return `${tag}, ${zweistellig(zeit.getHours())}:${zweistellig(zeit.getMinutes())}`;
}
