import { describe, expect, test } from 'vitest';

import { kanbanClient } from './kanban-client.mjs';

const HOST = 'https://board.example';

const TOKEN = 'geheim-tbx-0815';

const KONFIG = { toolbox: { host: HOST } };

/** Eine Antwort, wie `fetch` sie liefert — nur mit dem, was der Client liest. */
function antwort(status, inhalt, koepfe = {}) {
  const text = inhalt === undefined ? '' : JSON.stringify(inhalt);
  return {
    status,
    ok: status >= 200 && status < 300,
    headers: { get: (name) => koepfe[name.toLowerCase()] ?? null },
    text: async () => text,
  };
}

/**
 * Ein eingesetztes `fetch`, das die Antworten der Reihe nach ausgibt und jeden Aufruf
 * mitschreibt; dazu eine Warte-Funktion, die nur mitschreibt und nicht wartet.
 */
function aufbau(...antworten) {
  const aufrufe = [];
  const gewartet = [];
  const fetch = async (url, init) => {
    aufrufe.push({ url, ...init });
    return antworten.shift();
  };
  const warte = async (ms) => {
    gewartet.push(ms);
  };
  const client = kanbanClient({ fetch, env: { TBX_TOKEN: TOKEN }, konfig: KONFIG, warte });
  return { client, aufrufe, gewartet };
}

describe('karten', () => {
  test('zieht Spalte → Karten[] zu einer flachen Liste zusammen, jede Karte mit ihrer Spalte', async () => {
    const a = { id: 1, number: 10, title: 'A' };
    const b = { id: 2, number: 20, title: 'B' };
    const { client, aufrufe } = aufbau(antwort(200, { BACKLOG: [a], IN_REVIEW: [b] }));

    const karten = await client.karten();

    expect(karten).toEqual([
      { ...a, column: 'BACKLOG' },
      { ...b, column: 'IN_REVIEW' },
    ]);
    expect(aufrufe).toHaveLength(1);
    expect(aufrufe[0].url).toBe(`${HOST}/api/kanban/items`);
    expect(aufrufe[0].method).toBe('GET');
  });
});

describe('anlegen', () => {
  test('legt im Backlog an, direkt, mit Idempotency-Key, und liefert die Karte', async () => {
    const angelegt = { id: 7, number: 352, title: 'T', column: 'BACKLOG' };
    const { client, aufrufe } = aufbau(antwort(201, angelegt));

    const karte = await client.anlegen('T', 'B', 'mutation-Foo.java');

    expect(karte).toEqual(angelegt);
    expect(aufrufe[0].url).toBe(`${HOST}/api/kanban/items`);
    expect(aufrufe[0].method).toBe('POST');
    expect(JSON.parse(aufrufe[0].body)).toEqual({
      title: 'T',
      body: 'B',
      column: 'BACKLOG',
      direct: true,
    });
    expect(aufrufe[0].headers['Idempotency-Key']).toBe('mutation-Foo.java');
    expect(aufrufe[0].headers['Content-Type']).toBe('application/json');
  });
});

describe('bodySetzen', () => {
  test('schreibt per PUT auf die interne ID und schickt Titel und Body mit', async () => {
    const { client, aufrufe } = aufbau(antwort(200, { id: 7 }));

    await client.bodySetzen(7, 'T', 'B');

    expect(aufrufe[0].url).toBe(`${HOST}/api/kanban/items/7`);
    expect(aufrufe[0].method).toBe('PUT');
    expect(JSON.parse(aufrufe[0].body)).toEqual({ title: 'T', body: 'B' });
  });
});

describe('verlauf und kommentieren', () => {
  test('verlauf liest die Aktivitaet der internen ID', async () => {
    const eintraege = [{ type: 'CREATED', createdAt: '2026-10-01T10:00:00Z' }];
    const { client, aufrufe } = aufbau(antwort(200, eintraege));

    expect(await client.verlauf(7)).toEqual(eintraege);
    expect(aufrufe[0].url).toBe(`${HOST}/api/kanban/items/7/activity`);
    expect(aufrufe[0].method).toBe('GET');
  });

  test('kommentieren schreibt den Text an die interne ID', async () => {
    const { client, aufrufe } = aufbau(antwort(201, { id: 99 }));

    await client.kommentieren(7, 'x');

    expect(aufrufe[0].url).toBe(`${HOST}/api/kanban/items/7/comments`);
    expect(aufrufe[0].method).toBe('POST');
    expect(JSON.parse(aufrufe[0].body)).toEqual({ body: 'x' });
  });

  test('eine Antwort ohne Inhalt liefert null', async () => {
    const { client } = aufbau(antwort(204));

    expect(await client.kommentieren(7, 'x')).toBeNull();
  });
});

describe('interne ID statt Kartennummer', () => {
  test('eine Karte mit id 7 und number 352 fuehrt auf /api/kanban/items/7', async () => {
    const karte = { id: 7, number: 352, title: 'T', body: 'B' };
    const { client, aufrufe } = aufbau(
      antwort(200, { BACKLOG: [karte] }),
      antwort(200, karte),
      antwort(200, []),
      antwort(201, {}),
    );

    const [gefunden] = await client.karten();
    await client.bodySetzen(gefunden.id, gefunden.title, 'neu');
    await client.verlauf(gefunden.id);
    await client.kommentieren(gefunden.id, 'x');

    for (const aufruf of aufrufe.slice(1)) {
      expect(aufruf.url).toMatch(new RegExp(`^${HOST}/api/kanban/items/7(/|$)`));
      expect(aufruf.url).not.toContain('352');
    }
  });
});

