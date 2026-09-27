import {
  IconArchive,
  IconCircleCheck,
  IconCircleX,
  IconClockExclamation,
  IconPencil,
  IconSend,
} from '@tabler/icons-react';
import type { TablerIcon } from '@tabler/icons-react';

import type { ToenungName } from '../theme';

/**
 * Der Stand eines Angebots, wie ihn die Oberflaeche zeigt (Kriterium 18).
 *
 * Wort, Toenung und Symbol stehen hier zusammen und nicht in der Ansicht: Der Stand erscheint in
 * der Angebotsansicht, in der Liste am Vorgang und spaeter in den Auswertungen — dreimal dieselbe
 * Zuordnung waere dreimal dieselbe Pflege.
 *
 * **Das Wort traegt den Stand, die Toenung stuetzt ihn** (CLAUDE-design.md, „Zustandsformen"). Die
 * Toenungen sind die des Entwurfs und keine neuen: Himmel heisst „versendet, laufend", Salbei
 * „erfolgreich", Bernstein „Grenze erreicht", Rose „gescheitert, stillgelegt", Flieder ist die
 * neutrale Kategorie.
 *
 * Das Symbol kommt als **Komponente** heraus und nicht als Name. Ein Name braeuchte bei jedem
 * Leser dieselbe Aufloesung noch einmal — anders als in {@link navItems}, wo genau eine Ansicht
 * (die Schiene) sie aufloest und die Datei dafuer ohne React auskommen soll.
 *
 * Die Werte sind das Gegenstueck zu `Angebotsstand` im Backend. Der Typ steht hier und nicht in
 * `api/angebote.ts`: Dieses Paket legt die Grundlagen, die Schnittstelle der Angebote kommt mit
 * dem naechsten — sie nimmt den Typ von hier.
 */
export type Angebotsstand =
  | 'ENTWURF'
  | 'VERSENDET'
  | 'ABGELAUFEN'
  | 'ANGENOMMEN'
  | 'ABGELEHNT'
  | 'ABGELOEST';

/** Wie ein Stand erscheint: als Wort, in seiner Toenung, mit seinem stuetzenden Symbol. */
export interface Angebotsstandbild {
  readonly wort: string;
  readonly toenung: ToenungName;
  readonly symbol: TablerIcon;
}

const BILDER: Readonly<Record<Angebotsstand, Angebotsstandbild>> = {
  // Noch nichts geschehen, keine Nummer, kein Dokument — die neutrale Kategorie.
  ENTWURF: { wort: 'Entwurf', toenung: 'flieder', symbol: IconPencil },
  // „Versendet" ist die Bedeutung von Himmel, wortwoertlich (CLAUDE-design.md, „Toenungen").
  VERSENDET: { wort: 'Versendet', toenung: 'himmel', symbol: IconSend },
  // Die Gueltigkeit ist verstrichen — eine Grenze, die erreicht ist, aber kein Scheitern: Das
  // Angebot bleibt offen fuer eine Reaktion (Angebotsstand im Backend).
  ABGELAUFEN: { wort: 'Abgelaufen', toenung: 'bernstein', symbol: IconClockExclamation },
  ANGENOMMEN: { wort: 'Angenommen', toenung: 'salbei', symbol: IconCircleCheck },
  ABGELEHNT: { wort: 'Abgelehnt', toenung: 'rose', symbol: IconCircleX },
  // Durch ein spaeteres Angebot ersetzt — dieselbe Bedeutung wie „stillgelegt" bei der Firma.
  ABGELOEST: { wort: 'Abgelöst', toenung: 'rose', symbol: IconArchive },
};

/** Wort, Toenung und Symbol zum Stand (Kriterium 18). */
export function angebotsstandBild(stand: Angebotsstand): Angebotsstandbild {
  return BILDER[stand];
}
