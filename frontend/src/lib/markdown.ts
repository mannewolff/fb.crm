/**
 * Der Leser fuer die Dokumentation unter `docs/` (Issue #379; Plan #378, E3, E4, E7, E9).
 *
 * Bewusst ein eigener, beschraenkter Leser statt `react-markdown` samt `remark`/`micromark` (E3):
 * Er kennt genau die Bauformen, die die Dokumentation benutzt —
 *
 * - `#` als Titel des Teils (E7), `##` und `###` als Abschnitte mit Ankernamen (E9),
 * - Fliesstext, Aufzaehlung mit `-`/`*` und numerierte Aufzaehlung mit `1.`,
 * - Befehlsblock zwischen zwei ```-Zaeunen; eine Sprachangabe am oeffnenden Zaun wird gelesen und
 *   verworfen, ein nicht geschlossener Block endet am Dateiende,
 * - in der Zeile Betonung (`**…**`), Schnipsel (Backticks) und Verweis (`[text](ziel)`).
 *
 * Ergebnis sind React-freie Daten. Die Darstellung baut daraus Elemente und setzt nie rohes HTML
 * in die Seite (CLAUDE-security.md); HTML in der Quelle bleibt hier Klartext.
 *
 * **Der Leser verschluckt nie etwas** (E4): Was keiner bekannten Form entspricht — `####`, eine
 * Tabellenzeile, unvollstaendige Auszeichnung —, steht mit seinem Wortlaut im Fliesstext. Er wirft
 * auch nicht: Eine Ausnahme braeche die Ansicht.
 */

export type Inline =
  | { art: 'klartext'; text: string }
  | { art: 'betont'; text: string }
  | { art: 'schnipsel'; text: string }
  | { art: 'verweis'; text: string; ziel: string };

export type Block =
  | { art: 'ueberschrift'; ebene: 2 | 3; text: string; anker: string }
  | { art: 'text'; inhalt: Inline[] }
  | { art: 'liste'; numeriert: boolean; eintraege: Inline[][] }
  | { art: 'befehl'; inhalt: string };

export interface Dokument {
  titel: string;
  bloecke: Block[];
}

