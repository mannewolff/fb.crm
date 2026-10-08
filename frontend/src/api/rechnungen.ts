import { parseEinheit } from './angebote';
import type { Einheit } from './angebote';
import { apiJson, apiOhneInhalt } from './client';
import {
  FORMFEHLER,
  inHundertsteln,
  jaNein,
  liste,
  objekt,
  text,
  textOderNull,
  zahl,
  zahlOderNull,
} from './verengen';
import { alsRechnungszustand } from '../lib/rechnungszustand';
import type { Rechnungszustand } from '../lib/rechnungszustand';

/**
 * Alle Wege der Rechnung: die Liste, die Wahl „Neue Rechnung", der Entwurf und die gestellte
 * Rechnung samt ihrem Dokument, dazu der Abrechnungsstand eines Angebots (Plan #169, E11).
 *
 * Die Typen sind die Gegenstuecke zu `RechnungResponse`, `RechnungZeileResponse`,
 * `RechnungenUebersichtResponse`, `AbrechenbareAngeboteResponse`, `AngebotAbrechnungResponse`,
 * `BelegempfaengerResponse` und `BelegabsenderResponse` im Backend; aendert sich dort ein Feld,
 * aendert es sich hier mit (CLAUDE-react.md). Jede Antwort geht durch einen Parser: Was ueber das
 * Netz kommt, ist `unknown`, bis es geprueft ist — kein `as`.
 *
 * <b>Geld und Mengen tragen hier andere Namen als in der Antwort</b>, weil sie eine andere Einheit
 * tragen — genau wie in `api/angebote.ts`: Die Antwort schickt Dezimalzahlen, hier stehen sie als
 * <b>ganze Zahl</b>, die Mengen in Hundertsteln, das Geld in Cent (E5). Der Steuersatz folgt
 * derselben Regel wie in `api/rechnungseinstellungen.ts` und steht in Hundertstel-Prozent. Verengt
 * wird mit {@link inHundertsteln} und damit ueber die Ziffern, nicht ueber Gleitkomma; dieselben
 * Namen bei gewechselter Einheit waeren die gefaehrlichere Wahl — wer `brutto` fuer Euro haelt,
 * rechnet um den Faktor hundert daneben.
 *
 * Hinaus geht die Menge als <b>Dezimaltext</b> („80.00"). Jackson liest daraus ein `BigDecimal`;
 * eine Gleitkommazahl im Rumpf waere die eine Umwandlung, die die Rechnung in `lib/geld.ts`
 * vermeidet. Einzelpreis und Einheit gehen <b>nicht</b> hinaus: Beide sind an der Rechnung nicht
 * aenderbar, und sie einzureichen hiesse, dem Absender eine Angabe zu glauben, die die Anwendung
 * selbst kennt (`RechnungPositionRequest`).
 *
 * <b>Der Griff ist die Kennung der Angebotsposition</b> und nicht eine eigene der
 * Rechnungsposition: Die Maske zeigt jede Position des Angebots, und was sie zurueckschickt, haengt
 * an ihr (Plan #169, E2).
 *
 * Das <b>Dokument</b> holt diese Datei nicht, sie nennt nur seinen Weg
 * ({@link rechnungDokumentPfad}): Es geht als `attachment` hinaus und wird vom Browser gesichert,
 * nicht von der Oberflaeche gelesen — wie der Inhaltsweg einer Anlage in `api/anlagen.ts`.
 */

/** Eine Zeile der Liste aller Rechnungen (#160, Kriterium 1). */
export interface RechnungZeile {
  readonly id: number;
  /** Die Rechnungsnummer, oder `null` im Entwurf — sie entsteht erst mit dem Stellen. */
  readonly nummer: string | null;
  readonly firmaId: number;
  readonly firmaName: string;
  /** Tag (`YYYY-MM-DD`), wie das Backend ein `LocalDate` liefert. */
  readonly rechnungDatum: string;
  /** Bruttobetrag in ganzen Cent, vom Server gerechnet. */
  readonly bruttoInCent: number;
  readonly zustand: Rechnungszustand;
  /**
   * Die Art: `true` fuer eine nachgetragene Rechnung, die fb.crm nur mit ihren Eckdaten kennt
   * (#254). Sie hat einen eigenen Kennungsraum — `id` allein unterscheidet die Zeilen nicht mehr
   * (Plan #259, E21).
   */
  readonly nachgetragen: boolean;
  /** Ob ein Dokument herunterzuladen ist — fuer beide Arten genau dann, wenn eines hinterlegt ist (E18). */
  readonly dokument: boolean;
}

