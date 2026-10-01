/**
 * Die Rechen- und Schreibregeln der Arbeitszeit in der Oberflaeche (Issue #193, Plan #194).
 *
 * Reine Funktionen und kein React: Uhrzeit, Dauer und Monat stehen in der Monatsliste, im Kopf der
 * Ansicht und spaeter im Dialog. Hier stehen sie einmal und sind ohne Oberflaeche gegen die Zahlen
 * des Backends zu pruefen — dieselbe Begruendung wie bei `lib/abrechnung.ts`.
 *
 * <b>Gerechnet wird in ganzen Zahlen</b> (E5, wie `lib/geld.ts`): Die Dauer steht in Hundertstel-
 * Stunden, also 1,75 Std. als 175. Eine Gleitkommazahl dazwischen holte genau den Fehler zurueck,
 * den die ganzen Zahlen vermeiden, und die Summe eines Monats muesste dieselbe sein, die der Server
 * gleich zurueckschickt.
 *
 * <b>Dieselbe Rundung wie im Backend</b> ({@code Zeiteintrag#stundenAus}): Minuten durch 60,
 * kaufmaennisch auf zwei Stellen. Bei Viertelstunden ist das Ergebnis ohnehin exakt; die Rundung
 * greift nur dort, wo eine Uhrzeit das Raster verlassen hat — dann soll die Oberflaeche nicht eine
 * andere Dauer zeigen als der Bestand.
 *
 * <b>Was keine Uhrzeit, kein Monat ist, geht unveraendert heraus</b> — dieselbe Regel wie bei
 * {@link tagWort} in `lib/tag.ts`: Der Wert hat den Parser der Schnittstelle als Zeichenkette
 * passiert, und ein Platzhalter an seiner Stelle waere eine erfundene Angabe. Wo das Ergebnis eine
 * Zahl ist, steht statt des Platzhalters `null` — die Ansicht entscheidet dann, was sie zeigt.
 */

/** Eine Uhrzeit, wie das Backend ein `LocalTime` schreibt — „09:00:00"; Sekunden optional. */
const UHRZEIT = /^(\d{2}):(\d{2})(?::(\d{2}))?$/u;

/** Ein Monat, wie das Backend ein `YearMonth` schreibt — „2026-11". */
const MONAT = /^(\d{4})-(\d{2})$/u;

/** Die Schrittweite der Erfassung in Minuten (Issue #193, Antwort 6). */
const VIERTELSTUNDE = 15;

const MINUTEN_JE_STUNDE = 60;

const STUNDEN_JE_TAG = 24;

const MONATE_JE_JAHR = 12;

/** Hundert — der Faktor von Stunden auf Hundertstel-Stunden. */
const HUNDERT = 100;

/** Die Namen der Monate, wie der Kopf der Ansicht und der Leistungszeitraum sie schreiben. */
const MONATSNAMEN: readonly string[] = [
  'Januar',
  'Februar',
  'März',
  'April',
  'Mai',
  'Juni',
  'Juli',
  'August',
  'September',
  'Oktober',
  'November',
  'Dezember',
];

/** Stunde und Minute einer Uhrzeit, oder `null`, wenn der Wert keine ist. */
function geteilt(uhrzeit: string): { readonly stunde: number; readonly minute: number } | null {
  const treffer = UHRZEIT.exec(uhrzeit);
  if (treffer === null) {
    return null;
  }
  const stunde = parseInt(treffer[1], 10);
  const minute = parseInt(treffer[2], 10);
  // Die Sekunde gehoert nicht zur Uhrzeit dieser Anwendung, sie darf aber dastehen: Jackson
  // schreibt ein `LocalTime` als „09:00:00". Eine Sekunde ungleich null ist keine Uhrzeit des
  // Rasters — das entscheidet {@link imRaster} und nicht diese Zerlegung.
  if (stunde >= STUNDEN_JE_TAG || minute >= MINUTEN_JE_STUNDE) {
    return null;
  }
  return { stunde, minute };
}

