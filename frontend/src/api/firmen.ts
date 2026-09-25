import { apiJson, apiOhneInhalt } from './client';

/**
 * Die Wege zu Firmen und Ansprechpartnern.
 *
 * Die Typen sind die Gegenstuecke zu `FirmenUebersichtResponse`, `FirmaZeileResponse`,
 * `FirmaResponse` und `AnsprechpartnerResponse` im Backend; aendert sich dort ein Feld, aendert es
 * sich hier mit (CLAUDE-react.md). Jede Antwort geht durch einen Parser: Was ueber das Netz kommt,
 * ist `unknown`, bis es geprueft ist — kein `as`.
 *
 * Einen eigenen Leseweg fuer Ansprechpartner gibt es nicht: Sie kommen eingebettet in der
 * Detailantwort ihrer Firma (E7). Aus demselben Grund traegt die Eingabe des Ansprechpartners kein
 * Feld fuer die Firma — die steht im Pfad, und Umhaengen gibt es nicht (Kriterium 12).
 */

const FORMFEHLER = 'Die Antwort der Schnittstelle hat nicht die erwartete Form.';

/** Eine Zeile der Uebersicht. */
export interface FirmaZeile {
  readonly id: number;
  readonly name: string;
  readonly ort: string | null;
  readonly aktiveAnsprechpartner: number;
  readonly aktiv: boolean;
}

/**
 * Die Uebersicht: die gefundenen Zeilen und die Zahl aller Firmen.
 *
 * `gesamt` trennt drei Lagen, die eine leere Liste sonst nicht auseinanderhaelt: noch keine Firma,
 * nichts gefunden, alle stillgelegt (E5).
 */
export interface FirmenUebersicht {
  readonly firmen: readonly FirmaZeile[];
  readonly gesamt: number;
}

/** Ein Ansprechpartner, so wie er in der Antwort seiner Firma steht. */
export interface Ansprechpartner {
  readonly id: number;
  readonly vorname: string | null;
  readonly nachname: string;
  readonly rolle: string | null;
  readonly email: string | null;
  readonly telefonFestnetz: string | null;
  readonly telefonMobil: string | null;
  readonly aktiv: boolean;
}

/** Eine Firma mit allen Angaben und ihren Ansprechpartnern. */
export interface Firma {
  readonly id: number;
  readonly name: string;
  readonly strasse: string | null;
  readonly plz: string | null;
  readonly ort: string | null;
  readonly land: string | null;
  readonly steuernummer: string | null;
  readonly umsatzsteuerId: string | null;
  readonly aktiv: boolean;
  readonly ansprechpartner: readonly Ansprechpartner[];
}

/** Die Eingaben der Firma-Maske — die Felder von `FirmaRequest` im Backend. */
export interface FirmaEingabe {
  readonly name: string;
  readonly strasse: string | null;
  readonly plz: string | null;
  readonly ort: string | null;
  readonly land: string | null;
  readonly steuernummer: string | null;
  readonly umsatzsteuerId: string | null;
}

/** Die Eingaben der Ansprechpartner-Maske — die Felder von `AnsprechpartnerRequest`. */
export interface AnsprechpartnerEingabe {
  readonly vorname: string | null;
  readonly nachname: string;
  readonly rolle: string | null;
  readonly email: string | null;
  readonly telefonFestnetz: string | null;
  readonly telefonMobil: string | null;
}

function istObjekt(wert: unknown): wert is Record<string, unknown> {
  return typeof wert === 'object' && wert !== null;
}

function objekt(wert: unknown): Record<string, unknown> {
  if (!istObjekt(wert)) {
    throw new TypeError(FORMFEHLER);
  }
  return wert;
}

function liste(wert: unknown): readonly unknown[] {
  if (!Array.isArray(wert)) {
    throw new TypeError(FORMFEHLER);
  }
  return wert;
}

function zahl(wert: unknown): number {
  if (typeof wert !== 'number') {
    throw new TypeError(FORMFEHLER);
  }
  return wert;
}

function text(wert: unknown): string {
  if (typeof wert !== 'string') {
    throw new TypeError(FORMFEHLER);
  }
  return wert;
}

/** Eine Angabe, die fehlen darf — dann steht dort `null`, nie ein Platzhalter. */
function textOderNull(wert: unknown): string | null {
  return wert === null ? null : text(wert);
}

function jaNein(wert: unknown): boolean {
  if (typeof wert !== 'boolean') {
    throw new TypeError(FORMFEHLER);
  }
  return wert;
}

/** Verengt eine Zeile der Uebersicht oder scheitert. */
function parseFirmaZeile(wert: unknown): FirmaZeile {
  const zeile = objekt(wert);
  return {
    id: zahl(zeile.id),
    name: text(zeile.name),
    ort: textOderNull(zeile.ort),
    aktiveAnsprechpartner: zahl(zeile.aktiveAnsprechpartner),
    aktiv: jaNein(zeile.aktiv),
  };
}

/** Verengt die Antwort der Uebersicht oder scheitert. */
export function parseFirmenUebersicht(wert: unknown): FirmenUebersicht {
  const antwort = objekt(wert);
  return {
    firmen: liste(antwort.firmen).map(parseFirmaZeile),
    gesamt: zahl(antwort.gesamt),
  };
}

