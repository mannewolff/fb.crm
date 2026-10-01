import { apiJson, apiOhneInhalt } from './client';
import { inHundertsteln, liste, objekt, text, zahl } from './verengen';

/**
 * Alle Wege der Arbeitszeit: die Monatsliste, die buchbaren Positionen und die drei Schreibwege
 * (Issue #193, Kriterien 1, 5 und 6; Plan #194, A15, A20).
 *
 * Die Typen sind die Gegenstuecke zu `ArbeitszeitMonatResponse`, `BuchungspositionResponse` und
 * `ZeiteintragResponse` im Backend; aendert sich dort ein Feld, aendert es sich hier mit
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
 * {@link parseBuchungspositionen} eine Liste und nicht ein Objekt mit einem Listenfeld.
 */

/** Eine Angebotsposition, auf die Zeit gebucht werden darf (A15, A20). */
export interface Buchungsposition {
  readonly id: number;
  readonly bezeichnung: string;
  readonly angebotId: number;
  /** Tag (`YYYY-MM-DD`), wie das Backend ein `LocalDate` liefert. */
  readonly angebotDatum: string;
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
  readonly tage: readonly Arbeitstag[];
  readonly stundenInHundertsteln: number;
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
}

/** Ein Eintrag, wie `POST` und `PUT` ihn zurueckgeben. */
export interface Zeiteintrag {
  readonly id: number;
  readonly angebotPositionId: number;
  readonly tag: string;
  readonly von: string;
  readonly bis: string;
  readonly stundenInHundertsteln: number;
}

function parseBuchungsposition(wert: unknown): Buchungsposition {
  const position = objekt(wert);
  return {
    id: zahl(position.id),
    bezeichnung: text(position.bezeichnung),
    angebotId: zahl(position.angebotId),
    angebotDatum: text(position.angebotDatum),
    firmaName: text(position.firmaName),
  };
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
    tage: liste(antwort.tage).map(parseArbeitstag),
    stundenInHundertsteln: inHundertsteln(antwort.stunden),
  };
}

/** Verengt die Auswahlliste der buchbaren Positionen oder scheitert. */
export function parseBuchungspositionen(wert: unknown): readonly Buchungsposition[] {
  return liste(wert).map(parseBuchungsposition);
}

/** Verengt einen einzelnen Eintrag oder scheitert. */
export function parseZeiteintrag(wert: unknown): Zeiteintrag {
  const eintrag = objekt(wert);
  return {
    id: zahl(eintrag.id),
    angebotPositionId: zahl(eintrag.angebotPositionId),
    tag: text(eintrag.tag),
    von: text(eintrag.von),
    bis: text(eintrag.bis),
    stundenInHundertsteln: inHundertsteln(eintrag.stunden),
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
 */
export function arbeitszeitMonat(monat?: string): Promise<Arbeitsmonat> {
  const weg = monat === undefined ? PFAD : `${PFAD}?monat=${encodeURIComponent(monat)}`;
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

/** Erfasst eine Arbeitszeit (Kriterium 1); die Antwort traegt Kennung und gerechnete Dauer. */
export function zeiteintragAnlegen(eingabe: ZeiteintragEingabe): Promise<Zeiteintrag> {
  return apiJson(PFAD, { methode: 'POST', rumpf: eingabe }, parseZeiteintrag);
}

/** Aendert einen Eintrag (Kriterium 6) — derselbe Rumpf wie beim Erfassen. */
export function zeiteintragAendern(id: number, eingabe: ZeiteintragEingabe): Promise<Zeiteintrag> {
  return apiJson(
    `${PFAD}/${String(id)}`,
    { methode: 'PUT', rumpf: eingabe },
    parseZeiteintrag,
  );
}

/** Loescht einen Eintrag (Kriterium 6, Antwort 4) — die Antwort traegt keinen Inhalt. */
export function zeiteintragLoeschen(id: number): Promise<void> {
  return apiOhneInhalt(`${PFAD}/${String(id)}`, { methode: 'DELETE' });
}
