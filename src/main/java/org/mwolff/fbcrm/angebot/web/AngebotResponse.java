package org.mwolff.fbcrm.angebot.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.angebot.application.Kundenangaben;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.Angebotsstatus;

/**
 * Ein Angebot mit seinen Positionen, wie die Detailansicht es zeigt (Issue #127).
 *
 * <p>Die {@code summe} kommt gerechnet und steht in keiner Spalte: die Addition der gerundeten
 * Positionsbetraege (E5). Die Namen von Firma und Ansprechpartner kommen aus dem heutigen Bestand.
 *
 * @param id technische Id
 * @param firmaId Kennung der Firma, an die das Angebot geht
 * @param firmaName Name der Firma
 * @param ansprechpartnerId Kennung des Ansprechpartners, oder {@code null}
 * @param ansprechpartnerName Name des Ansprechpartners, oder {@code null}
 * @param intern ob das Angebot die eigene interne Arbeit festhaelt (Issue #226)
 * @param status wie weit das Angebot gediehen ist
 * @param angebotDatum Datum des Angebots
 * @param beschreibung der Text des Angebots, oder {@code null}
 * @param positionen die Positionen in ihrer Reihenfolge
 * @param summe die Netto-Summe, gerechnet
 */
public record AngebotResponse(
    long id,
    long firmaId,
    String firmaName,
    @Nullable Long ansprechpartnerId,
    @Nullable String ansprechpartnerName,
    boolean intern,
    Angebotsstatus status,
    LocalDate angebotDatum,
    @Nullable String beschreibung,
    List<AngebotPositionResponse> positionen,
    BigDecimal summe) {

  /** Die Sicht der Oberflaeche auf ein Angebot, beschriftet mit den Namen seines Kunden. */
  static AngebotResponse of(final Angebot angebot, final Kundenangaben kunde) {
    return new AngebotResponse(
        angebot.requireId(),
        angebot.firmaId(),
        kunde.firmaName(),
        angebot.ansprechpartnerId(),
        kunde.ansprechpartnerName(),
        angebot.intern(),
        angebot.status(),
        angebot.angebotDatum(),
        angebot.beschreibung(),
        angebot.positionen().stream().map(AngebotPositionResponse::of).toList(),
        angebot.summe());
  }
}
