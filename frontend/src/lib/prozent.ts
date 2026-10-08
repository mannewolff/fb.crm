import { mitTausenderpunkt } from './geld';

/**
 * Ein Prozentwert in ganzen Hundertstel-Prozent als deutscher Text mit einer Nachkommastelle —
 * `prozentWort(6670)` ergibt „66,7 %" (Plan #288, E18).
 *
 * Der Server rechnet und rundet die Quote bereits auf eine Nachkommastelle (E10); hier wird nur
 * gesetzt. Die zweite Hundertstelstelle ist darum stets null, und der Schritt auf Zehntel rundet
 * dennoch kaufmaennisch, statt still abzuschneiden. Gesetzt wird wie `euro` aus den Ziffern und
 * nicht ueber `Intl.NumberFormat` oder eine Division in Gleitkomma.
 */
export function prozentWort(hundertstelProzent: number): string {
  const zehntel = Math.floor((hundertstelProzent + 5) / 10);
  // Mindestens zwei Ziffern, damit „5" zu „0,5" wird und nicht zu „,5".
  const ziffern = String(zehntel).padStart(2, '0');
  return `${mitTausenderpunkt(ziffern.slice(0, -1))},${ziffern.slice(-1)} %`;
}
