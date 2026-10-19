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
 * **Zwei Ketten, nicht eine** (Issue #226, Plan #218): Das Angebot an einen Kunden laeuft
 * „Angelegt — Abgegeben — Bestellt — Erledigt — Abgerechnet", die eigene interne Arbeit
 * „Angelegt — Laeuft — Abgeschlossen". Welche gilt, bestimmt das Kennzeichen `intern` am Angebot;
 * ein Angebot zeigt nie Werte aus beiden Ketten. Dass „Laeuft" dieselbe Toenung traegt wie
 * „Bestellt" und „Abgeschlossen" dieselbe wie „Abgerechnet", ist darum kein Gleichklang, der sich
 * verwechseln liesse — das Wort steht daneben und ist je Kette eindeutig.
 *
 * Die Werte sind das Gegenstueck zu `Angebotsstatus` im Backend, in dessen Reihenfolge.
 */
export type Angebotsstatus =
  | 'ANGELEGT'
  | 'ABGEGEBEN'
  | 'BESTELLT'
  | 'ERLEDIGT'
  | 'ABGERECHNET'
  | 'LAEUFT'
  | 'ABGESCHLOSSEN';

/** Die sieben Status in ihrer Reihenfolge — fuer Filter und Auswahllisten. */
export const ANGEBOTSSTATUS: readonly Angebotsstatus[] = [
  'ANGELEGT',
  'ABGEGEBEN',
  'BESTELLT',
  'ERLEDIGT',
  'ABGERECHNET',
  'LAEUFT',
  'ABGESCHLOSSEN',
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
  LAEUFT: { wort: 'Läuft', toenung: 'pfirsich', symbol: IconTool },
  ABGESCHLOSSEN: { wort: 'Abgeschlossen', toenung: 'salbei', symbol: IconCircleCheck },
};

/** Wort, Toenung und Symbol zum Status. */
export function angebotsstatusBild(status: Angebotsstatus): Angebotsstatusbild {
  return BILDER[status];
}

/** Verengt einen Wert auf einen der sieben Status, oder `null`, wenn er keiner ist. */
export function alsAngebotsstatus(wert: unknown): Angebotsstatus | null {
  return ANGEBOTSSTATUS.find((status) => status === wert) ?? null;
}

/**
 * Ein Wert des einen Filters der Uebersicht aller Angebote: einer der sieben Status oder
 * „Storniert" (Plan #332, E7, E19).
 *
 * **„Storniert" ist kein Status** (E1): Ein storniertes Angebot behaelt den Status, aus dem es
 * storniert wurde. Darum ist der Filterwert ein eigener Typ und `BILDER` bleibt bei sieben
 * Eintraegen. Die Werte sind eine Partition (E7): Unter einem Status stehen nur die nicht
 * stornierten Angebote, unter `'STORNIERT'` jedes stornierte, gleich in welchem Status.
 */
export type Filterwert = Angebotsstatus | 'STORNIERT';

/** Die acht Filterwerte — die sieben Status in ihrer Reihenfolge, „Storniert" am Ende. */
export const FILTERWERTE: readonly Filterwert[] = [...ANGEBOTSSTATUS, 'STORNIERT'];

/**
 * Das Wort fuer „storniert" — geteilt von `StorniertChip` und dem Filter, damit beide dieselbe
 * Zeichenkette zeigen (E19).
 */
export const STORNIERT_WORT = 'Storniert';

/**
 * Verengt einen Wert, etwa aus der Adresse, auf einen der acht Filterwerte, oder `null` — ein
 * unbekannter Wert bedeutet wie bisher „alle" (E20).
 */
export function alsFilterwert(wert: unknown): Filterwert | null {
  return FILTERWERTE.find((filterwert) => filterwert === wert) ?? null;
}

/** Das Wort zum Filterwert: fuer einen Status das Wort seines Bilds, sonst {@link STORNIERT_WORT}. */
export function filterwortZu(wert: Filterwert): string {
  return wert === 'STORNIERT' ? STORNIERT_WORT : angebotsstatusBild(wert).wort;
}
