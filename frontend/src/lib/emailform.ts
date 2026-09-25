/**
 * Die Form einer E-Mail-Adresse: genau ein `@`, davor Text, danach ein Punkt (E8).
 *
 * Wort für Wort dieselbe Regel wie `AnsprechpartnerEmailConstraint` im Backend, geprueft gegen
 * dieselbe Tabelle. Sie steht hier ein zweites Mal, weil die Maske frueh fuehren soll — die
 * Entscheidung faellt trotzdem am Server, der die Quelle der Wahrheit bleibt.
 *
 * Der leere Wert ist gueltig: Die E-Mail-Adresse ist keine Pflichtangabe. Leerraum am Rand faellt
 * vor der Pruefung weg, weil die Normalisierung ihn ohnehin nimmt (E9).
 */
export function istEmailForm(wert: string): boolean {
  const sauber = wert.trim();
  if (sauber === '') {
    return true;
  }
  // `split` behaelt in JavaScript die leeren Stuecke am Rand; ohne sie saehe „max@" aus wie eine
  // Adresse ganz ohne @ (vgl. die Grenze -1 in der Java-Fassung).
  const teile = sauber.split('@');
  return teile.length === 2 && teile[0] !== '' && teile[1].includes('.');
}
