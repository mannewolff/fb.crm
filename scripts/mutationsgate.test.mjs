import { execFileSync, spawnSync } from 'node:child_process';
import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { afterEach, describe, expect, test, vi } from 'vitest';

import { kartenbody, kartentitel } from './mutationsbericht.mjs';
import { BERICHTSPFAD, main } from './mutationsgate.mjs';

/** Siehe bump-version.test.mjs: Vitest laeuft mit `frontend` als Arbeitsverzeichnis. */
const REPO = execFileSync('git', ['rev-parse', '--show-toplevel'], { encoding: 'utf8' }).trim();

const SKRIPT = path.join(REPO, 'scripts', 'mutationsgate.mjs');

const ANGEBOT = 'src/main/java/org/mwolff/fbcrm/angebot/domain/Angebot.java';

const wegwerf = [];

afterEach(() => {
  while (wegwerf.length > 0) {
    rmSync(wegwerf.pop(), { recursive: true, force: true });
  }
});

function wegwerfVerzeichnis() {
  const ziel = mkdtempSync(path.join(tmpdir(), 'fbcrm-gate-'));
  wegwerf.push(ziel);
  return ziel;
}

function bericht(name) {
  return readFileSync(path.join(REPO, 'scripts', 'fixtures', name), 'utf8');
}

/** Der Bericht mit 1957 Mutationen, davon 1956 getoetet — siehe mutationsbericht.test.mjs. */
function bericht999() {
  const text = bericht('mutations-99-9.xml');
  const getoetet = /<mutation detected='true'.*?<\/mutation>/s.exec(text)[0];
  return text.replace(getoetet, () => Array(1956).fill(getoetet).join('\n'));
}

/** Ein Board-Client, an dem jede Methode zaehlt, ob sie gerufen wurde. */
function boardClient() {
  return {
    karten: vi.fn(),
    anlegen: vi.fn(),
    bodySetzen: vi.fn(),
    verlauf: vi.fn(),
    kommentieren: vi.fn(),
  };
}

/** Der Pruefzweig aus `pushPruefung.zweig` der eingesetzten Konfiguration. */
const PRUEFZWEIG = 'kit-pruefung';

const KONFIG = { pushPruefung: { zweig: PRUEFZWEIG } };

/** Die Stelle des einen Ueberlebenden aus mutations-99-9.xml. */
const STELLE_27 =
  '- Zeile 27, Methode `<init>`, Mutator `org.pitest.mutationtest.engine.gregor.mutators.NullReturnValsMutator`';

/**
 * Ein Board mit Zustand: `karten` liefert die Karten samt Spalte, `anlegen` legt eine Karte im
 * Backlog mit fortlaufender Nummer an, `bodySetzen` ersetzt Titel und Body. Jede Methode
 * schreibt ihre Aufrufe mit.
 */
function board(karten = []) {
  const stand = karten.map((karte) => ({ ...karte }));
  let naechsteNummer = 900;
  return {
    stand,
    karten: vi.fn(async () => stand.map((karte) => ({ ...karte }))),
    anlegen: vi.fn(async (title, body) => {
      naechsteNummer += 1;
      const karte = {
        id: `intern-${naechsteNummer}`,
        number: naechsteNummer,
        title,
        body,
        column: 'BACKLOG',
        createdAt: '2026-10-18T12:00:00Z',
      };
      stand.push(karte);
      return { ...karte };
    }),
    bodySetzen: vi.fn(async (id, title, body) => {
      const karte = stand.find((vorhanden) => vorhanden.id === id);
      Object.assign(karte, { title, body });
      return { ...karte };
    }),
    verlauf: vi.fn(),
    kommentieren: vi.fn(),
  };
}

/** Eine Ueberlebenden-Karte fuer Angebot.java mit den gegebenen Stellen. */
function angebotKarte(column, stellen, weiteres = {}) {
  return {
    id: 'intern-41',
    number: 41,
    title: kartentitel(ANGEBOT),
    body: kartenbody(ANGEBOT, stellen),
    column,
    createdAt: '2026-10-16T12:00:00Z',
    ...weiteres,
  };
}

const STELLE_12 = { zeile: 12, methode: 'pruefen', mutator: 'org.pitest.Mutator' };

const STELLE_27_TRIPEL = {
  zeile: 27,
  methode: '<init>',
  mutator: 'org.pitest.mutationtest.engine.gregor.mutators.NullReturnValsMutator',
};