const ZEILENENDE = /\r?\n/u;
const LEERZEILE = /^\s*$/u;
const ZAUN_AUF = /^```[^`]*$/u;
const ZAUN_ZU = /^```\s*$/u;
const TITEL = /^# +(\S.*)$/u;
const ABSCHNITT = /^(##|###) +(\S.*)$/u;
const PUNKT = /^[-*] +(\S.*)$/u;
const NUMMER = /^\d+\. +(\S.*)$/u;
const FORTSETZUNG = /^\s+\S/u;

/** Ein Ankername fuer einen Text ohne Buchstaben und Ziffern — ein leeres `id` gaebe es nicht. */
const ANKER_OHNE_TEXT = 'abschnitt';

/**
 * Der Ankername einer Ueberschrift (E9).
 *
 * Kleinschreibung, Umlaute und `ß` ausgeschrieben, alles Uebrige ausser Buchstaben und Ziffern zu
 * `-`, mehrfache `-` zusammengezogen, Rand-`-` entfernt. Ist der Name in `schonBelegt` schon
 * vergeben, bekommt er eine laufende Nummer: die zweite „Sicherung" heisst `sicherung-2`.
 */
export function ankerName(text: string, schonBelegt: readonly string[]): string {
  const grund =
    text
      .toLowerCase()
      .replaceAll('ä', 'ae')
      .replaceAll('ö', 'oe')
      .replaceAll('ü', 'ue')
      .replaceAll('ß', 'ss')
      .replace(/[^\p{L}\p{N}]+/gu, '-')
      .replace(/^-|-$/gu, '') || ANKER_OHNE_TEXT;
  if (!schonBelegt.includes(grund)) {
    return grund;
  }
  let nummer = 2;
  while (schonBelegt.includes(`${grund}-${nummer}`)) {
    nummer += 1;
  }
  return `${grund}-${nummer}`;
}

/** Der Stand des Lesers zwischen zwei Zeilen. */
interface Lesestand {
  readonly zeilen: readonly string[];
  /** Die naechste noch nicht gelesene Zeile. */
  i: number;
  readonly bloecke: Block[];
  readonly belegt: string[];
  titel: string;
  // Hoechstens eines von beiden ist offen: Wer das eine beginnt, schliesst vorher das andere.
  absatz: string[];
  liste: { numeriert: boolean; eintraege: string[] } | null;
}

export function leseMarkdown(quelle: string): Dokument {
  const stand: Lesestand = {
    zeilen: quelle.split(ZEILENENDE),
    i: 0,
    bloecke: [],
    belegt: [],
    titel: '',
    absatz: [],
    liste: null,
  };
  while (stand.i < stand.zeilen.length) {
    const zeile = stand.zeilen[stand.i];
    stand.i += 1;
    leseZeile(stand, zeile);
  }
  schliesse(stand);

  return { titel: stand.titel, bloecke: stand.bloecke };
}

/** Die Zeile in den Stand: Die erste Blockart, die sie nimmt, gilt; sonst ist sie Fliesstext. */
function leseZeile(stand: Lesestand, zeile: string): void {
  if (
    leseBefehl(stand, zeile) ||
    leseLeerzeile(stand, zeile) ||
    leseTitel(stand, zeile) ||
    leseAbschnitt(stand, zeile) ||
    leseListenzeile(stand, zeile)
  ) {
    return;
  }
  if (stand.liste !== null) {
    schliesse(stand);
  }
  stand.absatz.push(zeile.trim());
}

/** Schliesst den offenen Absatz oder die offene Liste ab. */
function schliesse(stand: Lesestand): void {
  if (stand.absatz.length > 0) {
    stand.bloecke.push({ art: 'text', inhalt: leseInline(stand.absatz.join(' ')) });
    stand.absatz = [];
  }
  if (stand.liste !== null) {
    stand.bloecke.push({
      art: 'liste',
      numeriert: stand.liste.numeriert,
      eintraege: stand.liste.eintraege.map(leseInline),
    });
    stand.liste = null;
  }
}

function leseBefehl(stand: Lesestand, zeile: string): boolean {
  if (!ZAUN_AUF.test(zeile)) {
    return false;
  }
  schliesse(stand);
  const inhalt: string[] = [];
  while (stand.i < stand.zeilen.length && !ZAUN_ZU.test(stand.zeilen[stand.i])) {
    inhalt.push(stand.zeilen[stand.i]);
    stand.i += 1;
  }
  // Ueber den schliessenden Zaun hinweg; ohne ihn steht `i` danach hinter dem Dateiende.
  stand.i += 1;
  stand.bloecke.push({ art: 'befehl', inhalt: inhalt.join('\n') });
  return true;
}

function leseLeerzeile(stand: Lesestand, zeile: string): boolean {
  if (!LEERZEILE.test(zeile)) {
    return false;
  }
  schliesse(stand);
  return true;
}

/** Nur die erste `#`-Ueberschrift ist der Titel (E7); jede weitere bleibt Fliesstext (E4). */
function leseTitel(stand: Lesestand, zeile: string): boolean {
  const titelTreffer = TITEL.exec(zeile);
  if (titelTreffer === null || stand.titel !== '') {
    return false;
  }
  schliesse(stand);
  stand.titel = titelTreffer[1].trim();
  return true;
}

function leseAbschnitt(stand: Lesestand, zeile: string): boolean {
  const abschnitt = ABSCHNITT.exec(zeile);
  if (abschnitt === null) {
    return false;
  }
  schliesse(stand);
  const text = abschnitt[2].trim();
  const anker = ankerName(text, stand.belegt);
  stand.belegt.push(anker);
  stand.bloecke.push({ art: 'ueberschrift', ebene: abschnitt[1] === '##' ? 2 : 3, text, anker });
  return true;
}

