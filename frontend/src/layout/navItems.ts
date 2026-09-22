/**
 * Die Eintraege der Schiene (E15).
 *
 * In diesem Stand gibt es <b>nur</b> den Fuss: „Administration", „Dokumentation" und
 * „Einklappen". Oberhalb steht nichts — kein Navigationsblock, kein Etikett, kein ausgegrauter
 * Platzhalter (K11). Welche Bloecke dort einmal stehen, entsteht mit den Fachplaenen.
 */

export type Symbolname = 'administration' | 'dokumentation' | 'einklappen';

export type FussEintrag =
  | { readonly art: 'ziel'; readonly beschriftung: string; readonly ziel: string; readonly symbol: Symbolname }
  | { readonly art: 'einklappen'; readonly beschriftung: string; readonly symbol: Symbolname };

export const FUSS_EINTRAEGE: readonly FussEintrag[] = [
  { art: 'ziel', beschriftung: 'Administration', ziel: '/administration', symbol: 'administration' },
  { art: 'ziel', beschriftung: 'Dokumentation', ziel: '/dokumentation', symbol: 'dokumentation' },
  { art: 'einklappen', beschriftung: 'Einklappen', symbol: 'einklappen' },
];
