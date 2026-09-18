import { readdirSync, readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';

/**
 * Die Sitzung steckt im HttpOnly-Cookie. Im JS-zugaenglichen Speicher darf nichts von ihr
 * liegen (CLAUDE-security.md) — und weil das eine Zusage ueber den ganzen Quellbaum ist,
 * prueft sie hier der Quelltext selbst und nicht eine einzelne Ansicht.
 *
 * Bewusst kein pauschales Verbot von `localStorage`: Geraete-Eigenschaften wie der
 * Einklapp-Zustand der Schiene duerfen dort liegen. Verboten ist der Sitzungsbezug.
 */
const SRC = dirname(dirname(fileURLToPath(import.meta.url)));

/** Zugriffe der Form `localStorage.setItem('…', …)` samt ihres Schluessels. */
const SPEICHER_ZUGRIFF = /(?:local|session)Storage\s*\.\s*(?:get|set|remove)Item\s*\(\s*'([^']*)'/g;

/** Alles, was nach Sitzung, Konto oder Token klingt. */
const SITZUNGSBEZUG = /token|session|sitzung|auth|jwt|konto|kennwort|passwort|credential/i;

/** Die Schichten, die die Sitzung fuehren — hier ist jeder Speicherzugriff falsch. */
const AUTH_NAHE = ['api/', 'auth/', 'routes/'];

interface Quelle {
  readonly name: string;
  readonly text: string;
}

function quellen(): Quelle[] {
  return readdirSync(SRC, { recursive: true, encoding: 'utf8' })
    .filter((name) => name.endsWith('.ts') || name.endsWith('.tsx'))
    .filter((name) => !name.includes('.test.'))
    .map((name) => ({ name, text: readFileSync(join(SRC, name), 'utf8') }));
}

describe('Kein Sitzungsmerkmal im Speicher des Browsers', () => {
  it('legt unter keinem sitzungsnahen Schluessel etwas ab', () => {
    const treffer = quellen().flatMap((quelle) =>
      [...quelle.text.matchAll(SPEICHER_ZUGRIFF)]
        .filter((fund) => SITZUNGSBEZUG.test(fund[1]))
        .map((fund) => `${quelle.name}: ${fund[1]}`),
    );

    expect(treffer).toEqual([]);
  });

  it('greift in den sitzungsfuehrenden Schichten gar nicht auf den Speicher zu', () => {
    const treffer = quellen()
      .filter((quelle) => AUTH_NAHE.some((ordner) => quelle.name.replaceAll('\\', '/').startsWith(ordner)))
      .filter((quelle) => /(?:local|session)Storage/.test(quelle.text))
      .map((quelle) => quelle.name);

    expect(treffer).toEqual([]);
  });
});
