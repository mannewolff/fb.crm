import { apiFormular, apiJson, apiOhneInhalt } from './client';
import {
  FORMFEHLER,
  jaNein,
  liste,
  objekt,
  text,
  textOderNull,
  zahl,
  zahlOderNull,
} from './verengen';

/**
 * Die Wege zum Vorgang und zu den Eintraegen seiner Historie.
 *
 * Die Typen sind die Gegenstuecke zu `VorgaengeUebersichtResponse`, `VorgangZeileResponse`,
 * `VorgangResponse`, `ZuordnungResponse`, `EintragResponse`, `VorgangAngelegtResponse` und
 * `VorgaengeDerFirmaResponse` im Backend; aendert sich dort ein Feld, aendert es sich hier mit
 * (CLAUDE-react.md). Jede Antwort geht durch einen Parser: Was ueber das Netz kommt, ist
 * `unknown`, bis es geprueft ist — kein `as`.
 *
 * Zwei Dinge fallen gegenueber `firmen.ts` auf, und beide haben einen Grund:
 *
 * <ul>
 *   <li>Ein Eintrag wird als <b>Formular</b> hinzugefuegt und als <b>JSON</b> geaendert. Beim
 *       Hinzufuegen kann eine Datei dabei sein, beim Aendern nie — sie ist nicht austauschbar
 *       (E20). Zwei Wege, zwei Rumpfarten.</li>
 *   <li>Fuer den Anhang steht hier nur ein <b>Pfad</b> und kein Aufruf (E14). Der Download ist
 *       ein Verweis im Dokument: Der Browser holt die Datei selbst, mit dem Sitzungs-Cookie und
 *       dem `Content-Disposition` des Servers. Wer sie stattdessen ueber `fetch` in den Speicher
 *       holte, muesste sie danach als Blob-URL wieder herausgeben — eine zweite Kopie im
 *       Arbeitsspeicher und ein Dateiname, den die Oberflaeche noch einmal setzen muss.</li>
 * </ul>
 */

/**
 * Wie weit der Vorgang in der Kette ist (`Phase` im Backend).
 *
 * Die Phase wird nicht gepflegt, sondern aus dem Stand der Dokumente abgeleitet: `ANGEBOT`, sobald
 * ein Angebot festgeschrieben ist (Kriterium 22). Auftrag und Rechnung bringen ihre Phasen mit,
 * wenn es sie gibt. Das Wort dazu steht in `lib/phase.ts` — hier steht nur der Wert.
 */
export type Phase = 'ANBAHNUNG' | 'ANGEBOT';

/**
 * Was ein Eintrag der Historie ist (`Eintragsart` im Backend).
 *
 * `EREIGNIS` kommt nie von aussen: Es ist ein Zustandswechsel eines Dokuments, den die Anwendung
 * selbst vermerkt (Kriterium 19). Die Maske bietet es darum nicht an, und aendern laesst es sich
 * nicht — sonst waere die Historie kein Nachweis mehr.
 */
export type Eintragsart = 'KOMMENTAR' | 'ANHANG' | 'EREIGNIS';

/** Woher der Eintrag stammt: von Hand erfasst oder von der Anwendung vermerkt. */
export type Herkunft = 'VON_HAND' | 'AUTOMATISCH';

/** Eine Zeile der Uebersicht und der Listen an der Firma. */
export interface VorgangZeile {
  readonly id: number;
  readonly nummer: number;
  readonly titel: string;
  readonly firma: string;
  readonly phase: Phase;
  readonly abgeschlossen: boolean;
  /** Zeitpunkt in UTC, wie ihn das Backend als `Instant` liefert. */
  readonly letzteAktivitaet: string;
}

/**
 * Die Uebersicht: die gefundenen Zeilen und die Zahl aller Vorgaenge.
 *
 * `gesamt` trennt wie bei den Firmen drei Lagen, die eine leere Liste sonst zusammenwirft: noch
 * kein Vorgang, nichts gefunden, alle abgeschlossen.
 */
export interface VorgaengeUebersicht {
  readonly vorgaenge: readonly VorgangZeile[];
  readonly gesamt: number;
}

/** Die Vorgaenge einer Firma, getrennt nach Abschlussstand (Kriterium 12). */
export interface VorgaengeDerFirma {
  readonly offene: readonly VorgangZeile[];
  readonly abgeschlossene: readonly VorgangZeile[];
}

/** Firma oder Ansprechpartner, so wie sie am Vorgang haengen — mit Name und Abschaltstand. */
export interface Zuordnung {
  readonly id: number;
  readonly name: string;
  readonly aktiv: boolean;
}

/** Ein Eintrag der Historie. */
export interface Eintrag {
  readonly id: number;
  readonly art: Eintragsart;
  readonly text: string | null;
  readonly geschehenAm: string;
  readonly herkunft: Herkunft;
  readonly dateiName: string | null;
  readonly dateiGroesse: number | null;
  readonly geaendertAm: string | null;
}

