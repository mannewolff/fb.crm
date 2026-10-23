import { apiJson } from './client';
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
 *
 * Aendern, Stilllegen und Wiederaktivieren legen die gelesene Version als `If-Match` vor und
 * antworten mit dem neuen Stand samt erhoehter Version (#411, Kriterium 4; Plan #415, E19). Das
 * Anlegen legt keinen Stand vor (Kriterium 7).
 */

/** Ein Konto in der Ansicht „Mitarbeitende". */
export interface Mitarbeiter {
  readonly id: number;
  readonly displayName: string;
  readonly email: string;
  readonly role: Rolle;
  /** `false` heisst stillgelegt. */
  readonly aktiv: boolean;
  /** Der Stand, den Aendern, Stilllegen und Wiederaktivieren vorlegen (Plan #415, E19). */
  readonly version: number;
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
    version: zahl(konto.version),
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

/**
 * Schreibt Adresse, Name und Rolle fort (Kriterium 6).
 *
 * @param version der Stand, auf dem die Aenderung beruht
 */
export function mitarbeiterAendern(
  id: number,
  version: number,
  eingabe: MitarbeiterEingabe,
): Promise<Mitarbeiter> {
  return apiJson(
    `/api/mitarbeitende/${String(id)}`,
    { methode: 'PUT', rumpf: eingabe, version },
    parseMitarbeiter,
  );
}

/**
 * Legt das Konto still (Kriterium 7).
 *
 * @param version der Stand, auf dem das Stilllegen beruht
 */
export function mitarbeiterStilllegen(id: number, version: number): Promise<Mitarbeiter> {
  return apiJson(
    `/api/mitarbeitende/${String(id)}/stilllegen`,
    { methode: 'POST', version },
    parseMitarbeiter,
  );
}

/**
 * Nimmt das Konto wieder in Betrieb (Kriterium 7).
 *
 * @param version der Stand, auf dem das Wiederaktivieren beruht
 */
export function mitarbeiterAktivieren(id: number, version: number): Promise<Mitarbeiter> {
  return apiJson(
    `/api/mitarbeitende/${String(id)}/aktivieren`,
    { methode: 'POST', version },
    parseMitarbeiter,
  );
}
