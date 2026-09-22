import { vi } from 'vitest';

/**
 * Ein fetch-Doppel, das nach Methode und Pfad antwortet statt nach Reihenfolge.
 *
 * Mehrere Ansichten fragen beim Aufbau gleichzeitig: der AuthProvider nach der Sitzung, die
 * Anmeldeseite nach dem Einrichtungsstand. In welcher Reihenfolge die Effekte laufen, ist kein
 * Teil der Zusage — ein Doppel mit `mockResolvedValueOnce`-Kette hinge aber genau daran.
 *
 * Jeder Eintrag ist eine Fabrik, weil ein `Response`-Rumpf nur einmal gelesen werden kann.
 * Ein Aufruf ohne Eintrag scheitert laut, statt still eine leere Antwort zu liefern.
 */
export type Routen = Readonly<Record<string, () => Response | Promise<Response>>>;

export function fetchNachPfad(routen: Routen) {
  return vi.spyOn(globalThis, 'fetch').mockImplementation((ziel, init) => {
    const pfad = ziel instanceof Request ? ziel.url : ziel.toString();
    const schluessel = `${init?.method ?? 'GET'} ${pfad}`;
    const bauen = routen[schluessel];
    return bauen === undefined
      ? Promise.reject(new Error(`Unerwarteter Aufruf: ${schluessel}`))
      : Promise.resolve(bauen());
  });
}

/** Eine JSON-Antwort mit Status. */
export function json(status: number, rumpf: unknown): () => Response {
  return () =>
    new Response(JSON.stringify(rumpf), {
      status,
      headers: { 'Content-Type': 'application/json' },
    });
}

/** Eine Antwort ohne Rumpf. */
export function leer(status: number): () => Response {
  return () => new Response(null, { status });
}

/** Problem Details, wie der GlobalExceptionHandler sie liefert. */
export function problem(
  status: number,
  detail: string,
  fieldErrors?: Readonly<Record<string, readonly string[]>>,
): () => Response {
  return json(status, { title: 'Fehler', detail, fieldErrors });
}
