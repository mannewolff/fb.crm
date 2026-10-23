import { apiJson, apiOhneInhalt } from './client';

/**
 * Die Wege ins Konto und aus ihm heraus.
 *
 * Die Typen hier sind die Gegenstuecke zu `AccountResponse` und `SetupStatusResponse` im
 * Backend; aendert sich dort ein Feld, aendert es sich hier mit (CLAUDE-react.md). Jede
 * Antwort geht durch einen Parser: Was ueber das Netz kommt, ist `unknown`, bis es geprueft
 * ist.
 */

/** Die drei Rollen eines Kontos, so wie `Role` im Backend sie benennt (Plan #414, A8). */
export type Rolle = 'C_LEVEL' | 'EMPLOYEE' | 'READ_ONLY';

/** Das angemeldete Konto, so wie `AccountResponse` es liefert. */
export interface Konto {
  readonly id: number;
  readonly displayName: string;
  readonly email: string;
  readonly role: Rolle;
}

/** Ob die Instanz schon eingerichtet ist (E11). */
export interface SetupStatus {
  readonly initialized: boolean;
}

const FORMFEHLER = 'Die Antwort der Schnittstelle hat nicht die erwartete Form.';

function istObjekt(wert: unknown): wert is Record<string, unknown> {
  return typeof wert === 'object' && wert !== null;
}

/**
 * Verengt auf eine der drei Rollen. Eine unbekannte Rolle ist kein Konto mit weniger Rechten,
 * sondern eine Antwort in falscher Form — die Oberflaeche raet nicht, was sie bedeuten koennte.
 */
export function istRolle(wert: unknown): wert is Rolle {
  return wert === 'C_LEVEL' || wert === 'EMPLOYEE' || wert === 'READ_ONLY';
}

/** Verengt die Antwort auf ein Konto oder scheitert — nie ein `as` (CLAUDE-react.md). */
export function parseMe(wert: unknown): Konto {
  if (!istObjekt(wert)) {
    throw new TypeError(FORMFEHLER);
  }
  const { id, displayName, email, role } = wert;
  if (
    typeof id !== 'number' ||
    typeof displayName !== 'string' ||
    typeof email !== 'string' ||
    !istRolle(role)
  ) {
    throw new TypeError(FORMFEHLER);
  }
  return { id, displayName, email, role };
}

/** Verengt die Antwort des Einrichtungsstands oder scheitert. */
export function parseSetupStatus(wert: unknown): SetupStatus {
  if (!istObjekt(wert) || typeof wert.initialized !== 'boolean') {
    throw new TypeError(FORMFEHLER);
  }
  return { initialized: wert.initialized };
}

/**
 * Fordert einen Code fuer die Anmeldung an. Die Antwort ist fuer jede Adresse dieselbe (202 ohne
 * Rumpf) — ob es zu ihr ein Konto gibt, erfaehrt die Oberflaeche nicht (E13, E17).
 */
export function codeAnfordern(email: string): Promise<void> {
  return apiOhneInhalt('/api/auth/code', { methode: 'POST', rumpf: { email } });
}

/** Meldet mit dem Code an; die Sitzung kommt als HttpOnly-Cookie zurueck, nicht als Token. */
export function login(email: string, code: string): Promise<Konto> {
  return apiJson('/api/auth/login', { methode: 'POST', rumpf: { email, code } }, parseMe);
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

/**
 * Fordert den Code der Einrichtung an — die Felder von `SetupCodeRequest` im Backend. Ein falscher
 * Schluessel endet in 403 wie bisher; sonst 202 ohne Rumpf.
 */
export function einrichtungCodeAnfordern(
  bootstrapToken: string,
  displayName: string,
  email: string,
): Promise<void> {
  return apiOhneInhalt('/api/setup/code', {
    methode: 'POST',
    rumpf: { email, displayName, bootstrapToken },
  });
}

/**
 * Die Eingaben der Einrichtung — die Felder von `SetupRequest` im Backend. Ein Passwort und eine
 * Wiederholung der Adresse gibt es nicht mehr (E15): Den Vertipper faengt der Code ab.
 */
export interface Einrichtung {
  readonly bootstrapToken: string;
  readonly displayName: string;
  readonly email: string;
  readonly code: string;
}

/**
 * Richtet die Instanz mit dem Code ein (K3). Die Antwort ist das angelegte Konto, und die Sitzung kommt wie
 * beim Anmelden als HttpOnly-Cookie mit — der Betreiber ist danach angemeldet.
 */
export function setup(eingaben: Einrichtung): Promise<Konto> {
  return apiJson('/api/setup', { methode: 'POST', rumpf: eingaben }, parseMe);
}