/** Alle Rechnungen, neueste zuerst — das Gegenstueck zu `RechnungenUebersichtResponse`. */
export interface RechnungenUebersicht {
  readonly rechnungen: readonly RechnungZeile[];
}

/** Ein Angebot, aus dem eine Rechnung entstehen darf (#160, Kriterium 2). */
export interface AbrechenbaresAngebot {
  readonly angebotId: number;
  readonly firmaId: number;
  readonly firmaName: string;
  readonly angebotDatum: string;
  /** Summe der offenen Mengen mal Einzelpreis in ganzen Cent, vom Server gerechnet. */
  readonly offenerBetragInCent: number;
}

/** Die Wahl „Neue Rechnung" — das Gegenstueck zu `AbrechenbareAngeboteResponse`. */
export interface AbrechenbareAngebote {
  readonly angebote: readonly AbrechenbaresAngebot[];
}

/** Eine Zeile der Entwurfsmaske: eine Position des Angebots mit ihrem Stand (Kriterium 4). */
export interface Rechnungsmaskenzeile {
  /** Die Kennung der Angebotsposition — der Griff, an dem die Maske ihre Angaben zurueckschickt. */
  readonly angebotPositionId: number;
  readonly bezeichnung: string;
  /** Die Einheit kommt vom Angebot und ist an der Rechnung nicht aenderbar. */
  readonly einheit: Einheit;
  /** Netto-Einzelpreis in ganzen Cent, mit dem diese Rechnung rechnet. */
  readonly einzelpreisInCent: number;
  /** Die Menge am Angebot, in Hundertsteln. */
  readonly angebotenInHundertsteln: number;
  /** Die Menge aus allen anderen Rechnungen dieses Angebots, in Hundertsteln. */
  readonly abgerechnetInHundertsteln: number;
  /** Die offene Menge ohne diese Rechnung, in Hundertsteln; nie kleiner als 0. */
  readonly offenInHundertsteln: number;
  /** Die Menge, die diese Rechnung abrechnet, in Hundertsteln. */
  readonly mengeInHundertsteln: number;
  /** Was mit dieser Menge zusammen zu viel waere, in Hundertsteln; sonst 0. */
  readonly ueberschreitungInHundertsteln: number;
}

/** Der Empfaenger, wie er beim Stellen galt (Kriterium 14) — eine Kopie, kein Verweis. */
export interface Belegempfaenger {
  readonly firma: string;
  readonly strasse: string | null;
  readonly plz: string | null;
  readonly ort: string | null;
  readonly land: string | null;
}

/** Die eigenen Angaben, wie sie beim Stellen galten (Kriterium 14) — eine Kopie, kein Verweis. */
export interface Belegabsender {
  readonly name: string;
  readonly berufsbezeichnung: string | null;
  readonly strasse: string | null;
  readonly plz: string | null;
  readonly ort: string | null;
  readonly land: string | null;
  readonly email: string | null;
  readonly telefon: string | null;
  readonly steuernummer: string | null;
  readonly umsatzsteuerId: string | null;
  readonly bankverbindung: string | null;
  readonly webadresse: string | null;
}

