import { afterEach, vi } from 'vitest';

import type { Rolle } from '../api/auth';
import type * as RechteModul from '../auth/rechte';

/**
 * Ein Doppel fuer `auth/rechte`, das die Rolle der Sitzung ohne `AuthProvider` festlegt.
 *
 * Eine Ansicht, die {@link RechteModul.useRechte} fragt, braucht sonst die Sitzung — also einen
 * `AuthProvider` und eine Antwort auf `GET /api/auth/me` in jedem Testfall, auch in denen, die mit
 * der Rolle nichts zu tun haben. Das Doppel ersetzt nur die Frage nach der Sitzung: Welche Rechte
 * eine Rolle traegt, kommt weiter aus dem echten {@link RechteModul.rechteAus}, damit der Test
 * gegen dieselbe Tabelle laeuft wie die Oberflaeche.
 *
 * Eingebunden wird es je Testdatei:
 *
 * ```ts
 * vi.mock('../auth/rechte', () => import('../test/rechteDoppel'));
 * ```
 *
 * Ohne Aufruf von {@link alsRolle} gilt C-Level; nach jedem Testfall faellt es dorthin zurueck.
 */
const echt = await vi.importActual<typeof RechteModul>('../auth/rechte');

const VORGABE: Rolle = 'C_LEVEL';

let rolle: Rolle = VORGABE;

afterEach(() => {
  rolle = VORGABE;
});

/** Legt die Rolle der Sitzung fuer den laufenden Testfall fest. */
export function alsRolle(neu: Rolle): void {
  rolle = neu;
}

export const rechteAus = echt.rechteAus;
export const rollenname = echt.rollenname;

export function useRechte(): RechteModul.Rechte {
  return echt.rechteAus(rolle);
}
