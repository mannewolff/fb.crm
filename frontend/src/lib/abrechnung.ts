import { betrag } from './geld';

/**
 * Was die Entwurfsmaske einer Rechnung mitrechnet (#160, Kriterien 4 und 8; Plan #169, E5).
 *
 * Drei reine Funktionen und kein React: Die Maske zeigt beim Tippen Zahlen, die der Server erst
 * beim Speichern bestaetigt, und sie muss dabei <b>denselben Betrag zeigen, den der Server gleich
 * zurueckschickt</b> — sonst springt die Summe beim Speichern um einen Cent. Deshalb stehen die
 * Regeln hier und nicht in der Ansicht: So sind sie ohne Oberflaeche gegen die Zahlen des Backends
 * zu pruefen.
 *
 * <b>Dieselbe Reihenfolge der Rundungen wie im Backend</b> ({@code Rechnung#netto},
 * {@code Rechnung#steuer}, {@code Geldrechnung}): Der Betrag jeder Zeile wird zuerst auf den Cent
 * gerundet, dann werden die Betraege addiert, und die Steuer entsteht aus dieser Summe und nicht je
 * Zeile. Zweimal 249,98 ergeben je Zeile 19 Prozent zu 47,50 und zusammen 95,00, aus der Summe
 * 499,96 gerechnet aber 94,99 — und auf dem Beleg steht der Betrag aus der Summe.
 *
 * <b>Gerechnet wird in ganzen Zahlen</b>, wie in `lib/geld.ts` begruendet: Mengen in Hundertsteln,
 * Geld in Cent, der Steuersatz in Hundertstel-Prozent (19 Prozent sind 1900). Eine Gleitkommazahl
 * dazwischen holte genau den Fehler zurueck, den die ganzen Zahlen vermeiden.
 *
 * <b>Ueberschreitung ist keine negative offene Menge</b>, sondern eine eigene Angabe — dieselbe
 * Trennung wie in {@code Positionsstand} im Backend (Kriterium 7). Die Maske rechnet sie beim
 * Tippen neu, weil der Hinweis an der Zeile zeigen soll, was mit der gerade eingetragenen Menge
 * zusammen zu viel waere.
 */

/** Hundert Prozent in Hundertstel-Prozent — die Einheit, in der der Steuersatz steht. */
const PROZENT = 10000;

/** Die halbe Einheit: der Zuschlag, mit dem die ganzzahlige Teilung kaufmaennisch rundet. */
const HALBE = PROZENT / 2;

/** Eine Zeile, die zur Netto-Summe beitraegt — Menge in Hundertsteln, Preis in Cent. */
export interface Summenzeile {
  readonly mengeInHundertsteln: number;
  readonly einzelpreisInCent: number;
}

/** Die drei Betraege unter der Positionstafel, alle in ganzen Cent. */
export interface Rechnungssummen {
  readonly nettoInCent: number;
  readonly steuerInCent: number;
  readonly bruttoInCent: number;
}

/**
 * Was mit dieser Menge zusammen ueber das Angebot hinausgeht, sonst 0 (Kriterium 8).
 *
 * Die schon abgerechnete Menge zaehlt mit: Sie stammt aus den <b>anderen</b> Rechnungen dieses
 * Angebots, und zu viel ist erst, was zusammen mit ihnen zu viel ist. Das Ergebnis ist nie
 * negativ — „noch offen" ist die andere Angabe und kommt vom Server.
 *
 * @param abgerechnetInHundertsteln die Menge aus allen anderen Rechnungen dieses Angebots
 * @param angebotenInHundertsteln die Menge am Angebot
 * @param mengeInHundertsteln die Menge, die diese Rechnung abrechnet
 */
export function ueberschreitung(
  abgerechnetInHundertsteln: number,
  angebotenInHundertsteln: number,
  mengeInHundertsteln: number,
): number {
  return Math.max(0, abgerechnetInHundertsteln + mengeInHundertsteln - angebotenInHundertsteln);
}

/**
 * Die Steuer auf eine Netto-Summe in ganzen Cent, kaufmaennisch gerundet.
 *
 * Beide Angaben kommen als ganze Zahl herein — die Summe in Cent, der Satz in Hundertstel-Prozent.
 * Das Produkt steht in Hundertstel-Cent-Prozent; `+ HALBE` vor der ganzzahligen Teilung ist die
 * kaufmaennische Rundung fuer nicht negative Werte, wie `+ 50` in {@link betrag}.
 *
 * @param nettoInCent die Netto-Summe in ganzen Cent
 * @param satzInHundertsteln der Steuersatz in Hundertstel-Prozent — 19 Prozent sind 1900
 */
export function steuerBetrag(nettoInCent: number, satzInHundertsteln: number): number {
  return Math.floor((nettoInCent * satzInHundertsteln + HALBE) / PROZENT);
}

/**
 * Netto, Steuer und Brutto der Maske (Kriterium 4).
 *
 * Je Zeile gerundet, dann addiert — und die Steuer aus der Summe. Die Begruendung steht oben im
 * Modul; sie ist der Grund, warum diese drei Betraege zusammen entstehen und nicht jeder fuer sich.
 *
 * @param zeilen die Zeilen mit lesbarer Menge, in beliebiger Reihenfolge
 * @param satzInHundertsteln der geltende Steuersatz in Hundertstel-Prozent
 */
export function rechnungssummen(
  zeilen: readonly Summenzeile[],
  satzInHundertsteln: number,
): Rechnungssummen {
  const nettoInCent = zeilen.reduce(
    (summe, zeile) => summe + betrag(zeile.mengeInHundertsteln, zeile.einzelpreisInCent),
    0,
  );
  const steuerInCent = steuerBetrag(nettoInCent, satzInHundertsteln);
  return { nettoInCent, steuerInCent, bruttoInCent: nettoInCent + steuerInCent };
}
