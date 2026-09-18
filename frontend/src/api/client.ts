/**
 * Der einzige Weg der Oberflaeche zur Schnittstelle.
 *
 * Drei Zusagen macht dieses Modul, und jede hat einen Grund:
 *
 * <ul>
 *   <li><b>`credentials: 'same-origin'`</b> — die Sitzung steckt im HttpOnly-Cookie. Kein
 *       Token liegt im JS-Speicher, also traegt jede Anfrage das Cookie und sonst nichts
 *       (CLAUDE-security.md).</li>
 *   <li><b>Fehler an der Quelle</b> — das Backend antwortet mit RFC-9457 Problem Details
 *       (`GlobalExceptionHandler`). Hier werden sie zu {@link ApiError}; die Ansichten
 *       sehen nie eine rohe Antwort.</li>
 *   <li><b>Kein Vertrauen in die Form</b> — die Antwort ist `unknown`, bis ein Parser sie
 *       verengt hat. Kein `as` (CLAUDE-react.md).</li>
 * </ul>
 */

/** Feldweise Meldungen der Bean Validation, wie sie das Backend unter `fieldErrors` fuehrt. */
export type FieldErrors = Readonly<Record<string, readonly string[]>>;

/** Was die Oberflaeche sagt, wenn die Antwort keine eigene Meldung mitbringt. */
export const ALLGEMEINE_MELDUNG = 'Die Anfrage konnte nicht ausgeführt werden.';

/** Eine Antwort, die nicht in Ordnung war — mit allem, was die Ansicht darueber wissen darf. */
export class ApiError extends Error {
  readonly status: number;
  readonly fieldErrors: FieldErrors;

  constructor(status: number, meldung: string, fieldErrors: FieldErrors) {
    super(meldung);
    this.name = 'ApiError';
    this.status = status;
    this.fieldErrors = fieldErrors;
  }
}

/** Die beiden Methoden, die diese Anwendung kennt. */
type Methode = 'GET' | 'POST';

export interface Anfrage {
  readonly methode: Methode;
  readonly rumpf?: unknown;
}

function istObjekt(wert: unknown): wert is Record<string, unknown> {
  return typeof wert === 'object' && wert !== null;
}

function textOder(wert: unknown, ersatz: string): string {
  return typeof wert === 'string' ? wert : ersatz;
}

/**
 * Nimmt nur, was die Form von `fieldErrors` hat: Feldname auf eine Liste von Meldungen.
 * Alles andere faellt weg — eine halb gelesene Feldliste waere schlechter als keine.
 */
function feldFehler(wert: unknown): FieldErrors {
  if (!istObjekt(wert)) {
    return {};
  }
  const gelesen: Record<string, readonly string[]> = {};
  for (const [feld, meldungen] of Object.entries(wert)) {
    if (Array.isArray(meldungen) && meldungen.every((m) => typeof m === 'string')) {
      gelesen[feld] = meldungen;
    }
  }
  return gelesen;
}

/**
 * Liest den Rumpf, ohne an ihm zu scheitern.
 *
 * Nicht jede Fehlerantwort traegt JSON: Die Zugangsregel antwortet ueber
 * `HttpStatusEntryPoint` mit leerem 401. Ein ungeschuetztes `json()` wuerfe dort einen
 * SyntaxError, und die Ansicht saehe statt „nicht angemeldet" einen Programmfehler.
 */
async function rumpfLesen(antwort: Response): Promise<unknown> {
  try {
    return await antwort.json();
  } catch {
    // Kein JSON — dann traegt die Antwort eben keine Meldung. Der Statuscode genuegt.
    return null;
  }
}

async function fehlerAus(antwort: Response): Promise<ApiError> {
  const problem = await rumpfLesen(antwort);
  if (!istObjekt(problem)) {
    return new ApiError(antwort.status, ALLGEMEINE_MELDUNG, {});
  }
  const meldung = textOder(problem.detail, textOder(problem.title, ALLGEMEINE_MELDUNG));
  return new ApiError(antwort.status, meldung, feldFehler(problem.fieldErrors));
}

async function anfragen(pfad: string, anfrage: Anfrage): Promise<Response> {
  const mitRumpf = anfrage.rumpf !== undefined;
  const antwort = await fetch(pfad, {
    method: anfrage.methode,
    credentials: 'same-origin',
    headers: mitRumpf ? { 'Content-Type': 'application/json' } : undefined,
    body: mitRumpf ? JSON.stringify(anfrage.rumpf) : undefined,
  });
  if (!antwort.ok) {
    throw await fehlerAus(antwort);
  }
  return antwort;
}

/** Ruft auf und verengt die Antwort ueber den Parser der jeweiligen Schnittstelle. */
export async function apiJson<T>(
  pfad: string,
  anfrage: Anfrage,
  parse: (wert: unknown) => T,
): Promise<T> {
  const antwort = await anfragen(pfad, anfrage);
  return parse(await antwort.json());
}

/** Ruft auf, wo die Antwort keinen Inhalt traegt (204). */
export async function apiOhneInhalt(pfad: string, anfrage: Anfrage): Promise<void> {
  await anfragen(pfad, anfrage);
}
