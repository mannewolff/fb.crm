/**
 * Die Eintraege der Schiene (E15).
 *
 * Oberhalb des Fusses stehen die <b>Navigationsbloecke</b>, jeder mit einem Etikett als Titel
 * (CLAUDE-design.md, „Rahmen"). Den ersten Block traegt dieser Stand: „Stammdaten" mit dem
 * Eintrag „Firmen" (E18, Kriterium 1 aus Issue #35) — damit traegt die Schiene ein fachliches
 * Ziel, und die Zwischenloesung aus K11 („oberhalb des Fusses nichts") ist abgeloest. Welche
 * Bloecke dazukommen, entsteht mit den Fachplaenen.
 *
 * Der Fuss bleibt unveraendert: „Administration", „Dokumentation" und „Einklappen".
 */

export type Symbolname = 'firmen' | 'administration' | 'dokumentation' | 'einklappen';

export interface NavEintrag {
  readonly beschriftung: string;
  readonly ziel: string;
  readonly symbol: Symbolname;
}

export interface NavBlock {
  readonly etikett: string;
  readonly eintraege: readonly NavEintrag[];
}

export const NAV_BLOECKE: readonly NavBlock[] = [
  {
    etikett: 'Stammdaten',
    eintraege: [{ beschriftung: 'Firmen', ziel: '/firmen', symbol: 'firmen' }],
  },
];

export type FussEintrag =
  | { readonly art: 'ziel'; readonly beschriftung: string; readonly ziel: string; readonly symbol: Symbolname }
  | { readonly art: 'einklappen'; readonly beschriftung: string; readonly symbol: Symbolname };

export const FUSS_EINTRAEGE: readonly FussEintrag[] = [
  { art: 'ziel', beschriftung: 'Administration', ziel: '/administration', symbol: 'administration' },
  { art: 'ziel', beschriftung: 'Dokumentation', ziel: '/dokumentation', symbol: 'dokumentation' },
  { art: 'einklappen', beschriftung: 'Einklappen', symbol: 'einklappen' },
];