/** Ein Lauf mit eingesetzten Funktionen; `lesen` liefert den Text oder null. */
function lauf(argv, { text = null, env = {}, client, config = KONFIG } = {}) {
  const gelesen = [];
  const gemeldet = [];
  const ergebnis = main({
    argv,
    client,
    jetzt: () => new Date('2026-10-18T12:00:00Z'),
    lesen: (pfad) => {
      gelesen.push(pfad);
      return text;
    },
    melden: (zeile) => gemeldet.push(zeile),
    env,
    config,
  });
  return { ergebnis, gelesen, gemeldet };
}

describe('main --nur-quote', () => {
  test('Szenario: Quote unter der Sperrschwelle', async () => {
    // Given
    const client = boardClient();

    // When
    const { ergebnis, gemeldet } = lauf(['--nur-quote'], {
      text: bericht('mutations-75.xml'),
      client,
    });

    // Then
    await expect(ergebnis).rejects.toThrow('75,0 %');
    await expect(ergebnis).rejects.toThrow('Sperrschwelle 80 %');
    for (const methode of Object.values(client)) {
      expect(methode).not.toHaveBeenCalled();
    }
    expect(gemeldet).toEqual([]);
  });

  test('laesst 99,9 % durch und nennt den Ueberlebenden mit Datei und Stelle', async () => {
    // When
    const { ergebnis, gelesen, gemeldet } = lauf(['--nur-quote'], { text: bericht999() });

    // Then
    const text = await ergebnis;
    expect(gelesen).toEqual([BERICHTSPFAD]);
    expect(BERICHTSPFAD).toBe('target/pit-reports/mutations.xml');
    expect(text).toContain('99,9 %');
    expect(text).toContain('1956 von 1957');
    expect(text).toContain(ANGEBOT);
    expect(text).toContain(
      'Zeile 27, Methode `<init>`, Mutator `org.pitest.mutationtest.engine.gregor.mutators.NullReturnValsMutator`',
    );
    expect(gemeldet).toEqual([text]);
  });

  test('nennt ohne Ueberlebende ausdruecklich keine', async () => {
    // Given
    const getoetet = bericht('mutations-99-9.xml').replace(
      /<mutation detected='false'.*?<\/mutation>/s,
      '',
    );

    // When
    const text = await lauf(['--nur-quote'], { text: getoetet }).ergebnis;

    // Then
    expect(text).toContain('100,0 %');
    expect(text).toContain('Durchgelassene Ueberlebende: keine');
  });

  test('schreibt den Bericht zusaetzlich in die Datei aus GITHUB_STEP_SUMMARY', async () => {
    // Given
    const zusammenfassung = path.join(wegwerfVerzeichnis(), 'summary.md');
    writeFileSync(zusammenfassung, 'vorher\n', 'utf8');

    // When
    const text = await lauf(['--nur-quote'], {
      text: bericht999(),
      env: { GITHUB_STEP_SUMMARY: zusammenfassung },
    }).ergebnis;

    // Then
    expect(readFileSync(zusammenfassung, 'utf8')).toBe(`vorher\n${text}\n`);
  });

  test('sperrt bei einem Bericht ohne Mutanten, weil keine Quote feststellbar ist', async () => {
    // Given
    const leer = '<?xml version="1.0"?>\n<mutations partial="false">\n</mutations>\n';

    // When
    const { ergebnis } = lauf(['--nur-quote'], { text: leer });

    // Then
    await expect(ergebnis).rejects.toThrow('keine Quote feststellbar');
    await expect(ergebnis).rejects.toThrow('Sperrschwelle 80 %');
  });

  test.each([
    ['fehlender', null, 'Mutationsbericht fehlt'],
    ['kaputter', bericht('mutations-kaputt.xml'), 'kein gueltiges XML'],
    ['abgeschnittener', bericht('mutations-abgeschnitten.xml'), 'abgeschnitten'],
  ])('ein %s Bericht sperrt mit Grund und ohne Quote', async (_art, text, grund) => {
    // When
    const { ergebnis, gemeldet } = lauf(['--nur-quote'], { text });

    // Then
    await expect(ergebnis).rejects.toThrow(grund);
    await expect(ergebnis).rejects.toThrow('keine Quote feststellbar');
    expect(gemeldet).toEqual([]);
  });
});

describe('main mit anderen Argumenten', () => {
  test.each([
    [['--quatsch'], "Unbekanntes Argument '--quatsch'"],
    [[], 'Keine Betriebsart'],
    [['--nur-quote', '--quatsch'], "Unbekanntes Argument '--quatsch'"],
  ])('%j wirft, ohne den Bericht zu lesen', async (argv, meldung) => {
    // When
    const { ergebnis, gelesen } = lauf(argv, { text: bericht999() });

    // Then
    await expect(ergebnis).rejects.toThrow(meldung);
    expect(gelesen).toEqual([]);
  });

});

