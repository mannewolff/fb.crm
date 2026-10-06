import { apiJson, apiOhneInhalt } from './client';
import { FORMFEHLER, inHundertsteln, jaNein, objekt, text, zahl } from './verengen';
import { alsRechnungszustand } from '../lib/rechnungszustand';
import type { Rechnungszustand } from '../lib/rechnungszustand';

/**
 * Alle Wege der nachgetragenen Rechnung (#254; Plan #259, E11, E21): anlegen, lesen, aendern,
 * loeschen, den Zustand setzen und das Original ablegen, herunterladen und entfernen.
 *
 * Ein eigenes Modul neben `api/rechnungen.ts`, wie am Server ein eigener Weg neben
 * `/api/rechnungen` (E21): Die Kennungsraeume der beiden Rechnungsarten sind getrennt, und eine
 * Funktion, die je nach Art einen anderen Weg naehme, waere die Verwechslung, die der Plan
 * ausschliesst.
 *
 * Der Typ ist das Gegenstueck zu `NachtragResponse`, die Eingabe zu `NachtragRequest`; aendert sich
 * dort ein Feld, aendert es sich hier mit (CLAUDE-react.md). Jede Antwort geht durch einen Parser —
 * was ueber das Netz kommt, ist `unknown`, bis es geprueft ist.
 *
 * <b>Geld traegt hier andere Namen als in der Antwort</b>, mit derselben Begruendung wie in
 * `api/rechnungen.ts`: Die Antwort schickt Dezimalzahlen, hier stehen sie als ganze Cent, verengt
 * ueber {@link inHundertsteln} und damit ueber die Ziffern, nicht ueber Gleitkomma. Hinaus geht der
 * Betrag als <b>Dezimaltext</b> („1190.60"); Jackson liest daraus ein `BigDecimal`.
 *
 * <b>Angaben und Original gehen in zwei Aufrufen</b> (E11): erst {@link nachtragAnlegen} bzw.
 * {@link nachtragAendern} als JSON, dann {@link nachtragDokumentAblegen} als `multipart`. Das
 * Original holt diese Datei nicht, sie nennt nur seinen Weg ({@link nachtragDokumentPfad}) — wie
 * `rechnungDokumentPfad`.
 */

/** Eine nachgetragene Rechnung, wie die Oberflaeche sie sieht (Kriterien 2, 7, 8, 11). */
export interface Nachtrag {
  readonly id: number;
  readonly firmaId: number;
  /** Der heutige Name der Firma — die Rechnung verweist auf sie (Kriterium 11). */
  readonly firmaName: string;
  readonly nummer: string;
  /** Tag (`YYYY-MM-DD`), wie das Backend ein `LocalDate` liefert. */
  readonly rechnungDatum: string;
  /** Nettobetrag in ganzen Cent, wie erfasst. */
  readonly nettoInCent: number;
  /** Bruttobetrag in ganzen Cent, wie erfasst. */
  readonly bruttoInCent: number;
  readonly zustand: Rechnungszustand;
  /** Ob ein Original hinterlegt ist; der Schluessel im Speicher geht nicht nach aussen. */
  readonly dokument: boolean;
}

/** Die Eckdaten, wie die Maske sie schickt — die Felder von `NachtragRequest`. */
export interface NachtragEingabe {
  readonly firmaId: number;
  readonly nummer: string;
  /** Tag (`YYYY-MM-DD`). */
  readonly rechnungDatum: string;
  /** Der Nettobetrag als Dezimaltext mit Punkt — „1000.50", nie eine Gleitkommazahl. */
  readonly netto: string;
  /** Der Bruttobetrag als Dezimaltext mit Punkt. */
  readonly brutto: string;
}

function rechnungszustand(wert: unknown): Rechnungszustand {
  const zustand = alsRechnungszustand(wert);
  if (zustand === null) {
    throw new TypeError(FORMFEHLER);
  }
  return zustand;
}