/** Verengt einen Ansprechpartner oder scheitert. */
export function parseAnsprechpartner(wert: unknown): Ansprechpartner {
  const partner = objekt(wert);
  return {
    id: zahl(partner.id),
    vorname: textOderNull(partner.vorname),
    nachname: text(partner.nachname),
    rolle: textOderNull(partner.rolle),
    email: textOderNull(partner.email),
    telefonFestnetz: textOderNull(partner.telefonFestnetz),
    telefonMobil: textOderNull(partner.telefonMobil),
    aktiv: jaNein(partner.aktiv),
  };
}

/** Verengt eine Firma samt ihrer Ansprechpartner oder scheitert. */
export function parseFirma(wert: unknown): Firma {
  const firma = objekt(wert);
  return {
    id: zahl(firma.id),
    name: text(firma.name),
    strasse: textOderNull(firma.strasse),
    plz: textOderNull(firma.plz),
    ort: textOderNull(firma.ort),
    land: textOderNull(firma.land),
    steuernummer: textOderNull(firma.steuernummer),
    umsatzsteuerId: textOderNull(firma.umsatzsteuerId),
    aktiv: jaNein(firma.aktiv),
    ansprechpartner: liste(firma.ansprechpartner).map(parseAnsprechpartner),
  };
}

/**
 * Die Uebersicht (Kriterien 2, 3, 13).
 *
 * Suchtext und Schalter gehen ueber `URLSearchParams` in die Adresse: Ein Name mit `&` oder `%`
 * waere in einer zusammengesetzten Zeichenkette ein zweiter Parameter statt ein Suchtext.
 *
 * Als einziger Weg dieses Moduls nimmt die Uebersicht ein Abbruchsignal: Sie ist der einzige, den
 * die Oberflaeche waehrend des Tippens mehrfach anstoesst (Issue #46).
 */
export function firmenUebersicht(
  suche: string,
  auchStillgelegte: boolean,
  signal?: AbortSignal,
): Promise<FirmenUebersicht> {
  const parameter = new URLSearchParams({
    suche,
    auchStillgelegte: String(auchStillgelegte),
  });
  return apiJson(
    `/api/firmen?${parameter.toString()}`,
    { methode: 'GET', signal },
    parseFirmenUebersicht,
  );
}

/** Legt eine Firma an; die Antwort traegt die Kennung fuer den Weg zur Detailansicht (Kriterium 7). */
export function firmaAnlegen(eingabe: FirmaEingabe): Promise<Firma> {
  return apiJson('/api/firmen', { methode: 'POST', rumpf: eingabe }, parseFirma);
}

/** Liest die Detailansicht samt Ansprechpartnern (Kriterien 5, 10). */
export function firmaLesen(id: number): Promise<Firma> {
  return apiJson(`/api/firmen/${id}`, { methode: 'GET' }, parseFirma);
}

/** Schreibt die Angaben fort (Kriterium 8); die Antwort traegt keinen Rumpf. */
export function firmaAendern(id: number, eingabe: FirmaEingabe): Promise<void> {
  return apiOhneInhalt(`/api/firmen/${id}`, { methode: 'PUT', rumpf: eingabe });
}

/** Legt die Firma still (Kriterium 13). */
export function firmaStilllegen(id: number): Promise<void> {
  return apiOhneInhalt(`/api/firmen/${id}/stilllegen`, { methode: 'POST' });
}

/** Nimmt die Firma wieder in Betrieb (Kriterium 13). */
export function firmaAktivieren(id: number): Promise<void> {
  return apiOhneInhalt(`/api/firmen/${id}/aktivieren`, { methode: 'POST' });
}

/** Legt einen Ansprechpartner unter der Firma an (Kriterium 9). */
export function ansprechpartnerAnlegen(
  firmaId: number,
  eingabe: AnsprechpartnerEingabe,
): Promise<Ansprechpartner> {
  return apiJson(
    `/api/firmen/${firmaId}/ansprechpartner`,
    { methode: 'POST', rumpf: eingabe },
    parseAnsprechpartner,
  );
}

/** Schreibt die Angaben des Ansprechpartners fort (Kriterium 11). */
export function ansprechpartnerAendern(
  firmaId: number,
  id: number,
  eingabe: AnsprechpartnerEingabe,
): Promise<void> {
  return apiOhneInhalt(`/api/firmen/${firmaId}/ansprechpartner/${id}`, {
    methode: 'PUT',
    rumpf: eingabe,
  });
}

/** Legt den Ansprechpartner still (Kriterium 15). */
export function ansprechpartnerStilllegen(firmaId: number, id: number): Promise<void> {
  return apiOhneInhalt(`/api/firmen/${firmaId}/ansprechpartner/${id}/stilllegen`, {
    methode: 'POST',
  });
}

/** Nimmt den Ansprechpartner wieder in Betrieb (Kriterium 15). */
export function ansprechpartnerAktivieren(firmaId: number, id: number): Promise<void> {
  return apiOhneInhalt(`/api/firmen/${firmaId}/ansprechpartner/${id}/aktivieren`, {
    methode: 'POST',
  });
}
