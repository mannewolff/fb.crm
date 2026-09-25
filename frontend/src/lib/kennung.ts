/**
 * Die Kennung aus einem Adressteil — oder nichts.
 *
 * Ein Pfadparameter ist eine Zeichenkette aus der Adresszeile und damit eine Eingabe wie jede
 * andere. Sie geht nicht ungeprueft in einen Weg zur Schnittstelle: `/api/firmen/keine-zahl`
 * waere eine Anfrage, die es nie geben kann, und ihr Fehlschlag saehe in der Oberflaeche aus wie
 * ein Ausfall des Servers. Was hier abgewiesen wird, meldet die Ansicht als unbekannte Firma,
 * ohne das Netz zu bemuehen.
 *
 * Bewusst eine Erlaubnisliste: nur Ziffern, kein Vorzeichen, kein Punkt, kein Leerraum. Der
 * Bezeichner ist im Backend ein `Long` groesser null, und alles andere ist keine Kennung.
 */

const NUR_ZIFFERN = /^\d+$/;

export function kennungAus(wert: string | undefined): number | null {
  if (wert === undefined || !NUR_ZIFFERN.test(wert)) {
    return null;
  }
  return Number(wert);
}
