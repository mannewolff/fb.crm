import { apiJson } from './client';
import { FORMFEHLER, inHundertsteln, liste, objekt, text, zahl } from './verengen';
import { alsAngebotsstatus } from '../lib/angebotsstatus';
import type { Angebotsstatus } from '../lib/angebotsstatus';

/**
 * Der eine Weg der Startseite: ihr ganzer Stand in einer Antwort (Issue #214; Plan #208, E7).
 *
 * Die Typen sind die Gegenstuecke zu `StartseiteResponse` samt seinen vier inneren Records im
 * Backend; aendert sich dort ein Feld, aendert es sich hier mit (CLAUDE-react.md). Die Antwort geht
 * durch einen Parser: Was ueber das Netz kommt, ist `unknown`, bis es geprueft ist — kein `as`.
 *
 * <b>Die Betraege tragen hier andere Namen als in der Antwort</b>, weil sie eine andere Einheit
 * tragen — genau wie in `api/rechnungen.ts`: Die Antwort schickt Dezimalzahlen, hier stehen sie als
 * <b>ganze Zahl</b> in Cent (Issue #215, E5). Verengt wird mit {@link inHundertsteln} und damit
 * ueber die Ziffern, nicht ueber Gleitkomma; dieselben Namen bei gewechselter Einheit waeren die
 * gefaehrlichere Wahl — wer `netto` fuer Euro haelt, rechnet um den Faktor hundert daneben.
 *
 * <b>Die Monate stehen als Zeichenkette `JJJJ-MM`</b> — so schreibt Jackson ein `YearMonth`, und so
 * nimmt der Weg ihn auch wieder an. Ein `Date` daraus zu bauen hiesse, einen Tag und eine Zone zu
 * erfinden, die in der Angabe nicht stehen (wie `monat` in `api/arbeitszeit.ts`).
 *
 * <b>Die Zahl der Angebote in Arbeit steht in keinem Feld</b>: Die Liste ist die Wahrheit, und wer
 * sie zeigt, zaehlt sie (Plan #208, E20). Bei {@link Abgerechnet} ist `anzahl` dagegen ein Feld —
 * dort stehen die Rechnungen selbst nicht in der Antwort.
 */

/** Ein Angebot in Arbeit — „bestellt" oder „erledigt" (#206, Kriterium 4). */
export interface StartseiteAngebotszeile {
  readonly angebotId: number;
  readonly firmaName: string;
  /** Tag (`YYYY-MM-DD`), wie das Backend ein `LocalDate` liefert. */
  readonly angebotDatum: string;
  readonly status: Angebotsstatus;
}

/**
 * Was ein einzelnes Angebot zur Kennzahl „Noch nicht abgerechnet" beitraegt (#206, Kriterium 5).
 *
 * Ohne Status: Welche Angebote beitragen, entscheidet nicht der Status, sondern ob an ihren
 * Positionen noch etwas offen ist.
 */
export interface Anteilszeile {
  readonly angebotId: number;
  readonly firmaName: string;
  readonly angebotDatum: string;
  /** Sein Anteil am Betrag, netto und in ganzen Cent. */
  readonly nettoInCent: number;
}

/**
 * Die Kennzahl „Noch nicht abgerechnet" (#206, Kriterien 5 und 6).
 *
 * Die beiden Betraege haben verschiedene Zeitraeume, und das ist Absicht: `nettoInCent` ist der
 * Stand von heute ueber alle Monate, `erfasstImMonatInCent` haengt am gewaehlten Monat.
 */
export interface NichtAbgerechnet {
  readonly nettoInCent: number;
  readonly erfasstImMonatInCent: number;
  /** Je beitragendem Angebot sein Anteil, neueste zuerst. */
  readonly angebote: readonly Anteilszeile[];
}

