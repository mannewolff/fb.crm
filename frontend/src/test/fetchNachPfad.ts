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
 *
 * Die Fabrik bekommt den Rumpf des Aufrufs. Wer ihn nicht braucht, nimmt ihn nicht an; wer ein
 * Formular abschickt (E21), liest darueber, was hinausging — `fetch.mock.calls` gibt ihn nur als
 * `BodyInit` heraus, und der laesst sich ohne Verzweigung nicht befragen.
 */
export type Routen = Readonly<
  Record<string, (rumpf: BodyInit | null) => Response | Promise<Response>>
>;

export function fetchNachPfad(routen: Routen) {
  return vi.spyOn(globalThis, 'fetch').mockImplementation((ziel, init) => {
    const pfad = ziel instanceof Request ? ziel.url : ziel.toString();
    const schluessel = `${init?.method ?? 'GET'} ${pfad}`;
    const bauen = routen[schluessel];
    return bauen === undefined
      ? Promise.reject(new Error(`Unerwarteter Aufruf: ${schluessel}`))
      : Promise.resolve(bauen(init?.body ?? null));
  });
}

/**
 * Eine Route, die das abgeschickte Formular herausreicht und dann antwortet.
 *
 * Sie scheitert laut, wenn der Rumpf keines ist: Ein stilles Weiterreichen liesse die Erwartungen
 * gegen ein leeres Formular laufen und waere gruen, ohne etwas geprueft zu haben.
 */
export function formularWeg(
  merke: (formular: FormData) => void,
  antwort: () => Response,
): (rumpf: BodyInit | null) => Response {
  return (rumpf) => {
    if (!(rumpf instanceof FormData)) {
      throw new TypeError('Der Rumpf dieses Aufrufs ist kein Formular.');
    }
    merke(rumpf);
    return antwort();
  };
}

/**
 * Der Rumpf eines Aufrufs als gelesenes JSON.
 *
 * Er scheitert laut, wenn der Rumpf keine Zeichenkette ist — aus demselben Grund wie
 * {@link formularWeg}: Ein stilles Weiterreichen liesse die Erwartungen gegen nichts laufen.
 */
export function alsJson(rumpf: BodyInit | null): unknown {
  if (typeof rumpf !== 'string') {
    throw new TypeError('Der Rumpf dieses Aufrufs ist kein JSON-Text.');
  }
  const gelesen: unknown = JSON.parse(rumpf);
  return gelesen;
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