/** Eine Rechnung mit den Zeilen ihrer Maske (Kriterien 3 bis 10). */
export interface Rechnung {
  readonly id: number;
  readonly angebotId: number;
  readonly firmaId: number;
  readonly firmaName: string;
  readonly rechnungDatum: string;
  readonly leistungszeitraum: string | null;
  readonly zustand: Rechnungszustand;
  /** Die Rechnungsnummer, oder `null` im Entwurf. */
  readonly nummer: string | null;
  /** Der geltende Steuersatz in ganzen Hundertstel-Prozent — 19 Prozent sind 1900. */
  readonly steuersatzInHundertsteln: number;
  /** Netto-Summe in ganzen Cent, vom Server gerechnet. */
  readonly nettoInCent: number;
  /** Steuer auf die Netto-Summe in ganzen Cent, vom Server gerechnet. */
  readonly steuerInCent: number;
  /** Bruttobetrag in ganzen Cent, vom Server gerechnet. */
  readonly bruttoInCent: number;
  /** Das festgeschriebene Zahlungsziel in Tagen, oder `null` im Entwurf. */
  readonly zahlungszielTage: number | null;
  /** Kopie des Empfaengers, oder `null` im Entwurf. */
  readonly empfaenger: Belegempfaenger | null;
  /** Kopie der eigenen Angaben, oder `null` im Entwurf. */
  readonly absender: Belegabsender | null;
  readonly zeilen: readonly Rechnungsmaskenzeile[];
}

/**
 * Eine Position des Angebots mit ihrem Abrechnungsstand (Kriterium 26).
 *
 * `buchbar` und `angefallen` kommen aus der Zeiterfassung (Issue #193, Kriterien 7, 8, 11): Eine
 * Position traegt Stunden, wenn sie nach Aufwand in der Einheit Stunde abrechnet. An einer nicht
 * buchbaren Position ist `angefallenInHundertsteln` 0 und sagt nichts — die Spalte „Angefallen"
 * haengt deshalb an `buchbar` und nicht an der Zahl.
 */
export interface Abrechnungsposition {
  readonly angebotPositionId: number;
  readonly bezeichnung: string;
  readonly einheit: Einheit;
  readonly angebotenInHundertsteln: number;
  readonly abgerechnetInHundertsteln: number;
  readonly offenInHundertsteln: number;
  readonly ueberschreitungInHundertsteln: number;
  /** Ob auf die Position Arbeitszeit gebucht werden kann. */
  readonly buchbar: boolean;
  /** Die insgesamt erfassten Stunden, in Hundertsteln; 0 an einer nicht buchbaren Position. */
  readonly angefallenInHundertsteln: number;
}

/**
 * Eine Rechnung dieses Angebots.
 *
 * Ohne Firma: Am Angebot steht sie schon im Kopf, und eine zweite Angabe daneben waere dieselbe
 * Auskunft in jeder Zeile.
 */
export interface AngebotRechnungZeile {
  readonly id: number;
  readonly nummer: string | null;
  readonly rechnungDatum: string;
  readonly bruttoInCent: number;
  readonly zustand: Rechnungszustand;
}

/** Der Abrechnungsstand eines Angebots: Positionen und Rechnungen (Kriterium 26). */
export interface Angebotsabrechnung {
  readonly positionen: readonly Abrechnungsposition[];
  readonly rechnungen: readonly AngebotRechnungZeile[];
  /**
   * Die insgesamt erfassten Stunden ueber alle Positionen, in Hundertsteln; 0, wo keine gebucht
   * sind (Issue #231).
   *
   * Sie kommt vom Server und wird nicht aus den Zeilen summiert: Eine zweite Rechnung daneben liefe
   * beim ersten Filter der Liste auseinander.
   */
  readonly angefallenInHundertsteln: number;
}

/** Eine Angabe der Entwurfsmaske zu genau einer Angebotsposition. */
export interface AbrechnungsangabeEingabe {
  readonly angebotPositionId: number;
  readonly bezeichnung: string;
  /** Die Menge als Dezimaltext mit Punkt — „80.00", nie eine Gleitkommazahl. */
  readonly menge: string;
}

/** Die Rechnung als Ganzes — die Felder von `RechnungRequest`. */
export interface RechnungEingabe {
  /** Tag (`YYYY-MM-DD`). */
  readonly rechnungDatum: string;
  readonly leistungszeitraum: string;
  readonly positionen: readonly AbrechnungsangabeEingabe[];
}

