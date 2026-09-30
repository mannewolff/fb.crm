package org.mwolff.fbcrm.angebot.application;

import java.util.Comparator;
import org.mwolff.fbcrm.angebot.domain.Angebotskommentar;

/**
 * Die eine Reihenfolge jeder Kommentarliste: neuester zuerst (Issue #140, Kriterium 4).
 *
 * <p>Gezaehlt wird der Zeitpunkt der Anlage absteigend; bei Gleichstand entscheidet die hoehere
 * Kennung, damit zwei Aufrufe dieselbe Liste liefern. Sortiert wird in der Anwendungsschicht und
 * nicht im Bestand — dieselbe Aufteilung wie bei {@link Angebotsreihenfolge} (Plan #141, E8): Die
 * Ordnung ist eine Aussage der Ansicht, keine Eigenschaft der Zeilen.
 */
final class Kommentarreihenfolge {

  /** Neuester zuerst, bei gleichem Zeitpunkt die hoehere Kennung. */
  static final Comparator<Angebotskommentar> NEUESTE_ZUERST =
      Comparator.comparing(Angebotskommentar::createdAt, Comparator.reverseOrder())
          .thenComparing(Angebotskommentar::requireId, Comparator.reverseOrder());

  private Kommentarreihenfolge() {}
}