describe('main --karten', () => {
  const aufPruefzweig = { GITHUB_REF_NAME: PRUEFZWEIG, GITHUB_SHA: 'abc123' };

  test('Szenario: Hohe Quote mit einem Überlebenden', async () => {
    // Given
    const client = board();

    // When
    const { ergebnis, gemeldet } = lauf(['--karten'], {
      text: bericht999(),
      env: aufPruefzweig,
      client,
    });

    // Then
    const text = await ergebnis;
    expect(client.anlegen).toHaveBeenCalledTimes(1);
    const [titel, body, schluessel] = client.anlegen.mock.calls[0];
    expect(titel.startsWith('[Task] ')).toBe(true);
    expect(titel).toBe(kartentitel(ANGEBOT));
    expect(body).toContain(STELLE_27);
    expect(typeof schluessel).toBe('string');
    expect(schluessel).not.toBe('');
    expect(client.bodySetzen).not.toHaveBeenCalled();
    expect(text).toContain('99,9 %');
    expect(text).toContain(`Neu angelegte Karten (1):\n- #901 ${kartentitel(ANGEBOT)}`);
    expect(text).toContain('Ergaenzte Karten: keine');
    expect(gemeldet).toEqual([text]);
  });

  test('Szenario: Neue Überlebende in einer Datei mit offener Karte', async () => {
    // Given: seit zwei Tagen offen, mit einer anderen Stelle derselben Datei
    const vorhanden = angebotKarte('BACKLOG', [STELLE_12]);
    const client = board([vorhanden]);

    // When
    const text = await lauf(['--karten'], { text: bericht999(), env: aufPruefzweig, client })
      .ergebnis;

    // Then
    expect(client.anlegen).not.toHaveBeenCalled();
    expect(client.bodySetzen).toHaveBeenCalledTimes(1);
    const [id, titel, body] = client.bodySetzen.mock.calls[0];
    expect(id).toBe('intern-41');
    expect(titel).toBe(vorhanden.title);
    expect(body).toContain('- Zeile 12, Methode `pruefen`, Mutator `org.pitest.Mutator`');
    expect(body).toContain(STELLE_27);
    expect(client.stand[0].createdAt).toBe('2026-10-16T12:00:00Z');
    expect(text).toContain(`Ergaenzte Karten (1):\n- #41 ${vorhanden.title}`);
    expect(text).toContain('Neu angelegte Karten: keine');
  });

  test('Szenario: Board nicht erreichbar', async () => {
    // Given
    const client = board();
    client.karten.mockRejectedValue(
      new Error('Board-Aufruf GET /api/kanban/items gescheitert: Board nicht erreichbar'),
    );

    // When
    const { ergebnis, gemeldet } = lauf(['--karten'], {
      text: bericht999(),
      env: aufPruefzweig,
      client,
    });

    // Then
    await expect(ergebnis).rejects.toThrow('Board nicht erreichbar');
    await expect(ergebnis).rejects.toThrow('99,9 %');
    expect(client.anlegen).not.toHaveBeenCalled();
    expect(gemeldet).toEqual([]);
  });

  test('ein Fehlschlag beim Anlegen sperrt mit Grund und Quote', async () => {
    // Given
    const client = board();
    client.anlegen.mockRejectedValue(
      new Error('Board-Aufruf POST /api/kanban/items gescheitert: Status 500'),
    );

    // When
    const { ergebnis, gemeldet } = lauf(['--karten'], {
      text: bericht999(),
      env: aufPruefzweig,
      client,
    });

    // Then
    await expect(ergebnis).rejects.toThrow('Status 500');
    await expect(ergebnis).rejects.toThrow('99,9 %');
    expect(gemeldet).toEqual([]);
  });

  test('ein Fehlschlag beim Ergaenzen sperrt mit Grund und Quote', async () => {
    // Given
    const client = board([angebotKarte('READY', [STELLE_12])]);
    client.bodySetzen.mockRejectedValue(
      new Error('Board-Aufruf PUT /api/kanban/items/intern-41 gescheitert: Status 409'),
    );

    // When
    const { ergebnis } = lauf(['--karten'], { text: bericht999(), env: aufPruefzweig, client });

    // Then
    await expect(ergebnis).rejects.toThrow('Status 409');
    await expect(ergebnis).rejects.toThrow('99,9 %');
  });

  test('eine Karte in IN_REVIEW wird ergaenzt, nicht neu angelegt', async () => {
    // Given
    const client = board([angebotKarte('IN_REVIEW', [STELLE_12])]);

    // When
    await lauf(['--karten'], { text: bericht999(), env: aufPruefzweig, client }).ergebnis;

    // Then
    expect(client.bodySetzen).toHaveBeenCalledTimes(1);
    expect(client.anlegen).not.toHaveBeenCalled();
  });

  test('eine Karte in DONE wird nicht ergaenzt, es entsteht eine neue', async () => {
    // Given
    const client = board([angebotKarte('DONE', [STELLE_12])]);

    // When
    await lauf(['--karten'], { text: bericht999(), env: aufPruefzweig, client }).ergebnis;

    // Then
    expect(client.anlegen).toHaveBeenCalledTimes(1);
    expect(client.bodySetzen).not.toHaveBeenCalled();
  });

  test('eine Karte mit anderem Titel zaehlt nicht als Karte der Datei', async () => {
    // Given: derselbe Pfad, aber ohne das Praefix [Task]
    const client = board([
      angebotKarte('BACKLOG', [STELLE_12], { title: `Mutations-Ueberlebende in ${ANGEBOT}` }),
    ]);

    // When
    await lauf(['--karten'], { text: bericht999(), env: aufPruefzweig, client }).ergebnis;

    // Then
    expect(client.anlegen).toHaveBeenCalledTimes(1);
    expect(client.bodySetzen).not.toHaveBeenCalled();
  });

  test('stehen alle Stellen schon auf der offenen Karte, gibt es keinen Schreibaufruf', async () => {
    // Given
    const client = board([angebotKarte('IN_PROGRESS', [STELLE_27_TRIPEL])]);

    // When
    const text = await lauf(['--karten'], { text: bericht999(), env: aufPruefzweig, client })
      .ergebnis;

    // Then
    expect(client.anlegen).not.toHaveBeenCalled();
    expect(client.bodySetzen).not.toHaveBeenCalled();
    expect(text).toContain('Neu angelegte Karten: keine');
    expect(text).toContain('Ergaenzte Karten: keine');
  });

  test.each([
    ['einem anderen Zweig', { GITHUB_REF_NAME: 'main' }],
    ['unbekanntem Zweig', {}],
  ])('auf %s ruft der Lauf keine Methode am Client und wirft nicht (E2)', async (_art, env) => {
    // Given
    const client = boardClient();

    // When
    const { ergebnis, gemeldet } = lauf(['--karten'], { text: bericht999(), env, client });

    // Then
    const text = await ergebnis;
    for (const methode of Object.values(client)) {
      expect(methode).not.toHaveBeenCalled();
    }
    expect(text).toContain('99,9 %');
    expect(text).toContain('nicht der Pruefzweig kit-pruefung');
    expect(gemeldet).toEqual([text]);
  });

  test('unter der Sperrschwelle sperrt auch --karten, ohne das Board anzufassen', async () => {
    // Given
    const client = boardClient();

    // When
    const { ergebnis } = lauf(['--karten'], {
      text: bericht('mutations-75.xml'),
      env: aufPruefzweig,
      client,
    });

    // Then
    await expect(ergebnis).rejects.toThrow('75,0 %');
    for (const methode of Object.values(client)) {
      expect(methode).not.toHaveBeenCalled();
    }
  });

  test('ohne pushPruefung.zweig in der Konfiguration sperrt der Lauf mit Grund und Quote', async () => {
    // Given
    const client = boardClient();

    // When
    const { ergebnis } = lauf(['--karten'], {
      text: bericht999(),
      env: aufPruefzweig,
      client,
      config: {},
    });

    // Then
    await expect(ergebnis).rejects.toThrow('pushPruefung.zweig');
    await expect(ergebnis).rejects.toThrow('99,9 %');
    for (const methode of Object.values(client)) {
      expect(methode).not.toHaveBeenCalled();
    }
  });

  test('zwei Laeufe mit demselben Bericht legen genau eine Karte an, mit demselben Schluessel (E13)', async () => {
    // Given
    const client = board();

    // When
    await lauf(['--karten'], { text: bericht999(), env: aufPruefzweig, client }).ergebnis;
    await lauf(['--karten'], { text: bericht999(), env: aufPruefzweig, client }).ergebnis;

    // Then
    expect(client.anlegen).toHaveBeenCalledTimes(1);
    expect(client.bodySetzen).not.toHaveBeenCalled();
  });

  test('der Schluessel ist je Datei und Stand stabil und unterscheidet beide', async () => {
    // Given
    const schluessel = async (env) => {
      const client = board();
      await lauf(['--karten'], { text: bericht999(), env: { ...aufPruefzweig, ...env }, client })
        .ergebnis;
      return client.anlegen.mock.calls[0][2];
    };

    // When
    const erster = await schluessel({});
    const zweiter = await schluessel({});
    const andererStand = await schluessel({ GITHUB_SHA: 'def456' });

    // Then
    expect(zweiter).toBe(erster);
    expect(andererStand).not.toBe(erster);
    expect(erster).toMatch(/^mutationsgate-[0-9a-f]{32}$/);
  });

  test('ohne eingesetzte Konfiguration sperrt eine fehlende Konfigurationsdatei mit Grund und Quote', async () => {
    // Given: Vitest laeuft in frontend/, dort liegt keine .claude/workflow.config.json
    const client = boardClient();

    // When
    const { ergebnis } = lauf(['--karten'], {
      text: bericht999(),
      env: aufPruefzweig,
      client,
      config: null,
    });

    // Then
    await expect(ergebnis).rejects.toThrow('.claude/workflow.config.json ist nicht lesbar: ENOENT');
    await expect(ergebnis).rejects.toThrow('99,9 %');
    for (const methode of Object.values(client)) {
      expect(methode).not.toHaveBeenCalled();
    }
  });

  test('ohne eingesetzten Client sperrt ein fehlendes Token mit Grund und Quote', async () => {
    // When
    const { ergebnis } = lauf(['--karten'], {
      text: bericht999(),
      env: aufPruefzweig,
      config: { ...KONFIG, toolbox: { host: 'https://board.example' } },
    });

    // Then
    await expect(ergebnis).rejects.toThrow('TBX_TOKEN');
    await expect(ergebnis).rejects.toThrow('99,9 %');
  });
});

