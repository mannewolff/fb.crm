import { hundertstel } from '../lib/geld';

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

/**
 * Eine Dezimalzahl der Antwort als ganze Hundertstel — Cent beim Geld, Hundertstel bei der Menge.
 *
 * Was keine Zahl mit hoechstens zwei Nachkommastellen ist, ist hier kein Betrag: eine dritte
 * Stelle, ein negativer Wert oder eine Zeichenkette brechen ab. Das Backend laesst nichts davon
 * entstehen (`@Digits(fraction = 2)`, `@DecimalMin("0")`); ein stiller Ersatzwert waere ein
 * Betrag, den niemand gerechnet hat.
 *
 * Umgerechnet wird ueber {@link hundertstel} und damit ueber die Ziffern, nicht ueber Gleitkomma
 * (E5). Die Pruefung steht hier und nicht in jedem Schnittstellenmodul: Angebot und jeder weitere
 * Beleg tragen dieselben Betraege, und zwei Abschriften derselben Regel liefen mit der Zeit
 * auseinander.
 */
export function inHundertsteln(wert: unknown): number {
  const gelesen = hundertstel(zahl(wert));
  if (gelesen === null) {
    throw new TypeError(FORMFEHLER);
  }
  return gelesen;
}

/** Ein Wahrheitswert — „ja" ist keiner. */
export function jaNein(wert: unknown): boolean {
  if (typeof wert !== 'boolean') {
    throw new TypeError(FORMFEHLER);
  }
  return wert;
}
