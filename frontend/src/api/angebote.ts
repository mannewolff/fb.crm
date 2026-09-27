import { apiJson, apiOhneInhalt } from './client';
import { FORMFEHLER, inHundertsteln, liste, objekt, text, textOderNull, zahl } from './verengen';
import type { Angebotsstand } from '../lib/angebotsstand';

/**
 * Die Wege zum Angebot: die Liste am Vorgang, das Anlegen, das Fortschreiben eines Entwurfs, das
 * Verwerfen, das Versenden, die Reaktion des Kunden und der Beleg.
 *
 * Die Typen sind die Gegenstuecke zu `AngebotResponse`, `AngebotPositionResponse`,
 * `AngebotZeileResponse` und `VorgangAngeboteResponse` im Backend; aendert sich dort ein Feld,
 * aendert es sich hier mit (CLAUDE-react.md). Jede Antwort geht durch einen Parser: Was ueber das
 * Netz kommt, ist `unknown`, bis es geprueft ist — kein `as`.
 *
 * <b>Geld und Menge tragen hier andere Namen als in der Antwort</b>, weil sie eine andere Einheit
 * tragen: Die Antwort schickt `menge`, `einzelpreis`, `betrag` und `summe` als Dezimalzahl, hier
 * stehen sie als <b>ganze Zahl</b> — die Menge in Hundertsteln, das Geld in Cent (E5). Der Schritt
 * geschieht beim Verengen und nicht in der Ansicht: Er kann scheitern (eine dritte
 * Nachkommastelle ist kein Betrag), und ein Fehlschlag gehoert an die Systemgrenze, nicht in die
 * Darstellung. Dieselben Namen bei gewechselter Einheit waeren die gefaehrlichere Wahl — ein
 * Aufrufer, der `summe` fuer Euro haelt, rechnet um den Faktor hundert daneben.
 *
 * Umgerechnet wird mit {@link inHundertsteln} und damit ueber die Ziffern, nicht ueber Gleitkomma:
 * `wert * 100` waere fuer jeden Betrag dieser Anwendung genau genug, aber es waere derselbe Weg,
 * den `lib/geld.ts` fuer die Rechnung ausdruecklich ausschliesst — zwei Regeln fuer dasselbe Geld
 * laufen auseinander.
 *
 * Hinaus gehen Menge und Preis als <b>Dezimaltext</b> („2.50"). Jackson liest daraus ein
 * `BigDecimal`; eine Gleitkommazahl im Rumpf waere die eine Umwandlung, die die Rechnung in
 * `lib/geld.ts` vermeidet.
 *
 * Fuer den Beleg steht hier nur ein <b>Pfad</b> und kein Aufruf (E17), wie bei `anhangPfad` am
 * Vorgang: Der Browser oeffnet das PDF selbst, mit dem Sitzungs-Cookie und dem
 * `Content-Disposition` des Servers.
 */

/** Wie eine Position abgerechnet wird (`Abrechnungsmodus` im Backend, Kriterium 4). */
export type Abrechnungsmodus = 'AUFWAND' | 'FESTPREIS';

/** Die Einheit der Menge (`Einheit` im Backend, Kriterium 4, F7). */
export type Einheit = 'STUNDE' | 'PERSONENTAG' | 'PAUSCHAL';

/** Eine Position des Angebots, wie die Ansicht sie zeigt (Kriterien 4, 5). */
export interface AngebotPosition {
  readonly bezeichnung: string;
  readonly abrechnungsmodus: Abrechnungsmodus;
  /** Menge in ganzen Hundertsteln — „2,5 Personentage" sind 250. */
  readonly mengeInHundertsteln: number;
  readonly einheit: Einheit;
  /** Netto-Einzelpreis in ganzen Cent. */
  readonly einzelpreisInCent: number;
  /** Netto-Betrag der Position in ganzen Cent, vom Server gerechnet (E5). */
  readonly betragInCent: number;
}

