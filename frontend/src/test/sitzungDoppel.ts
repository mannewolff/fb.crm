import { afterEach } from 'vitest';

import type { Konto, Rolle } from '../api/auth';
import type { AuthWert } from '../auth/AuthContext';

/**
 * Ein Doppel fuer `auth/AuthContext`, das eine angemeldete Sitzung ohne `AuthProvider` festlegt
 * (Plan #426, E17).
 *
 * Fuer eine Ansicht, die neben den Rechten auch das angemeldete Konto selbst braucht — etwa die
 * {@link ZeiteintragMaske}, die bei „Alle" das eigene Konto vorbelegt. Anders als
 * `rechteDoppel` ersetzt es die Sitzung und nicht die Frage nach den Rechten: Rolle und Konto
 * kommen damit aus <b>einer</b> Quelle, und das echte `useRechte` liest die Rolle aus genau dieser
 * Sitzung. Eingebunden wird es je Testdatei:
 *
 * ```ts
 * vi.mock('../auth/AuthContext', () => import('../test/sitzungDoppel'));
 * ```
 *
 * Ohne Aufruf von {@link alsRolle} gilt C-Level; nach jedem Testfall faellt es dorthin zurueck.
 */

/** Das angemeldete Konto jeder Probe; die Kennung ist die, mit der die Proben rechnen. */
export const ANGEMELDET: Omit<Konto, 'role'> = {
  id: 1,
  displayName: 'Manfred Wolff',
  email: 'manfred@firma.de',
};

const VORGABE: Rolle = 'C_LEVEL';

let rolle: Rolle = VORGABE;

afterEach(() => {
  rolle = VORGABE;
});

/** Legt die Rolle der Sitzung fuer den laufenden Testfall fest. */
export function alsRolle(neu: Rolle): void {
  rolle = neu;
}

/** Der Wert einer gesetzten Sitzung; die Wege, die sie aendern, tun in einer Probe nichts. */
export function useAuth(): AuthWert {
  return {
    sitzung: { status: 'angemeldet', konto: { ...ANGEMELDET, role: rolle } },
    codeAnfordern: () => Promise.resolve(),
    anmelden: () => Promise.resolve(),
    einrichten: () => Promise.resolve(),
    abmelden: () => Promise.resolve(),
    rolleNeuLesen: () => Promise.resolve(),
  };
}
