import { apiJson, apiOhneInhalt } from './client';
import { objekt, textOderNull } from './verengen';

/**
 * Die zwei Wege der eigenen Angaben: lesen und fortschreiben (Kriterium 1).
 *
 * Der Typ ist das Gegenstueck zu `EigeneAngabenResponse` im Backend; aendert sich dort ein Feld,
 * aendert es sich hier mit (CLAUDE-react.md). Die Antwort geht durch einen Parser: Was ueber das
 * Netz kommt, ist `unknown`, bis es geprueft ist — kein `as`.
 *
 * **Ein Typ fuer beide Richtungen.** `EigeneAngabenRequest` und `EigeneAngabenResponse` tragen
 * dieselben zwoelf Felder; die Antwort traegt nichts, was die Eingabe nicht traegt — weder eine
 * Kennung noch den Zeitpunkt der letzten Aenderung. Ein zweiter, gleichlautender Typ waere eine
 * Abschrift, die beim ersten neuen Feld auseinanderlaeuft. Bei der Firma ist das anders: Dort
 * traegt die Antwort `id`, `aktiv` und die Ansprechpartner, die keine Eingabe sind.
 *
 * Kein Anlegen und kein Loeschen: Die eine Zeile gibt es von der Migration an, und sie
 * verschwindet nicht wieder.
 *
 * Die Anschrift steht flach und nicht geschachtelt — so, wie das Backend sie ausliefert: Die Maske
 * hat fuer jede ihrer Angaben ein eigenes Feld.
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

/** Verengt die Antwort oder scheitert. */
export function parseEigeneAngaben(wert: unknown): EigeneAngaben {
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
  };
}

/** Liest die eigenen Angaben; auf einer frischen Instanz mit lauter leeren Feldern. */
export function eigeneAngabenLesen(): Promise<EigeneAngaben> {
  return apiJson('/api/eigene-angaben', { methode: 'GET' }, parseEigeneAngaben);
}

/** Schreibt die Angaben fort; die Antwort traegt keinen Rumpf. */
export function eigeneAngabenPflegen(angaben: EigeneAngaben): Promise<void> {
  return apiOhneInhalt('/api/eigene-angaben', { methode: 'PUT', rumpf: angaben });
}
