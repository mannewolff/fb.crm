import { apiJson } from './client';
import {
  FORMFEHLER,
  inHundertsteln,
  liste,
  objekt,
  text,
  textOderNull,
  zahl,
  zahlOderNull,
} from './verengen';
import { alsAngebotsstatus } from '../lib/angebotsstatus';
import type { Angebotsstatus } from '../lib/angebotsstatus';

/**
 * Die Wege zum Angebot: die Liste an der Firma, das Anlegen, das Lesen, das Aendern und der
 * Statuswechsel (Issue #127).
 *
 * Die Typen sind die Gegenstuecke zu `AngebotResponse`, `AngebotPositionResponse`,
 * `AngebotZeileResponse` und `FirmaAngeboteResponse` im Backend; aendert sich dort ein Feld,
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

/** Ein Angebot mit seinen Positionen (Issue #127). */
export interface Angebot {
  readonly id: number;
  readonly firmaId: number;
  readonly firmaName: string;
  /** Der Ansprechpartner ist optional (Issue #126). */
  readonly ansprechpartnerId: number | null;
  readonly ansprechpartnerName: string | null;
  readonly status: Angebotsstatus;
  /** Tag (`YYYY-MM-DD`), wie das Backend ein `LocalDate` liefert. */
  readonly angebotDatum: string;
  readonly beschreibung: string | null;
  readonly positionen: readonly AngebotPosition[];
  /** Netto-Summe in ganzen Cent, vom Server gerechnet (E5). */
  readonly summeInCent: number;
}

/** Eine Zeile der Angebotsliste an der Firma (Kriterium 7). */
export interface AngebotZeile {
  readonly id: number;
  readonly angebotDatum: string;
  readonly status: Angebotsstatus;
  readonly summeInCent: number;
}

/** Die Angebote einer Firma, neueste zuerst (Kriterium 7). */
export interface FirmaAngebote {
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

/** Das Angebot als Ganzes — die Felder von `AngebotRequest` (E8). */
export interface AngebotEingabe {
  /** Tag (`YYYY-MM-DD`). */
  readonly angebotDatum: string;
  readonly ansprechpartnerId: number | null;
  readonly beschreibung: string | null;
  readonly positionen: readonly PositionEingabe[];
}

function angebotsstatus(wert: unknown): Angebotsstatus {
  const status = alsAngebotsstatus(wert);
  if (status === null) {
    throw new TypeError(FORMFEHLER);
  }
  return status;
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
    firmaId: zahl(angebot.firmaId),
    firmaName: text(angebot.firmaName),
    ansprechpartnerId: zahlOderNull(angebot.ansprechpartnerId),
    ansprechpartnerName: textOderNull(angebot.ansprechpartnerName),
    status: angebotsstatus(angebot.status),
    angebotDatum: text(angebot.angebotDatum),
    beschreibung: textOderNull(angebot.beschreibung),
    positionen: liste(angebot.positionen).map(parsePosition),
    summeInCent: inHundertsteln(angebot.summe),
  };
}

function parseZeile(wert: unknown): AngebotZeile {
  const zeile = objekt(wert);
  return {
    id: zahl(zeile.id),
    angebotDatum: text(zeile.angebotDatum),
    status: angebotsstatus(zeile.status),
    summeInCent: inHundertsteln(zeile.summe),
  };
}

/** Verengt die Angebotsliste einer Firma oder scheitert. */
export function parseFirmaAngebote(wert: unknown): FirmaAngebote {
  const antwort = objekt(wert);
  return { angebote: liste(antwort.angebote).map(parseZeile) };
}

/** Die Angebote der Firma, neueste zuerst (Kriterium 7). */
export function angeboteDerFirma(firmaId: number): Promise<FirmaAngebote> {
  return apiJson(
    `/api/firmen/${String(firmaId)}/angebote`,
    { methode: 'GET' },
    parseFirmaAngebote,
  );
}

/**
 * Legt an die Firma ein Angebot an (Kriterium 2; Issue #126).
 *
 * Der Rumpf geht immer hinaus, auch ohne Ansprechpartner: Das Backend nimmt ihn optional
 * (`@RequestBody(required = false)`), aber ein Aufruf mit stets derselben Form hat nur einen Weg
 * statt zweier, die auseinanderlaufen koennen.
 */
export function angebotAnlegen(
  firmaId: number,
  ansprechpartnerId: number | null,
): Promise<Angebot> {
  return apiJson(
    `/api/firmen/${String(firmaId)}/angebote`,
    { methode: 'POST', rumpf: { ansprechpartnerId } },
    parseAngebot,
  );
}

/** Liest ein Angebot samt Positionen. */
export function angebotLesen(id: number): Promise<Angebot> {
  return apiJson(`/api/angebote/${String(id)}`, { methode: 'GET' }, parseAngebot);
}

/**
 * Aendert das Angebot als Ganzes, in jedem Status (Kriterium 5, E8).
 *
 * Die Antwort traegt das Angebot mit den neu gerechneten Betraegen — die Maske zeigt sie ohne
 * zweiten Aufruf.
 */
export function angebotAendern(id: number, eingabe: AngebotEingabe): Promise<Angebot> {
  return apiJson(`/api/angebote/${String(id)}`, { methode: 'PUT', rumpf: eingabe }, parseAngebot);
}

/** Schaltet den Status eine Stufe weiter; die Antwort traegt das Angebot im neuen Status. */
export function angebotStatusWeiter(id: number): Promise<Angebot> {
  return apiJson(`/api/angebote/${String(id)}/status/weiter`, { methode: 'POST' }, parseAngebot);
}

/** Schaltet den Status eine Stufe zurueck; die Antwort traegt das Angebot im neuen Status. */
export function angebotStatusZurueck(id: number): Promise<Angebot> {
  return apiJson(`/api/angebote/${String(id)}/status/zurueck`, { methode: 'POST' }, parseAngebot);
}
