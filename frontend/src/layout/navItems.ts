/**
 * Die Eintraege der Schiene (E15).
 *
 * Oberhalb des Fusses stehen die <b>Navigationsbloecke</b>, jeder mit einem Etikett als Titel
 * (CLAUDE-design.md, „Rahmen"). Zwei Bloecke traegt dieser Stand, und ihre Reihenfolge ist die
 * Aussage: <b>„Geschäft" steht ueber „Stammdaten"</b> (E24). Der Vorgang ist die taegliche Arbeit,
 * die Firma ihre Voraussetzung — wer die Anwendung oeffnet, will zuerst an seine Vorgaenge.
 * Welche Bloecke dazukommen, entsteht mit den Fachplaenen.
 *
 * Der Fuss bleibt unveraendert: „Administration", „Dokumentation" und „Einklappen".
 */

export type Symbolname =
  | 'vorgaenge'
  | 'firmen'
  | 'administration'
  | 'dokumentation'
  | 'einklappen';

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
    etikett: 'Geschäft',
    eintraege: [{ beschriftung: 'Vorgänge', ziel: '/vorgaenge', symbol: 'vorgaenge' }],
  },
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
