import { IconCircleCheck, IconFileInvoice, IconProgress } from '@tabler/icons-react';
import type { TablerIcon } from '@tabler/icons-react';

import type { ToenungName } from '../theme';

/**
 * Der Status eines Auftrags, wie ihn die Oberflaeche zeigt (Kriterium 6, Plan E18).
 *
 * Wort, Toenung und Symbol stehen hier zusammen und nicht in einer Ansicht: Der Status erscheint in
 * der Auftragsansicht, in der Liste am Vorgang und im Auftragsbestand — dreimal dieselbe Zuordnung
 * waere dreimal dieselbe Gelegenheit zum Auseinanderlaufen. Das Muster ist {@link angebotsstandBild}.
 *
 * **Das Wort traegt den Status, die Toenung stuetzt ihn** (CLAUDE-design.md, „Zustandsformen"). Die
 * drei Toene sind die des Entwurfs und keine neuen: Flieder ist die neutrale Kategorie, Himmel heisst
 * „laufend", Salbei „zu Ende gegangen, erfolgreich" — dieselbe Paarung wie der Chip „Abgeschlossen"
 * am Vorgang.
 *
 * Die Werte sind das Gegenstueck zu `Auftragsstatus` im Backend. Das Backend fuehrt dort **kein**
 * Anzeigewort (Plan E18, Fund 14 der Plan-Pruefung): Das Wort der Oberflaeche steht allein hier.
 *
 * Jedes Bild ist eingefroren. Die Zuordnung ist eine Aussage dieser Datei; wer sie in einer Ansicht
 * ueberschriebe, aenderte sie still fuer alle anderen mit.
 */
export type Auftragsstatus = 'OFFEN' | 'IN_ARBEIT' | 'ABGESCHLOSSEN';

/** Wie ein Status erscheint: als Wort, in seiner Toenung, mit seinem stuetzenden Symbol. */
export interface Auftragsstatusbild {
  readonly wort: string;
  readonly toenung: ToenungName;
  readonly symbol: TablerIcon;
}

const BILDER: Readonly<Record<Auftragsstatus, Auftragsstatusbild>> = Object.freeze({
  // Beauftragt, aber noch nicht begonnen — die neutrale Kategorie.
  OFFEN: Object.freeze({ wort: 'Offen', toenung: 'flieder', symbol: IconFileInvoice }),
  // „Laufend" ist die Bedeutung von Himmel (CLAUDE-design.md, „Toenungen").
  IN_ARBEIT: Object.freeze({ wort: 'In Arbeit', toenung: 'himmel', symbol: IconProgress }),
  ABGESCHLOSSEN: Object.freeze({
    wort: 'Abgeschlossen',
    toenung: 'salbei',
    symbol: IconCircleCheck,
  }),
});

/** Wort, Toenung und Symbol zum Status (Kriterium 6). */
export function auftragsstatusBild(status: Auftragsstatus): Auftragsstatusbild {
  return BILDER[status];
}
