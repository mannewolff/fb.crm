import { execFileSync, spawnSync } from 'node:child_process';
import { cpSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { afterEach, describe, expect, test } from 'vitest';

import { block, main, mitBlock } from './gen-changelog.mjs';

/** Siehe bump-version.test.mjs: Vitest laeuft mit `frontend` als Arbeitsverzeichnis. */
const REPO = execFileSync('git', ['rev-parse', '--show-toplevel'], { encoding: 'utf8' }).trim();

const SKRIPT = path.join(REPO, 'scripts', 'gen-changelog.mjs');

const DATUM = '2026-09-18';

const wegwerf = [];

afterEach(() => {
  while (wegwerf.length > 0) {
    rmSync(wegwerf.pop(), { recursive: true, force: true });
  }
});

/**
 * Ein Wegwerf-Repository mit der echten CHANGELOG.md des Projekts.
 *
 * Die Datei ist der Ankerpunkt des Skripts — es sucht darin die Ueberschrift
 * `## [Unreleased]`. Ein selbstgebauter Schnipsel pruefte nur die eigene Annahme darueber, wie
 * die Datei aussieht; hier laeuft das Skript gegen die Datei, die es spaeter wirklich anfasst.
 *
 * `VERSION` traegt die Zielversion — im echten Ablauf hat `bump-version.mjs` sie unmittelbar
 * davor hineingeschrieben (RELEASING.md: erst Bump, dann Changelog).
 */
function wegwerfRepo(version) {
  const ziel = mkdtempSync(path.join(tmpdir(), 'fbcrm-changelog-'));
  wegwerf.push(ziel);
  cpSync(path.join(REPO, 'CHANGELOG.md'), path.join(ziel, 'CHANGELOG.md'));
  writeFileSync(path.join(ziel, 'VERSION'), `${version}\n`, 'utf8');

  const umgebung = { ...process.env, GIT_AUTHOR_DATE: `${DATUM}T10:00:00Z` };
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

  const commit = (titel) => {
    writeFileSync(path.join(ziel, 'notiz.txt'), `${titel}\n`, 'utf8');
    git(['add', '-A']);
    git(['commit', '-m', titel]);
  };
  return { ziel, git, commit };
}

function changelog(wurzel) {
  return readFileSync(path.join(wurzel, 'CHANGELOG.md'), 'utf8');
}

describe('block', () => {
  test('setzt Version, Datum und die Commit-Titel als Liste', () => {
    expect(block('0.2.0', DATUM, ['Erster Schritt', 'Zweiter Schritt'])).toBe(
      `## [0.2.0] - ${DATUM}\n\n- Erster Schritt\n- Zweiter Schritt\n`,
    );
  });

  test('ein leerer Bereich ergibt einen Block, der das auch sagt', () => {
    // Then — die Ueberschrift entfaellt nicht: eine Version ohne Block saehe aus wie ein
    // vergessener Lauf.
    expect(block('0.2.0', DATUM, [])).toBe(`## [0.2.0] - ${DATUM}\n\n- Keine Aenderungen.\n`);
  });
});

describe('mitBlock', () => {
  test('setzt den Block unmittelbar unter die Ueberschrift Unreleased', () => {
    // Given — die echte CHANGELOG.md des Projekts.
    const vorher = readFileSync(path.join(REPO, 'CHANGELOG.md'), 'utf8');

    // When
    const nachher = mitBlock(vorher, `## [0.2.0] - ${DATUM}\n\n- Erster Schritt\n`);

    // Then
    expect(nachher).toContain(`## [Unreleased]\n\n## [0.2.0] - ${DATUM}\n\n- Erster Schritt\n`);
  });

  test('der Block einer neuen Version steht vor dem der alten', () => {
    // Given
    const einmal = mitBlock(
      readFileSync(path.join(REPO, 'CHANGELOG.md'), 'utf8'),
      `## [0.2.0] - ${DATUM}\n\n- Alt\n`,
    );

    // When
    const zweimal = mitBlock(einmal, `## [0.3.0] - ${DATUM}\n\n- Neu\n`);

    // Then
    expect(zweimal.indexOf('## [0.3.0]')).toBeLessThan(zweimal.indexOf('## [0.2.0]'));
  });

  test('eine Datei ohne Ueberschrift Unreleased wird abgewiesen', () => {
    expect(() => mitBlock('# Changelog\n', '## [0.2.0]\n')).toThrow(/Unreleased/);
  });
});

describe('main', () => {
  test('schreibt den Block aus den Commits seit dem Vorgaenger-Tag', () => {
    // Given
    const { ziel, git, commit } = wegwerfRepo('0.2.0');
    commit('Ausgangsstand');
    git(['tag', '-a', 'v0.1.0', '-m', 'fb.crm 0.1.0']);
    commit('Erster Schritt');
    commit('Zweiter Schritt');

    // When
    main([], { wurzel: ziel });

    // Then — der Commit vor dem Tag gehoert nicht mehr dazu.
    expect(changelog(ziel)).toContain(
      `## [Unreleased]\n\n## [0.2.0] - ${DATUM}\n\n- Zweiter Schritt\n- Erster Schritt\n`,
    );
    expect(changelog(ziel)).not.toContain('Ausgangsstand');
  });

  test('ohne Vorgaenger-Tag zaehlt die ganze Geschichte', () => {
    // Given
    const { ziel, commit } = wegwerfRepo('0.1.0');
    commit('Aller Anfang');

    // When
    main([], { wurzel: ziel });

    // Then
    expect(changelog(ziel)).toContain(`## [0.1.0] - ${DATUM}\n\n- Aller Anfang\n`);
  });

  test('ein Bereich ohne Commits ergibt den Block mit dem Hinweis', () => {
    // Given — getaggt und seitdem nichts passiert.
    const { ziel, git, commit } = wegwerfRepo('0.2.0');
    commit('Ausgangsstand');
    git(['tag', '-a', 'v0.1.0', '-m', 'fb.crm 0.1.0']);

    // When
    main([], { wurzel: ziel });

    // Then
    expect(changelog(ziel)).toContain(`## [0.2.0] - ${DATUM}\n\n- Keine Aenderungen.\n`);
  });

  test('Merge-Commits stehen nicht im Block', () => {
    // Given — ein Merge-Titel ist kein Eintrag, den jemand lesen will.
    const { ziel, git, commit } = wegwerfRepo('0.2.0');
    commit('Ausgangsstand');
    git(['checkout', '-b', 'seitenzweig']);
    commit('Arbeit im Zweig');
    git(['checkout', 'main']);
    git(['merge', '--no-ff', '-m', 'Merge branch seitenzweig', 'seitenzweig']);

    // When
    main([], { wurzel: ziel });

    // Then
    expect(changelog(ziel)).toContain('- Arbeit im Zweig');
    expect(changelog(ziel)).not.toContain('Merge branch');
  });

  test('ein Repository ohne Commit wird abgewiesen', () => {
    // Given
    const { ziel } = wegwerfRepo('0.1.0');

    // When / Then — ohne HEAD gibt es kein Datum und keinen Bereich.
    expect(() => main([], { wurzel: ziel })).toThrow(/Commit/);
  });
});

describe('als Kommandozeilenwerkzeug', () => {
  test('node scripts/gen-changelog.mjs laeuft ohne weiteren Repo-Kontext', () => {
    // Given — kein npm install, kein mvn, nur node und ein Repository mit zwei Commits.
    const { ziel, commit } = wegwerfRepo('0.2.0');
    commit('Erster Schritt');
    commit('Zweiter Schritt');

    // When
    const lauf = spawnSync(process.execPath, [SKRIPT], { cwd: ziel, encoding: 'utf8' });

    // Then
    expect(lauf.status).toBe(0);
    expect(changelog(ziel)).toContain(
      `## [Unreleased]\n\n## [0.2.0] - ${DATUM}\n\n- Zweiter Schritt\n- Erster Schritt\n`,
    );
  });

  test('ein Repository ohne Commit scheitert mit einem Exit-Code ungleich 0', () => {
    // Given
    const { ziel } = wegwerfRepo('0.1.0');

    // When
    const lauf = spawnSync(process.execPath, [SKRIPT], { cwd: ziel, encoding: 'utf8' });

    // Then
    expect(lauf.status).not.toBe(0);
    expect(lauf.stderr).toContain('Commit');
  });
});
