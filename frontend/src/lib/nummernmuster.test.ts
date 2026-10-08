import { describe, expect, it } from 'vitest';

import { MAX_LAENGE, istGueltigesMuster, rechnungsnummer } from './nummernmuster';

/**
 * Die Schreibweise der Rechnungsnummer (Plan #161, E5).
 *
 * Die Beispiele stehen woertlich genauso in
 * `src/test/java/org/mwolff/fbcrm/rechnung/domain/NummernmusterTest.java`: Die Regel lebt zweimal —
 * dort entscheidet sie beim Speichern, hier speist sie die Vorschau beim Tippen. Die gemeinsame
 * Tabelle ist das Band zwischen den beiden Fassungen.
 */

/** Die gueltigen Muster mit dem, was sie aus laufender Nummer und Jahr bilden. */
const GUELTIG: readonly [string, number, number, string][] = [
  ['{NNNN}-{JJJJ}', 3, 2026, '0003-2026'],
  ['R{JJ}-{NNNN}', 3, 2026, 'R26-0003'],
  ['{JJJJ}/{N}', 3, 2026, '2026/3'],
  ['{NNNN}-{JJJJ}', 10000, 2026, '10000-2026'],
  ['{NNNN}', 3, 2026, '0003'],
];

/** Die ungueltigen Muster, jedes mit dem Grund, aus dem es ungueltig ist. */
const UNGUELTIG: readonly [string, string][] = [
  ['ohne Nummern-Platzhalter', 'RECHNUNG'],
  ['zwei Nummern-Platzhalter', '{NN}-{NN}'],
  ['zwei Jahres-Platzhalter', '{JJJJ}-{JJ}-{NNNN}'],
  ['{JJJ} ist keine der beiden Jahresformen', '{NNNN}-{JJJ}'],
  ['unbekannter Platzhalter', '{X}-{NNNN}'],
  ['das Leerzeichen steht nicht in der Liste der erlaubten Zeichen', '{NNNN} 2026'],
  ['51 Zeichen — eines zu viel', `{NNNN}${'A'.repeat(45)}`],
];

describe('rechnungsnummer', () => {
  it.each(GUELTIG)('bildet aus %s mit %i im Jahr %i die Nummer %s', (muster, nummer, jahr, erwartet) => {
    expect(rechnungsnummer(muster, nummer, jahr)).toBe(erwartet);
  });

  it.each(UNGUELTIG)('liefert null bei einem Muster mit %s', (_grund, muster) => {
    expect(rechnungsnummer(muster, 3, 2026)).toBeNull();
  });
});

describe('istGueltigesMuster', () => {
  it.each(GUELTIG.map(([muster]) => muster))('laesst %s zu', (muster) => {
    expect(istGueltigesMuster(muster)).toBe(true);
  });

  it('laesst die Laengengrenze selbst zu — das Gegenstueck zu "51 Zeichen"', () => {
    const grenzlang = `{NNNN}${'A'.repeat(44)}`;
    expect(grenzlang).toHaveLength(MAX_LAENGE);
    expect(istGueltigesMuster(grenzlang)).toBe(true);
  });

  it.each(UNGUELTIG)('weist ein Muster mit %s ab', (_grund, muster) => {
    expect(istGueltigesMuster(muster)).toBe(false);
  });
});
