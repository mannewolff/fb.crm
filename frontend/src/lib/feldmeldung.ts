import type { FieldErrors } from '../api/client';

/**
 * Die Meldungen des Servers zu einem Feld als ein Satz fuer den Hilfetext — oder nichts.
 *
 * `undefined` statt eines Leerstrings: MUI verknuepft den Hilfetext nur dann ueber
 * `aria-describedby` mit dem Feld, wenn es ihn gibt, und ein leerer Hilfetext waere eine
 * Beschreibung, die nichts beschreibt.
 */
export function meldungAm(fehler: FieldErrors, feld: string): string | undefined {
  const meldungen = fehler[feld] ?? [];
  return meldungen.length === 0 ? undefined : meldungen.join('; ');
}

/**
 * Der Hinweis, den eine Navigation im Zustand mitgibt — etwa „Das neue Passwort ist gesetzt."
 * auf dem Weg zur Anmeldeseite.
 *
 * Der Zustand einer Navigation ist `unknown`: Er ueberlebt ein Neuladen im Verlauf des Browsers
 * und kann von jeder Stelle stammen. Also wird er gelesen wie eine Antwort der Schnittstelle.
 */
export function hinweisAus(zustand: unknown): string | null {
  if (typeof zustand !== 'object' || zustand === null || !('hinweis' in zustand)) {
    return null;
  }
  return typeof zustand.hinweis === 'string' ? zustand.hinweis : null;
}