/** Die Kennzahl „Abgerechnet" fuer den gewaehlten Monat (#206, Kriterium 7). */
export interface Abgerechnet {
  readonly nettoInCent: number;
  readonly bruttoInCent: number;
  /** Die Zahl der im Monat gestellten Rechnungen — eine Anzahl, kein Betrag. */
  readonly anzahl: number;
}

/** Der ganze Stand der Startseite (#206, Kriterien 3 bis 8). */
export interface Startseitenstand {
  /** Der geltende Monat als `JJJJ-MM`. */
  readonly monat: string;
  /** Die zwoelf waehlbaren Monate, neuester zuerst; {@link monat} ist einer von ihnen. */
  readonly monate: readonly string[];
  readonly inArbeit: readonly StartseiteAngebotszeile[];
  readonly nichtAbgerechnet: NichtAbgerechnet;
  readonly abgerechnet: Abgerechnet;
}

function angebotsstatus(wert: unknown): Angebotsstatus {
  const status = alsAngebotsstatus(wert);
  if (status === null) {
    throw new TypeError(FORMFEHLER);
  }
  return status;
}

function parseAngebotszeile(wert: unknown): StartseiteAngebotszeile {
  const zeile = objekt(wert);
  return {
    angebotId: zahl(zeile.angebotId),
    firmaName: text(zeile.firmaName),
    angebotDatum: text(zeile.angebotDatum),
    status: angebotsstatus(zeile.status),
  };
}

function parseAnteilszeile(wert: unknown): Anteilszeile {
  const zeile = objekt(wert);
  return {
    angebotId: zahl(zeile.angebotId),
    firmaName: text(zeile.firmaName),
    angebotDatum: text(zeile.angebotDatum),
    nettoInCent: inHundertsteln(zeile.netto),
  };
}

function parseNichtAbgerechnet(wert: unknown): NichtAbgerechnet {
  const kennzahl = objekt(wert);
  return {
    nettoInCent: inHundertsteln(kennzahl.netto),
    erfasstImMonatInCent: inHundertsteln(kennzahl.erfasstImMonat),
    angebote: liste(kennzahl.angebote).map(parseAnteilszeile),
  };
}

function parseAbgerechnet(wert: unknown): Abgerechnet {
  const kennzahl = objekt(wert);
  return {
    nettoInCent: inHundertsteln(kennzahl.netto),
    bruttoInCent: inHundertsteln(kennzahl.brutto),
    anzahl: zahl(kennzahl.anzahl),
  };
}

/** Verengt den Stand der Startseite samt beiden Kennzahlen oder scheitert. */
export function parseStartseitenstand(wert: unknown): Startseitenstand {
  const antwort = objekt(wert);
  return {
    monat: text(antwort.monat),
    monate: liste(antwort.monate).map(text),
    inArbeit: liste(antwort.inArbeit).map(parseAngebotszeile),
    nichtAbgerechnet: parseNichtAbgerechnet(antwort.nichtAbgerechnet),
    abgerechnet: parseAbgerechnet(antwort.abgerechnet),
  };
}

/** Der Weg der Startseite. */
const PFAD = '/api/startseite';

/**
 * Der Stand der Startseite: die Kennzahlen, der geltende Monat und die zwoelf waehlbaren.
 *
 * <b>Ohne Monat fragt die Ansicht ohne Parameter</b>: Welcher der laufende ist, entscheidet der
 * Server an seiner Uhr in der Geschaeftszone (Plan #208, E8). Ein hier gerechneter Monat waere ein
 * zweiter Wahrheitsort daneben — in einem Browser, der in einer anderen Zone steht, der falsche.
 *
 * @param monat der gewuenschte Monat als `JJJJ-MM`, oder weggelassen fuer den laufenden
 */
export function startseite(monat?: string): Promise<Startseitenstand> {
  const weg = monat === undefined ? PFAD : `${PFAD}?monat=${encodeURIComponent(monat)}`;
  return apiJson(weg, { methode: 'GET' }, parseStartseitenstand);
}
