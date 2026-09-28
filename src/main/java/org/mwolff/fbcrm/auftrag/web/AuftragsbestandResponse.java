package org.mwolff.fbcrm.auftrag.web;

import java.math.BigDecimal;
import java.util.List;
import org.mwolff.fbcrm.auftrag.application.Auftragsbestand;

/**
 * Der Auftragsbestand, wie die Oberflaeche ihn liest (Kriterien 12, 13).
 *
 * <p>Die beiden Kennzahlen stehen neben den Zeilen und nicht nur darin: Die Ansicht zeigt sie oben,
 * und sie selbst aufzuaddieren hiesse, die Rundungsregel aus E5 ein zweites Mal zu schreiben. Eine
 * leere Liste mit zwei Nullsummen heisst „nichts liegt mehr vor mir".
 *
 * @param zeilen die laufenden Auftraege in der Reihenfolge der Auswertung
 * @param beauftragt die Summe der Auftragssummen
 * @param nochOffen die Summe der offenen Reste
 */
public record AuftragsbestandResponse(
    List<AuftragsbestandZeileResponse> zeilen, BigDecimal beauftragt, BigDecimal nochOffen) {

  /** Die Sicht der Oberflaeche auf den Auftragsbestand. */
  static AuftragsbestandResponse of(final Auftragsbestand bestand) {
    return new AuftragsbestandResponse(
        bestand.zeilen().stream().map(AuftragsbestandZeileResponse::of).toList(),
        bestand.beauftragt(),
        bestand.nochOffen());
  }
}
