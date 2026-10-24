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
} from './verengen';

/**
 * Alle Wege der Arbeitszeit: die Monatsliste, die buchbaren Positionen, die waehlbaren Personen
 * und die drei Schreibwege (Issue #193, Kriterien 1, 5 und 6; Plan #194, A15, A20; Plan #426,
 * E10 bis E12).
 *
 * Die Typen sind die Gegenstuecke zu `ArbeitszeitMonatResponse`, `BuchungspositionResponse`,
 * `PersonResponse` und `ZeiteintragResponse` im Backend; aendert sich dort ein Feld, aendert es sich hier mit
 * (CLAUDE-react.md). Jede Antwort geht durch einen Parser: Was ueber das Netz kommt, ist
 * `unknown`, bis es geprueft ist — kein `as`.
 *
 * <b>Die Stunden tragen hier einen anderen Namen als in der Antwort</b>, weil sie eine andere
 * Einheit tragen — genau wie Geld und Menge in `api/rechnungen.ts`: Die Antwort schickt eine
 * Dezimalzahl, hier steht sie als <b>ganze Zahl</b> in Hundertstel-Stunden (E5). Verengt wird mit
 * {@link inHundertsteln} und damit ueber die Ziffern, nicht ueber Gleitkomma. Derselbe Name bei
 * gewechselter Einheit waere die gefaehrlichere Wahl — wer `stunden` fuer Stunden haelt, rechnet um
 * den Faktor hundert daneben.
 *
 * <b>Die Dauer geht nie hinaus.</b> Sie wird aus Beginn und Ende gerechnet (A3), und
 * {@code ZeiteintragRequest} kennt kein Feld dafuer: Sie einzureichen hiesse, dem Absender eine
 * Angabe zu glauben, die die Anwendung selbst kennt.
 *
 * <b>Die buchbaren Positionen kommen als Feld und nicht als Objekt um ein Feld herum</b> — so
 * antwortet der Controller ({@code List<BuchungspositionResponse>}). Darum verengt
 * {@link parseBuchungspositionen} eine Liste und nicht ein Objekt mit einem Listenfeld. Dasselbe
 * gilt fuer die waehlbaren Personen und {@link parsePersonen}.
 *
 * <b>Jede Zeile traegt ihre Person, nicht nur die Sicht „Alle"</b> (E14): Eine Antwort, deren
 * Felder von der Auswahl abhingen, zwaenge {@link parseArbeitsmonat} zu einer Fallunterscheidung
 * ueber etwas, das er nicht entschieden hat. Ob die Ansicht die Person zeigt, entscheidet sie.
 *
 * <b>Der Monat nennt die geltende Auswahl</b> (E10) — eine Kontokennung oder „Alle". Fragt die
 * Ansicht ohne Auswahl, entscheidet der Server die Vorauswahl aus der Rolle; die Antwort sagt,
 * welche er getroffen hat, und genau diese Form geht beim naechsten Aufruf wieder als `person`
 * hinaus.
 *
 * <b>Das Konto der Einreichung ist optional</b> (E12): Fehlt es, geht das Feld `accountId` nicht
 * mit, und der Server liest das beim Anlegen als „das angemeldete Konto", beim Aendern als „bleibt
 * bei seiner Person".
 */

/** Das Wort, mit dem die Auswahl alle Personen meint — wie {@code Personenauswahl.ALLE}. */
export const ALLE = 'alle';

/** Wessen Zeiten die Monatsliste zeigt: die Kennung eines Kontos oder „Alle" (E10). */
export type Personenauswahl = number | typeof ALLE;

/** Eine Person, deren Zeiten gezeigt oder fuer die erfasst werden darf (E11). */
export interface Person {
  readonly id: number;
  /** Der aktuelle Name des Kontos — nach einer Umbenennung der neue (Kriterium 9). */
  readonly name: string;
  /** `false` fuer ein stillgelegtes Konto: auswaehlbar zum Ansehen, nicht zum Erfassen. */
  readonly aktiv: boolean;
}

