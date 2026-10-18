import { execFileSync, spawnSync } from 'node:child_process';
import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { afterEach, describe, expect, test, vi } from 'vitest';

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

/** Ein Lauf mit eingesetzten Funktionen; `lesen` liefert den Text oder null. */
function lauf(argv, { text = null, env = {}, client } = {}) {
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

  test('--karten wird als noch nicht vorhanden abgewiesen', async () => {
    // Given
    const client = boardClient();

    // When
    const { ergebnis, gelesen } = lauf(['--karten'], { text: bericht999(), client });

    // Then
    await expect(ergebnis).rejects.toThrow('Betriebsart --karten ist noch nicht vorhanden');
    expect(gelesen).toEqual([]);
    for (const methode of Object.values(client)) {
      expect(methode).not.toHaveBeenCalled();
    }
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
