import { apiJson } from './client';
import { objekt, textOderNull, zahl } from './verengen';

/**
 * Die zwei Wege der eigenen Angaben: lesen und fortschreiben (Kriterium 1).
 *
 * Der Typ ist das Gegenstueck zu `EigeneAngabenResponse` im Backend; aendert sich dort ein Feld,
 * aendert es sich hier mit (CLAUDE-react.md). Die Antwort geht durch einen Parser: Was ueber das
 * Netz kommt, ist `unknown`, bis es geprueft ist — kein `as`.
 *
 * **Ein Typ fuer die zwoelf Felder beider Richtungen.** `EigeneAngabenRequest` und
 * `EigeneAngabenResponse` tragen dieselben zwoelf Felder; die Antwort traegt dazu nur den Stand
 * (`version`), den {@link GespeicherteEigeneAngaben} ergaenzt — weder eine Kennung noch den
 * Zeitpunkt der letzten Aenderung.
 *
 * Kein Anlegen und kein Loeschen: Die eine Zeile gibt es von der Migration an, und sie
 * verschwindet nicht wieder.
 *
 * Die Anschrift steht flach und nicht geschachtelt — so, wie das Backend sie ausliefert: Die Maske
 * hat fuer jede ihrer Angaben ein eigenes Feld.
 *
 * **Das Speichern legt den Stand vor** (#411, Kriterium 4; Plan #415, E2): Die Version der Antwort
 * geht beim naechsten Speichern als `If-Match` hinaus. Hat inzwischen jemand anderes gespeichert,
 * weist der Server mit einem Bearbeitungskonflikt ab; ein gelungenes Speichern antwortet mit dem
 * neuen Stand samt erhoehter Version.
 */

/** Die eigenen Angaben — jedes Feld darf fehlen, dann steht dort `null`. */
export interface EigeneAngaben {
  readonly name: string | null;
  readonly berufsbezeichnung: string | null;
  readonly strasse: string | null;
  readonly plz: string | null;
  readonly ort: string | null;
  readonly land: string | null;
  readonly email: string | null;
  readonly telefon: string | null;
  readonly webadresse: string | null;
  readonly steuernummer: string | null;
  readonly umsatzsteuerId: string | null;
  readonly bankverbindung: string | null;
}

/** Die Angaben, wie der Server sie liefert: samt dem Stand, den das Speichern vorlegt. */
export interface GespeicherteEigeneAngaben extends EigeneAngaben {
  /** Der Stand der Zeile (Plan #415, E1). */
  readonly version: number;
}

/** Verengt die Antwort oder scheitert. */
export function parseEigeneAngaben(wert: unknown): GespeicherteEigeneAngaben {
  const angaben = objekt(wert);
  return {
    name: textOderNull(angaben.name),
    berufsbezeichnung: textOderNull(angaben.berufsbezeichnung),
    strasse: textOderNull(angaben.strasse),
    plz: textOderNull(angaben.plz),
    ort: textOderNull(angaben.ort),
    land: textOderNull(angaben.land),
    email: textOderNull(angaben.email),
    telefon: textOderNull(angaben.telefon),
    webadresse: textOderNull(angaben.webadresse),
    steuernummer: textOderNull(angaben.steuernummer),
    umsatzsteuerId: textOderNull(angaben.umsatzsteuerId),
    bankverbindung: textOderNull(angaben.bankverbindung),
    version: zahl(angaben.version),
  };
}

/** Liest die eigenen Angaben; auf einer frischen Instanz mit lauter leeren Feldern. */
export function eigeneAngabenLesen(): Promise<GespeicherteEigeneAngaben> {
  return apiJson('/api/eigene-angaben', { methode: 'GET' }, parseEigeneAngaben);
}

/**
 * Schreibt die Angaben fort; die Antwort traegt den neuen Stand.
 *
 * @param version der Stand, auf dem die Aenderung beruht
 */
export function eigeneAngabenPflegen(
  version: number,
  angaben: EigeneAngaben,
): Promise<GespeicherteEigeneAngaben> {
  return apiJson(
    '/api/eigene-angaben',
    { methode: 'PUT', rumpf: angaben, version },
    parseEigeneAngaben,
  );
}
