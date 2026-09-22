/**
 * Das Kuerzel im Nutzer-Mal (E12): erster Buchstabe des ersten und des letzten Wortes.
 *
 * Ein einzelnes Wort mit Bindestrich zaehlt seine Teile als Woerter („Anna-Lena" → „AL"); bei
 * mehreren Woertern gilt der Bindestrich als Teil des Wortes, und „Anna-Lena Schmidt" ergibt
 * „AS". Gezaehlt wird in Codepoints, nicht in UTF-16-Einheiten — sonst zerfiele ein Zeichen
 * jenseits der Grundebene in eine halbe Hilfskodierung. Ein Name ohne Buchstaben ergibt „?":
 * Ein leeres Mal saehe aus wie ein Darstellungsfehler.
 */
export function initialen(anzeigename: string): string {
  const woerter = anzeigename.trim().split(/\s+/);
  const teile = woerter.length === 1 ? woerter[0].split('-') : woerter;
  const belegt = teile.filter((teil) => teil !== '');
  if (belegt.length === 0) {
    return '?';
  }
  const erster = belegt[0];
  const letzter = belegt[belegt.length - 1];
  const buchstaben = belegt.length === 1 ? [erster] : [erster, letzter];
  return buchstaben.map((wort) => Array.from(wort)[0].toLocaleUpperCase('de')).join('');
}
