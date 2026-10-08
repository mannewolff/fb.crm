#!/usr/bin/env node
/**
 * Erhoeht die Betriebsversion von fb.crm und zieht alle Abschriften nach (RELEASING.md).
 *
 * Quelle der Wahrheit ist die Datei `VERSION` im Wurzelverzeichnis; `pom.xml`,
 * `frontend/package.json` und `frontend/package-lock.json` sind Abschriften. Wer eine davon von
 * Hand aendert, bricht `VersionConsistencyTest`.
 *
 *   node scripts/bump-version.mjs patch   # Z+1        (push main)
 *   node scripts/bump-version.mjs minor   # Y+1, Z->0  (merge production)
 *   node scripts/bump-version.mjs major   # X+1, Y->0, Z->0 (nur auf Anordnung)
 *   node scripts/bump-version.mjs tag     # annotierten Tag v<VERSION> auf HEAD setzen
 *
 * Laeuft mit nichts als Node: keine Abhaengigkeit, kein npm install, kein Maven-Lauf. Aufgerufen
 * wird es aus dem Wurzelverzeichnis des Repositories — von dort kommen die vier Dateien.
 */
import { execFileSync } from 'node:child_process';
import { readFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

/** Welche Stelle der dreiteiligen Version ein Befehl erhoeht. */
const STELLE = { major: 0, minor: 1, patch: 2 };

const BEFEHLE = `${Object.keys(STELLE).join(', ')}, tag`;

/**
 * Die naechste Version — die genannte Stelle um eins hoeher, alle niedrigeren auf 0.
 *
 * @param {string} aktuell Inhalt der Datei VERSION, Zeilenumbruch erlaubt
 * @param {'major'|'minor'|'patch'} stufe welche Stelle erhoeht wird
 * @returns {string} die neue Version in der Form X.Y.Z
 */
export function naechsteVersion(aktuell, stufe) {
  const teile = aktuell.trim().split('.');
  if (teile.length !== 3 || !teile.every((teil) => /^\d+$/.test(teil))) {
    throw new Error(`VERSION traegt keine dreiteilige Version X.Y.Z: '${aktuell.trim()}'`);
  }
  const stelle = STELLE[stufe];
  return teile
    .map(Number)
    .map((zahl, i) => {
      if (i < stelle) return zahl;
      return i === stelle ? zahl + 1 : 0;
    })
    .join('.');
}

/**
 * Setzt die Projektversion in einer pom.xml.
 *
 * Gesucht wird erst hinter `</parent>`: Die erste `<version>` der Datei gehoert zum Parent und
 * traegt die Spring-Boot-Version — wer sie ueberschreibt, haengt das Projekt an ein Parent-POM,
 * das es nicht gibt.
 *
 * @param {string} text Inhalt der pom.xml
 * @param {string} version die neue Projektversion
 * @returns {string} der neue Inhalt
 */
export function mitPomVersion(text, version) {
  const ende = text.indexOf('</parent>');
  if (ende < 0) {
    throw new Error('pom.xml hat keinen </parent>-Block — die Projektversion ist nicht auffindbar');
  }
  const kopf = text.slice(0, ende);
  const rest = text.slice(ende);
  const treffer = /<version>[^<]*<\/version>/.exec(rest);
  if (treffer === null) {
    throw new Error('pom.xml traegt hinter </parent> kein <version>');
  }
  return kopf + rest.replace(treffer[0], () => `<version>${version}</version>`);
}

/**
 * Setzt die Version in einer package.json oder package-lock.json.
 *
 * In der Lockdatei steht sie zweimal — einmal oben und einmal unter `packages[""]`; `npm ci`
 * bricht ab, sobald die beiden auseinanderlaufen. Geschrieben wird im Format von npm selbst
 * (zwei Leerzeichen Einzug, abschliessender Zeilenumbruch), damit die Datei sonst unveraendert
 * bleibt.
 *
 * @param {string} text Inhalt der Datei
 * @param {string} version die neue Version
 * @returns {string} der neue Inhalt
 */
export function mitJsonVersion(text, version) {
  const daten = JSON.parse(text);
  daten.version = version;
  const wurzelpaket = daten.packages?.[''];
  if (wurzelpaket !== undefined) {
    wurzelpaket.version = version;
  }
  return `${JSON.stringify(daten, null, 2)}\n`;
}

function git(args, wurzel) {
  return execFileSync('git', args, { cwd: wurzel, encoding: 'utf8' });
}

/**
 * Fuehrt einen Aufruf aus und liefert, was er hergestellt hat.
 *
 * @param {string[]} argv die Argumente hinter dem Skriptnamen
 * @param {{wurzel?: string}} optionen Wurzelverzeichnis des Repositories
 * @returns {string} die neue Version bzw. der gesetzte Tag
 */
export function main(argv, { wurzel = process.cwd() } = {}) {
  const befehl = argv[0];
  const versionsdatei = path.join(wurzel, 'VERSION');

  if (befehl === 'tag') {
    const version = readFileSync(versionsdatei, 'utf8').trim();
    const tag = `v${version}`;
    // Annotiert statt lightweight, damit `git push --follow-tags` ihn mitnimmt (RELEASING.md).
    git(['tag', '-a', tag, '-m', `fb.crm ${version}`], wurzel);
    return tag;
  }

  if (!Object.hasOwn(STELLE, befehl ?? '')) {
    throw new Error(`Unbekanntes Argument '${befehl ?? ''}'. Erlaubt: ${BEFEHLE}.`);
  }

  const version = naechsteVersion(readFileSync(versionsdatei, 'utf8'), befehl);
  writeFileSync(versionsdatei, `${version}\n`, 'utf8');

  const pom = path.join(wurzel, 'pom.xml');
  writeFileSync(pom, mitPomVersion(readFileSync(pom, 'utf8'), version), 'utf8');

  for (const rel of ['frontend/package.json', 'frontend/package-lock.json']) {
    const datei = path.join(wurzel, rel);
    writeFileSync(datei, mitJsonVersion(readFileSync(datei, 'utf8'), version), 'utf8');
  }

  return version;
}

// Der Einstieg als Kommandozeilenwerkzeug. Die Bedingung haelt ihn beim Import still — die Tests
// rufen main() direkt auf und pruefen den Prozess zusaetzlich ueber spawnSync.
const alsWerkzeugAufgerufen =
  process.argv[1] !== undefined &&
  path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);

if (alsWerkzeugAufgerufen) {
  try {
    process.stdout.write(`${main(process.argv.slice(2))}\n`);
  } catch (fehler) {
    process.stderr.write(`${fehler.message}\n`);
    process.exitCode = 1;
  }
}
