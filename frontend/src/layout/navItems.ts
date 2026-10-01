/**
 * Die Eintraege der Schiene (E15).
 *
 * Oberhalb des Fusses stehen die <b>Navigationsbloecke</b>, jeder mit einem Gruppentitel in
 * Satzschreibung (CLAUDE-design.md, „Rahmen"). Dieser Stand traegt zwei Bloecke: „Geschäft" mit der
 * Uebersicht aller Angebote (Issue #127, Kriterium 8) und der Liste aller Rechnungen
 * (Issue #184, #160 Kriterium 1), darunter „Stammdaten". Das Tagesgeschaeft
 * steht oben, weil es der haeufigere Weg ist; Angebote entstehen weiterhin an der Firma
 * (Issue #126). Welche Bloecke dazukommen, entsteht mit den Fachplaenen.
 *
 * Im Fuss stehen „Administration" und „Dokumentation" als eigene Gruppe ueber der Nutzerkarte
 * (E7). „Administration" traegt heute den Bereich „Rechnung" — Nummernmuster, naechste Nummer,
 * Steuersatz und Zahlungsziel (Issue #166). „Einklappen" steht nicht mehr darunter: Es ist eine
 * Icontaste neben der Marke geworden und damit kein Ziel der Navigation mehr.
 *
 * Die <b>Symbolnamen sind die der Symbolfamilie Tabler</b> (E3) — dieselbe, die die Vorlage
 * benutzt. {@link NavRail} loest sie in Komponenten auf; hier bleibt es bei Namen, damit diese
 * Datei ohne React auskommt.
 */

export type Symbolname =
  | 'file-description'
  | 'file-invoice'
  | 'building-community'
  | 'id'
  | 'settings'
  | 'book';

export interface NavEintrag {
  readonly beschriftung: string;
  readonly ziel: string;
  readonly symbol: Symbolname;
}

export interface NavBlock {
  readonly titel: string;
  readonly eintraege: readonly NavEintrag[];
}

export const NAV_BLOECKE: readonly NavBlock[] = [
  {
    titel: 'Geschäft',
    eintraege: [
      { beschriftung: 'Angebote', ziel: '/angebote', symbol: 'file-description' },
      // „Rechnungen" steht unter „Angebote": Das ist die Reihenfolge der Kette, und eine Rechnung
      // entsteht aus einem Angebot (Issue #184).
      { beschriftung: 'Rechnungen', ziel: '/rechnungen', symbol: 'file-invoice' },
    ],
  },
  {
    titel: 'Stammdaten',
    eintraege: [
      { beschriftung: 'Firmen', ziel: '/firmen', symbol: 'building-community' },
      // „Eigene Angaben" ist ein Stammdatum wie die Firma und steht neben ihr — nicht hinter
      // „Administration" im Fuss (dort gehoeren Konto, Instanz und Rollen hin) und nicht in einem
      // eigenen Block, denn ein Block mit einem Eintrag benennt keine Gruppe (E14).
      { beschriftung: 'Eigene Angaben', ziel: '/eigene-angaben', symbol: 'id' },
    ],
  },
];

export const FUSS_EINTRAEGE: readonly NavEintrag[] = [
  { beschriftung: 'Administration', ziel: '/administration', symbol: 'settings' },
  { beschriftung: 'Dokumentation', ziel: '/dokumentation', symbol: 'book' },
];
