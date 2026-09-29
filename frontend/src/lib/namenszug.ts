/**
 * Der Name eines Ansprechpartners als eine Zeile.
 *
 * „Anna Berg" oder, ohne Vornamen, „Clausen" — nie ein fuehrendes Leerzeichen. Die Regel steht
 * hier und nicht in jeder Ansicht, weil sie an mehreren Stellen dieselbe ist: in der Liste der
 * Firma und in der Auswahl der Angebotsmaske. Zwei Abschriften liefen beim ersten Nachziehen
 * auseinander.
 */
export function namensZug(partner: {
  readonly vorname: string | null;
  readonly nachname: string;
}): string {
  return partner.vorname === null ? partner.nachname : `${partner.vorname} ${partner.nachname}`;
}