function rechnungszustand(wert: unknown): Rechnungszustand {
  const zustand = alsRechnungszustand(wert);
  if (zustand === null) {
    throw new TypeError(FORMFEHLER);
  }
  return zustand;
}

function parseEmpfaenger(wert: unknown): Belegempfaenger | null {
  if (wert === null) {
    return null;
  }
  const empfaenger = objekt(wert);
  return {
    firma: text(empfaenger.firma),
    strasse: textOderNull(empfaenger.strasse),
    plz: textOderNull(empfaenger.plz),
    ort: textOderNull(empfaenger.ort),
    land: textOderNull(empfaenger.land),
  };
}

function parseAbsender(wert: unknown): Belegabsender | null {
  if (wert === null) {
    return null;
  }
  const absender = objekt(wert);
  return {
    name: text(absender.name),
    berufsbezeichnung: textOderNull(absender.berufsbezeichnung),
    strasse: textOderNull(absender.strasse),
    plz: textOderNull(absender.plz),
    ort: textOderNull(absender.ort),
    land: textOderNull(absender.land),
    email: textOderNull(absender.email),
    telefon: textOderNull(absender.telefon),
    steuernummer: textOderNull(absender.steuernummer),
    umsatzsteuerId: textOderNull(absender.umsatzsteuerId),
    bankverbindung: textOderNull(absender.bankverbindung),
    webadresse: textOderNull(absender.webadresse),
  };
}

function parseMaskenzeile(wert: unknown): Rechnungsmaskenzeile {
  const zeile = objekt(wert);
  return {
    angebotPositionId: zahl(zeile.angebotPositionId),
    bezeichnung: text(zeile.bezeichnung),
    einheit: parseEinheit(zeile.einheit),
    einzelpreisInCent: inHundertsteln(zeile.einzelpreis),
    angebotenInHundertsteln: inHundertsteln(zeile.angeboten),
    abgerechnetInHundertsteln: inHundertsteln(zeile.abgerechnet),
    offenInHundertsteln: inHundertsteln(zeile.offen),
    mengeInHundertsteln: inHundertsteln(zeile.menge),
    ueberschreitungInHundertsteln: inHundertsteln(zeile.ueberschreitung),
  };
}

/** Verengt eine Rechnung samt Maskenzeilen oder scheitert. */
export function parseRechnung(wert: unknown): Rechnung {
  const rechnung = objekt(wert);
  return {
    id: zahl(rechnung.id),
    angebotId: zahl(rechnung.angebotId),
    firmaId: zahl(rechnung.firmaId),
    firmaName: text(rechnung.firmaName),
    rechnungDatum: text(rechnung.rechnungDatum),
    leistungszeitraum: textOderNull(rechnung.leistungszeitraum),
    zustand: rechnungszustand(rechnung.zustand),
    nummer: textOderNull(rechnung.nummer),
    steuersatzInHundertsteln: inHundertsteln(rechnung.steuersatz),
    nettoInCent: inHundertsteln(rechnung.netto),
    steuerInCent: inHundertsteln(rechnung.steuer),
    bruttoInCent: inHundertsteln(rechnung.brutto),
    zahlungszielTage: zahlOderNull(rechnung.zahlungszielTage),
    empfaenger: parseEmpfaenger(rechnung.empfaenger),
    absender: parseAbsender(rechnung.absender),
    zeilen: liste(rechnung.zeilen).map(parseMaskenzeile),
  };
}

function parseUebersichtZeile(wert: unknown): RechnungZeile {
  const zeile = objekt(wert);
  return {
    id: zahl(zeile.id),
    nummer: textOderNull(zeile.nummer),
    firmaId: zahl(zeile.firmaId),
    firmaName: text(zeile.firmaName),
    rechnungDatum: text(zeile.rechnungDatum),
    bruttoInCent: inHundertsteln(zeile.brutto),
    zustand: rechnungszustand(zeile.zustand),
    nachgetragen: jaNein(zeile.nachgetragen),
    dokument: jaNein(zeile.dokument),
  };
}

