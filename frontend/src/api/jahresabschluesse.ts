import { apiJson } from './client';
import { inHundertsteln, inHundertstelnOderNull, jaNein, liste, objekt, text, zahl } from './verengen';

/**
 * Die beiden Wege des Jahresabschlusses: die Uebersicht der Jahre und der Abschluss eines Jahres
 * (Issue #295; Plan #288).
 *
 * Die Typen sind die Gegenstuecke zu `JahresuebersichtResponse` und `JahresabschlussResponse` samt
 * deren inneren Records im Backend; aendert sich dort ein Feld, aendert es sich hier mit
 * (CLAUDE-react.md). Jede Antwort geht durch einen Parser — kein `as`.
 *
 * <b>Die Zahlen tragen hier ihre Einheit im Namen</b>, wie in `api/startseite.ts`: Betraege in
 * ganzen Cent, Stunden in ganzen Hundertsteln, Prozentwerte in ganzen Hundertstel-Prozent — alle
 * ueber {@link inHundertsteln} und damit ueber die Ziffern verengt, nicht ueber Gleitkomma (E18).
 * Gerechnet und gerundet hat der Server (E10); hier entsteht keine Zahl.
 *
 * <b>Eine nicht berechenbare Kennzahl steht als `null`</b> (E11): Annahmequote, Umsatzanteil und
 * Erloes je Stunde bei Nenner null, der Steuersatz in der Zeile der nachgetragenen Rechnungen.
 * Welcher Grund dann neben dem Strich steht, entscheidet die Ansicht.
 *
 * <b>Das Jahr steht als Zeichenkette `JJJJ`</b> — genau der Wert, den die Ansicht in die Adresse setzt.
 */

/** Ein Jahr der Uebersicht mit seinen drei Hauptzahlen (#287, Kriterien 1 und 3). */
export interface Jahresuebersichtszeile {
  readonly jahr: string;
  readonly laeuftNoch: boolean;
  readonly nettoInCent: number;
  /** Die Zahl der gestellten Rechnungen — eine Anzahl, kein Betrag. */
  readonly anzahl: number;
  /** In Hundertstel-Prozent; `null` ohne abgegebene Angebote. */
  readonly annahmequoteInHundertstelProzent: number | null;
}

/** Die Einnahmen des Jahres (#287, Kriterium 4). */
export interface Einnahmen {
  readonly nettoInCent: number;
  readonly bruttoInCent: number;
  readonly umsatzsteuerInCent: number;
}

/** Der Stand der Rechnungen des Jahres (#287, Kriterium 5). */
export interface Rechnungsstand {
  readonly anzahl: number;
  readonly offenAnzahl: number;
  readonly offenNettoInCent: number;
  readonly abgeschriebenAnzahl: number;
  readonly abgeschriebenNettoInCent: number;
}

/** Eine Zeile der Umsatzsteuer je Satz (#287, Kriterium 6). */
export interface Steuerzeile {
  /** In Hundertstel-Prozent; `null` in der Zeile „Steuersatz nicht erfasst". */
  readonly satzInHundertstelProzent: number | null;
  readonly nettoInCent: number;
  readonly umsatzsteuerInCent: number;
}

/** Die Angebote des Jahres (#287, Kriterien 7, 8 und 12). */
export interface Angebotsbilanz {
  readonly abgegeben: number;
  readonly angenommen: number;
  readonly offen: number;
  /** In Hundertstel-Prozent; `null` ohne abgegebene Angebote. */
  readonly annahmequoteInHundertstelProzent: number | null;
  readonly volumenAbgegebenInCent: number;
  readonly volumenAngenommenInCent: number;
}

/** Ein Kunde mit seinem Umsatz im Jahr (#287, Kriterium 9). */
export interface Kundenzeile {
  readonly firmaName: string;
  readonly nettoInCent: number;
  /** In Hundertstel-Prozent; `null` ohne Umsatz im Jahr. */
  readonly anteilInHundertstelProzent: number | null;
}

/** Die Arbeitszeit des Jahres (#287, Kriterium 10). */
export interface Jahresarbeitszeit {
  readonly kundenStundenInHundertsteln: number;
  readonly interneStundenInHundertsteln: number;
  /** In ganzen Cent; `null` ohne Kundenstunden. */
  readonly erloesJeStundeInCent: number | null;
}

/** Der vollstaendige Jahresabschluss eines Jahres (#287, Kriterien 4 bis 12). */
export interface Jahresabschluss {
  readonly jahr: string;
  readonly laeuftNoch: boolean;
  readonly einnahmen: Einnahmen;
  readonly rechnungsstand: Rechnungsstand;
  readonly steuerzeilen: readonly Steuerzeile[];
  readonly angebotsbilanz: Angebotsbilanz;
  readonly kunden: readonly Kundenzeile[];
  readonly arbeitszeit: Jahresarbeitszeit;
}