/** Eine Angebotsposition, auf die Zeit gebucht werden darf (A15, A20). */
export interface Buchungsposition {
  readonly id: number;
  readonly bezeichnung: string;
  readonly angebotId: number;
  /** Tag (`YYYY-MM-DD`), wie das Backend ein `LocalDate` liefert. */
  readonly angebotDatum: string;
  /** Titel des Angebots, `null`, wo es keinen traegt (Issue #328). */
  readonly angebotTitel: string | null;
  /** Ob die Position zu einem internen Angebot gehoert (Issue #229). */
  readonly intern: boolean;
  readonly firmaName: string;
}

/**
 * Eine Zeile der Monatsliste (Kriterium 5).
 *
 * Sie traegt ihren Tag ein zweites Mal, obwohl sie unter ihm steht — so steht es in der Antwort,
 * und der Dialog zum Aendern bekommt die Zeile allein uebergeben und braucht den Tag als
 * Vorbelegung.
 */
export interface Zeitzeile {
  readonly id: number;
  readonly tag: string;
  /** Beginn, wie das Backend ein `LocalTime` liefert — „09:00:00". */
  readonly von: string;
  /** Ende in derselben Form. */
  readonly bis: string;
  /** Die gerechnete Dauer in ganzen Hundertstel-Stunden. */
  readonly stundenInHundertsteln: number;
  readonly position: Buchungsposition;
  /** Wem der Eintrag gehoert — an jeder Zeile, gleich welche Auswahl gilt (E14). */
  readonly person: Person;
  /** Der Stand des Eintrags; der Dialog legt ihn beim Aendern als `If-Match` vor (Plan #415, E2). */
  readonly version: number;
}

/** Ein Tag mit seinen Zeilen und seiner Summe; ein Tag ohne Eintrag fehlt. */
export interface Arbeitstag {
  readonly tag: string;
  readonly eintraege: readonly Zeitzeile[];
  readonly stundenInHundertsteln: number;
}

/**
 * Die Eintraege eines Monats nach Tagen, mit den Summen.
 *
 * Der Monat steht in der Antwort, weil der Aufruf ihn weglassen darf und dann den laufenden
 * bekommt (E4) — ohne diese Angabe muesste die Ansicht die Geschaeftszone selbst nachrechnen.
 */
export interface Arbeitsmonat {
  readonly monat: string;
  /** Die Auswahl, die der Server angewandt hat — auch dann, wenn er sie selbst getroffen hat. */
  readonly auswahl: Personenauswahl;
  readonly tage: readonly Arbeitstag[];
  readonly stundenInHundertsteln: number;
  /** Der Teil der Monatssumme, der auf Angebote an Kunden gebucht ist (Issue #230). */
  readonly stundenFuerKundenInHundertsteln: number;
  /** Der Teil der Monatssumme, der auf interne Angebote gebucht ist (Issue #230). */
  readonly stundenInternInHundertsteln: number;
}

/** Die Eingaben des Dialogs — beim Erfassen und beim Aendern derselbe Rumpf. */
export interface ZeiteintragEingabe {
  readonly angebotPositionId: number;
  /** Tag (`YYYY-MM-DD`). */
  readonly tag: string;
  /** Beginn als `HH:MM`, wie das Zeitfeld des Browsers ihn schreibt. */
  readonly von: string;
  /** Ende in derselben Form. */
  readonly bis: string;
  /** Fuer wen erfasst wird; fehlt es, geht das Feld nicht hinaus (E12). */
  readonly accountId?: number;
}

/** Ein Eintrag, wie `POST` und `PUT` ihn zurueckgeben. */
export interface Zeiteintrag {
  readonly id: number;
  /** Das Konto, dem der Eintrag gehoert (E12). */
  readonly accountId: number;
  readonly angebotPositionId: number;
  readonly tag: string;
  readonly von: string;
  readonly bis: string;
  readonly stundenInHundertsteln: number;
  /** Der Stand nach dem Speichern (Plan #415, E2). */
  readonly version: number;
}

