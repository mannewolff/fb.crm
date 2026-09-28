import { apiJson } from './client';
import { FORMFEHLER, inHundertsteln, liste, objekt, text, zahl } from './verengen';
import type { Auftragsstatus } from '../lib/auftragsstatus';

/**
 * Der Weg zum Auftragsbestand (Kriterien 12, 13; Plan #112, E12, E13, E19).
 *
 * Die Typen sind die Gegenstuecke zu `AuftragsbestandResponse` und `AuftragsbestandZeileResponse`
 * im Backend; jede Antwort geht durch einen Parser, kein `as`. Geld steht wie in `api/pipeline.ts`
 * als ganze Cent und traegt darum andere Namen als in der Antwort.
 *
 * **Beide Summen kommen vom Server** — „Beauftragt" und „Noch offen". Die Rundungsregel steht im
 * Backend; die Oberflaeche addiert die Zeilen nicht nach.
 */

/** Eine Zeile des Auftragsbestands (Kriterium 12). */
export interface AuftragsbestandZeile {
  /** Technische Id des Vorgangs — daran haengt der Weg auf den Auftrag. */
  readonly vorgangId: number;
  /** Fortlaufende Vorgangsnummer als Zahl; das `#` setzt die Oberflaeche. */
  readonly vorgangNummer: number;
  readonly vorgangTitel: string;
  readonly firma: string;
  readonly auftragId: number;
  readonly nummer: string;
  readonly status: Auftragsstatus;
  /** Netto-Summe des Auftrags in ganzen Cent. */
  readonly auftragssummeInCent: number;
  /** Bereits abgerechnet in ganzen Cent — bis zur Rechnung (Idee #7) ueberall 0 (Plan E12). */
  readonly abgerechnetInCent: number;
  /** Offener Rest in ganzen Cent (Kriterium 13). */
  readonly offenerRestInCent: number;
}

/** Die Auswertung als Ganzes: die Zeilen und die beiden Summen (Kriterium 13). */
export interface Auftragsbestand {
  readonly zeilen: readonly AuftragsbestandZeile[];
  /** Summe der Auftragssummen in ganzen Cent. */
  readonly beauftragtInCent: number;
  /** Summe der offenen Reste in ganzen Cent. */
  readonly nochOffenInCent: number;
}

function auftragsstatus(wert: unknown): Auftragsstatus {
  if (wert !== 'OFFEN' && wert !== 'IN_ARBEIT' && wert !== 'ABGESCHLOSSEN') {
    throw new TypeError(FORMFEHLER);
  }
  return wert;
}

function parseZeile(wert: unknown): AuftragsbestandZeile {
  const zeile = objekt(wert);
  return {
    vorgangId: zahl(zeile.vorgangId),
    vorgangNummer: zahl(zeile.vorgangNummer),
    vorgangTitel: text(zeile.vorgangTitel),
    firma: text(zeile.firma),
    auftragId: zahl(zeile.auftragId),
    nummer: text(zeile.nummer),
    status: auftragsstatus(zeile.status),
    auftragssummeInCent: inHundertsteln(zeile.auftragssumme),
    abgerechnetInCent: inHundertsteln(zeile.abgerechnet),
    offenerRestInCent: inHundertsteln(zeile.offenerRest),
  };
}

/** Verengt die Auswertung oder scheitert. */
export function parseAuftragsbestand(wert: unknown): Auftragsbestand {
  const antwort = objekt(wert);
  return {
    zeilen: liste(antwort.zeilen).map(parseZeile),
    beauftragtInCent: inHundertsteln(antwort.beauftragt),
    nochOffenInCent: inHundertsteln(antwort.nochOffen),
  };
}

/**
 * Die nicht abgeschlossenen Auftraege offener Vorgaenge samt den beiden Summen (Kriterien 12, 13).
 *
 * Das Abbruchsignal laesst einen laufenden Aufruf fallen, wenn die Ansicht verlassen wird — wie in
 * der Pipeline.
 */
export function auftragsbestand(signal?: AbortSignal): Promise<Auftragsbestand> {
  return apiJson('/api/auftragsbestand', { methode: 'GET', signal }, parseAuftragsbestand);
}
