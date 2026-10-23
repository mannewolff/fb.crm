import { apiJson } from './client';
import { inHundertsteln, objekt, text, zahl } from './verengen';
import { dezimal } from '../lib/geld';

/**
 * Die zwei Wege der Rechnungseinstellungen: lesen und fortschreiben (Plan #161, E6).
 *
 * Die Typen sind die Gegenstuecke zu `RechnungseinstellungenResponse` und
 * `RechnungseinstellungenRequest` im Backend; aendert sich dort ein Feld, aendert es sich hier mit
 * (CLAUDE-react.md). Die Antwort geht durch einen Parser: Was ueber das Netz kommt, ist `unknown`,
 * bis es geprueft ist — kein `as`.
 *
 * **Der Steuersatz traegt hier einen anderen Namen als in der Antwort**, weil er eine andere Einheit
 * traegt: Die Antwort schickt `steuersatz` als Dezimalzahl (19.5), hier steht er als **ganze Zahl**
 * in Hundertstel-Prozent (1950) — wie das Geld in `angebote.ts` (E5). Verengt wird mit
 * {@link inHundertsteln} und damit ueber die Ziffern, nicht ueber Gleitkomma; `wert * 100` waere
 * fuer einen Steuersatz genau genug, aber es waere derselbe Weg, den `lib/geld.ts` ausdruecklich
 * ausschliesst — zwei Regeln fuer dieselbe Dezimalzahl laufen auseinander. Dieselben Namen bei
 * gewechselter Einheit waeren die gefaehrlichere Wahl: Wer `steuersatz` fuer Prozent haelt, rechnet
 * um den Faktor hundert daneben.
 *
 * Hinaus geht der Steuersatz als **Dezimaltext** mit Punkt („19.50"). Jackson liest daraus ein
 * `BigDecimal`; eine Gleitkommazahl im Rumpf waere die eine Umwandlung, die die Rechnung in
 * `lib/geld.ts` vermeidet. Die Umrechnung steht hier und nicht in der Maske: Sie gehoert zur
 * Systemgrenze, so wie das Verengen der Antwort.
 *
 * Kein Anlegen und kein Loeschen: Die eine Zeile gibt es von der Migration an, und sie verschwindet
 * nicht wieder.
 *
 * **Das Speichern legt den Stand vor** (#411, Kriterium 4; Plan #415, E2): Die Version der Antwort
 * geht beim naechsten Speichern als `If-Match` hinaus. Hat inzwischen jemand anderes gespeichert,
 * weist der Server mit einem Bearbeitungskonflikt ab; ein gelungenes Speichern antwortet mit dem
 * neuen Stand samt erhoehter Version.
 */

/** Die Rechnungseinstellungen mit ihren vier Werten (#159, Kriterium 5). */
export interface Rechnungseinstellungen {
  /** Schreibweise der Rechnungsnummer, siehe `lib/nummernmuster.ts`. */
  readonly nummerMuster: string;
  /** Die naechste laufende Nummer, ab 1. */
  readonly naechsteNummer: number;
  /** Mehrwertsteuersatz in ganzen Hundertstel-Prozent — 19,5 Prozent sind 1950. */
  readonly steuersatzInHundertsteln: number;
  /** Zahlungsziel in Tagen, ab 0. */
  readonly zahlungszielTage: number;
}

/** Die Einstellungen, wie der Server sie liefert: samt dem Stand, den das Speichern vorlegt. */
export interface GespeicherteRechnungseinstellungen extends Rechnungseinstellungen {
  /** Der Stand der Zeile (Plan #415, E1). */
  readonly version: number;
}

/** Verengt die Antwort oder scheitert. */
export function parseRechnungseinstellungen(wert: unknown): GespeicherteRechnungseinstellungen {
  const einstellungen = objekt(wert);
  return {
    nummerMuster: text(einstellungen.nummerMuster),
    naechsteNummer: zahl(einstellungen.naechsteNummer),
    steuersatzInHundertsteln: inHundertsteln(einstellungen.steuersatz),
    zahlungszielTage: zahl(einstellungen.zahlungszielTage),
    version: zahl(einstellungen.version),
  };
}

/** Liest die Einstellungen; auf einer frischen Instanz die Vorbelegungen der Migration. */
export function rechnungseinstellungenLesen(): Promise<GespeicherteRechnungseinstellungen> {
  return apiJson(
    '/api/rechnung/einstellungen',
    { methode: 'GET' },
    parseRechnungseinstellungen,
  );
}

/**
 * Schreibt die Einstellungen fort; die Antwort traegt den neuen Stand.
 *
 * @param version der Stand, auf dem die Aenderung beruht
 */
export function rechnungseinstellungenPflegen(
  version: number,
  einstellungen: Rechnungseinstellungen,
): Promise<GespeicherteRechnungseinstellungen> {
  return apiJson(
    '/api/rechnung/einstellungen',
    {
      methode: 'PUT',
      rumpf: {
        nummerMuster: einstellungen.nummerMuster,
        naechsteNummer: einstellungen.naechsteNummer,
        steuersatz: dezimal(einstellungen.steuersatzInHundertsteln, '.'),
        zahlungszielTage: einstellungen.zahlungszielTage,
      },
      version,
    },
    parseRechnungseinstellungen,
  );
}
