package org.mwolff.fbcrm.angebot.web;

import java.math.BigDecimal;
import java.util.List;
import org.mwolff.fbcrm.angebot.application.Pipeline;

/**
 * Die Pipeline, wie die Oberflaeche sie liest (Kriterien 23, 24).
 *
 * <p>Die beiden Summen stehen neben den Zeilen und nicht nur darin: Die Ansicht zeigt sie oben als
 * Kennzahlen, und sie selbst aufzuaddieren hiesse, die Rundungsregel aus E20 ein zweites Mal zu
 * schreiben. Eine leere Liste mit zwei Nullsummen heisst „keine offene Chance".
 *
 * @param zeilen die offenen Angebote in der Reihenfolge der Auswertung
 * @param summe die ungewichtete Netto-Summe
 * @param gewichteteSumme die gewichtete Pipeline
 */
public record PipelineResponse(
    List<PipelineZeileResponse> zeilen, BigDecimal summe, BigDecimal gewichteteSumme) {

  /** Die Sicht der Oberflaeche auf die Pipeline. */
  static PipelineResponse of(final Pipeline pipeline) {
    return new PipelineResponse(
        pipeline.zeilen().stream().map(PipelineZeileResponse::of).toList(),
        pipeline.summe(),
        pipeline.gewichteteSumme());
  }
}
