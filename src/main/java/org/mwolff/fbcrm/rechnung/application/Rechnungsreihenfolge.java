package org.mwolff.fbcrm.rechnung.application;

import java.util.Comparator;
import org.mwolff.fbcrm.rechnung.domain.Rechnung;

/**
 * Die eine Reihenfolge jeder Rechnungsliste: neueste zuerst (#160, Kriterium 1).
 *
 * <p>Gezaehlt wird das Rechnungsdatum absteigend; bei gleichem Datum entscheidet die hoehere
 * Kennung, damit zwei Aufrufe dieselbe Liste liefern. Sortiert wird in der Anwendungsschicht und
 * nicht im Bestand — dieselbe Aufteilung wie bei {@code angebot.application.Angebotsreihenfolge}:
 * Die Ordnung ist eine Aussage der Ansicht, keine Eigenschaft der Zeilen.
 */
final class Rechnungsreihenfolge {

  /** Neueste zuerst, bei gleichem Datum die hoehere Kennung. */
  static final Comparator<Rechnung> NEUESTE_ZUERST =
      Comparator.comparing(Rechnung::rechnungDatum, Comparator.reverseOrder())
          .thenComparing(Rechnung::requireId, Comparator.reverseOrder());

  private Rechnungsreihenfolge() {}
}
