import type { FieldErrors } from '../api/client';
import { feldMeldungen, istBearbeitungskonflikt } from './apifehler';

/**
 * Was eine Maske nach einem abgewiesenen Speichern zeigt — genau eine der drei Angaben ist belegt.
 *
 * Die Form folgt dem Zustand der Maske: `abweisung` liest die {@link Konfliktmeldung}, `feldFehler`
 * stehen an den Feldern, `fehler` steht als Alert ueber dem Formular.
 */
export interface Speicherabweisung {
  /** Der Bearbeitungskonflikt, ungeprueft, oder `null`. */
  readonly abweisung: unknown;
  /** Die Meldungen des Servers je Feld; leer, wo keine anstehen. */
  readonly feldFehler: FieldErrors;
  /** Der Satz zum Ausfall, oder `null`. */
  readonly fehler: string | null;
}

/**
 * Ordnet den Fehlschlag eines Speicherns ein: Bearbeitungskonflikt, Feldfehler oder Ausfall
 * (Issue #490).
 *
 * Der Bearbeitungskonflikt geht vor, auch wenn der Server Feldmeldungen mitschickt: Die Eingaben
 * bleiben stehen, und den Weg weiter zeigt die Konfliktmeldung (#411, Kriterium 3). Alles, was
 * weder Konflikt ist noch eine Feldmeldung traegt, ist ein Ausfall mit dem Satz der Maske.
 */
export function speicherabweisung(ursache: unknown, ausfall: string): Speicherabweisung {
  if (istBearbeitungskonflikt(ursache)) {
    return { abweisung: ursache, feldFehler: {}, fehler: null };
  }
  const felder = feldMeldungen(ursache);
  if (Object.keys(felder).length > 0) {
    return { abweisung: null, feldFehler: felder, fehler: null };
  }
  return { abweisung: null, feldFehler: {}, fehler: ausfall };
}
