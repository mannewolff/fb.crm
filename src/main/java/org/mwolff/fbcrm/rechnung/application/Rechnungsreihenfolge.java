package org.mwolff.fbcrm.rechnung.application;

import java.util.Comparator;
import org.mwolff.fbcrm.rechnung.domain.Rechnung;

/**
 * Die Reihenfolge der Rechnungslisten: neueste zuerst (#160, Kriterium 1).
 *
 * <p>Gezaehlt wird das Rechnungsdatum absteigend; bei gleichem Datum entscheidet die hoehere
 * Kennung, damit zwei Aufrufe dieselbe Liste liefern. Die Liste aller Rechnungen mischt zwei Arten
 * mit zwei Kennungsraeumen — dort steht die Art dazwischen ({@link #GEMISCHT_NEUESTE_ZUERST}, Plan
 * #259, E19). Sortiert wird in der Anwendungsschicht und nicht im Bestand — dieselbe Aufteilung wie
 * bei {@code angebot.application.Angebotsreihenfolge}: Die Ordnung ist eine Aussage der Ansicht,
 * keine Eigenschaft der Zeilen.
 */
final class Rechnungsreihenfolge {

  /** Neueste zuerst, bei gleichem Datum die hoehere Kennung. */
  static final Comparator<Rechnung> NEUESTE_ZUERST =
      Comparator.comparing(Rechnung::rechnungDatum, Comparator.reverseOrder())
          .thenComparing(Rechnung::requireId, Comparator.reverseOrder());

  /**
   * Die Gesamtordnung der gemischten Liste: neueste zuerst, bei gleichem Datum die von fb.crm
   * geschriebene vor der nachgetragenen, bei gleicher Art die hoehere Kennung. Ohne die Art waere
   * die Ordnung bei gleichem Datum nicht eindeutig, denn dieselbe Kennung kann in beiden Raeumen
   * vorkommen.
   */
  static final Comparator<Rechnungslistenzeile> GEMISCHT_NEUESTE_ZUERST =
      Comparator.comparing(Rechnungslistenzeile::rechnungDatum, Comparator.reverseOrder())
          .thenComparing(Rechnungslistenzeile::nachgetragen)
          .thenComparing(Rechnungslistenzeile::id, Comparator.reverseOrder());

  private Rechnungsreihenfolge() {}
}