function parseJahresuebersichtszeile(wert: unknown): Jahresuebersichtszeile {
  const zeile = objekt(wert);
  return {
    jahr: text(zeile.jahr),
    laeuftNoch: jaNein(zeile.laeuftNoch),
    nettoInCent: inHundertsteln(zeile.netto),
    anzahl: zahl(zeile.anzahl),
    annahmequoteInHundertstelProzent: inHundertstelnOderNull(zeile.annahmequote),
  };
}

/** Verengt die Uebersicht der Jahre oder scheitert. */
export function parseJahresuebersicht(wert: unknown): readonly Jahresuebersichtszeile[] {
  return liste(wert).map(parseJahresuebersichtszeile);
}

function parseEinnahmen(wert: unknown): Einnahmen {
  const einnahmen = objekt(wert);
  return {
    nettoInCent: inHundertsteln(einnahmen.netto),
    bruttoInCent: inHundertsteln(einnahmen.brutto),
    umsatzsteuerInCent: inHundertsteln(einnahmen.umsatzsteuer),
  };
}

function parseRechnungsstand(wert: unknown): Rechnungsstand {
  const stand = objekt(wert);
  return {
    anzahl: zahl(stand.anzahl),
    offenAnzahl: zahl(stand.offenAnzahl),
    offenNettoInCent: inHundertsteln(stand.offenNetto),
    abgeschriebenAnzahl: zahl(stand.abgeschriebenAnzahl),
    abgeschriebenNettoInCent: inHundertsteln(stand.abgeschriebenNetto),
  };
}

function parseSteuerzeile(wert: unknown): Steuerzeile {
  const zeile = objekt(wert);
  return {
    satzInHundertstelProzent: inHundertstelnOderNull(zeile.satz),
    nettoInCent: inHundertsteln(zeile.netto),
    umsatzsteuerInCent: inHundertsteln(zeile.umsatzsteuer),
  };
}

function parseAngebotsbilanz(wert: unknown): Angebotsbilanz {
  const bilanz = objekt(wert);
  return {
    abgegeben: zahl(bilanz.abgegeben),
    angenommen: zahl(bilanz.angenommen),
    offen: zahl(bilanz.offen),
    annahmequoteInHundertstelProzent: inHundertstelnOderNull(bilanz.annahmequote),
    volumenAbgegebenInCent: inHundertsteln(bilanz.volumenAbgegeben),
    volumenAngenommenInCent: inHundertsteln(bilanz.volumenAngenommen),
  };
}

function parseKundenzeile(wert: unknown): Kundenzeile {
  const zeile = objekt(wert);
  return {
    firmaName: text(zeile.firmaName),
    nettoInCent: inHundertsteln(zeile.netto),
    anteilInHundertstelProzent: inHundertstelnOderNull(zeile.anteil),
  };
}

function parseArbeitszeit(wert: unknown): Jahresarbeitszeit {
  const arbeitszeit = objekt(wert);
  return {
    kundenStundenInHundertsteln: inHundertsteln(arbeitszeit.kundenStunden),
    interneStundenInHundertsteln: inHundertsteln(arbeitszeit.interneStunden),
    erloesJeStundeInCent: inHundertstelnOderNull(arbeitszeit.erloesJeStunde),
  };
}

/** Verengt den Abschluss eines Jahres samt allen Teilen oder scheitert. */
export function parseJahresabschluss(wert: unknown): Jahresabschluss {
  const antwort = objekt(wert);
  return {
    jahr: text(antwort.jahr),
    laeuftNoch: jaNein(antwort.laeuftNoch),
    einnahmen: parseEinnahmen(antwort.einnahmen),
    rechnungsstand: parseRechnungsstand(antwort.rechnungsstand),
    steuerzeilen: liste(antwort.steuerzeilen).map(parseSteuerzeile),
    angebotsbilanz: parseAngebotsbilanz(antwort.angebotsbilanz),
    kunden: liste(antwort.kunden).map(parseKundenzeile),
    arbeitszeit: parseArbeitszeit(antwort.arbeitszeit),
  };
}

/** Der Weg des Jahresabschlusses. */
const PFAD = '/api/jahresabschluesse';

/** Die Jahre mit gestellter Rechnung oder abgegebenem Angebot, das juengste zuerst. */
export function jahresuebersicht(): Promise<readonly Jahresuebersichtszeile[]> {
  return apiJson(PFAD, { methode: 'GET' }, parseJahresuebersicht);
}

/** @param jahr das Jahr als `JJJJ`, wie es in der Adresse der Ansicht steht */
export function jahresabschluss(jahr: string): Promise<Jahresabschluss> {
  return apiJson(`${PFAD}/${encodeURIComponent(jahr)}`, { methode: 'GET' }, parseJahresabschluss);
}