/** Verengt die Liste aller Rechnungen oder scheitert. */
export function parseRechnungenUebersicht(wert: unknown): RechnungenUebersicht {
  const antwort = objekt(wert);
  return { rechnungen: liste(antwort.rechnungen).map(parseUebersichtZeile) };
}

function parseWahlZeile(wert: unknown): AbrechenbaresAngebot {
  const zeile = objekt(wert);
  return {
    angebotId: zahl(zeile.angebotId),
    firmaId: zahl(zeile.firmaId),
    firmaName: text(zeile.firmaName),
    angebotDatum: text(zeile.angebotDatum),
    offenerBetragInCent: inHundertsteln(zeile.offenerBetrag),
  };
}

/** Verengt die Wahl „Neue Rechnung" oder scheitert. */
export function parseAbrechenbareAngebote(wert: unknown): AbrechenbareAngebote {
  const antwort = objekt(wert);
  return { angebote: liste(antwort.angebote).map(parseWahlZeile) };
}

function parseAbrechnungsposition(wert: unknown): Abrechnungsposition {
  const zeile = objekt(wert);
  return {
    angebotPositionId: zahl(zeile.angebotPositionId),
    bezeichnung: text(zeile.bezeichnung),
    einheit: parseEinheit(zeile.einheit),
    angebotenInHundertsteln: inHundertsteln(zeile.angeboten),
    abgerechnetInHundertsteln: inHundertsteln(zeile.abgerechnet),
    offenInHundertsteln: inHundertsteln(zeile.offen),
    ueberschreitungInHundertsteln: inHundertsteln(zeile.ueberschreitung),
    buchbar: jaNein(zeile.buchbar),
    angefallenInHundertsteln: inHundertsteln(zeile.angefallen),
  };
}

function parseAngebotRechnungZeile(wert: unknown): AngebotRechnungZeile {
  const zeile = objekt(wert);
  return {
    id: zahl(zeile.id),
    nummer: textOderNull(zeile.nummer),
    rechnungDatum: text(zeile.rechnungDatum),
    bruttoInCent: inHundertsteln(zeile.brutto),
    zustand: rechnungszustand(zeile.zustand),
  };
}

/** Verengt den Abrechnungsstand eines Angebots oder scheitert. */
export function parseAngebotsabrechnung(wert: unknown): Angebotsabrechnung {
  const antwort = objekt(wert);
  return {
    positionen: liste(antwort.positionen).map(parseAbrechnungsposition),
    rechnungen: liste(antwort.rechnungen).map(parseAngebotRechnungZeile),
    angefallenInHundertsteln: inHundertsteln(antwort.angefallen),
  };
}

/** Der Weg zur einzelnen Rechnung. */
function pfad(id: number): string {
  return `/api/rechnungen/${String(id)}`;
}

/** Alle Rechnungen, neueste zuerst (Kriterium 1). */
export function rechnungenUebersicht(): Promise<RechnungenUebersicht> {
  return apiJson('/api/rechnungen', { methode: 'GET' }, parseRechnungenUebersicht);
}

/**
 * Die Angebote, aus denen eine Rechnung entstehen darf (Kriterium 2).
 *
 * Die Liste fuehrt, der Server entscheidet: Dass ein Angebot hier steht, ist eine Auskunft und
 * keine Zusage — das Anlegen prueft denselben Stand noch einmal und darf mit 409 abweisen.
 */
export function abrechenbareAngebote(): Promise<AbrechenbareAngebote> {
  return apiJson(
    '/api/rechnungen/abrechenbare-angebote',
    { methode: 'GET' },
    parseAbrechenbareAngebote,
  );
}

