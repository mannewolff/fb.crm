import { ApiError } from '../api/client';
import type { FieldErrors } from '../api/client';

/**
 * Die beiden Fragen, die eine Maske oder eine Ansicht an einen Fehlschlag stellt.
 *
 * Sie stehen hier und nicht in jeder Ansicht, weil ein `catch` alles faengt — auch einen Abbruch
 * des Netzes oder einen Programmfehler im Parser. Wer dort blind auf `ursache.status` zugreift,
 * hat im schlechtesten Fall einen zweiten Fehler im Fehlerpfad. Beide Funktionen nehmen deshalb
 * `unknown` und geben etwas zurueck, das die Ansicht ohne weitere Pruefung verwenden kann.
 */

/** Wahr nur bei einer Antwort mit Status 404 — jeder andere Fehlschlag ist ein Ausfall. */
export function nichtGefunden(ursache: unknown): boolean {
  return ursache instanceof ApiError && ursache.status === 404;
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
