import { ApiError } from '../api/client';
import type { FieldErrors } from '../api/client';
import { zeitpunktKurz } from './zeitpunkt';

/**
 * Die Fragen, die eine Maske oder eine Ansicht an einen Fehlschlag stellt.
 *
 * Sie stehen hier und nicht in jeder Ansicht, weil ein `catch` alles faengt — auch einen Abbruch
 * des Netzes oder einen Programmfehler im Parser. Wer dort blind auf `ursache.status` zugreift,
 * hat im schlechtesten Fall einen zweiten Fehler im Fehlerpfad. Alle Funktionen nehmen deshalb
 * `unknown` und geben etwas zurueck, das die Ansicht ohne weitere Pruefung verwenden kann.
 */

/** Wahr nur bei einer Antwort mit Status 404 — jeder andere Fehlschlag ist ein Ausfall. */
export function nichtGefunden(ursache: unknown): boolean {
  return ursache instanceof ApiError && ursache.status === 404;
}

/**
 * Wahr nur bei einer Antwort mit Status 403 — die Zugangsregel der API hat die Rolle abgewiesen.
 * Wie bei {@link nichtGefunden} ist jeder andere Fehlschlag kein Verbot.
 */
export function verboten(ursache: unknown): boolean {
  return ursache instanceof ApiError && ursache.status === 403;
}

/**
 * Wahr nur bei einer Antwort mit Status 409 — die Anfrage widerspricht dem Stand, etwa beim letzten
 * aktiven C-Level (#409, Kriterium 8). Ihr Grund steht in der Meldung des Servers; jeder andere
 * Fehlschlag ist ein Ausfall.
 */
export function konflikt(ursache: unknown): boolean {
  return ursache instanceof ApiError && ursache.status === 409;
}

/** Die feldweisen Meldungen des Servers, oder nichts, wenn der Fehlschlag keine traegt. */
export function feldMeldungen(ursache: unknown): FieldErrors {
  return ursache instanceof ApiError ? ursache.fieldErrors : {};
}

/**
 * Die Meldung des Servers zu einem Fehlschlag, oder der eigene Ersatztext.
 *
 * Gedacht fuer die fachliche Abweisung, deren Grund nur der Server kennt — eine Rechnung, die sich
 * im Zustand des Augenblicks nicht stellen laesst (409). Dort waere ein eigener Satz der Ansicht
 * eine zweite, aermere Auskunft neben der vorhandenen.
 *
 * Der Ersatz greift, wo gar keine Antwort ankam: Ein abgebrochenes Netz traegt keine Meldung, und
 * die Ansicht soll dann ihren eigenen Satz zeigen statt den einer Ausnahme aus dem Programm. Was
 * der Server selbst nicht sagen will, sagt er auch nicht — unerwartete Fehler kommen durchgaengig
 * als ein Satz ohne Innenleben heraus (`GlobalExceptionHandler`).
 */
export function serverMeldung(ursache: unknown, ersatz: string): string {
  return ursache instanceof ApiError ? ursache.message : ersatz;
}

/** Was der Bearbeitungskonflikt meldet: ein geaenderter oder ein geloeschter Eintrag (E16). */
export type Konfliktart = 'veraltet' | 'gelöscht';

/** Was die Ansicht zu einem Bearbeitungskonflikt zeigt — die Art waehlt den Weg weiter. */
export interface Konfliktangaben {
  readonly art: Konfliktart;
  readonly satz: string;
}

function istObjekt(wert: unknown): wert is Record<string, unknown> {
  return typeof wert === 'object' && wert !== null;
}

/** Die Erweiterung `konflikt` des Problems, oder `null`, wo es keine traegt. */
function konfliktErweiterung(ursache: unknown): Record<string, unknown> | null {
  if (!(ursache instanceof ApiError) || !istObjekt(ursache.problem)) {
    return null;
  }
  const erweiterung = ursache.problem.konflikt;
  return istObjekt(erweiterung) ? erweiterung : null;
}

/**
 * Wahr nur, wenn der Server die Aenderung abgewiesen hat, weil sich der Stand seit dem Oeffnen
 * geaendert hat (Plan #415, E4).
 *
 * Erkannt an der Erweiterung `konflikt` und **nicht am Status**: Das Projekt meldet acht weitere
 * fachliche Lagen mit 409 (etwa das stornierte Angebot), und keine davon bietet „Neu laden" an.
 */
export function istBearbeitungskonflikt(ursache: unknown): boolean {
  return konfliktErweiterung(ursache) !== null;
}

/**
 * Der eine Satz zur Abweisung und ihre Art, oder `null`, wo kein Bearbeitungskonflikt vorliegt
 * (#411, Kriterien 2 und 6).
 *
 * Drei Formen je Art: mit Urheber, als eigene Aenderung aus einem anderen Fenster — das entscheidet
 * der Server (`eigenesFenster`, E15), nicht ein Vergleich von Namen hier — und ohne Urheber, wenn
 * der Server keinen kennt. Der Zeitpunkt steht, wo er bekannt ist, knapp nach
 * {@link zeitpunktKurz}. Eine unbekannte Art gilt als `veraltet`: Neu laden ist dann der Weg, der
 * nichts verliert.
 */
export function konfliktAngaben(ursache: unknown, jetzt: Date = new Date()): Konfliktangaben | null {
  const erweiterung = konfliktErweiterung(ursache);
  if (erweiterung === null) {
    return null;
  }
  const art: Konfliktart = erweiterung.art === 'gelöscht' ? 'gelöscht' : 'veraltet';
  const verb = art === 'gelöscht' ? 'gelöscht' : 'geändert';
  const zeit =
    typeof erweiterung.geaendertAm === 'string'
      ? ` (${zeitpunktKurz(erweiterung.geaendertAm, jetzt)})`
      : '';
  if (erweiterung.eigenesFenster === true) {
    return { art, satz: `Du hast diesen Eintrag inzwischen in einem anderen Fenster ${verb}${zeit}.` };
  }
  if (typeof erweiterung.geaendertVon === 'string') {
    return { art, satz: `${erweiterung.geaendertVon} hat diesen Eintrag inzwischen ${verb}${zeit}.` };
  }
  return { art, satz: `Inzwischen hat jemand diesen Eintrag ${verb}${zeit}.` };
}
