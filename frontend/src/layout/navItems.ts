import type { Recht, Rechte } from '../auth/rechte';

/**
 * Die Eintraege der Schiene (E15).
 *
 * Oberhalb des Fusses stehen die <b>Navigationsbloecke</b>, jeder mit einem Gruppentitel in
 * Satzschreibung (CLAUDE-design.md, „Rahmen"). Dieser Stand traegt zwei Bloecke: „Geschäft" mit der
 * Uebersicht aller Angebote (Issue #127, Kriterium 8), der Ansicht „Arbeitszeit" (Issue #193,
 * Kriterium 5), der Liste aller Rechnungen (Issue #184, #160 Kriterium 1) und den
 * Jahresabschluessen (#287, Kriterium 1), darunter „Stammdaten". Das Tagesgeschaeft steht oben, weil es der haeufigere Weg ist; Angebote entstehen
 * weiterhin an der Firma (Issue #126). Welche Bloecke dazukommen, entsteht mit den Fachplaenen.
 *
 * Im Fuss stehen „Administration" und „Dokumentation" als eigene Gruppe ueber der Nutzerkarte
 * (E7). „Administration" traegt heute den Bereich „Rechnung" — Nummernmuster, naechste Nummer,
 * Steuersatz und Zahlungsziel (Issue #166). „Einklappen" steht nicht mehr darunter: Es ist eine
 * Icontaste neben der Marke geworden und damit kein Ziel der Navigation mehr.
 *
 * Die <b>Symbolnamen sind die der Symbolfamilie Tabler</b> (E3) — dieselbe, die die Vorlage
 * benutzt. {@link NavRail} loest sie in Komponenten auf; hier bleibt es bei Namen, damit diese
 * Datei ohne React auskommt.
 *
 * Ein Eintrag mit `recht` steht nur in der Schiene einer Rolle, die dieses Recht traegt
 * (Plan #414, A11); ohne `recht` steht er fuer jede Rolle. Die Schiene filtert, die Adresse
 * selbst bewacht {@link RollenRoute} im Routenbaum mit demselben Recht.
 */

export type Symbolname =
  | 'file-description'
  | 'clock'
  | 'file-invoice'
  | 'chart-histogram'
  | 'building-community'
  | 'id'
  | 'settings'
  | 'book';

export interface NavEintrag {
  readonly beschriftung: string;
  readonly ziel: string;
  readonly symbol: Symbolname;
  /** Das Recht, ohne das der Eintrag fehlt; ohne Angabe steht er fuer jede Rolle. */
  readonly recht?: Recht;
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
      // „Arbeitszeit" steht zwischen „Angebote" und „Rechnungen": Gearbeitet wird zwischen
      // Bestellung und Rechnung (Plan #194, A13). Die Uhr sagt Arbeitszeit ohne Verwechslung mit
      // einem Kalender — Terminplanung ist ein Nicht-Ziel (CLAUDE.md, „Nicht im Umfang").
      { beschriftung: 'Arbeitszeit', ziel: '/arbeitszeit', symbol: 'clock' },
      // „Rechnungen" steht unter „Arbeitszeit": Das ist die Reihenfolge der Kette, und eine
      // Rechnung entsteht aus einem Angebot (Issue #184).
      { beschriftung: 'Rechnungen', ziel: '/rechnungen', symbol: 'file-invoice' },
      // „Jahresabschlüsse" steht unter „Rechnungen": Die Kette Angebot → Arbeitszeit → Rechnung
      // endet in der Auswertung des Jahres, und ein eigener Block mit einem Eintrag benennt keine
      // Gruppe (Plan #288, E15).
      { beschriftung: 'Jahresabschlüsse', ziel: '/jahresabschluesse', symbol: 'chart-histogram' },
    ],
  },
  {
    titel: 'Stammdaten',
    eintraege: [
      { beschriftung: 'Firmen', ziel: '/firmen', symbol: 'building-community' },
      // „Eigene Angaben" ist ein Stammdatum wie die Firma und steht neben ihr — nicht hinter
      // „Administration" im Fuss (dort gehoeren Konto, Instanz und Rollen hin) und nicht in einem
      // eigenen Block, denn ein Block mit einem Eintrag benennt keine Gruppe (E14).
      {
        beschriftung: 'Eigene Angaben',
        ziel: '/eigene-angaben',
        symbol: 'id',
        recht: 'darfEinstellungenSehen',
      },
    ],
  },
];

export const FUSS_EINTRAEGE: readonly NavEintrag[] = [
  {
    beschriftung: 'Administration',
    ziel: '/administration',
    symbol: 'settings',
    recht: 'darfEinstellungenSehen',
  },
  { beschriftung: 'Dokumentation', ziel: '/dokumentation', symbol: 'book' },
];

/** Ob ein Eintrag fuer diese Rechte in der Schiene steht. */
export function sichtbar(eintrag: NavEintrag, rechte: Rechte): boolean {
  return eintrag.recht === undefined || rechte[eintrag.recht];
}

/** Die Bloecke mit ihren sichtbaren Eintraegen; ein Block ohne sichtbaren Eintrag faellt weg. */
export function sichtbareBloecke(bloecke: readonly NavBlock[], rechte: Rechte): NavBlock[] {
  return bloecke
    .map((block) => ({
      ...block,
      eintraege: block.eintraege.filter((eintrag) => sichtbar(eintrag, rechte)),
    }))
    .filter((block) => block.eintraege.length > 0);
}
