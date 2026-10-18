/**
 * Das Mutationsgate der CI (Plan #348): aus dem PIT-Bericht die Quote bilden, unter der
 * Sperrschwelle von 80 % sperren, den Bericht schreiben.
 *
 * Zwei Betriebsarten, die der Workflow in getrennten Jobs ruft (Issue #355):
 * - `--nur-quote` — ohne Board und ohne Token, auf jedem `push`-Lauf (E2).
 * - `--karten` — mit Board, nur auf dem Pruefzweig aus `pushPruefung.zweig` (E2, E21): legt je
 *   betroffener Datei eine Karte an oder ergaenzt die vorhandene (Issue #356). Auf jedem anderen
 *   Zweig berichtet sie bloss, wie `--nur-quote`.
 *
 * Eine Sperre meldet `main` mit einem Wurf; erst der Einstieg unten schreibt die Meldung nach
 * `stderr` und setzt `process.exitCode = 1` (E19) — ein `process.exit` in `main` risse den
 * Vitest-Lauf ab. Lesen, Melden, Umgebung und Uhr werden eingesetzt, damit die Tests ohne PIT,
 * ohne Netz und ohne Prozess laufen. Laeuft mit nichts als Node.
 */
import { createHash } from 'node:crypto';
import { appendFileSync, readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import { kanbanClient } from './kanban-client.mjs';
import {
  ERGAENZBARE_SPALTEN,
  berichtLesen,
  bodyMitStellen,
  kartenbody,
  kartentitel,
  nachDateiGruppieren,
  quote,
  quoteText,
  stellenAusBody,
} from './mutationsbericht.mjs';

/** Wo PIT seinen XML-Bericht ablegt, relativ zum Arbeitsverzeichnis (Issue #351). */
export const BERICHTSPFAD = 'target/pit-reports/mutations.xml';

/** Unter dieser Quote wird gesperrt; verglichen wird ungerundet (E4). */
const SPERRSCHWELLE = 0.8;
const SPERRSCHWELLE_TEXT = '80 %';

const KEINE_QUOTE = 'keine Quote feststellbar, die Veroeffentlichung ist gesperrt';

/** Die versionierte Konfiguration: Pruefzweig und Board-Host. */
const KONFIG_DATEI = '.claude/workflow.config.json';

/** Der Bericht als Text; fehlt die Datei, `null` — `berichtLesen` nennt das als Grund. */
function standardLesen(pfad) {
  try {
    return readFileSync(pfad, 'utf8');
  } catch (fehler) {
    if (fehler.code === 'ENOENT') return null;
    throw new Error(`Mutationsbericht ist unlesbar: ${fehler.code ?? fehler.message}`);
  }
}

function standardConfig() {
  return JSON.parse(readFileSync(KONFIG_DATEI, 'utf8'));
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

function kartenzeilen(ueberschrift, karten) {
  if (karten.length === 0) return [`${ueberschrift}: keine`];
  return [
    `${ueberschrift} (${karten.length}):`,
    ...karten.map(({ number, title }) => `- #${number} ${title}`),
  ];
}

/** Der Abschnitt ueber die Karten; ohne Board-Lauf nennt er den Grund. */
function kartenabschnitt(karten) {
  if (karten.ohneBoard !== undefined) return ['', `Karten: ${karten.ohneBoard}`];
  return [
    '',
    ...kartenzeilen('Neu angelegte Karten', karten.angelegt),
    ...kartenzeilen('Ergaenzte Karten', karten.ergaenzt),
  ];
}

function berichtstext({ erzeugt, getoetet, ueberlebende }, q, karten) {
  const kopf = [
    '## Mutationsgate',
    '',
    `Quote: ${quoteText(q)} (${getoetet} von ${erzeugt} Mutanten getoetet), ` +
      `Sperrschwelle ${SPERRSCHWELLE_TEXT}`,
    '',
  ];
  const stellen =
    ueberlebende.length === 0
      ? ['Durchgelassene Ueberlebende: keine']
      : [`Durchgelassene Ueberlebende (${ueberlebende.length}):`, ...stellenzeilen(ueberlebende)];
  const abschnitt = karten === undefined ? [] : kartenabschnitt(karten);
  return [...kopf, ...stellen, ...abschnitt].join('\n');
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
  return argv[0];
}

/**
 * Der Idempotenz-Schluessel des Anlegens (E13): stabil je Datei und Stand, damit eine
 * Wiederholung derselben Veroeffentlichung keine zweite Karte erzeugt. Der Commit gehoert dazu,
 * weil ein Schluessel allein aus dem Pfad nach dem Schliessen der Karte auch das Anlegen der
 * neuen Karte aus Kriterium 5 als Wiederholung verwerfen koennte. Gehasht, damit der Kopf nur
 * ASCII traegt.
 */
function anlegeSchluessel(datei, stand) {
  const hash = createHash('sha256').update(`${stand}\n${datei}`).digest('hex');
  return `mutationsgate-${hash.slice(0, 32)}`;
}

/** Zwei Stellen sind gleich, wenn Zeile, Methode und Mutator uebereinstimmen (Kriterium 4). */
function gleicheStelle(a, b) {
  return a.zeile === b.zeile && a.methode === b.methode && a.mutator === b.mutator;
}

/** Eine Sperre am Board: Grund und Quote in der Meldung (Kriterium 8). */
function boardSperre(grund, q) {
  return new Error(
    `${grund} — Mutationsquote ${quoteText(q)}, die Veroeffentlichung ist gesperrt.`,
  );
}

/**
 * Legt je betroffener Datei die Karte an oder ergaenzt die vorhandene (E11, E12, E18).
 * Liefert die angelegten und die ergaenzten Karten fuer den Bericht.
 */
async function kartenSchreiben(client, ueberlebende, stand) {
  const karten = await client.karten();
  const angelegt = [];
  const ergaenzt = [];
  for (const [datei, stellen] of nachDateiGruppieren(ueberlebende)) {
    const titel = kartentitel(datei);
    const vorhanden = karten.find(
      (karte) => karte.title === titel && ERGAENZBARE_SPALTEN.includes(karte.column),
    );
    if (vorhanden === undefined) {
      angelegt.push(
        await client.anlegen(titel, kartenbody(datei, stellen), anlegeSchluessel(datei, stand)),
      );
      continue;
    }
    const bekannt = stellenAusBody(vorhanden.body ?? '');
    const neu = stellen.filter((stelle) => !bekannt.some((b) => gleicheStelle(b, stelle)));
    if (neu.length > 0) {
      await client.bodySetzen(vorhanden.id, vorhanden.title, bodyMitStellen(vorhanden.body, neu));
      ergaenzt.push(vorhanden);
    }
  }
  return { angelegt, ergaenzt };
}

/**
 * Der Board-Teil der Betriebsart `--karten`. Ausserhalb des Pruefzweigs wird das Board nicht
 * angefasst (E2) — das Skript prueft den Zweig selbst, auch wenn der Workflow den Job schon
 * darauf begrenzt. Jeder Fehlschlag am Board sperrt mit Grund und Quote.
 */
async function kartenLauf({ client, config, env, ueberlebende, q }) {
  let konfig;
  try {
    konfig = config ?? standardConfig();
  } catch (fehler) {
    throw boardSperre(`${KONFIG_DATEI} ist nicht lesbar: ${fehler.code ?? fehler.message}`, q);
  }
  const zweig = konfig?.pushPruefung?.zweig;
  if (typeof zweig !== 'string' || zweig === '') {
    throw boardSperre(`Kein Pruefzweig: ${KONFIG_DATEI} nennt kein Feld pushPruefung.zweig`, q);
  }
  if (env.GITHUB_REF_NAME !== zweig) {
    return { ohneBoard: `nicht der Pruefzweig ${zweig}, das Board bleibt unberuehrt` };
  }
  try {
    const board = client ?? kanbanClient({ fetch: globalThis.fetch, env, konfig });
    return await kartenSchreiben(board, ueberlebende, env.GITHUB_SHA ?? '');
  } catch (fehler) {
    throw boardSperre(fehler.message, q);
  }
}

/**
 * Fuehrt das Gate aus und liefert den Bericht. Wirft bei jeder Sperre mit der Meldung.
 *
 * `client`, `config` und `jetzt` braucht erst die Betriebsart `--karten`; `--nur-quote`
 * fasst das Board nicht an. Ohne eingesetzten `client` baut `--karten` den Client aus
 * `kanban-client.mjs`, ohne `config` liest es `.claude/workflow.config.json`.
 *
 * @param {{argv: string[], client?: object, jetzt?: () => Date,
 *   lesen?: (pfad: string) => string|null, melden?: (text: string) => void,
 *   env?: Record<string, string|undefined>, config?: object}} optionen
 * @returns {Promise<string>} der Bericht
 */
export async function main({
  argv,
  client,
  config,
  lesen = standardLesen,
  melden = standardMelden,
  env = process.env,
}) {
  const art = betriebsart(argv);

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

  const karten =
    art === '--karten'
      ? await kartenLauf({
          client,
          config,
          env,
          ueberlebende: zaehlung.ueberlebende,
          q,
        })
      : undefined;

  const text = berichtstext(zaehlung, q, karten);
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