/** Die Minuten seit Mitternacht, oder `null`, wenn der Wert keine Uhrzeit ist. */
function minutenSeitMitternacht(uhrzeit: string): number | null {
  const teile = geteilt(uhrzeit);
  return teile === null ? null : teile.stunde * MINUTEN_JE_STUNDE + teile.minute;
}

/**
 * Die Uhrzeit als Wort — „9:00".
 *
 * Ohne die fuehrende Null der Stunde und ohne Sekunden, genau wie die Meldung der Ueberschneidung
 * sie setzt ({@code ZeitenUeberschneidenSich}, A8): Beide nennen dieselbe Uhrzeit, und zwei
 * Schreibweisen daneben liessen den Leser zwei Eintraege vermuten.
 */
export function uhrzeitWort(uhrzeit: string): string {
  const teile = geteilt(uhrzeit);
  if (teile === null) {
    return uhrzeit;
  }
  return `${String(teile.stunde)}:${String(teile.minute).padStart(2, '0')}`;
}

/**
 * Die Uhrzeit als Wert eines Zeitfeldes — „09:00" (A16).
 *
 * Zweistellig und ohne Sekunden, denn genau das nimmt `input type="time"` an. Was keine Uhrzeit
 * ist, wird zum <b>leeren Feld</b> und nicht zum rohen Wert — anders als bei {@link uhrzeitWort}:
 * Ein Zeitfeld traegt eine Uhrzeit oder nichts, und der Browser verwirft alles andere ohnehin
 * stillschweigend. Dann lieber ein sichtbar leeres Feld als eine Eingabe, die niemand sieht.
 */
export function uhrzeitFeld(uhrzeit: string): string {
  const teile = geteilt(uhrzeit);
  if (teile === null) {
    return '';
  }
  return `${String(teile.stunde).padStart(2, '0')}:${String(teile.minute).padStart(2, '0')}`;
}

/** Die Zeitspanne einer Zeile als Wort — „9:00 bis 11:00" (A14). */
export function zeitspanneWort(von: string, bis: string): string {
  return `${uhrzeitWort(von)} bis ${uhrzeitWort(bis)}`;
}

/**
 * Ob die Uhrzeit auf einer Viertelstunde ohne Sekunden liegt (Antwort 6).
 *
 * Dieselbe Regel wie {@code Zeiteintrag#imRaster}. Sie steht hier nicht, um die Pruefung des
 * Servers zu ersetzen — der entscheidet und meldet am Feld (A19) —, sondern damit die Ansicht die
 * Dauer erst zeigt, wenn beide Uhrzeiten ueberhaupt erfassbar sind.
 */
export function imRaster(uhrzeit: string): boolean {
  const treffer = UHRZEIT.exec(uhrzeit);
  if (treffer === null || geteilt(uhrzeit) === null) {
    return false;
  }
  const sekunde = treffer[3];
  if (sekunde !== undefined && parseInt(sekunde, 10) !== 0) {
    return false;
  }
  return parseInt(treffer[2], 10) % VIERTELSTUNDE === 0;
}

/**
 * Die Dauer zwischen zwei Uhrzeiten in Hundertstel-Stunden, oder `null` (Kriterium 2).
 *
 * `null` heisst „daraus wird keine Dauer": eine der Angaben ist keine Uhrzeit, oder das Ende liegt
 * nicht nach dem Beginn. Die Ansicht zeigt dann keine Dauer — eine Null oder ein negativer Wert
 * waere eine Angabe, die niemand gerechnet hat.
 *
 * Gerundet wird wie im Backend ({@code Zeiteintrag#stundenAus}): kaufmaennisch auf zwei Stellen.
 * `+ MINUTEN_JE_STUNDE` im Zaehler bei doppeltem Nenner ist die Rundung fuer nicht negative Werte,
 * wie `+ 50` in {@link betrag}.
 */
export function dauerInHundertsteln(von: string, bis: string): number | null {
  const ab = minutenSeitMitternacht(von);
  const ende = minutenSeitMitternacht(bis);
  if (ab === null || ende === null || ende <= ab) {
    return null;
  }
  const minuten = ende - ab;
  return Math.floor((minuten * HUNDERT * 2 + MINUTEN_JE_STUNDE) / (MINUTEN_JE_STUNDE * 2));
}

