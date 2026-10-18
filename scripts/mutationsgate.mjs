/**
 * Das Mutationsgate der CI (Plan #348): aus dem PIT-Bericht die Quote bilden, unter der
 * Sperrschwelle von 80 % sperren, den Bericht schreiben.
 *
 * Zwei Betriebsarten, die der Workflow in getrennten Jobs ruft (Issue #355):
 * - `--nur-quote` — ohne Board und ohne Token, auf jedem `push`-Lauf (E2).
 * - `--karten` — mit Board, nur auf dem Pruefzweig; entsteht im Folgepaket.
 *
 * Eine Sperre meldet `main` mit einem Wurf; erst der Einstieg unten schreibt die Meldung nach
 * `stderr` und setzt `process.exitCode = 1` (E19) — ein `process.exit` in `main` risse den
 * Vitest-Lauf ab. Lesen, Melden, Umgebung und Uhr werden eingesetzt, damit die Tests ohne PIT,
 * ohne Netz und ohne Prozess laufen. Laeuft mit nichts als Node.
 */
import { appendFileSync, readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import { berichtLesen, nachDateiGruppieren, quote, quoteText } from './mutationsbericht.mjs';

/** Wo PIT seinen XML-Bericht ablegt, relativ zum Arbeitsverzeichnis (Issue #351). */
export const BERICHTSPFAD = 'target/pit-reports/mutations.xml';

/** Unter dieser Quote wird gesperrt; verglichen wird ungerundet (E4). */
const SPERRSCHWELLE = 0.8;
const SPERRSCHWELLE_TEXT = '80 %';

const KEINE_QUOTE = 'keine Quote feststellbar, die Veroeffentlichung ist gesperrt';

/** Der Bericht als Text; fehlt die Datei, `null` — `berichtLesen` nennt das als Grund. */
function standardLesen(pfad) {
  try {
    return readFileSync(pfad, 'utf8');
  } catch (fehler) {
    if (fehler.code === 'ENOENT') return null;
    throw new Error(`Mutationsbericht ist unlesbar: ${fehler.code ?? fehler.message}`);
  }
}

function standardMelden(text) {
  process.stdout.write(`${text}\n`);
}

/**
 * Liest und zaehlt den Bericht. Fehlt er, ist er unlesbar, kaputt oder abgeschnitten, sperrt
 * das Gate (E6, E17): Ein nicht gemessener Stand ist kein gemessener.
 */
function gelesenerBericht(lesen) {
  try {
    return berichtLesen(lesen(BERICHTSPFAD));
  } catch (fehler) {
    throw new Error(`${fehler.message} — ${KEINE_QUOTE}.`);
  }
}

function stellenzeilen(ueberlebende) {
  const zeilen = [];
  for (const [datei, stellen] of nachDateiGruppieren(ueberlebende)) {
    for (const { zeile, methode, mutator } of stellen) {
      zeilen.push(`- ${datei}: Zeile ${zeile}, Methode \`${methode}\`, Mutator \`${mutator}\``);
    }
  }
  return zeilen;
}

function berichtstext({ erzeugt, getoetet, ueberlebende }, q) {
  const kopf = [
    '## Mutationsgate',
    '',
    `Quote: ${quoteText(q)} (${getoetet} von ${erzeugt} Mutanten getoetet), ` +
      `Sperrschwelle ${SPERRSCHWELLE_TEXT}`,
    '',
  ];
  if (ueberlebende.length === 0) {
    return [...kopf, 'Durchgelassene Ueberlebende: keine'].join('\n');
  }
  return [
    ...kopf,
    `Durchgelassene Ueberlebende (${ueberlebende.length}):`,
    ...stellenzeilen(ueberlebende),
  ].join('\n');
}

/** Prueft die Argumente, bevor irgendetwas gelesen wird; liefert die Betriebsart. */
function betriebsart(argv) {
  for (const argument of argv) {
    if (argument !== '--nur-quote' && argument !== '--karten') {
      throw new Error(`Unbekanntes Argument '${argument}'. Erlaubt: --nur-quote, --karten.`);
    }
  }
  if (argv.length !== 1) {
    throw new Error('Keine Betriebsart: genau eines von --nur-quote oder --karten angeben.');
  }
  if (argv[0] === '--karten') {
    throw new Error('Die Betriebsart --karten ist noch nicht vorhanden (Plan #348, Folgepaket).');
  }
  return argv[0];
}

/**
 * Fuehrt das Gate aus und liefert den Bericht. Wirft bei jeder Sperre mit der Meldung.
 *
 * `client`, `jetzt` und `config` braucht erst die Betriebsart `--karten`; `--nur-quote`
 * fasst das Board nicht an.
 *
 * @param {{argv: string[], client?: object, jetzt?: () => Date,
 *   lesen?: (pfad: string) => string|null, melden?: (text: string) => void,
 *   env?: Record<string, string|undefined>, config?: object}} optionen
 * @returns {Promise<string>} der Bericht
 */
export async function main({ argv, lesen = standardLesen, melden = standardMelden, env = process.env }) {
  betriebsart(argv);

  const zaehlung = gelesenerBericht(lesen);
  const q = quote(zaehlung);
  if (q === null) {
    throw new Error(
      `Der Mutationsbericht enthaelt keine Mutanten: ${quoteText(q)}, gegen die ` +
        `Sperrschwelle ${SPERRSCHWELLE_TEXT} ist nichts gemessen — die Veroeffentlichung ist gesperrt.`,
    );
  }
  if (q < SPERRSCHWELLE) {
    throw new Error(
      `Mutationsquote ${quoteText(q)} liegt unter der Sperrschwelle ${SPERRSCHWELLE_TEXT} ` +
        '— die Veroeffentlichung ist gesperrt.',
    );
  }

  const text = berichtstext(zaehlung, q);
  melden(text);
  if (env.GITHUB_STEP_SUMMARY) {
    appendFileSync(env.GITHUB_STEP_SUMMARY, `${text}\n`, 'utf8');
  }
  return text;
}

// Der Einstieg als Kommandozeilenwerkzeug; beim Import bleibt er still (siehe bump-version.mjs).
const alsWerkzeugAufgerufen =
  process.argv[1] !== undefined &&
  path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);

if (alsWerkzeugAufgerufen) {
  main({ argv: process.argv.slice(2) }).catch((fehler) => {
    process.stderr.write(`${fehler.message}\n`);
    process.exitCode = 1;
  });
}