/** Ein Vorgang mit seinen Zuordnungen und seiner Historie. */
export interface Vorgang {
  readonly id: number;
  readonly nummer: number;
  readonly titel: string;
  readonly phase: Phase;
  readonly abgeschlossen: boolean;
  /** Abschlusswahrscheinlichkeit in Zehnerschritten, oder `null` fuer „nicht eingeschaetzt". */
  readonly abschlusswahrscheinlichkeit: number | null;
  /** Erwarteter Entscheidungszeitpunkt als Tag (`YYYY-MM-DD`), oder `null` (Kriterium 21). */
  readonly entscheidungErwartetAm: string | null;
  readonly firma: Zuordnung;
  readonly ansprechpartner: Zuordnung | null;
  readonly historie: readonly Eintrag[];
}

/** Was das Anlegen zurueckgibt: gerade genug fuer den Weg zur Detailansicht. */
export interface VorgangAngelegt {
  readonly id: number;
  readonly nummer: number;
}

/** Die Eingaben der Vorgangsmaske — die Felder von `VorgangRequest` im Backend. */
export interface VorgangEingabe {
  readonly titel: string;
  readonly firmaId: number;
  readonly ansprechpartnerId: number | null;
  /** Abschlusswahrscheinlichkeit in Zehnerschritten, oder `null` — nie eine stille Null. */
  readonly abschlusswahrscheinlichkeit: number | null;
  /** Erwarteter Entscheidungszeitpunkt als Tag (`YYYY-MM-DD`), oder `null`. */
  readonly entscheidungErwartetAm: string | null;
}

/** Die Eingaben beim Aendern eines Eintrags — die Felder von `EintragAenderungRequest`. */
export interface EintragAenderung {
  readonly text: string;
  /** Zeitpunkt in UTC, wie ihn `lib/zeitpunkt` aus dem Feld der Maske macht. */
  readonly geschehenAm: string;
}

function phase(wert: unknown): Phase {
  if (wert !== 'ANBAHNUNG' && wert !== 'ANGEBOT') {
    throw new TypeError(FORMFEHLER);
  }
  return wert;
}

function eintragsart(wert: unknown): Eintragsart {
  if (wert !== 'KOMMENTAR' && wert !== 'ANHANG' && wert !== 'EREIGNIS') {
    throw new TypeError(FORMFEHLER);
  }
  return wert;
}

function herkunft(wert: unknown): Herkunft {
  if (wert !== 'VON_HAND' && wert !== 'AUTOMATISCH') {
    throw new TypeError(FORMFEHLER);
  }
  return wert;
}

/** Verengt eine Zeile der Uebersicht oder scheitert. */
function parseVorgangZeile(wert: unknown): VorgangZeile {
  const zeile = objekt(wert);
  return {
    id: zahl(zeile.id),
    nummer: zahl(zeile.nummer),
    titel: text(zeile.titel),
    firma: text(zeile.firma),
    phase: phase(zeile.phase),
    abgeschlossen: jaNein(zeile.abgeschlossen),
    letzteAktivitaet: text(zeile.letzteAktivitaet),
  };
}

function parseZeilen(wert: unknown): readonly VorgangZeile[] {
  return liste(wert).map(parseVorgangZeile);
}

/** Verengt die Antwort der Uebersicht oder scheitert. */
export function parseVorgaengeUebersicht(wert: unknown): VorgaengeUebersicht {
  const antwort = objekt(wert);
  return {
    vorgaenge: parseZeilen(antwort.vorgaenge),
    gesamt: zahl(antwort.gesamt),
  };
}

/** Verengt die Vorgaenge einer Firma oder scheitert. */
export function parseVorgaengeDerFirma(wert: unknown): VorgaengeDerFirma {
  const antwort = objekt(wert);
  return {
    offene: parseZeilen(antwort.offene),
    abgeschlossene: parseZeilen(antwort.abgeschlossene),
  };
}

/** Verengt eine Zuordnung oder scheitert. */
function parseZuordnung(wert: unknown): Zuordnung {
  const zuordnung = objekt(wert);
  return {
    id: zahl(zuordnung.id),
    name: text(zuordnung.name),
    aktiv: jaNein(zuordnung.aktiv),
  };
}

/** Verengt einen Eintrag der Historie oder scheitert. */
export function parseEintrag(wert: unknown): Eintrag {
  const eintrag = objekt(wert);
  return {
    id: zahl(eintrag.id),
    art: eintragsart(eintrag.art),
    text: textOderNull(eintrag.text),
    geschehenAm: text(eintrag.geschehenAm),
    herkunft: herkunft(eintrag.herkunft),
    dateiName: textOderNull(eintrag.dateiName),
    dateiGroesse: zahlOderNull(eintrag.dateiGroesse),
    geaendertAm: textOderNull(eintrag.geaendertAm),
  };
}

/** Verengt einen Vorgang samt Historie oder scheitert. */
export function parseVorgang(wert: unknown): Vorgang {
  const vorgang = objekt(wert);
  const partner = vorgang.ansprechpartner;
  return {
    id: zahl(vorgang.id),
    nummer: zahl(vorgang.nummer),
    titel: text(vorgang.titel),
    phase: phase(vorgang.phase),
    abgeschlossen: jaNein(vorgang.abgeschlossen),
    abschlusswahrscheinlichkeit: zahlOderNull(vorgang.abschlusswahrscheinlichkeit),
    entscheidungErwartetAm: textOderNull(vorgang.entscheidungErwartetAm),
    firma: parseZuordnung(vorgang.firma),
    ansprechpartner: partner === null ? null : parseZuordnung(partner),
    historie: liste(vorgang.historie).map(parseEintrag),
  };
}