/** Ein Listeneintrag oder die eingerueckte Fortsetzung des letzten. */
function leseListenzeile(stand: Lesestand, zeile: string): boolean {
  const eintrag = listenEintrag(zeile);
  if (eintrag !== null) {
    let liste = stand.liste;
    if (liste?.numeriert !== eintrag.numeriert) {
      schliesse(stand);
      liste = { numeriert: eintrag.numeriert, eintraege: [] };
      stand.liste = liste;
    }
    liste.eintraege.push(eintrag.text);
    return true;
  }
  if (stand.liste !== null && FORTSETZUNG.test(zeile)) {
    stand.liste.eintraege[stand.liste.eintraege.length - 1] += ` ${zeile.trim()}`;
    return true;
  }
  return false;
}

function listenEintrag(zeile: string): { numeriert: boolean; text: string } | null {
  const punkt = PUNKT.exec(zeile);
  if (punkt !== null) {
    return { numeriert: false, text: punkt[1].trim() };
  }
  const nummer = NUMMER.exec(zeile);
  if (nummer !== null) {
    return { numeriert: true, text: nummer[1].trim() };
  }
  return null;
}

interface Treffer {
  stueck: Inline;
  /** Die erste Stelle hinter der Auszeichnung. */
  ende: number;
}

/** Die Inline-Folge einer Zeile. Unvollstaendige Auszeichnung bleibt Klartext (E4). */
function leseInline(text: string): Inline[] {
  const folge: Inline[] = [];
  let klartext = '';
  const gibKlartext = (): void => {
    if (klartext !== '') {
      folge.push({ art: 'klartext', text: klartext });
      klartext = '';
    }
  };

  let i = 0;
  while (i < text.length) {
    const treffer = schnipselAb(text, i) ?? betontAb(text, i) ?? verweisAb(text, i);
    if (treffer === null) {
      klartext += text[i];
      i += 1;
    } else {
      gibKlartext();
      folge.push(treffer.stueck);
      i = treffer.ende;
    }
  }
  gibKlartext();
  return folge;
}

function schnipselAb(text: string, start: number): Treffer | null {
  if (text[start] !== '`') {
    return null;
  }
  const zu = text.indexOf('`', start + 1);
  // Ein leerer Schnipsel zeigte nichts an und verschluckte damit seine Zeichen.
  if (zu <= start + 1) {
    return null;
  }
  return { stueck: { art: 'schnipsel', text: text.slice(start + 1, zu) }, ende: zu + 1 };
}

function betontAb(text: string, start: number): Treffer | null {
  if (!text.startsWith('**', start)) {
    return null;
  }
  const zu = text.indexOf('**', start + 2);
  if (zu <= start + 2) {
    return null;
  }
  return { stueck: { art: 'betont', text: text.slice(start + 2, zu) }, ende: zu + 2 };
}

function verweisAb(text: string, start: number): Treffer | null {
  if (text[start] !== '[') {
    return null;
  }
  const textZu = schliessendeKlammer(text, start, '[', ']');
  if (textZu === -1 || text[textZu + 1] !== '(') {
    return null;
  }
  const zielZu = schliessendeKlammer(text, textZu + 1, '(', ')');
  if (zielZu === -1) {
    return null;
  }
  const verweistext = text.slice(start + 1, textZu);
  const ziel = text.slice(textZu + 2, zielZu);
  if (verweistext === '' || ziel === '') {
    return null;
  }
  return { stueck: { art: 'verweis', text: verweistext, ziel }, ende: zielZu + 1 };
}

/**
 * Die Stelle der Klammer, die die bei `start` geoeffnete schliesst, oder -1. Gezaehlt wird die
 * Tiefe, damit Klammern im Verweistext und im Ziel stehen duerfen.
 */
function schliessendeKlammer(text: string, start: number, auf: string, zu: string): number {
  let tiefe = 0;
  for (let j = start; j < text.length; j += 1) {
    if (text[j] === auf) {
      tiefe += 1;
    } else if (text[j] === zu) {
      tiefe -= 1;
      if (tiefe === 0) {
        return j;
      }
    }
  }
  return -1;
}
