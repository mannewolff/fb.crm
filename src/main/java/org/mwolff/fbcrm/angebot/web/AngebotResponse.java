package org.mwolff.fbcrm.angebot.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.angebot.application.AngebotAnsicht;
import org.mwolff.fbcrm.angebot.application.Kundenangaben;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.Angebotsstand;

/**
 * Ein Angebot mit seinen Positionen, wie die Detailansicht es zeigt.
 *
 * <p>Zwei Werte kommen gerechnet und stehen in keiner Spalte: der {@code stand} aus dem Vergleich
 * der Gueltigkeit mit dem heutigen Tag (E4) und die {@code summe} als Addition der gerundeten
 * Positionsbetraege (E5). Der gespeicherte Zustand erscheint <b>nicht</b> daneben — er waere eine
 * zweite Wahrheit, und „abgelaufen" stuende in keiner der beiden.
 *
 * <p>Die beiden Anschriftskopien und der Schluessel des Dokuments fehlen hier: Sie entstehen erst
 * beim Versenden und kommen mit dem Paket, das es baut.
 *
 * @param id technische Id
 * @param firmaId Kennung der Firma, an die das Angebot geht
 * @param firmaName Name der Firma
 * @param ansprechpartnerId Kennung des Ansprechpartners, oder {@code null}
 * @param ansprechpartnerName Name des Ansprechpartners, oder {@code null}
 * @param nummer Angebotsnummer, oder {@code null} im Entwurf (Kriterium 11)
 * @param stand der Stand, als der das Angebot heute gilt (Kriterium 18)
 * @param angebotDatum Datum des Angebots
 * @param gueltigBis letzter Tag der Gueltigkeit
 * @param leistungsbeschreibung einleitender Text, oder {@code null}
 * @param zahlungsbedingungen Zahlungsbedingungen, oder {@code null}
 * @param versendetAm Zeitpunkt des Versendens, oder {@code null} im Entwurf
 * @param reaktionAm Zeitpunkt der Reaktion des Kunden, oder {@code null}
 * @param positionen die Positionen in ihrer Reihenfolge
 * @param summe die Netto-Summe, gerechnet
 */
public record AngebotResponse(
    long id,
    long firmaId,
    String firmaName,
    @Nullable Long ansprechpartnerId,
    @Nullable String ansprechpartnerName,
    @Nullable String nummer,
    Angebotsstand stand,
    LocalDate angebotDatum,
    LocalDate gueltigBis,
    @Nullable String leistungsbeschreibung,
    @Nullable String zahlungsbedingungen,
    @Nullable Instant versendetAm,
    @Nullable Instant reaktionAm,
    List<AngebotPositionResponse> positionen,
    BigDecimal summe) {

  /** Die Sicht der Oberflaeche auf ein Angebot, beschriftet mit den Namen seines Kunden. */
  static AngebotResponse of(final AngebotAnsicht ansicht, final Kundenangaben kunde) {
    final Angebot angebot = ansicht.angebot();
    return new AngebotResponse(
        angebot.requireId(),
        angebot.firmaId(),
        kunde.firmaName(),
        angebot.ansprechpartnerId(),
        kunde.ansprechpartnerName(),
        angebot.nummer(),
        ansicht.stand(),
        angebot.angebotDatum(),
        angebot.gueltigBis(),
        angebot.leistungsbeschreibung(),
        angebot.zahlungsbedingungen(),
        angebot.versendetAm(),
        angebot.reaktionAm(),
        angebot.positionen().stream().map(AngebotPositionResponse::of).toList(),
        angebot.summe());
  }
}