/**
 * Legt zum Angebot einen Rechnungsentwurf an (Kriterium 3; Issue #203, Plan A17).
 *
 * Datum, Leistungszeitraum und die vorbelegten Mengen entstehen im Anwendungsfall. Die Antwort
 * traegt den fertigen Entwurf — die Maske zeigt ihn ohne zweiten Aufruf.
 *
 * <b>Der Monat ist die einzige Angabe von aussen</b>: Mit ihm belegt der Anwendungsfall die Mengen
 * aus der Arbeitszeit dieses Monats vor (Issue #199). Ohne ihn geht <b>kein Rumpf</b> hinaus und
 * der Entwurf entsteht ohne Mengen — nicht ein Rumpf mit `null`, denn das waere dieselbe Auskunft
 * in einer Form, die die Schnittstelle nicht braucht.
 *
 * @param monat der Monat als `JJJJ-MM`, oder `undefined` fuer „ohne Arbeitszeit"
 */
export function rechnungAnlegen(angebotId: number, monat?: string): Promise<Rechnung> {
  return apiJson(
    `/api/angebote/${String(angebotId)}/rechnungen`,
    { methode: 'POST', rumpf: monat === undefined ? undefined : { monat } },
    parseRechnung,
  );
}

/** Liest eine Rechnung samt den Zeilen ihrer Maske. */
export function rechnungLesen(id: number): Promise<Rechnung> {
  return apiJson(pfad(id), { methode: 'GET' }, parseRechnung);
}

/**
 * Aendert den Entwurf als Ganzes (Kriterien 4, 5, 10).
 *
 * Die Antwort traegt die neu gerechneten Werte — Netto, Steuer, Brutto und je Zeile offen und
 * Ueberschreitung. Die Maske zeigt sie ohne zweiten Aufruf.
 */
export function rechnungAendern(id: number, eingabe: RechnungEingabe): Promise<Rechnung> {
  return apiJson(pfad(id), { methode: 'PUT', rumpf: eingabe }, parseRechnung);
}

/** Loescht den Entwurf (Kriterium 12) — die Antwort traegt keinen Inhalt. */
export function rechnungLoeschen(id: number): Promise<void> {
  return apiOhneInhalt(pfad(id), { methode: 'DELETE' });
}

/** Stellt den Entwurf; die Antwort traegt die gestellte Rechnung (Kriterien 13 bis 16). */
export function rechnungStellen(id: number): Promise<Rechnung> {
  return apiJson(`${pfad(id)}/stellen`, { methode: 'POST' }, parseRechnung);
}

/**
 * Stellt die gestellte Rechnung auf bezahlt, abgeschrieben oder zurueck auf gestellt (Issue #253).
 *
 * Ein Weg fuer alle drei Ziele, wie im Backend: Das Ziel geht im Rumpf hinaus, und die Antwort traegt
 * die Rechnung mit ihrem neuen Zustand — die Seite zeigt ihn ohne zweiten Aufruf. Ein unzulaessiger
 * Uebergang kommt als 409 zurueck; welcher zulaessig ist, entscheidet der Server, nicht diese Datei.
 */
export function setzeRechnungszustand(id: number, zustand: Rechnungszustand): Promise<Rechnung> {
  return apiJson(`${pfad(id)}/zustand`, { methode: 'PUT', rumpf: { zustand } }, parseRechnung);
}

/**
 * Der Weg zum archivierten Dokument einer gestellten Rechnung (Kriterium 24).
 *
 * Nur der Weg und kein Abruf: Die Antwort geht mit `Content-Disposition: attachment` hinaus, und
 * ein `<a download>` darauf ist genau das, was sie verlangt. Ein Entwurf hat kein Dokument — der
 * Weg antwortet dort mit 409; die Oberflaeche bietet ihn deshalb erst ab „gestellt" an.
 */
export function rechnungDokumentPfad(id: number): string {
  return `${pfad(id)}/dokument`;
}

/** Der Abrechnungsstand des Angebots samt seinen Rechnungen (Kriterium 26). */
export function angebotAbrechnung(angebotId: number): Promise<Angebotsabrechnung> {
  return apiJson(
    `/api/angebote/${String(angebotId)}/abrechnung`,
    { methode: 'GET' },
    parseAngebotsabrechnung,
  );
}
