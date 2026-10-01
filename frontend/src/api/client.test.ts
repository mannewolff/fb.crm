import { afterEach, describe, expect, it, vi } from 'vitest';

import { ApiError, apiBlob, apiJson, apiOhneInhalt } from './client';
import { blobText } from '../test/blobText';

/** Gibt den Wert unveraendert zurueck — fuer Faelle, in denen nicht der Parser geprueft wird. */
const durchreichen = (wert: unknown): unknown => wert;

function problemAntwort(status: number, problem: unknown): Response {
  return new Response(JSON.stringify(problem), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  });
}

/**
 * Jeder Aufruf bekommt eine frische Antwort: Ein `Response`-Rumpf laesst sich nur einmal
 * lesen, und zwei Erwartungen auf dieselbe Instanz scheiterten an genau dieser Stelle.
 */
function fetchLiefert(bauen: () => Response) {
  return vi.spyOn(globalThis, 'fetch').mockImplementation(() => Promise.resolve(bauen()));
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('apiJson', () => {
  it('schickt das Sitzungs-Cookie mit und reicht den Rumpf an den Parser', async () => {
    const fetchMock = fetchLiefert(() => problemAntwort(200, { id: 7 }));

    const ergebnis = await apiJson('/api/test', { methode: 'GET' }, durchreichen);

    expect(ergebnis).toEqual({ id: 7 });
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/test',
      expect.objectContaining({ method: 'GET', credentials: 'same-origin' }),
    );
  });

  it('schickt ohne Rumpf weder Inhaltstyp noch Nutzlast', async () => {
    const fetchMock = fetchLiefert(() => problemAntwort(200, {}));

    await apiJson('/api/test', { methode: 'GET' }, durchreichen);

    const optionen = fetchMock.mock.calls[0][1];
    expect(optionen?.body).toBeUndefined();
    expect(optionen?.headers).toBeUndefined();
  });
});

describe('apiOhneInhalt', () => {
  it('schickt den Rumpf als JSON', async () => {
    const fetchMock = fetchLiefert(() => new Response(null, { status: 204 }));

    await apiOhneInhalt('/api/auth/logout', { methode: 'POST', rumpf: { a: 1 } });

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/auth/logout',
      expect.objectContaining({
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: '{"a":1}',
      }),
    );
  });

  it('nimmt eine Antwort ohne Inhalt an', async () => {
    fetchLiefert(() => new Response(null, { status: 204 }));

    await expect(apiOhneInhalt('/api/auth/logout', { methode: 'POST' })).resolves.toBeUndefined();
  });

  it('traegt DELETE ohne Rumpf hinaus (E19)', async () => {
    const fetchMock = fetchLiefert(() => new Response(null, { status: 204 }));

    await apiOhneInhalt('/api/angebote/9', { methode: 'DELETE' });

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/angebote/9',
      expect.objectContaining({ method: 'DELETE', credentials: 'same-origin' }),
    );
    const optionen = fetchMock.mock.calls[0][1];
    expect(optionen?.body).toBeUndefined();
    expect(optionen?.headers).toBeUndefined();
  });
});

