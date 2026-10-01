import { IconFileInvoice, IconPencil } from '@tabler/icons-react';
import type { TablerIcon } from '@tabler/icons-react';

import type { ToenungName } from '../theme';

/**
 * Der Zustand einer Rechnung, wie ihn die Oberflaeche zeigt (#160, Kriterium 15).
 *
 * Wort, Toenung und Symbol stehen hier zusammen und nicht in der Ansicht: Der Zustand erscheint in
 * der Liste aller Rechnungen, in der Ansicht der einzelnen Rechnung und am Angebot — dreimal
 * dieselbe Zuordnung waere dreimal dieselbe Pflege. Aufbau und Begruendung wie bei
 * {@link angebotsstatusBild}.
 *
 * **Das Wort traegt den Zustand, die Toenung stuetzt ihn** (CLAUDE-design.md, „Zustandsformen").
 * Flieder ist die neutrale Kategorie — ein Entwurf ist noch nichts —, Himmel steht fuer „abgegeben,
 * laufend", und genau das ist eine gestellte Rechnung: hinaus, noch nicht bezahlt.
 *
 * Das Symbol kommt als **Komponente** heraus und nicht als Name, aus demselben Grund wie dort: Ein
 * Name braeuchte bei jedem Leser dieselbe Aufloesung noch einmal.
 *
 * Die Werte sind das Gegenstueck zu `Rechnungszustand` im Backend, in dessen Reihenfolge.
 */
export type Rechnungszustand = 'ENTWURF' | 'GESTELLT';

/** Die zwei Zustaende in ihrer Reihenfolge. */
export const RECHNUNGSZUSTAENDE: readonly Rechnungszustand[] = ['ENTWURF', 'GESTELLT'];

/** Wie ein Zustand erscheint: als Wort, in seiner Toenung, mit seinem stuetzenden Symbol. */
export interface Rechnungszustandsbild {
  readonly wort: string;
  readonly toenung: ToenungName;
  readonly symbol: TablerIcon;
}

const BILDER: Readonly<Record<Rechnungszustand, Rechnungszustandsbild>> = {
  ENTWURF: { wort: 'Entwurf', toenung: 'flieder', symbol: IconPencil },
  GESTELLT: { wort: 'Gestellt', toenung: 'himmel', symbol: IconFileInvoice },
};

/** Wort, Toenung und Symbol zum Zustand. */
export function rechnungszustandBild(zustand: Rechnungszustand): Rechnungszustandsbild {
  return BILDER[zustand];
}

/** Verengt einen Wert auf einen der zwei Zustaende, oder `null`, wenn er keiner ist. */
export function alsRechnungszustand(wert: unknown): Rechnungszustand | null {
  return RECHNUNGSZUSTAENDE.find((zustand) => zustand === wert) ?? null;
}