function parseBuchungsposition(wert: unknown): Buchungsposition {
  const position = objekt(wert);
  return {
    id: zahl(position.id),
    bezeichnung: text(position.bezeichnung),
    angebotId: zahl(position.angebotId),
    angebotDatum: text(position.angebotDatum),
    angebotTitel: textOderNull(position.angebotTitel),
    intern: jaNein(position.intern),
    firmaName: text(position.firmaName),
  };
}

function parsePerson(wert: unknown): Person {
  const person = objekt(wert);
  return {
    id: zahl(person.id),
    name: text(person.name),
    aktiv: jaNein(person.aktiv),
  };
}

/**
 * Die Auswahl in der Textform des Parameters `person` — die Kennung als Ziffern oder `alle`.
 *
 * Was keines von beiden ist, ist keine Auswahl; eine Zahl ohne Anfuehrungszeichen ebenso wenig,
 * denn so schreibt das Backend sie nicht ({@code Personenauswahl#alsParameter}).
 */
function parsePersonenauswahl(wert: unknown): Personenauswahl {
  const gelesen = text(wert);
  if (gelesen === ALLE) {
    return ALLE;
  }
  if (!/^\d+$/.test(gelesen)) {
    throw new TypeError(FORMFEHLER);
  }
  return Number(gelesen);
}

function parseZeitzeile(wert: unknown): Zeitzeile {
  const zeile = objekt(wert);
  return {
    id: zahl(zeile.id),
    tag: text(zeile.tag),
    von: text(zeile.von),
    bis: text(zeile.bis),
    stundenInHundertsteln: inHundertsteln(zeile.stunden),
    position: parseBuchungsposition(zeile.position),
    person: parsePerson(zeile.person),
    version: zahl(zeile.version),
  };
}

function parseArbeitstag(wert: unknown): Arbeitstag {
  const arbeitstag = objekt(wert);
  return {
    tag: text(arbeitstag.tag),
    eintraege: liste(arbeitstag.eintraege).map(parseZeitzeile),
    stundenInHundertsteln: inHundertsteln(arbeitstag.stunden),
  };
}

/** Verengt die Monatsliste samt Tagen, Zeilen und Summen oder scheitert. */
export function parseArbeitsmonat(wert: unknown): Arbeitsmonat {
  const antwort = objekt(wert);
  return {
    monat: text(antwort.monat),
    auswahl: parsePersonenauswahl(antwort.auswahl),
    tage: liste(antwort.tage).map(parseArbeitstag),
    stundenInHundertsteln: inHundertsteln(antwort.stunden),
    stundenFuerKundenInHundertsteln: inHundertsteln(antwort.stundenFuerKunden),
    stundenInternInHundertsteln: inHundertsteln(antwort.stundenIntern),
  };
}

/** Verengt die Auswahlliste der buchbaren Positionen oder scheitert. */
export function parseBuchungspositionen(wert: unknown): readonly Buchungsposition[] {
  return liste(wert).map(parseBuchungsposition);
}

/** Verengt die Liste der waehlbaren Personen oder scheitert (E11). */
export function parsePersonen(wert: unknown): readonly Person[] {
  return liste(wert).map(parsePerson);
}

/** Verengt einen einzelnen Eintrag oder scheitert. */
export function parseZeiteintrag(wert: unknown): Zeiteintrag {
  const eintrag = objekt(wert);
  return {
    id: zahl(eintrag.id),
    accountId: zahl(eintrag.accountId),
    angebotPositionId: zahl(eintrag.angebotPositionId),
    tag: text(eintrag.tag),
    von: text(eintrag.von),
    bis: text(eintrag.bis),
    stundenInHundertsteln: inHundertsteln(eintrag.stunden),
    version: zahl(eintrag.version),
  };
}

/** Der Weg des Bestands; der einzelne Eintrag haengt mit seiner Kennung daran. */
const PFAD = '/api/arbeitszeit';