/** Ein Angebot mit seinen Positionen (Kriterien 5, 18). */
export interface Angebot {
  readonly id: number;
  readonly vorgangId: number;
  /** Angebotsnummer, oder `null` im Entwurf (Kriterium 11). */
  readonly nummer: string | null;
  readonly stand: Angebotsstand;
  /** Tag (`YYYY-MM-DD`), wie das Backend ein `LocalDate` liefert. */
  readonly angebotDatum: string;
  readonly gueltigBis: string;
  readonly leistungsbeschreibung: string | null;
  readonly zahlungsbedingungen: string | null;
  /** Zeitpunkt in UTC, oder `null` im Entwurf. */
  readonly versendetAm: string | null;
  readonly reaktionAm: string | null;
  readonly positionen: readonly AngebotPosition[];
  /** Netto-Summe in ganzen Cent, vom Server gerechnet (E5). */
  readonly summeInCent: number;
}

/** Eine Zeile der Angebotsliste am Vorgang (Kriterium 20). */
export interface AngebotZeile {
  readonly id: number;
  readonly nummer: string | null;
  readonly stand: Angebotsstand;
  readonly angebotDatum: string;
  readonly gueltigBis: string;
  readonly summeInCent: number;
}

/** Die Angebote eines Vorgangs, Entwuerfe zuerst (Kriterium 20, E25). */
export interface VorgangAngebote {
  readonly angebote: readonly AngebotZeile[];
}

/** Eine Position, wie die Maske sie einreicht — die Felder von `AngebotPositionRequest`. */
export interface PositionEingabe {
  readonly bezeichnung: string;
  readonly abrechnungsmodus: Abrechnungsmodus;
  /** Menge als Dezimaltext mit Punkt — „2.50", nie eine Gleitkommazahl. */
  readonly menge: string;
  readonly einheit: Einheit;
  /** Netto-Einzelpreis als Dezimaltext mit Punkt — „1000.01". */
  readonly einzelpreis: string;
}

/** Der Entwurf als Ganzes — die Felder von `AngebotEntwurfRequest` (E8). */
export interface EntwurfEingabe {
  readonly gueltigBis: string;
  readonly leistungsbeschreibung: string | null;
  readonly zahlungsbedingungen: string | null;
  readonly positionen: readonly PositionEingabe[];
}

function angebotsstand(wert: unknown): Angebotsstand {
  if (
    wert !== 'ENTWURF' &&
    wert !== 'VERSENDET' &&
    wert !== 'ABGELAUFEN' &&
    wert !== 'ANGENOMMEN' &&
    wert !== 'ABGELEHNT' &&
    wert !== 'ABGELOEST'
  ) {
    throw new TypeError(FORMFEHLER);
  }
  return wert;
}

function abrechnungsmodus(wert: unknown): Abrechnungsmodus {
  if (wert !== 'AUFWAND' && wert !== 'FESTPREIS') {
    throw new TypeError(FORMFEHLER);
  }
  return wert;
}

function einheit(wert: unknown): Einheit {
  if (wert !== 'STUNDE' && wert !== 'PERSONENTAG' && wert !== 'PAUSCHAL') {
    throw new TypeError(FORMFEHLER);
  }
  return wert;
}

function parsePosition(wert: unknown): AngebotPosition {
  const position = objekt(wert);
  return {
    bezeichnung: text(position.bezeichnung),
    abrechnungsmodus: abrechnungsmodus(position.abrechnungsmodus),
    mengeInHundertsteln: inHundertsteln(position.menge),
    einheit: einheit(position.einheit),
    einzelpreisInCent: inHundertsteln(position.einzelpreis),
    betragInCent: inHundertsteln(position.betrag),
  };
}

/** Verengt ein Angebot samt Positionen oder scheitert. */
export function parseAngebot(wert: unknown): Angebot {
  const angebot = objekt(wert);
  return {
    id: zahl(angebot.id),
    vorgangId: zahl(angebot.vorgangId),
    nummer: textOderNull(angebot.nummer),
    stand: angebotsstand(angebot.stand),
    angebotDatum: text(angebot.angebotDatum),
    gueltigBis: text(angebot.gueltigBis),
    leistungsbeschreibung: textOderNull(angebot.leistungsbeschreibung),
    zahlungsbedingungen: textOderNull(angebot.zahlungsbedingungen),
    versendetAm: textOderNull(angebot.versendetAm),
    reaktionAm: textOderNull(angebot.reaktionAm),
    positionen: liste(angebot.positionen).map(parsePosition),
    summeInCent: inHundertsteln(angebot.summe),
  };
}

