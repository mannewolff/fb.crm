#!/usr/bin/env node
/**
 * Schreibt den Changelog-Block der neuen Version in CHANGELOG.md (RELEASING.md).
 *
 *   node scripts/gen-changelog.mjs
 *
 * Die Zielversion kommt aus `VERSION` — im Ablauf von `merge production` hat
 * `bump-version.mjs minor` sie unmittelbar davor gesetzt. Liefe dieses Skript vor dem Bump,
 * entstuende ein Block fuer die alte Version.
 *
 * Den Bereich grenzt der Tag der Vorversion ab (`v*`, annotiert von `bump-version.mjs tag`);
 * gibt es keinen, zaehlt die ganze Geschichte. Aufgesammelt werden die Commit-Titel — ein roher
 * Dump im Keep-a-Changelog-Format, keine Einordnung in Rubriken.
 *
 * Das Datum ist der Commit-Zeitpunkt von HEAD und nicht die Uhr des Rechners: Der Block soll den
 * Stand datieren, den er beschreibt, und zweimal dasselbe Ergebnis liefern.
 *
 * Laeuft mit nichts als Node und git: keine Abhaengigkeit, kein npm install, kein Maven-Lauf.
 * Aufgerufen wird es aus dem Wurzelverzeichnis des Repositories.
 */
import { spawnSync } from 'node:child_process';
import { readFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

/** Die Ueberschrift, unter die jeder neue Block wandert. */
const ANKER = '## [Unreleased]\n';

/**
 * Ein git-Aufruf, der scheitern darf.
 *
 * @returns {string|null} die Ausgabe, oder null wenn der Aufruf nicht mit 0 endete
 */
function git(args, wurzel) {
  const lauf = spawnSync('git', args, { cwd: wurzel, encoding: 'utf8' });
  return lauf.status === 0 ? lauf.stdout : null;
}

/**
 * Der Changelog-Block einer Version.
 *
 * @param {string} version die Version, die der Block beschreibt
 * @param {string} datum das Datum in der Form YYYY-MM-DD
 * @param {string[]} titel die Commit-Titel, neueste zuerst
 * @returns {string} der Block, mit abschliessendem Zeilenumbruch
 */
export function block(version, datum, titel) {
  // Auch ein leerer Bereich bekommt seinen Block: Eine Version ohne Eintrag im Changelog saehe
  // aus wie ein vergessener Lauf.
  const zeilen = titel.length === 0 ? ['Keine Aenderungen.'] : titel;
  return `## [${version}] - ${datum}\n\n${zeilen.map((zeile) => `- ${zeile}`).join('\n')}\n`;
}

/**
 * Setzt einen Block unmittelbar unter die Ueberschrift `## [Unreleased]`.
 *
 * @param {string} changelog Inhalt der CHANGELOG.md
 * @param {string} neu der einzufuegende Block
 * @returns {string} der neue Inhalt
 */
export function mitBlock(changelog, neu) {
  const stelle = changelog.indexOf(ANKER);
  if (stelle < 0) {
    throw new Error('CHANGELOG.md hat keine Ueberschrift "## [Unreleased]" — kein Ankerpunkt');
  }
  const schnitt = stelle + ANKER.length;
  return `${changelog.slice(0, schnitt)}\n${neu}${changelog.slice(schnitt)}`;
}

/**
 * Fuehrt den Lauf aus und liefert den geschriebenen Block.
 *
 * @param {string[]} argv die Argumente hinter dem Skriptnamen (werden nicht ausgewertet)
 * @param {{wurzel?: string}} optionen Wurzelverzeichnis des Repositories
 * @returns {string} der Block, der in CHANGELOG.md gelandet ist
 */
export function main(argv, { wurzel = process.cwd() } = {}) {
  const version = readFileSync(path.join(wurzel, 'VERSION'), 'utf8').trim();

  const datum = git(['log', '-1', '--format=%cs'], wurzel);
  if (datum === null) {
    throw new Error('HEAD traegt keinen Commit — ohne Commit gibt es nichts zu berichten');
  }

  const vorgaenger = git(['describe', '--tags', '--abbrev=0', '--match', 'v*'], wurzel);
  const bereich = vorgaenger === null ? 'HEAD' : `${vorgaenger.trim()}..HEAD`;
  const titel = (git(['log', '--no-merges', '--format=%s', bereich], wurzel) ?? '')
    .split('\n')
    .map((zeile) => zeile.trim())
    .filter((zeile) => zeile.length > 0);

  const neu = block(version, datum.trim(), titel);
  const datei = path.join(wurzel, 'CHANGELOG.md');
  writeFileSync(datei, mitBlock(readFileSync(datei, 'utf8'), neu), 'utf8');
  return neu;
}

// Der Einstieg als Kommandozeilenwerkzeug. Die Bedingung haelt ihn beim Import still — die Tests
// rufen main() direkt auf und pruefen den Prozess zusaetzlich ueber spawnSync.
const alsWerkzeugAufgerufen =
  process.argv[1] !== undefined &&
  path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);

if (alsWerkzeugAufgerufen) {
  try {
    process.stdout.write(main(process.argv.slice(2)));
  } catch (fehler) {
    process.stderr.write(`${fehler.message}\n`);
    process.exitCode = 1;
  }
}