describe('Token', () => {
  test('jeder Aufruf traegt X-Kanban-Token mit dem Wert aus TBX_TOKEN', async () => {
    const { client, aufrufe } = aufbau(
      antwort(200, {}),
      antwort(201, {}),
      antwort(200, {}),
      antwort(200, []),
      antwort(201, {}),
    );

    await client.karten();
    await client.anlegen('T', 'B', 'k');
    await client.bodySetzen(7, 'T', 'B');
    await client.verlauf(7);
    await client.kommentieren(7, 'x');

    expect(aufrufe).toHaveLength(5);
    for (const aufruf of aufrufe) {
      expect(aufruf.headers['X-Kanban-Token']).toBe(TOKEN);
    }
  });

  test('fehlt TBX_TOKEN, wirft die Fabrik und nennt die Variable', () => {
    expect(() => kanbanClient({ fetch: async () => {}, env: {}, konfig: KONFIG })).toThrow(
      /TBX_TOKEN/,
    );
  });
});

describe('429 mit Retry-After', () => {
  test('wartet einmal und wiederholt — genau zwei Aufrufe, der zweite gelingt', async () => {
    const { client, aufrufe, gewartet } = aufbau(
      antwort(429, undefined, { 'retry-after': '1' }),
      antwort(200, { BACKLOG: [] }),
    );

    expect(await client.karten()).toEqual([]);
    expect(aufrufe).toHaveLength(2);
    expect(gewartet).toHaveLength(1);
    expect(gewartet[0]).toBeGreaterThanOrEqual(1000);
  });

  test('die Wiederholung traegt denselben Idempotency-Key', async () => {
    const { client, aufrufe } = aufbau(
      antwort(429, undefined, { 'retry-after': '2' }),
      antwort(201, { id: 7 }),
    );

    await client.anlegen('T', 'B', 'k-1');

    expect(aufrufe.map((a) => a.headers['Idempotency-Key'])).toEqual(['k-1', 'k-1']);
  });

  test('ein zweites 429 wirft, statt erneut zu warten', async () => {
    const { client, aufrufe, gewartet } = aufbau(
      antwort(429, undefined, { 'retry-after': '1' }),
      antwort(429, undefined, { 'retry-after': '1' }),
    );

    await expect(client.karten()).rejects.toThrow(/429/);
    expect(aufrufe).toHaveLength(2);
    expect(gewartet).toHaveLength(1);
  });

  test('ein 429 ohne Retry-After wirft ohne Wiederholung', async () => {
    const { client, aufrufe, gewartet } = aufbau(antwort(429, undefined));

    await expect(client.karten()).rejects.toThrow(/429/);
    expect(aufrufe).toHaveLength(1);
    expect(gewartet).toHaveLength(0);
  });

  test('ein 429 mit unlesbarem Retry-After wirft ohne Wiederholung', async () => {
    const { client, aufrufe } = aufbau(antwort(429, undefined, { 'retry-after': 'bald' }));

    await expect(client.karten()).rejects.toThrow(/429/);
    expect(aufrufe).toHaveLength(1);
  });
});

describe('Fehlschlaege', () => {
  const ROH = `interner Fehler mit Token ${TOKEN} und Stacktrace`;

  for (const status of [500, 401]) {
    test(`ein ${status} wirft mit Status und Pfad, ohne Token und ohne rohen Antworttext`, async () => {
      const { client } = aufbau(antwort(status, { message: ROH }));

      const fehler = await client.bodySetzen(7, 'T', 'B').catch((e) => e);

      expect(fehler).toBeInstanceOf(Error);
      expect(fehler.message).toContain(String(status));
      expect(fehler.message).toContain('PUT /api/kanban/items/7');
      expect(fehler.message).not.toContain(TOKEN);
      expect(fehler.message).not.toContain('interner Fehler');
    });
  }

  test('ein Netzfehler wirft mit Methode und Pfad, ohne Token', async () => {
    const fetch = async () => {
      throw new TypeError('fetch failed');
    };
    const client = kanbanClient({ fetch, env: { TBX_TOKEN: TOKEN }, konfig: KONFIG });

    const fehler = await client.karten().catch((e) => e);

    expect(fehler.message).toContain('GET /api/kanban/items');
    expect(fehler.message).not.toContain(TOKEN);
  });

  test('eine Antwort, die kein JSON ist, wirft ohne den rohen Text', async () => {
    const kaputt = {
      status: 200,
      ok: true,
      headers: { get: () => null },
      text: async () => `<html>${TOKEN}</html>`,
    };
    const { client } = aufbau(kaputt);

    const fehler = await client.karten().catch((e) => e);

    expect(fehler.message).toContain('GET /api/kanban/items');
    expect(fehler.message).not.toContain(TOKEN);
    expect(fehler.message).not.toContain('<html>');
  });
});

describe('Host', () => {
  test('fehlt toolbox.host, wirft die Fabrik und nennt Datei und Feld', () => {
    for (const konfig of [{}, { toolbox: {} }, { toolbox: { host: '' } }]) {
      expect(() =>
        kanbanClient({ fetch: async () => {}, env: { TBX_TOKEN: TOKEN }, konfig }),
      ).toThrow(/\.claude\/workflow\.config\.json.*toolbox\.host|toolbox\.host.*\.claude\/workflow\.config\.json/);
    }
  });

  test('ein Schraegstrich am Ende des Hosts verdoppelt sich nicht im Pfad', async () => {
    const aufrufe = [];
    const fetch = async (url) => {
      aufrufe.push(url);
      return antwort(200, {});
    };
    const client = kanbanClient({
      fetch,
      env: { TBX_TOKEN: TOKEN },
      konfig: { toolbox: { host: `${HOST}/` } },
    });

    await client.karten();

    expect(aufrufe).toEqual([`${HOST}/api/kanban/items`]);
  });
});
