import { IconCircleCheck, IconPencil, IconReceipt, IconSend, IconTool } from '@tabler/icons-react';
import type { TablerIcon } from '@tabler/icons-react';

import type { ToenungName } from '../theme';

/**
 * Der Status eines Angebots, wie ihn die Oberflaeche zeigt (Issue #127).
 *
 * Wort, Toenung und Symbol stehen hier zusammen und nicht in der Ansicht: Der Status erscheint in
 * der Angebotsansicht, in der Liste an der Firma und in der Uebersicht aller Angebote — dreimal
 * dieselbe Zuordnung waere dreimal dieselbe Pflege.
 *
 * **Das Wort traegt den Status, die Toenung stuetzt ihn** (CLAUDE-design.md, „Zustandsformen"). Die
 * Toenungen folgen der Tabelle dort: Flieder ist die neutrale Kategorie, Himmel „abgegeben,
 * laufend", Pfirsich „bestellte Angebote (laufende Arbeit)", Bernstein „Grenze erreicht" — die
 * Arbeit ist getan, die Rechnung steht aus —, Salbei „erfolgreich".
 *
 * Das Symbol kommt als **Komponente** heraus und nicht als Name, aus demselben Grund wie bisher:
 * Ein Name braeuchte bei jedem Leser dieselbe Aufloesung noch einmal.
 *
 * Die Werte sind das Gegenstueck zu `Angebotsstatus` im Backend, in dessen Reihenfolge.
 */
export type Angebotsstatus = 'ANGELEGT' | 'ABGEGEBEN' | 'BESTELLT' | 'ERLEDIGT' | 'ABGERECHNET';

/** Die fuenf Status in ihrer Reihenfolge — fuer Filter und Auswahllisten. */
export const ANGEBOTSSTATUS: readonly Angebotsstatus[] = [
  'ANGELEGT',
  'ABGEGEBEN',
  'BESTELLT',
  'ERLEDIGT',
  'ABGERECHNET',
];

/** Wie ein Status erscheint: als Wort, in seiner Toenung, mit seinem stuetzenden Symbol. */
export interface Angebotsstatusbild {
  readonly wort: string;
  readonly toenung: ToenungName;
  readonly symbol: TablerIcon;
}

const BILDER: Readonly<Record<Angebotsstatus, Angebotsstatusbild>> = {
  ANGELEGT: { wort: 'Angelegt', toenung: 'flieder', symbol: IconPencil },
  ABGEGEBEN: { wort: 'Abgegeben', toenung: 'himmel', symbol: IconSend },
  BESTELLT: { wort: 'Bestellt', toenung: 'pfirsich', symbol: IconTool },
  ERLEDIGT: { wort: 'Erledigt', toenung: 'bernstein', symbol: IconCircleCheck },
  ABGERECHNET: { wort: 'Abgerechnet', toenung: 'salbei', symbol: IconReceipt },
};

/** Wort, Toenung und Symbol zum Status. */
export function angebotsstatusBild(status: Angebotsstatus): Angebotsstatusbild {
  return BILDER[status];
}

/** Verengt einen Wert auf einen der fuenf Status, oder `null`, wenn er keiner ist. */
export function alsAngebotsstatus(wert: unknown): Angebotsstatus | null {
  return ANGEBOTSSTATUS.find((status) => status === wert) ?? null;
}
