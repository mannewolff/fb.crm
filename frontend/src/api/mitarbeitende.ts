import { apiJson, apiOhneInhalt } from './client';
import type { Rolle } from './auth';
import { istRolle } from './auth';
import { FORMFEHLER, jaNein, liste, objekt, text, zahl } from './verengen';

/**
 * Die Wege der Kontenverwaltung (Plan #414, A12; fachliche Quelle #409, Kriterien 3 bis 8).
 *
 * Die Typen sind die Gegenstuecke zu `MitarbeiterRequest`, `MitarbeiterResponse` und
 * `MitarbeitendeResponse` im Backend; aendert sich dort ein Feld, aendert es sich hier mit
 * (CLAUDE-react.md). Jede Antwort geht durch einen Parser — kein `as`.
 *
 * Einen Leseweg fuer ein einzelnes Konto gibt es nicht: Die Maske findet das Konto in der Liste.
 * Stilllegen und Wiederaktivieren antworten ohne Rumpf, wie bei der Firma; den neuen Stand liest
 * die Ansicht ueber die Liste.
 */

/** Ein Konto in der Ansicht „Mitarbeitende". */
export interface Mitarbeiter {
  readonly id: number;
  readonly displayName: string;
  readonly email: string;
  readonly role: Rolle;
  /** `false` heisst stillgelegt. */
  readonly aktiv: boolean;
  /**
   * Nur in der Antwort auf das Anlegen: ob eine Begruessung hinausging (A13). Sonst fehlt das Feld
   * in der Antwort, und hier steht `null`.
   */
  readonly begruessungMailVersandt: boolean | null;
}

/** Die Eingaben der Maske — fuer Anlegen und Aendern dieselben, alle drei Pflicht. */
export interface MitarbeiterEingabe {
  readonly email: string;
  readonly displayName: string;
  readonly role: Rolle;
}

function rolle(wert: unknown): Rolle {
  if (!istRolle(wert)) {
    throw new TypeError(FORMFEHLER);
  }
  return wert;
}

/** Verengt ein Konto oder scheitert. */
export function parseMitarbeiter(wert: unknown): Mitarbeiter {
  const konto = objekt(wert);
  return {
    id: zahl(konto.id),
    displayName: text(konto.displayName),
    email: text(konto.email),
    role: rolle(konto.role),
    aktiv: jaNein(konto.aktiv),
    begruessungMailVersandt:
      konto.begruessungMailVersandt === undefined ? null : jaNein(konto.begruessungMailVersandt),
  };
}

/** Verengt die Liste oder scheitert; die Reihenfolge ist die des Servers. */
export function parseMitarbeitende(wert: unknown): readonly Mitarbeiter[] {
  return liste(objekt(wert).mitarbeitende).map(parseMitarbeiter);
}

/** Jedes Konto, aktiv oder stillgelegt (Kriterium 3). */
export function mitarbeitendeListe(): Promise<readonly Mitarbeiter[]> {
  return apiJson('/api/mitarbeitende', { methode: 'GET' }, parseMitarbeitende);
}

/** Legt ein Konto an; die Antwort sagt, ob eine Begruessung hinausging (Kriterium 4). */
export function mitarbeiterAnlegen(eingabe: MitarbeiterEingabe): Promise<Mitarbeiter> {
  return apiJson('/api/mitarbeitende', { methode: 'POST', rumpf: eingabe }, parseMitarbeiter);
}

/** Schreibt Adresse, Name und Rolle fort (Kriterium 6). */
export function mitarbeiterAendern(id: number, eingabe: MitarbeiterEingabe): Promise<Mitarbeiter> {
  return apiJson(
    `/api/mitarbeitende/${String(id)}`,
    { methode: 'PUT', rumpf: eingabe },
    parseMitarbeiter,
  );
}

/** Legt das Konto still (Kriterium 7). */
export function mitarbeiterStilllegen(id: number): Promise<void> {
  return apiOhneInhalt(`/api/mitarbeitende/${String(id)}/stilllegen`, { methode: 'POST' });
}

/** Nimmt das Konto wieder in Betrieb (Kriterium 7). */
export function mitarbeiterAktivieren(id: number): Promise<void> {
  return apiOhneInhalt(`/api/mitarbeitende/${String(id)}/aktivieren`, { methode: 'POST' });
}