/** Verengt eine nachgetragene Rechnung oder scheitert. */
export function parseNachtrag(wert: unknown): Nachtrag {
  const nachtrag = objekt(wert);
  return {
    id: zahl(nachtrag.id),
    firmaId: zahl(nachtrag.firmaId),
    firmaName: text(nachtrag.firmaName),
    nummer: text(nachtrag.nummer),
    rechnungDatum: text(nachtrag.rechnungDatum),
    nettoInCent: inHundertsteln(nachtrag.netto),
    bruttoInCent: inHundertsteln(nachtrag.brutto),
    zustand: rechnungszustand(nachtrag.zustand),
    dokument: jaNein(nachtrag.dokument),
  };
}

const WURZEL = '/api/nachgetragene-rechnungen';

/** Der Weg zur einzelnen nachgetragenen Rechnung. */
function pfad(id: number): string {
  return `${WURZEL}/${String(id)}`;
}

/** Traegt eine Rechnung nach (Kriterium 2); die Antwort traegt sie samt Kennung. */
export function nachtragAnlegen(eingabe: NachtragEingabe): Promise<Nachtrag> {
  return apiJson(WURZEL, { methode: 'POST', rumpf: eingabe }, parseNachtrag);
}

/** Liest eine nachgetragene Rechnung samt dem heutigen Namen ihrer Firma. */
export function nachtragLesen(id: number): Promise<Nachtrag> {
  return apiJson(pfad(id), { methode: 'GET' }, parseNachtrag);
}

/** Aendert die Eckdaten in jedem Zustand (Kriterium 10); die Antwort traegt den neuen Stand. */
export function nachtragAendern(id: number, eingabe: NachtragEingabe): Promise<Nachtrag> {
  return apiJson(pfad(id), { methode: 'PUT', rumpf: eingabe }, parseNachtrag);
}

/** Loescht die Rechnung samt ihrem Original (Kriterium 10) — die Antwort traegt keinen Inhalt. */
export function nachtragLoeschen(id: number): Promise<void> {
  return apiOhneInhalt(pfad(id), { methode: 'DELETE' });
}

/**
 * Stellt den Zustand um, mit demselben Rumpf wie bei der gestellten Rechnung (Kriterium 8, E24).
 *
 * Welcher Uebergang zulaessig ist, entscheidet der Server; ein unzulaessiger kommt als 409 zurueck.
 */
export function setzeNachtragszustand(id: number, zustand: Rechnungszustand): Promise<Nachtrag> {
  return apiJson(`${pfad(id)}/zustand`, { methode: 'PUT', rumpf: { zustand } }, parseNachtrag);
}

/**
 * Der Weg zum hinterlegten Original (Kriterium 7).
 *
 * Nur der Weg und kein Abruf: Die Antwort geht mit `Content-Disposition: attachment` hinaus, und
 * ein `<a download>` darauf ist genau das, was sie verlangt. Ohne Original antwortet er mit 404 —
 * die Oberflaeche bietet ihn deshalb nur an, wo `dokument` wahr ist.
 */
export function nachtragDokumentPfad(id: number): string {
  return `${pfad(id)}/dokument`;
}

/**
 * Legt das Original ab oder ersetzt es (Kriterien 2 und 10); die Antwort traegt den neuen Stand.
 *
 * Der Teil heisst `datei`, wie ihn `NachgetrageneRechnungController` erwartet. Ob die Datei ein
 * PDF ist, entscheidet der Server am Inhalt (E12) — was die Maske vorher prueft, fuehrt nur.
 */
export function nachtragDokumentAblegen(id: number, datei: File): Promise<Nachtrag> {
  const formular = new FormData();
  formular.append('datei', datei);
  return apiJson(nachtragDokumentPfad(id), { methode: 'POST', formular }, parseNachtrag);
}

/** Entfernt das Original; die Rechnung bleibt (Kriterium 10). */
export function nachtragDokumentEntfernen(id: number): Promise<void> {
  return apiOhneInhalt(nachtragDokumentPfad(id), { methode: 'DELETE' });
}
