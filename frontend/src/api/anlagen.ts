import { apiBlob, apiJson, apiOhneInhalt } from './client';
import { FORMFEHLER, liste, objekt, text, zahl } from './verengen';

/**
 * Die Wege zu den Anlagen eines Angebots: lesen, hochladen, Inhalt holen, loeschen (Issue #148).
 *
 * Die Typen sind die Gegenstuecke zu `AngebotAnlageResponse` und `AngebotAnlagenResponse` im
 * Backend; aendert sich dort ein Feld, aendert es sich hier mit (CLAUDE-react.md). Jede Antwort
 * geht durch einen Parser: Was ueber das Netz kommt, ist `unknown`, bis es geprueft ist — kein
 * `as`.
 *
 * Ein eigenes Modul und kein Anhang an `api/angebote.ts`, aus demselben Grund wie bei
 * `api/kommentare.ts`: Der Bereich laedt seine Anlagen selbst und nicht mit dem Angebot, die Wege
 * gehoeren also nicht zum Angebot, sondern neben es.
 *
 * Der Zeitpunkt bleibt hier der Text, den das Backend geschickt hat, und wird nicht zu `Date`
 * verengt: Gesetzt wird er in der Darstellung ueber `lib/zeitpunkt.ts`.
 */

/**
 * Die Arten, zu denen es eine Vorschau gibt — das Gegenstueck zu `Vorschauart` im Backend.
 *
 * Sie ist die einzige Angabe, an der die Oberflaeche entscheidet, ob sie „Anzeigen" anbietet, und
 * sie ist dieselbe, der auch der `Content-Type` des Inhaltswegs folgt (Plan #150, E5).
 */
export type Vorschauart = 'PNG' | 'JPEG' | 'GIF' | 'WEBP' | 'PDF';

const VORSCHAUARTEN: readonly Vorschauart[] = ['PNG', 'JPEG', 'GIF', 'WEBP', 'PDF'];

/** Eine Anlage am Angebot (Kriterien 1 und 4). */
export interface Anlage {
  readonly id: number;
  readonly dateiName: string;
  /** Groesse des Inhalts in Byte — gesetzt wird sie ueber `lib/dateigroesse.ts`. */
  readonly groesse: number;
  /** Die am Inhalt erkannte Art, oder `null`, wenn es keine Vorschau gibt. */
  readonly vorschauArt: Vorschauart | null;
  /** Zeitpunkt des Hochladens als Zeitstempel-Text, wie Jackson einen `Instant` schreibt. */
  readonly createdAt: string;
}

/** Die Anlagen eines Angebots, neueste zuerst (Kriterium 4). */
export interface Anlagen {
  readonly anlagen: readonly Anlage[];
}

/**
 * Verengt die Vorschauart oder scheitert.
 *
 * Eine Art, die diese Anwendung nicht kennt, ist ein Formfehler und kein `null`: Wer sie still
 * zu „keine Vorschau" machte, verbaergte, dass Backend und Oberflaeche auseinandergelaufen sind
 * — und die Anlage saehe aus wie eine Tabelle, obwohl der Server ein Bild meldet.
 */
function vorschauart(wert: unknown): Vorschauart | null {
  if (wert === null) {
    return null;
  }
  const name = text(wert);
  const bekannt = VORSCHAUARTEN.find((art) => art === name);
  if (bekannt === undefined) {
    throw new TypeError(FORMFEHLER);
  }
  return bekannt;
}

/** Verengt eine Anlage oder scheitert. */
export function parseAnlage(wert: unknown): Anlage {
  const anlage = objekt(wert);
  return {
    id: zahl(anlage.id),
    dateiName: text(anlage.dateiName),
    groesse: zahl(anlage.groesse),
    vorschauArt: vorschauart(anlage.vorschauArt),
    createdAt: text(anlage.createdAt),
  };
}

/** Verengt die Anlagen eines Angebots oder scheitert. */
export function parseAnlagen(wert: unknown): Anlagen {
  const antwort = objekt(wert);
  return { anlagen: liste(antwort.anlagen).map(parseAnlage) };
}

function pfad(angebotId: number): string {
  return `/api/angebote/${String(angebotId)}/anlagen`;
}

/** Die Anlagen des Angebots, neueste zuerst (Kriterium 4). */
export function anlagenLesen(angebotId: number): Promise<Anlagen> {
  return apiJson(pfad(angebotId), { methode: 'GET' }, parseAnlagen);
}

/**
 * Laedt eine Datei als Anlage hoch; die Antwort traegt sie samt Kennung und Zeitpunkt
 * (Kriterium 2).
 *
 * Damit steht die neue Anlage ohne zweiten Aufruf an ihrem Platz — der Bereich muss die Liste
 * nicht neu laden. Der Teil heisst `datei`, wie ihn `AngebotAnlagenController` erwartet; eine
 * Datei je Aufruf, mehr nimmt kein Weg entgegen.
 */
export function anlageHochladen(angebotId: number, datei: File): Promise<Anlage> {
  const formular = new FormData();
  formular.append('datei', datei);
  return apiJson(pfad(angebotId), { methode: 'POST', formular }, parseAnlage);
}

/**
 * Der Weg zum Inhalt einer Anlage.
 *
 * Er steht als eigene Funktion da, weil ihn zwei Seiten brauchen: {@link anlageInhalt} fuer den
 * Abruf und der Verweis „Herunterladen" als sein `href`. Zwei Abschriften derselben Zeile liefen
 * mit der Zeit auseinander, und eine davon zeigte dann ins Leere.
 */
export function anlageInhaltPfad(angebotId: number, anlageId: number): string {
  return `${pfad(angebotId)}/${String(anlageId)}/inhalt`;
}

/**
 * Holt den Inhalt einer Anlage als `Blob` (Kriterium 15).
 *
 * Fuer die Vorschau: Sie zeigt den Inhalt aus einer Objekt-URL statt den Inhaltsweg einzubetten,
 * damit die Sicherheits-Kopfzeilen der Auslieferung unberuehrt bleiben (Plan #150, E7).
 */
export function anlageInhalt(angebotId: number, anlageId: number): Promise<Blob> {
  return apiBlob(anlageInhaltPfad(angebotId, anlageId), { methode: 'GET' });
}

/** Loescht die Anlage (Kriterium 10) — die Antwort traegt keinen Inhalt. */
export function anlageLoeschen(angebotId: number, anlageId: number): Promise<void> {
  return apiOhneInhalt(`${pfad(angebotId)}/${String(anlageId)}`, { methode: 'DELETE' });
}
