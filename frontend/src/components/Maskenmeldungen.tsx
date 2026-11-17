import Alert from '@mui/material/Alert';

import EigeneEingaben from './EigeneEingaben';
import type { EigeneEingabe } from './EigeneEingaben';
import Konfliktmeldung from './Konfliktmeldung';

/**
 * Die Meldungen ueber dem Formular einer Maske, in fester Reihenfolge (Issue #490): der Ausfall,
 * die Bestaetigung des Speicherns, die {@link Konfliktmeldung} und die {@link EigeneEingaben}.
 *
 * Eine Komponente fuer alle Masken statt einer Abschrift je Maske — dieselbe Begruendung wie bei
 * der Konfliktmeldung selbst (Plan #415, E12). Die Meldungen stehen als Geschwister ohne eigene
 * Huelle, damit der Abstand der Maske zwischen ihnen gilt.
 */
export interface MaskenmeldungenProps {
  /** Der Satz zum Ausfall, oder `null`. */
  readonly fehler: string | null;
  /** Der Satz zum gelungenen Speichern; ohne Angabe oder mit `null` keine Bestaetigung. */
  readonly erfolg?: string | null;
  /** Der Fehlschlag des Speicherns, ungeprueft — die Konfliktmeldung liest ihn selbst. */
  readonly abweisung: unknown;
  /**
   * Holt den Stand des Servers in die Maske. `null`, wo es nichts neu zu laden gibt — etwa bei
   * einem Eintrag, der noch nicht angelegt ist; dann entfaellt die Konfliktmeldung.
   */
  readonly onNeuLaden: (() => void) | null;
  /** Der Weg zur Uebersicht, wenn der Eintrag geloescht ist. */
  readonly uebersicht: string;
  /** Die eigenen Eingaben nach „Neu laden", oder `null`. */
  readonly eigene: readonly EigeneEingabe[] | null;
  /** Die eigenen Eingaben werden nicht mehr gebraucht. */
  readonly onVerwerfen: () => void;
}

export default function Maskenmeldungen({
  fehler,
  erfolg = null,
  abweisung,
  onNeuLaden,
  uebersicht,
  eigene,
  onVerwerfen,
}: MaskenmeldungenProps) {
  return (
    <>
      {fehler === null ? null : <Alert severity="error">{fehler}</Alert>}
      {erfolg === null ? null : <Alert severity="success">{erfolg}</Alert>}
      {onNeuLaden === null ? null : (
        <Konfliktmeldung ursache={abweisung} onNeuLaden={onNeuLaden} uebersicht={uebersicht} />
      )}
      {eigene === null ? null : <EigeneEingaben werte={eigene} onVerwerfen={onVerwerfen} />}
    </>
  );
}