describe('Einstieg', () => {
  test('ohne Bericht im Arbeitsverzeichnis: Exitcode 1, Meldung auf stderr, kein Bericht', () => {
    // Given
    const leer = wegwerfVerzeichnis();
    const env = { ...process.env };
    delete env.GITHUB_STEP_SUMMARY;

    // When
    const prozess = spawnSync(process.execPath, [SKRIPT, '--nur-quote'], {
      cwd: leer,
      env,
      encoding: 'utf8',
    });

    // Then
    expect(prozess.status).toBe(1);
    expect(prozess.stderr).toContain('Mutationsbericht fehlt');
    expect(prozess.stderr).toContain('keine Quote feststellbar');
    expect(prozess.stdout).toBe('');
  });

  test('mit Bericht: Exitcode 0, der Bericht steht auf stdout und in der Zusammenfassung', () => {
    // Given
    const arbeit = wegwerfVerzeichnis();
    mkdirSync(path.join(arbeit, 'target', 'pit-reports'), { recursive: true });
    writeFileSync(path.join(arbeit, BERICHTSPFAD), bericht999(), 'utf8');
    const zusammenfassung = path.join(arbeit, 'summary.md');

    // When
    const prozess = spawnSync(process.execPath, [SKRIPT, '--nur-quote'], {
      cwd: arbeit,
      env: { ...process.env, GITHUB_STEP_SUMMARY: zusammenfassung },
      encoding: 'utf8',
    });

    // Then
    expect(prozess.status).toBe(0);
    expect(prozess.stderr).toBe('');
    expect(prozess.stdout).toContain('99,9 %');
    expect(prozess.stdout).toContain(ANGEBOT);
    expect(readFileSync(zusammenfassung, 'utf8')).toBe(prozess.stdout);
  });

  test('ein unlesbarer Bericht sperrt mit Grund', () => {
    // Given: an der Stelle des Berichts liegt ein Verzeichnis
    const arbeit = wegwerfVerzeichnis();
    mkdirSync(path.join(arbeit, BERICHTSPFAD), { recursive: true });

    // When
    const prozess = spawnSync(process.execPath, [SKRIPT, '--nur-quote'], {
      cwd: arbeit,
      encoding: 'utf8',
    });

    // Then
    expect(prozess.status).toBe(1);
    expect(prozess.stderr).toContain('Mutationsbericht ist unlesbar');
    expect(prozess.stderr).toContain('keine Quote feststellbar');
    expect(prozess.stdout).toBe('');
  });
});
