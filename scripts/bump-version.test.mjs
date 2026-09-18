import { execFileSync, spawnSync } from 'node:child_process';
import { cpSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { afterEach, describe, expect, test } from 'vitest';

import { main, mitJsonVersion, mitPomVersion, naechsteVersion } from './bump-version.mjs';

/**
 * Das Wurzelverzeichnis des Repositories — bei git erfragt und nicht aus `import.meta.url`
 * abgeleitet: Vitest laeuft mit `frontend` als Arbeitsverzeichnis und reicht `import.meta.url`
 * nicht als `file:`-URL durch.
 */
const REPO = execFileSync('git', ['rev-parse', '--show-toplevel'], { encoding: 'utf8' }).trim();

const SKRIPT = path.join(REPO, 'scripts', 'bump-version.mjs');

/** Die Dateien, die das Skript nachzieht — in genau der Form, in der sie im Repository liegen. */
const ABSCHRIFTEN = ['pom.xml', 'frontend/package.json', 'frontend/package-lock.json'];

const wegwerf = [];

afterEach(() => {
  while (wegwerf.length > 0) {
    rmSync(wegwerf.pop(), { recursive: true, force: true });
  }
});

/**
 * Ein Wegwerf-Klon mit den echten Dateien des Repositories.
 *
 * <p>Bewusst Kopien der echten `pom.xml` und `package-lock.json` und keine selbstgebauten
 * Schnipsel: Was das Skript kann, muss es an der Datei koennen, die es spaeter anfasst — ein
 * Minimal-POM pruefte nur die eigene Annahme darueber, wie ein POM aussieht.
 */
function wegwerfKlon() {
  const ziel = mkdtempSync(path.join(tmpdir(), 'fbcrm-bump-'));
  wegwerf.push(ziel);
  mkdirSync(path.join(ziel, 'frontend'));
  for (const rel of ['VERSION', ...ABSCHRIFTEN]) {
    cpSync(path.join(REPO, rel), path.join(ziel, rel));
  }
  return ziel;
}

function wegwerfRepo() {
  const ziel = wegwerfKlon();
  const umgebung = { ...process.env, GIT_AUTHOR_DATE: '2026-09-18T10:00:00Z' };
  umgebung.GIT_COMMITTER_DATE = umgebung.GIT_AUTHOR_DATE;
  // stderr wird mitgefangen, damit die Fortschrittsmeldungen von git nicht im Testprotokoll landen.
  const git = (args) =>
    execFileSync('git', args, {
      cwd: ziel,
      env: umgebung,
      encoding: 'utf8',
      stdio: ['ignore', 'pipe', 'pipe'],
    });
  git(['init', '-b', 'main']);
  git(['config', 'user.email', 'test@example.org']);
  git(['config', 'user.name', 'Test']);
  git(['config', 'commit.gpgsign', 'false']);
  git(['add', '-A']);
  git(['commit', '-m', 'Ausgangsstand']);
  return { ziel, git };
}

function lies(wurzel, rel) {
  return readFileSync(path.join(wurzel, rel), 'utf8');
}

function jsonVersion(wurzel, rel) {
  return JSON.parse(lies(wurzel, rel)).version;
}

function pomProjektVersion(wurzel) {
  const text = lies(wurzel, 'pom.xml');
  const hinterParent = text.slice(text.indexOf('</parent>'));
  return /<version>([^<]*)<\/version>/.exec(hinterParent)[1];
}

describe('naechsteVersion', () => {
  test('patch erhoeht die letzte Stelle', () => {
    expect(naechsteVersion('0.1.0', 'patch')).toBe('0.1.1');
  });

  test('minor erhoeht die mittlere Stelle und setzt die letzte zurueck', () => {
    expect(naechsteVersion('1.4.7', 'minor')).toBe('1.5.0');
  });

  test('major erhoeht die erste Stelle und setzt beide anderen zurueck', () => {
    expect(naechsteVersion('1.4.7', 'major')).toBe('2.0.0');
  });

  test('der Zeilenumbruch der VERSION-Datei stoert nicht', () => {
    expect(naechsteVersion('0.1.0\n', 'patch')).toBe('0.1.1');
  });

  test('zweiteilige Versionen werden abgewiesen', () => {
    expect(() => naechsteVersion('1.4', 'patch')).toThrow(/dreiteilige/);
  });

  test('nicht numerische Stellen werden abgewiesen', () => {
    expect(() => naechsteVersion('1.4.x', 'patch')).toThrow(/dreiteilige/);
  });
});

describe('mitPomVersion', () => {
  test('setzt die Projektversion und laesst die Version des Parent stehen', () => {
    // Given — die echte pom.xml: die erste <version> darin gehoert zu <parent>.
    const vorher = readFileSync(path.join(REPO, 'pom.xml'), 'utf8');
    const parent = /<version>([^<]*)<\/version>/.exec(vorher)[1];

    // When
    const nachher = mitPomVersion(vorher, '9.9.9');

    // Then
    expect(/<version>([^<]*)<\/version>/.exec(nachher)[1]).toBe(parent);
    expect(nachher.slice(nachher.indexOf('</parent>'))).toContain('<version>9.9.9</version>');
  });

  test('aendert sonst nichts an der Datei', () => {
    const vorher = readFileSync(path.join(REPO, 'pom.xml'), 'utf8');
    const nachher = mitPomVersion(vorher, '0.1.0');
    expect(nachher).toBe(vorher);
  });

  test('ein POM ohne Parent-Block wird abgewiesen', () => {
    expect(() => mitPomVersion('<project><version>1.0.0</version></project>', '2.0.0')).toThrow(
      /parent/,
    );
  });

  test('ein POM ohne eigene Version wird abgewiesen', () => {
    expect(() => mitPomVersion('<project><parent/></parent><name>x</name></project>', '2.0.0'))
      .toThrow(/version/);
  });
});

describe('mitJsonVersion', () => {
  test('schreibt die package.json zeichengleich zurueck, wenn die Version gleich bleibt', () => {
    // Given — der schaerfste Nachweis: die echte Datei darf sich sonst nicht veraendern.
    const vorher = readFileSync(path.join(REPO, 'frontend/package.json'), 'utf8');

    // When / Then
    expect(mitJsonVersion(vorher, JSON.parse(vorher).version)).toBe(vorher);
  });

  test('schreibt die package-lock.json zeichengleich zurueck, wenn die Version gleich bleibt', () => {
    const vorher = readFileSync(path.join(REPO, 'frontend/package-lock.json'), 'utf8');
    expect(mitJsonVersion(vorher, JSON.parse(vorher).version)).toBe(vorher);
  });

  test('setzt die Version in der package.json', () => {
    const vorher = readFileSync(path.join(REPO, 'frontend/package.json'), 'utf8');
    expect(JSON.parse(mitJsonVersion(vorher, '9.9.9')).version).toBe('9.9.9');
  });

  test('setzt in der package-lock.json auch den Eintrag des Wurzelpakets', () => {
    // Given — npm fuehrt die Version ein zweites Mal unter packages[""]; npm ci bricht ab,
    // sobald die beiden auseinanderlaufen.
    const vorher = readFileSync(path.join(REPO, 'frontend/package-lock.json'), 'utf8');

    // When
    const nachher = JSON.parse(mitJsonVersion(vorher, '9.9.9'));

    // Then
    expect(nachher.version).toBe('9.9.9');
    expect(nachher.packages[''].version).toBe('9.9.9');
  });
});

describe('main', () => {
  test('patch zieht alle vier Dateien gemeinsam nach', () => {
    // Given
    const klon = wegwerfKlon();

    // When
    const neu = main(['patch'], { wurzel: klon });

    // Then
    expect(neu).toBe('0.1.1');
    expect(lies(klon, 'VERSION')).toBe('0.1.1\n');
    expect(pomProjektVersion(klon)).toBe('0.1.1');
    expect(jsonVersion(klon, 'frontend/package.json')).toBe('0.1.1');
    expect(jsonVersion(klon, 'frontend/package-lock.json')).toBe('0.1.1');
  });

  test('minor setzt den Patch-Teil zurueck', () => {
    const klon = wegwerfKlon();
    writeFileSync(path.join(klon, 'VERSION'), '0.1.7\n');
    expect(main(['minor'], { wurzel: klon })).toBe('0.2.0');
    expect(pomProjektVersion(klon)).toBe('0.2.0');
  });

  test('major setzt Minor- und Patch-Teil zurueck', () => {
    const klon = wegwerfKlon();
    writeFileSync(path.join(klon, 'VERSION'), '0.4.7\n');
    expect(main(['major'], { wurzel: klon })).toBe('1.0.0');
    expect(pomProjektVersion(klon)).toBe('1.0.0');
  });

  test('ein unbekanntes Argument wird abgewiesen', () => {
    expect(() => main(['quatsch'], { wurzel: wegwerfKlon() })).toThrow(/quatsch/);
  });

  test('ohne Argument wird abgewiesen', () => {
    expect(() => main([], { wurzel: wegwerfKlon() })).toThrow(/major, minor, patch, tag/);
  });

  test('ein geerbter Name aus Object.prototype ist kein Befehl', () => {
    expect(() => main(['toString'], { wurzel: wegwerfKlon() })).toThrow(/toString/);
  });

  test('tag setzt einen annotierten Tag auf die Version aus VERSION', () => {
    // Given — ein echtes Wegwerf-Repository, damit wirklich git laeuft.
    const { ziel, git } = wegwerfRepo();

    // When
    const tag = main(['tag'], { wurzel: ziel });

    // Then — annotiert, damit git push --follow-tags ihn mitnimmt (RELEASING.md).
    expect(tag).toBe('v0.1.0');
    expect(git(['tag', '--list'])).toContain('v0.1.0');
    expect(git(['cat-file', '-t', 'v0.1.0']).trim()).toBe('tag');
  });
});

describe('als Kommandozeilenwerkzeug', () => {
  test('node scripts/bump-version.mjs patch laeuft ohne weiteren Repo-Kontext', () => {
    // Given — kein npm install, kein mvn, nur node und die vier Dateien.
    const klon = wegwerfKlon();

    // When
    const lauf = spawnSync(process.execPath, [SKRIPT, 'patch'], { cwd: klon, encoding: 'utf8' });

    // Then
    expect(lauf.status).toBe(0);
    expect(lauf.stdout.trim()).toBe('0.1.1');
    expect(lies(klon, 'VERSION')).toBe('0.1.1\n');
    expect(pomProjektVersion(klon)).toBe('0.1.1');
    expect(jsonVersion(klon, 'frontend/package.json')).toBe('0.1.1');
  });

  test('ein unbekanntes Argument scheitert mit einem Exit-Code ungleich 0', () => {
    // Given
    const klon = wegwerfKlon();

    // When
    const lauf = spawnSync(process.execPath, [SKRIPT, 'quatsch'], { cwd: klon, encoding: 'utf8' });

    // Then
    expect(lauf.status).not.toBe(0);
    expect(lauf.stderr).toContain('quatsch');
  });
});