function parseZeile(wert: unknown): AngebotZeile {
  const zeile = objekt(wert);
  return {
    id: zahl(zeile.id),
    nummer: textOderNull(zeile.nummer),
    stand: angebotsstand(zeile.stand),
    angebotDatum: text(zeile.angebotDatum),
    gueltigBis: text(zeile.gueltigBis),
    summeInCent: inHundertsteln(zeile.summe),
  };
}

/** Verengt die Angebotsliste eines Vorgangs oder scheitert. */
export function parseVorgangAngebote(wert: unknown): VorgangAngebote {
  const antwort = objekt(wert);
  return { angebote: liste(antwort.angebote).map(parseZeile) };
}

/** Die Angebote des Vorgangs, Entwuerfe zuerst (Kriterium 20). */
export function angeboteDesVorgangs(vorgangId: number): Promise<VorgangAngebote> {
  return apiJson(
    `/api/vorgaenge/${String(vorgangId)}/angebote`,
    { methode: 'GET' },
    parseVorgangAngebote,
  );
}

/**
 * Legt am Vorgang einen Angebotsentwurf an (Kriterien 2, 3, 8).
 *
 * Der Rumpf geht immer hinaus, auch ohne Vorlage: Das Backend nimmt ihn optional
 * (`@RequestBody(required = false)`), aber ein Aufruf mit stets derselben Form hat nur einen Weg
 * statt zweier, die auseinanderlaufen koennen.
 */
export function angebotAnlegen(
  vorgangId: number,
  vorlageAngebotId: number | null,
): Promise<Angebot> {
  return apiJson(
    `/api/vorgaenge/${String(vorgangId)}/angebote`,
    { methode: 'POST', rumpf: { vorlageAngebotId } },
    parseAngebot,
  );
}

/** Liest ein Angebot samt Positionen (Kriterien 5, 18). */
export function angebotLesen(id: number): Promise<Angebot> {
  return apiJson(`/api/angebote/${String(id)}`, { methode: 'GET' }, parseAngebot);
}

/**
 * Schreibt den Entwurf als Ganzes fort (Kriterium 6, E8).
 *
 * Die Antwort traegt das Angebot mit den neu gerechneten Betraegen — die Maske zeigt sie ohne
 * zweiten Aufruf.
 */
export function angebotAendern(id: number, eingabe: EntwurfEingabe): Promise<Angebot> {
  return apiJson(`/api/angebote/${String(id)}`, { methode: 'PUT', rumpf: eingabe }, parseAngebot);
}

/** Verwirft den Entwurf samt seinen Positionen (Kriterium 7, E19); die Antwort traegt nichts. */
export function angebotVerwerfen(id: number): Promise<void> {
  return apiOhneInhalt(`/api/angebote/${String(id)}`, { methode: 'DELETE' });
}

/** Macht aus dem Entwurf ein festes Dokument (Kriterien 10 bis 16). */
export function angebotVersenden(id: number): Promise<Angebot> {
  return apiJson(`/api/angebote/${String(id)}/versenden`, { methode: 'POST' }, parseAngebot);
}

/** Haelt die Zusage des Kunden fest (Kriterium 17). */
export function angebotAnnehmen(id: number): Promise<Angebot> {
  return apiJson(`/api/angebote/${String(id)}/annehmen`, { methode: 'POST' }, parseAngebot);
}

/** Haelt die Absage des Kunden fest (Kriterium 17). */
export function angebotAblehnen(id: number): Promise<Angebot> {
  return apiJson(`/api/angebote/${String(id)}/ablehnen`, { methode: 'POST' }, parseAngebot);
}

/**
 * Der Weg zum Beleg — ein Pfad, kein Aufruf (E17, Kriterium 14).
 *
 * Er gehoert in ein `href` mit `target="_blank" rel="noopener"`. Der Server liefert das PDF mit
 * `Content-Disposition: inline`, also zeigt der Browser es an, statt es zu speichern.
 */
export function angebotPdfPfad(id: number): string {
  return `/api/angebote/${String(id)}/pdf`;
}
