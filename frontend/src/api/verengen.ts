/**
 * Die Bausteine, mit denen eine Antwort aus `unknown` zu einem Typ wird.
 *
 * Was ueber das Netz kommt, ist unbekannt, bis es geprueft ist — kein `as` (CLAUDE-react.md).
 * Diese Pruefungen sind in jedem Schnittstellenmodul dieselben und stehen deshalb einmal hier:
 * Zwei Abschriften derselben zehn Zeilen wuerden mit der Zeit auseinanderlaufen, und die Ansicht
 * saehe je nach Modul eine andere Strenge.
 *
 * Jede Funktion tut genau zwei Dinge: den Wert durchlassen oder mit `TypeError` abbrechen. Wer
 * eine halb gelesene Antwort weiterreichte, verschoebe den Fehler nur an eine Stelle, an der
 * niemand mehr sieht, woher er kommt.
 */

export const FORMFEHLER = 'Die Antwort der Schnittstelle hat nicht die erwartete Form.';

function istObjekt(wert: unknown): wert is Record<string, unknown> {
  return typeof wert === 'object' && wert !== null;
}

/** Ein Objekt, dessen Felder noch unbekannt sind. */
export function objekt(wert: unknown): Record<string, unknown> {
  if (!istObjekt(wert)) {
    throw new TypeError(FORMFEHLER);
  }
  return wert;
}

/** Eine Liste, deren Elemente noch unbekannt sind. */
export function liste(wert: unknown): readonly unknown[] {
  if (!Array.isArray(wert)) {
    throw new TypeError(FORMFEHLER);
  }
  return wert;
}

/** Eine Zahl — eine Zahl in Anfuehrungszeichen ist keine. */
export function zahl(wert: unknown): number {
  if (typeof wert !== 'number') {
    throw new TypeError(FORMFEHLER);
  }
  return wert;
}

/** Eine Zahl, die fehlen darf — dann steht dort `null`, nie eine Null. */
export function zahlOderNull(wert: unknown): number | null {
  return wert === null ? null : zahl(wert);
}

/** Eine Zeichenkette. */
export function text(wert: unknown): string {
  if (typeof wert !== 'string') {
    throw new TypeError(FORMFEHLER);
  }
  return wert;
}

/** Eine Angabe, die fehlen darf — dann steht dort `null`, nie ein Platzhalter. */
export function textOderNull(wert: unknown): string | null {
  return wert === null ? null : text(wert);
}

/** Ein Wahrheitswert — „ja" ist keiner. */
export function jaNein(wert: unknown): boolean {
  if (typeof wert !== 'boolean') {
    throw new TypeError(FORMFEHLER);
  }
  return wert;
}
