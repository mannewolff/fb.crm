/**
 * Die Rechenregel fuer Geld in der Oberflaeche (E5).
 *
 * **Hier wird nicht in Gleitkomma gerechnet.** Menge und Preis kommen als Dezimalzahl mit
 * hoechstens zwei Nachkommastellen — so schreibt sie die Maske, und so begrenzt sie das Backend
 * (`@Digits(integer = 10, fraction = 2)`). Aus jeder wird darum zuerst eine **ganze Zahl**: die
 * Menge in Hundertsteln, der Preis in Cent. Das Produkt ist dann in Hundertstel-Cent, und erst
 * beim Schritt zurueck auf ganze Cent wird gerundet — kaufmaennisch, HALF_UP, wie im Backend
 * (`Angebotsposition#betrag`).
 *
 * Der Umweg ist kein Selbstzweck: `2.5 * 1000.01` ergibt in IEEE-754 `2500.0249999999996`, und wer
 * das auf zwei Stellen abschneidet, zeigt 2.500,02 € — einen Cent neben dem, was der Server
 * gleich zurueckschickt und was auf dem PDF steht. Nach dem Speichern gilt ohnehin der Wert aus
 * der Antwort; die mittippende Summe muss aber dasselbe zeigen, sonst springt der Betrag beim
 * Speichern.
 *
 * Auch das Lesen und das Schreiben laufen ueber Zeichenketten und nicht ueber eine Umwandlung in
 * eine Gleitkommazahl: Eine solche Zwischenstufe holte genau den Fehler wieder herein, den die
 * ganzen Zahlen vermeiden.
 *
 * Alle Betraege sind **netto** und nicht negativ — das Backend laesst nichts anderes zu
 * (`@DecimalMin("0")`). Die Zahlenbereiche bleiben damit weit innerhalb der ganzzahlig exakten
 * Spanne von JavaScript.
 */

/** Eine Dezimalzahl mit hoechstens zwei Nachkommastellen, Punkt oder Komma als Trenner. */
const DEZIMAL = /^(\d+)(?:[.,](\d{1,2}))?$/u;

/** Setzt den Tausenderpunkt vor jede volle Dreiergruppe, die noch Ziffern hinter sich hat. */
const TAUSENDER = /\B(?=(\d{3})+(?!\d))/gu;

/**
 * Eine Dezimalangabe als ganze Hundertstel — `null`, wenn sie keine ist.
 *
 * Dieselbe Funktion fuer Menge und Geld: Beide tragen zwei Nachkommastellen, und zwei Abschriften
 * derselben sechs Zeilen liefen beim ersten Nachziehen auseinander. Aus der Menge werden
 * Hundertstel, aus dem Preis Cent.
 *
 * `null` heisst „das ist keine Zahl" — leeres Feld, Buchstaben, drei Nachkommastellen. Die Maske
 * macht daraus eine Meldung am Feld; ein stiller Ersatzwert waere ein Betrag, den niemand
 * eingegeben hat.
 */
export function hundertstel(wert: string | number): number | null {
  const gelesen = typeof wert === 'number' ? String(wert) : wert.trim();
  const treffer = DEZIMAL.exec(gelesen);
  if (treffer === null) {
    return null;
  }
  // `padEnd` und nicht `padStart`: „2,5" sind fuenfzig Hundertstel, nicht fuenf.
  const nachkomma = (treffer[2] ?? '').padEnd(2, '0');
  return parseInt(treffer[1], 10) * 100 + parseInt(nachkomma, 10);
}

/**
 * Der Betrag einer Position in ganzen Cent: Menge mal Einzelpreis, HALF_UP gerundet (E5).
 *
 * Beide Angaben kommen bereits als ganze Zahl herein — die Menge in Hundertsteln, der Preis in
 * Cent, beide aus {@link hundertstel}. Das Produkt steht in Hundertstel-Cent; `+ 50` vor der
 * ganzzahligen Teilung ist die kaufmaennische Rundung fuer nicht negative Werte.
 */
export function betrag(mengeInHundertsteln: number, preisInCent: number): number {
  return Math.floor((mengeInHundertsteln * preisInCent + 50) / 100);
}

/**
 * Ein Betrag in ganzen Cent als deutscher Eurobetrag — „2.500,03 €".
 *
 * Gesetzt wird aus den Ziffern und nicht aus einer Zahl: `Intl.NumberFormat` braeuchte den Betrag
 * als Gleitkommazahl, und genau die gibt es hier bewusst nicht. Die beiden Dezimalstellen stehen
 * immer da — auch bei glatten Betraegen, sonst stuenden in einer Spalte verschieden lange Zahlen.
 */
export function euro(cent: number): string {
  // Mindestens drei Ziffern, damit „5" zu „0,05" wird und nicht zu „,5".
  const ziffern = String(cent).padStart(3, '0');
  const ganze = ziffern.slice(0, -2).replace(TAUSENDER, '.');
  return `${ganze},${ziffern.slice(-2)} €`;
}

/**
 * Ein Hundertstelwert als Dezimaltext — `dezimal(250, ',')` ergibt „2,50".
 *
 * Zwei Leser brauchen ihn mit verschiedenem Trenner: Das Eingabefeld zeigt das Komma, der Rumpf
 * einer Anfrage traegt den Punkt, den `BigDecimal` liest. Gesetzt wird wie in {@link euro} aus den
 * Ziffern und nicht aus einer Zahl — eine Gleitkommazahl dazwischen holte genau den Fehler zurueck,
 * den die ganzen Zahlen vermeiden.
 *
 * Die beiden Dezimalstellen stehen immer da: In einer Spalte stuenden sonst verschieden lange
 * Zahlen, und ein Rumpf mit „2,5" und einer mit „2,50" waeren zwei Formen fuer denselben Wert.
 */
export function dezimal(hundertstelWert: number, trenner: string): string {
  // Mindestens drei Ziffern, damit „5" zu „0,05" wird und nicht zu „,5".
  const ziffern = String(hundertstelWert).padStart(3, '0');
  return `${ziffern.slice(0, -2)}${trenner}${ziffern.slice(-2)}`;
}
