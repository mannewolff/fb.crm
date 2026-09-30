import { apiJson, apiOhneInhalt } from './client';
import { liste, objekt, text, zahl } from './verengen';

/**
 * Die Wege zu den Kommentaren eines Angebots: lesen, schreiben, aendern, loeschen (Issue #140).
 *
 * Die Typen sind die Gegenstuecke zu `AngebotKommentarResponse` und `AngebotKommentareResponse` im
 * Backend; aendert sich dort ein Feld, aendert es sich hier mit (CLAUDE-react.md). Jede Antwort
 * geht durch einen Parser: Was ueber das Netz kommt, ist `unknown`, bis es geprueft ist — kein
 * `as`.
 *
 * Ein eigenes Modul und kein Anhang an `api/angebote.ts`: Der Bereich laedt seine Kommentare selbst
 * und nicht mit dem Angebot (Plan #141, E10), die Wege gehoeren also nicht zum Angebot, sondern
 * neben es.
 *
 * Der Zeitpunkt bleibt hier der Text, den das Backend geschickt hat, und wird nicht zu `Date`
 * verengt: Gesetzt wird er in der Darstellung ueber `lib/zeitpunkt.ts`, und ein `Date` im
 * Fachobjekt waere ein zweiter Ort, an dem eine Zone entstehen kann.
 */

/** Ein Kommentar am Angebot (Kriterien 1 und 3). */
export interface Kommentar {
  readonly id: number;
  readonly text: string;
  /** Zeitpunkt der Anlage als Zeitstempel-Text, wie Jackson einen `Instant` schreibt. */
  readonly createdAt: string;
}

/** Die Kommentare eines Angebots, neuester zuerst (Kriterium 4). */
export interface Kommentare {
  readonly kommentare: readonly Kommentar[];
}

/** Verengt einen Kommentar oder scheitert. */
export function parseKommentar(wert: unknown): Kommentar {
  const kommentar = objekt(wert);
  return {
    id: zahl(kommentar.id),
    text: text(kommentar.text),
    createdAt: text(kommentar.createdAt),
  };
}

/** Verengt die Kommentare eines Angebots oder scheitert. */
export function parseKommentare(wert: unknown): Kommentare {
  const antwort = objekt(wert);
  return { kommentare: liste(antwort.kommentare).map(parseKommentar) };
}

function pfad(angebotId: number): string {
  return `/api/angebote/${String(angebotId)}/kommentare`;
}

/** Die Kommentare des Angebots, neuester zuerst (Kriterium 4). */
export function kommentareLesen(angebotId: number): Promise<Kommentare> {
  return apiJson(pfad(angebotId), { methode: 'GET' }, parseKommentare);
}

/**
 * Schreibt einen Kommentar; die Antwort traegt ihn samt Kennung und Zeitpunkt (Kriterium 2).
 *
 * Damit steht der neue Kommentar ohne zweiten Aufruf an seinem Platz — der Bereich muss die Liste
 * nicht neu laden.
 */
export function kommentarSchreiben(angebotId: number, text: string): Promise<Kommentar> {
  return apiJson(pfad(angebotId), { methode: 'POST', rumpf: { text } }, parseKommentar);
}

/** Aendert den Text eines Kommentars; der Zeitpunkt bleibt derselbe (Kriterium 9). */
export function kommentarAendern(
  angebotId: number,
  kommentarId: number,
  text: string,
): Promise<Kommentar> {
  return apiJson(
    `${pfad(angebotId)}/${String(kommentarId)}`,
    { methode: 'PUT', rumpf: { text } },
    parseKommentar,
  );
}

/** Loescht den Kommentar (Kriterium 10) — die Antwort traegt keinen Inhalt. */
export function kommentarLoeschen(angebotId: number, kommentarId: number): Promise<void> {
  return apiOhneInhalt(`${pfad(angebotId)}/${String(kommentarId)}`, { methode: 'DELETE' });
}
