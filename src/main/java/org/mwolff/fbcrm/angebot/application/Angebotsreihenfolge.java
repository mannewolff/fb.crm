package org.mwolff.fbcrm.angebot.application;

import java.util.Comparator;
import org.mwolff.fbcrm.angebot.domain.Angebot;

/**
 * Die eine Reihenfolge jeder Angebotsliste: neueste zuerst (Issue #127, Kriterium 7).
 *
 * <p>Gezaehlt wird das Angebotsdatum absteigend; bei gleichem Datum entscheidet die hoehere
 * Kennung, damit zwei Aufrufe dieselbe Liste liefern. Sortiert wird in der Anwendungsschicht und
 * nicht im Bestand: Die Ordnung ist eine Aussage der Ansicht, keine Eigenschaft der Zeilen.
 */
final class Angebotsreihenfolge {

  /** Neueste zuerst, bei gleichem Datum die hoehere Kennung. */
  static final Comparator<Angebot> NEUESTE_ZUERST =
      Comparator.comparing(Angebot::angebotDatum, Comparator.reverseOrder())
          .thenComparing(Angebot::requireId, Comparator.reverseOrder());

  private Angebotsreihenfolge() {}
}
