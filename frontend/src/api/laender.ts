import { apiJson } from './client';
import { liste, objekt, text } from './verengen';

/**
 * Die Laenderliste (Plan #461, E11).
 *
 * Der Typ ist das Gegenstueck zu `LandResponse` im Backend. Die Liste ist dieselbe, gegen die der
 * Server das Land der Firma prueft — eine zweite im Frontend liefe auseinander.
 */

/** Ein Land: Kuerzel nach ISO 3166-1 alpha-2 und deutscher Name. */
export interface Land {
  readonly kuerzel: string;
  readonly name: string;
}

/** Verengt die Antwort der Laenderliste oder scheitert. */
export function parseLaender(wert: unknown): readonly Land[] {
  return liste(wert).map((eintrag) => {
    const land = objekt(eintrag);
    return { kuerzel: text(land.kuerzel), name: text(land.name) };
  });
}

/** Alle Laender, sortiert nach dem deutschen Namen. */
export function laenderLaden(): Promise<readonly Land[]> {
  return apiJson('/api/laender', { methode: 'GET' }, parseLaender);
}