/**
 * Eine Dauer in Hundertstel-Stunden als Wort — „1,75 Std.".
 *
 * Beide Dezimalstellen stehen immer da, wie bei {@link euro} und {@link dezimal}: In einer Spalte
 * stuenden sonst verschieden lange Zahlen untereinander.
 */
export function stundenWort(stundenInHundertsteln: number): string {
  // Mindestens drei Ziffern, damit „5" zu „0,05" wird und nicht zu „,5".
  const ziffern = String(stundenInHundertsteln).padStart(3, '0');
  return `${ziffern.slice(0, -2)},${ziffern.slice(-2)} Std.`;
}

/**
 * Ein Wert aus der Adresse als Monat, oder `null` (A13).
 *
 * Der Parameter `?monat=` kommt von aussen; was kein Monat ist, ist hier keiner — dann gilt der
 * laufende, und den kennt der Server (E4). Ein stiller Ersatzwert waere ein Monat, den niemand
 * gewaehlt hat.
 */
export function alsMonat(wert: string | null): string | null {
  if (wert === null) {
    return null;
  }
  const treffer = MONAT.exec(wert);
  if (treffer === null) {
    return null;
  }
  const monat = parseInt(treffer[2], 10);
  return monat >= 1 && monat <= MONATE_JE_JAHR ? wert : null;
}

/** Setzt Jahr und Monat wieder zu `JJJJ-MM` zusammen. */
function alsText(jahr: number, monat: number): string {
  return `${String(jahr).padStart(4, '0')}-${String(monat).padStart(2, '0')}`;
}

/**
 * Der laufende Monat als `JJJJ-MM`, aus der Ortszeit des Betrachters (A17).
 *
 * Aus den Feldern der Ortszeit gesetzt und nicht ueber `toISOString`, aus demselben Grund wie bei
 * {@link heute}: Das rechnet nach UTC um, und am letzten Abend eines Monats stuende oestlich von
 * Greenwich noch der alte da.
 *
 * <b>Hier entscheidet nicht der Server</b>, anders als in der Monatsliste (E4): Die Wahl des
 * Monats trifft der Betrachter, und sie geht als ausdrueckliche Angabe hinaus. Vorbelegt ist sie
 * mit dem Monat, in dem er gerade sitzt — die Liste der waehlbaren Monate muss dastehen, bevor
 * irgendeine Antwort da ist.
 */
export function laufenderMonat(): string {
  const jetzt = new Date();
  return alsText(jetzt.getFullYear(), jetzt.getMonth() + 1);
}

/** Der Monat vor diesem — am Jahresanfang der Dezember des Vorjahres. */
export function vormonat(monat: string): string {
  const treffer = MONAT.exec(monat);
  if (treffer === null) {
    return monat;
  }
  const jahr = parseInt(treffer[1], 10);
  const nummer = parseInt(treffer[2], 10);
  return nummer === 1 ? alsText(jahr - 1, MONATE_JE_JAHR) : alsText(jahr, nummer - 1);
}

/** Der Monat nach diesem — am Jahresende der Januar des Folgejahres. */
export function folgemonat(monat: string): string {
  const treffer = MONAT.exec(monat);
  if (treffer === null) {
    return monat;
  }
  const jahr = parseInt(treffer[1], 10);
  const nummer = parseInt(treffer[2], 10);
  return nummer === MONATE_JE_JAHR ? alsText(jahr + 1, 1) : alsText(jahr, nummer + 1);
}

/** Der Monat als Name mit Jahr — „November 2026"; was kein Monat ist, geht unveraendert heraus. */
export function monatWort(monat: string): string {
  const treffer = MONAT.exec(monat);
  if (treffer === null) {
    return monat;
  }
  const name = MONATSNAMEN[parseInt(treffer[2], 10) - 1];
  return name === undefined ? monat : `${name} ${treffer[1]}`;
}