/**
 * Die Eintraege eines Monats nach Tagen, mit den Summen (Kriterium 5).
 *
 * <b>Ohne Monat fragt die Ansicht ohne Parameter</b>: Welcher der laufende ist, entscheidet der
 * Server an seiner Uhr in der Geschaeftszone (E4). Ein hier gerechneter Monat waere ein zweiter
 * Wahrheitsort daneben — in einem Browser, der in einer anderen Zone steht, der falsche.
 *
 * <b>Ohne Auswahl fehlt der Parameter `person` ebenso</b>, und die Vorauswahl aus der Rolle trifft
 * der Server (E10); welche gilt, nennt {@link Arbeitsmonat.auswahl}.
 */
export function arbeitszeitMonat(
  monat?: string,
  auswahl?: Personenauswahl,
): Promise<Arbeitsmonat> {
  const parameter = new URLSearchParams();
  if (monat !== undefined) {
    parameter.set('monat', monat);
  }
  if (auswahl !== undefined) {
    parameter.set('person', String(auswahl));
  }
  const abfrage = parameter.toString();
  const weg = abfrage === '' ? PFAD : `${PFAD}?${abfrage}`;
  return apiJson(weg, { methode: 'GET' }, parseArbeitsmonat);
}

/**
 * Die Positionen, auf die gebucht werden darf — die Auswahlliste des Dialogs (A15).
 *
 * Die Liste fuehrt, der Server entscheidet: Dass eine Position hier steht, ist eine Auskunft und
 * keine Zusage — das Anlegen prueft dieselbe Buchbarkeit noch einmal.
 */
export function buchbarePositionen(): Promise<readonly Buchungsposition[]> {
  return apiJson(`${PFAD}/buchbare-positionen`, { methode: 'GET' }, parseBuchungspositionen);
}

/**
 * Die Personen fuer Umschalter und Auswahl (E11) — fuer ein C-Level und ReadOnly alle Konten
 * einschliesslich der stillgelegten, fuer einen Employee allein sein eigenes. Wer waehlbar ist,
 * entscheidet der Server; die Liste ist eine Auskunft, keine Zusage.
 */
export function personen(): Promise<readonly Person[]> {
  return apiJson(`${PFAD}/personen`, { methode: 'GET' }, parsePersonen);
}

/** Erfasst eine Arbeitszeit (Kriterium 1); die Antwort traegt Kennung und gerechnete Dauer. */
export function zeiteintragAnlegen(eingabe: ZeiteintragEingabe): Promise<Zeiteintrag> {
  return apiJson(PFAD, { methode: 'POST', rumpf: eingabe }, parseZeiteintrag);
}

/**
 * Aendert einen Eintrag (Kriterium 6) — derselbe Rumpf wie beim Erfassen, auf dem vorgelegten Stand
 * (#411, Kriterien 1 und 4; Plan #415, E2). Hat sich der Eintrag seither geaendert, weist der
 * Server mit dem Bearbeitungskonflikt ab; das Erfassen legt keinen Stand vor (Kriterium 7).
 */
export function zeiteintragAendern(
  id: number,
  version: number,
  eingabe: ZeiteintragEingabe,
): Promise<Zeiteintrag> {
  return apiJson(
    `${PFAD}/${String(id)}`,
    { methode: 'PUT', rumpf: eingabe, version },
    parseZeiteintrag,
  );
}

/**
 * Loescht einen Eintrag (Kriterium 6, Antwort 4) — die Antwort traegt keinen Inhalt.
 *
 * Geloescht wird mit Kennzeichen (Plan #415, E17); ist der Eintrag inzwischen geaendert oder
 * geloescht, weist der Server mit einem Bearbeitungskonflikt ab.
 *
 * @param version der Stand, auf dem das Loeschen beruht
 */
export function zeiteintragLoeschen(id: number, version: number): Promise<void> {
  return apiOhneInhalt(`${PFAD}/${String(id)}`, { methode: 'DELETE', version });
}
