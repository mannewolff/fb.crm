import { apiJson } from './client';

/**
 * Der Versionsstand der Instanz, so wie `InstanceResponse` im Backend ihn liefert (Issue #26).
 *
 * Der Pfad verlangt eine Sitzung (E10) — gefragt wird also erst in der angemeldeten Oberflaeche.
 */
export interface Instanz {
  readonly version: string;
}

/** Verengt die Antwort oder scheitert — nie ein `as` (CLAUDE-react.md). */
export function parseInstance(wert: unknown): Instanz {
  if (typeof wert !== 'object' || wert === null || !('version' in wert)) {
    throw new TypeError('Die Antwort der Schnittstelle hat nicht die erwartete Form.');
  }
  if (typeof wert.version !== 'string') {
    throw new TypeError('Die Antwort der Schnittstelle hat nicht die erwartete Form.');
  }
  return { version: wert.version };
}

export function instance(): Promise<Instanz> {
  return apiJson('/api/instance', { methode: 'GET' }, parseInstance);
}