/** Verengt die Antwort des Anlegens oder scheitert. */
export function parseVorgangAngelegt(wert: unknown): VorgangAngelegt {
  const antwort = objekt(wert);
  return {
    id: zahl(antwort.id),
    nummer: zahl(antwort.nummer),
  };
}

/**
 * Die Uebersicht (Kriterien 1–4).
 *
 * Suchtext und Schalter gehen ueber `URLSearchParams` in die Adresse: Ein Titel mit `&` oder `%`
 * waere in einer zusammengesetzten Zeichenkette ein zweiter Parameter statt ein Suchtext.
 *
 * Als einziger Weg dieses Moduls nimmt die Uebersicht ein Abbruchsignal: Sie ist der einzige, den
 * die Oberflaeche waehrend des Tippens mehrfach anstoesst.
 */
export function vorgaengeUebersicht(
  suche: string,
  auchAbgeschlossene: boolean,
  signal?: AbortSignal,
): Promise<VorgaengeUebersicht> {
  const parameter = new URLSearchParams({
    suche,
    auchAbgeschlossene: String(auchAbgeschlossene),
  });
  return apiJson(
    `/api/vorgaenge?${parameter.toString()}`,
    { methode: 'GET', signal },
    parseVorgaengeUebersicht,
  );
}

/** Legt einen Vorgang an; die Antwort traegt Kennung und Nummer (Kriterium 5). */
export function vorgangAnlegen(eingabe: VorgangEingabe): Promise<VorgangAngelegt> {
  return apiJson('/api/vorgaenge', { methode: 'POST', rumpf: eingabe }, parseVorgangAngelegt);
}

/** Liest den Vorgang samt Historie (Kriterien 9, 15). */
export function vorgangLesen(id: number): Promise<Vorgang> {
  return apiJson(`/api/vorgaenge/${id}`, { methode: 'GET' }, parseVorgang);
}

/** Schreibt Titel und Zuordnungen fort (Kriterium 6); die Antwort traegt keinen Rumpf. */
export function vorgangAendern(id: number, eingabe: VorgangEingabe): Promise<void> {
  return apiOhneInhalt(`/api/vorgaenge/${id}`, { methode: 'PUT', rumpf: eingabe });
}

/** Schliesst den Vorgang ab (Kriterium 11). */
export function vorgangAbschliessen(id: number): Promise<void> {
  return apiOhneInhalt(`/api/vorgaenge/${id}/abschliessen`, { methode: 'POST' });
}

/** Eroeffnet den Vorgang wieder (Kriterium 11). */
export function vorgangWiederEroeffnen(id: number): Promise<void> {
  return apiOhneInhalt(`/api/vorgaenge/${id}/wiedereroeffnen`, { methode: 'POST' });
}

/**
 * Die Vorgaenge einer Firma (Kriterium 12).
 *
 * Ein eigener Weg neben der Detailantwort der Firma und nicht in sie eingebettet (E2): Das
 * Backend-Modul `firma` weiss nichts vom Vorgang, und das soll so bleiben.
 */
export function vorgaengeDerFirma(firmaId: number): Promise<VorgaengeDerFirma> {
  return apiJson(`/api/firmen/${firmaId}/vorgaenge`, { methode: 'GET' }, parseVorgaengeDerFirma);
}

/**
 * Fuegt der Historie einen Eintrag hinzu (Kriterien 13, 14).
 *
 * Das Formular baut die Maske, weil nur sie weiss, welche Felder die gewaehlte Art mitbringt —
 * `art`, `geschehenAm`, dazu `text` oder `datei`. Hier laeuft es unveraendert durch.
 */
export function eintragHinzufuegen(vorgangId: number, formular: FormData): Promise<void> {
  return apiFormular(`/api/vorgaenge/${vorgangId}/eintraege`, formular);
}

/** Schreibt Text und Zeitpunkt eines Eintrags fort (Kriterien 18, 19). */
export function eintragAendern(
  vorgangId: number,
  eintragId: number,
  aenderung: EintragAenderung,
): Promise<void> {
  return apiOhneInhalt(`/api/vorgaenge/${vorgangId}/eintraege/${eintragId}`, {
    methode: 'PUT',
    rumpf: aenderung,
  });
}

/**
 * Der Weg zur Datei eines Anhangs — ein Pfad, kein Aufruf (E14, Kriterium 17).
 *
 * Er gehoert in ein `href` mit `download`. Der Server liefert die Datei mit
 * `Content-Disposition: attachment`, also speichert der Browser sie, statt sie anzuzeigen.
 */
export function anhangPfad(vorgangId: number, eintragId: number): string {
  return `/api/vorgaenge/${vorgangId}/eintraege/${eintragId}/datei`;
}
