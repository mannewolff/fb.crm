import { apiJson } from './client';
import { FORMFEHLER, inHundertsteln, liste, objekt, text, zahl } from './verengen';
import { alsAngebotsstatus } from '../lib/angebotsstatus';
import type { Angebotsstatus } from '../lib/angebotsstatus';
import { alsZeitraumart } from '../lib/zeitraum';
import type { Zeitraumart } from '../lib/zeitraum';

export type { Zeitraumart } from '../lib/zeitraum';

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
 * <b>Dasselbe gilt fuer die Stunden</b>: `interneStundenInHundertsteln` steht in ganzen Hundertstel-
 * Stunden, wie `stundenInHundertsteln` in `api/arbeitszeit.ts` — die Antwort schickt eine
 * Dezimalzahl.
 *
 * <b>Die Monate stehen als Zeichenkette `JJJJ-MM`</b> — so schreibt Jackson ein `YearMonth`, und so
 * nimmt der Weg ihn auch wieder an. Ein `Date` daraus zu bauen hiesse, einen Tag und eine Zone zu
 * erfinden, die in der Angabe nicht stehen (wie `monat` in `api/arbeitszeit.ts`). <b>Die Jahre
 * stehen als Zeichenkette `JJJJ`</b> (Plan #274, E10): genau der Wert, den die Ansicht in die
 * Adresse und in `option value` setzt.
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
 * Der geltende Zeitraum (#273, Kriterien 1 und 2; Plan #274, E10).
 *
 * Die Art steht neben dem Wert, damit die Ansicht sie nicht aus der Laenge des Werts schliessen
 * muss.
 */
export interface Zeitraum {
  readonly art: Zeitraumart;
  /** `JJJJ-MM` bei einem Monat, `JJJJ` bei einem Jahr — der Wert des Adressparameters. */
  readonly wert: string;
}

/** Die waehlbaren Zeitraeume (#273, Kriterien 1 und 2). */
export interface Waehlbar {
  /** Die waehlbaren Jahre als `JJJJ`, neuestes zuerst. */
  readonly jahre: readonly string[];
  /** Die waehlbaren Monate als `JJJJ-MM`, neuester zuerst. */
  readonly monate: readonly string[];
}

/** Ein Monat der Liste unter „Abgerechnet" bei Jahreswahl (#273, Kriterium 7). */
export interface Monatszeile {
  /** Der Monat des Rechnungsdatums als `JJJJ-MM`. */
  readonly monat: string;
  /** Die Zahl der in ihm gestellten Rechnungen. */
  readonly anzahl: number;
  readonly nettoInCent: number;
  readonly bruttoInCent: number;
}

/**
 * Die Kennzahl „Noch nicht abgerechnet" (#206, Kriterien 5 und 6).
 *
 * Die beiden Betraege haben verschiedene Zeitraeume, und das ist Absicht: `nettoInCent` ist der
 * Stand von heute ueber alle Monate, `erfasstImZeitraumInCent` haengt am gewaehlten Zeitraum.
 */
export interface NichtAbgerechnet {
  readonly nettoInCent: number;
  readonly erfasstImZeitraumInCent: number;
  /** Je beitragendem Angebot sein Anteil, neueste zuerst. */
  readonly angebote: readonly Anteilszeile[];
}

/** Die Kennzahl „Abgerechnet" fuer den gewaehlten Zeitraum (#206, Kriterium 7; #273, 4 und 7). */
export interface Abgerechnet {
  readonly nettoInCent: number;
  readonly bruttoInCent: number;
  /** Die Zahl der im Zeitraum gestellten Rechnungen — eine Anzahl, kein Betrag. */
  readonly anzahl: number;
  /** Bei Jahreswahl je Monat mit gestellter Rechnung eine Zeile, aeltester zuerst; sonst leer. */
  readonly monate: readonly Monatszeile[];
}

/** Der ganze Stand der Startseite (#206, Kriterien 3 bis 8; #273). */
export interface Startseitenstand {
  /** Der geltende Zeitraum — immer einer der waehlbaren. */
  readonly zeitraum: Zeitraum;
  readonly waehlbar: Waehlbar;
  readonly inArbeit: readonly StartseiteAngebotszeile[];
  readonly nichtAbgerechnet: NichtAbgerechnet;
  readonly abgerechnet: Abgerechnet;
  /**
   * Die im gewaehlten Zeitraum auf interne Angebote gebuchten Stunden, in ganzen Hundertsteln.
   *
   * Eine Stundenzahl und kein Betrag (#207, Kriterium 9): Interne Arbeit traegt keinen Preis, und
   * ein Euro-Wert daraus ist Nicht-Ziel von #207. Darum steht sie neben den Kennzahlen und in
   * keiner von ihnen.
   */
  readonly interneStundenInHundertsteln: number;
}

function angebotsstatus(wert: unknown): Angebotsstatus {
  const status = alsAngebotsstatus(wert);
  if (status === null) {
    throw new TypeError(FORMFEHLER);
  }
  return status;
}

function zeitraumart(wert: unknown): Zeitraumart {
  const art = alsZeitraumart(wert);
  if (art === null) {
    throw new TypeError(FORMFEHLER);
  }
  return art;
}

function parseZeitraum(wert: unknown): Zeitraum {
  const zeitraum = objekt(wert);
  return {
    art: zeitraumart(zeitraum.art),
    wert: text(zeitraum.wert),
  };
}

function parseWaehlbar(wert: unknown): Waehlbar {
  const waehlbar = objekt(wert);
  return {
    jahre: liste(waehlbar.jahre).map(text),
    monate: liste(waehlbar.monate).map(text),
  };
}

function parseMonatszeile(wert: unknown): Monatszeile {
  const zeile = objekt(wert);
  return {
    monat: text(zeile.monat),
    anzahl: zahl(zeile.anzahl),
    nettoInCent: inHundertsteln(zeile.netto),
    bruttoInCent: inHundertsteln(zeile.brutto),
  };
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
    erfasstImZeitraumInCent: inHundertsteln(kennzahl.erfasstImZeitraum),
    angebote: liste(kennzahl.angebote).map(parseAnteilszeile),
  };
}

function parseAbgerechnet(wert: unknown): Abgerechnet {
  const kennzahl = objekt(wert);
  return {
    nettoInCent: inHundertsteln(kennzahl.netto),
    bruttoInCent: inHundertsteln(kennzahl.brutto),
    anzahl: zahl(kennzahl.anzahl),
    monate: liste(kennzahl.monate).map(parseMonatszeile),
  };
}

/** Verengt den Stand der Startseite samt beiden Kennzahlen oder scheitert. */
export function parseStartseitenstand(wert: unknown): Startseitenstand {
  const antwort = objekt(wert);
  return {
    zeitraum: parseZeitraum(antwort.zeitraum),
    waehlbar: parseWaehlbar(antwort.waehlbar),
    inArbeit: liste(antwort.inArbeit).map(parseAngebotszeile),
    nichtAbgerechnet: parseNichtAbgerechnet(antwort.nichtAbgerechnet),
    abgerechnet: parseAbgerechnet(antwort.abgerechnet),
    interneStundenInHundertsteln: inHundertsteln(antwort.interneStundenImZeitraum),
  };
}

/** Der Weg der Startseite. */
const PFAD = '/api/startseite';

/**
 * Der Stand der Startseite: die Kennzahlen, der geltende Zeitraum und die waehlbaren.
 *
 * <b>Ohne Zeitraum fragt die Ansicht ohne Parameter</b>: Welches Jahr das laufende ist, entscheidet
 * der Server an seiner Uhr in der Geschaeftszone (Plan #208, E8; Issue #283). Ein hier gerechneter
 * Zeitraum waere ein zweiter Wahrheitsort daneben — in einem Browser, der in einer anderen Zone
 * steht, der falsche.
 *
 * @param zeitraum der gewuenschte Zeitraum als `JJJJ-MM` oder `JJJJ` (Plan #274, E1), oder
 *     weggelassen fuer das laufende Jahr
 */
export function startseite(zeitraum?: string): Promise<Startseitenstand> {
  const weg = zeitraum === undefined ? PFAD : `${PFAD}?zeitraum=${encodeURIComponent(zeitraum)}`;
  return apiJson(weg, { methode: 'GET' }, parseStartseitenstand);
}
