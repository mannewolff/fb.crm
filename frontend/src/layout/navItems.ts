/**
 * Die Eintraege der Schiene (E15).
 *
 * Oberhalb des Fusses stehen die <b>Navigationsbloecke</b>, jeder mit einem Gruppentitel in
 * Satzschreibung (CLAUDE-design.md, „Rahmen"). Drei Bloecke traegt dieser Stand, und ihre
 * Reihenfolge ist die Aussage: <b>„Geschäft", dann „Auswertungen", dann „Stammdaten"</b> (E24).
 * Der Vorgang ist die taegliche Arbeit, die Firma ihre Voraussetzung — wer die Anwendung oeffnet,
 * will zuerst an seine Vorgaenge. „Auswertungen" steht <b>unter „Geschäft"</b> (Kriterium 28): Die
 * Auswertung folgt dem Geschaeft, das sie auswertet, und steht vor seiner Voraussetzung (E15).
 * Welche Bloecke dazukommen, entsteht mit den Fachplaenen.
 *
 * Im Fuss stehen „Administration" und „Dokumentation" als eigene Gruppe ueber der Nutzerkarte
 * (E7). „Einklappen" steht nicht mehr darunter: Es ist eine Icontaste neben der Marke geworden
 * und damit kein Ziel der Navigation mehr.
 *
 * Die <b>Symbolnamen sind die der Symbolfamilie Tabler</b> (E3) — dieselbe, die die Vorlage
 * benutzt. {@link NavRail} loest sie in Komponenten auf; hier bleibt es bei Namen, damit diese
 * Datei ohne React auskommt.
 */

export type Symbolname =
  | 'stack-2'
  | 'trending-up'
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
    eintraege: [{ beschriftung: 'Vorgänge', ziel: '/vorgaenge', symbol: 'stack-2' }],
  },
  {
    // Ein Block mit einem Eintrag benennt fuer sich noch keine Gruppe (E14) — „Auswertungen" ist
    // dennoch einer: Der naechste Auswertungsschritt tritt hier hinzu, und Pipeline neben
    // „Vorgänge" zu stellen machte aus der Auswertung eine Arbeitsansicht.
    titel: 'Auswertungen',
    eintraege: [{ beschriftung: 'Pipeline', ziel: '/pipeline', symbol: 'trending-up' }],
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
