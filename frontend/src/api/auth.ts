import { apiJson, apiOhneInhalt } from './client';

/**
 * Die Wege ins Konto und aus ihm heraus.
 *
 * Die Typen hier sind die Gegenstuecke zu `AccountResponse` und `SetupStatusResponse` im
 * Backend; aendert sich dort ein Feld, aendert es sich hier mit (CLAUDE-react.md). Jede
 * Antwort geht durch einen Parser: Was ueber das Netz kommt, ist `unknown`, bis es geprueft
 * ist.
 */

/** Das angemeldete Konto, so wie `AccountResponse` es liefert. */
export interface Konto {
  readonly id: number;
  readonly displayName: string;
  readonly email: string;
}

/** Ob die Instanz schon eingerichtet ist (E11). */
export interface SetupStatus {
  readonly initialized: boolean;
}

const FORMFEHLER = 'Die Antwort der Schnittstelle hat nicht die erwartete Form.';

function istObjekt(wert: unknown): wert is Record<string, unknown> {
  return typeof wert === 'object' && wert !== null;
}

/** Verengt die Antwort auf ein Konto oder scheitert — nie ein `as` (CLAUDE-react.md). */
export function parseMe(wert: unknown): Konto {
  if (!istObjekt(wert)) {
    throw new TypeError(FORMFEHLER);
  }
  const { id, displayName, email } = wert;
  if (typeof id !== 'number' || typeof displayName !== 'string' || typeof email !== 'string') {
    throw new TypeError(FORMFEHLER);
  }
  return { id, displayName, email };
}

/** Verengt die Antwort des Einrichtungsstands oder scheitert. */
export function parseSetupStatus(wert: unknown): SetupStatus {
  if (!istObjekt(wert) || typeof wert.initialized !== 'boolean') {
    throw new TypeError(FORMFEHLER);
  }
  return { initialized: wert.initialized };
}

/** Meldet an; die Sitzung kommt als HttpOnly-Cookie zurueck, nicht als Token. */
export function login(email: string, password: string): Promise<Konto> {
  return apiJson('/api/auth/login', { methode: 'POST', rumpf: { email, password } }, parseMe);
}

/** Meldet ab; das Cookie wird serverseitig geleert. */
export function logout(): Promise<void> {
  return apiOhneInhalt('/api/auth/logout', { methode: 'POST' });
}

/** Liest das eigene Konto — die einzige Quelle des Sitzungszustands im Frontend. */
export function me(): Promise<Konto> {
  return apiJson('/api/auth/me', { methode: 'GET' }, parseMe);
}

/** Fragt, ob die Instanz schon ein Konto hat (E11). */
export function setupStatus(): Promise<SetupStatus> {
  return apiJson('/api/setup/status', { methode: 'GET' }, parseSetupStatus);
}