describe('Fehlerantworten', () => {
  it('uebersetzt Problem Details in einen ApiError', async () => {
    fetchLiefert(() =>
      problemAntwort(400, {
        title: 'Ungueltige Eingabe',
        detail: 'Die Eingabe ist ungueltig.',
        fieldErrors: { email: ['ist ungueltig'] },
      }),
    );

    await expect(apiJson('/api/test', { methode: 'POST' }, durchreichen)).rejects.toBeInstanceOf(
      ApiError,
    );
  });

  it('traegt Status, Meldung und Feldfehler des Problems', async () => {
    fetchLiefert(() =>
      problemAntwort(400, {
        title: 'Ungueltige Eingabe',
        detail: 'Die Eingabe ist ungueltig.',
        fieldErrors: { email: ['ist ungueltig'], password: ['ist zu kurz'] },
      }),
    );

    await expect(apiJson('/api/test', { methode: 'POST' }, durchreichen)).rejects.toMatchObject({
      status: 400,
      message: 'Die Eingabe ist ungueltig.',
      fieldErrors: { email: ['ist ungueltig'], password: ['ist zu kurz'] },
    });
  });

  it('nimmt den Titel, wenn das Problem kein Detail traegt', async () => {
    fetchLiefert(() => problemAntwort(409, { title: 'Bereits eingerichtet' }));

    await expect(apiJson('/api/test', { methode: 'POST' }, durchreichen)).rejects.toMatchObject({
      status: 409,
      message: 'Bereits eingerichtet',
    });
  });

  it('laesst eine Antwort ohne JSON-Rumpf nicht unbehandelt entkommen', async () => {
    // 401 ohne Rumpf ist der Regelfall der Zugangsregel: Spring Security antwortet ueber
    // HttpStatusEntryPoint mit leerem Koerper. Ein ungeschuetztes response.json() wuerfe
    // hier einen SyntaxError statt eines ApiError.
    fetchLiefert(() => new Response(null, { status: 401 }));

    await expect(apiJson('/api/auth/me', { methode: 'GET' }, durchreichen)).rejects.toMatchObject({
      status: 401,
      fieldErrors: {},
    });
  });

  it('traegt bei einer Antwort ohne Meldung einen allgemeinen Text', async () => {
    fetchLiefert(() => new Response(null, { status: 500 }));

    await expect(apiJson('/api/test', { methode: 'GET' }, durchreichen)).rejects.toThrowError(
      'Die Anfrage konnte nicht ausgeführt werden.',
    );
  });

  it('verwirft Feldfehler, die nicht die erwartete Form haben', async () => {
    fetchLiefert(() =>
      problemAntwort(400, {
        detail: 'Die Eingabe ist ungueltig.',
        fieldErrors: { email: 'ist ungueltig', password: [7] },
      }),
    );

    await expect(apiJson('/api/test', { methode: 'POST' }, durchreichen)).rejects.toMatchObject({
      fieldErrors: {},
    });
  });

  it('verwirft ein Problem, das gar kein Objekt ist', async () => {
    fetchLiefert(() => problemAntwort(503, 'nicht verfuegbar'));

    await expect(apiJson('/api/test', { methode: 'GET' }, durchreichen)).rejects.toMatchObject({
      status: 503,
      fieldErrors: {},
    });
  });
});

describe('Formulardaten', () => {
  it('schickt das Formular als Rumpf, ohne einen Inhaltstyp zu setzen', async () => {
    const fetchMock = fetchLiefert(() => problemAntwort(201, { id: 4 }));
    const formular = new FormData();
    formular.append('datei', new File(['x'], 'anfrage.pdf'));

    await apiJson('/api/angebote/9/anlagen', { methode: 'POST', formular }, durchreichen);

    const optionen = fetchMock.mock.calls[0][1];
    // Ohne Kopf: Die `boundary` kennt erst der Browser, wenn er das FormData serialisiert.
    expect(optionen?.headers).toBeUndefined();
    expect(optionen?.body).toBe(formular);
  });

  it('laesst das Formular vor einem JSON-Rumpf den Vorrang', async () => {
    const fetchMock = fetchLiefert(() => problemAntwort(201, {}));
    const formular = new FormData();

    await apiJson('/api/test', { methode: 'POST', formular, rumpf: { a: 1 } }, durchreichen);

    const optionen = fetchMock.mock.calls[0][1];
    expect(optionen?.headers).toBeUndefined();
    expect(optionen?.body).toBe(formular);
  });
});

describe('apiBlob', () => {
  it('gibt den Rumpf der Antwort als Blob heraus', async () => {
    fetchLiefert(
      () =>
        new Response('inhalt', { status: 200, headers: { 'Content-Type': 'application/pdf' } }),
    );

    const blob = await apiBlob('/api/angebote/9/anlagen/3/inhalt', { methode: 'GET' });

    // Geprueft werden Art und Inhalt, nicht die Klasse: Darauf verlaesst sich der Aufrufer,
    // waehrend `instanceof` an der Testumgebung haengt (Issue #191).
    expect(blob.type).toBe('application/pdf');
    await expect(blobText(blob)).resolves.toBe('inhalt');
  });

  it('schickt das Sitzungs-Cookie mit', async () => {
    const fetchMock = fetchLiefert(() => new Response('inhalt', { status: 200 }));

    await apiBlob('/api/angebote/9/anlagen/3/inhalt', { methode: 'GET' });

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/angebote/9/anlagen/3/inhalt',
      expect.objectContaining({ method: 'GET', credentials: 'same-origin' }),
    );
  });

  it('wirft bei einer verschwundenen Anlage einen ApiError', async () => {
    fetchLiefert(() => problemAntwort(404, { detail: 'Die Anlage wurde nicht gefunden.' }));

    await expect(
      apiBlob('/api/angebote/9/anlagen/3/inhalt', { methode: 'GET' }),
    ).rejects.toMatchObject({ status: 404, message: 'Die Anlage wurde nicht gefunden.' });
  });
});
