package org.mwolff.fbcrm.rechnung.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.rechnung.application.Rechnungsansicht;
import org.mwolff.fbcrm.rechnung.domain.Rechnung;
import org.mwolff.fbcrm.rechnung.domain.Rechnungszustand;

/**
 * Eine Rechnung mit den Zeilen ihrer Maske (#160, Kriterien 3 bis 10).
 *
 * <p>{@code netto}, {@code steuer} und {@code brutto} kommen gerechnet und stehen in keiner Spalte
 * (E5): Netto ist die Summe der gerundeten Positionsbetraege, die Steuer entsteht aus dieser Summe
 * und nicht je Position. Der {@code steuersatz} ist der, der fuer diese Rechnung gilt — beim
 * Entwurf der der aktuellen Einstellungen, bei der gestellten Rechnung ihr eigener (Kriterium 14).
 *
 * <p>Die {@code nummer} fehlt beim Entwurf; sie entsteht erst mit dem Stellen. Ebenso das {@code
 * zahlungszielTage} und die beiden Kopien {@code empfaenger} und {@code absender} — sie werden mit
 * dem Stellen festgeschrieben (Kriterium 14).
 *
 * <p><b>Der {@code firmaName} einer gestellten Rechnung kommt aus der Kopie</b> und nicht von der
 * Firma von heute; entschieden wird das in {@code RechnungLesenUseCase}, das hier die fertige
 * Ansicht liefert. Die {@code firmaId} bleibt die der Firma — sie ist der Griff, mit dem die
 * Oberflaeche zu ihr springt, und eine Kopie hat keinen.
 *
 * @param id Kennung der Rechnung
 * @param angebotId Kennung des Angebots, das abgerechnet wird
 * @param firmaId Kennung der Firma, an die die Rechnung geht
 * @param firmaName Name dieser Firma
 * @param rechnungDatum Datum der Rechnung
 * @param leistungszeitraum Zeitraum der Leistung als Text, oder {@code null}
 * @param zustand Entwurf oder gestellt
 * @param nummer die Rechnungsnummer, oder {@code null} im Entwurf
 * @param steuersatz der geltende Steuersatz in Prozent
 * @param netto die Netto-Summe, gerechnet
 * @param steuer die Steuer auf die Netto-Summe, gerechnet
 * @param brutto die Brutto-Summe, gerechnet
 * @param zahlungszielTage das festgeschriebene Zahlungsziel in Tagen, oder {@code null} im Entwurf
 * @param empfaenger Kopie des Empfaengers, oder {@code null} im Entwurf
 * @param absender Kopie der eigenen Angaben, oder {@code null} im Entwurf
 * @param zeilen je Position des Angebots eine Zeile, in der Reihenfolge des Angebots
 */
public record RechnungResponse(
    long id,
    long angebotId,
    long firmaId,
    String firmaName,
    LocalDate rechnungDatum,
    @Nullable String leistungszeitraum,
    Rechnungszustand zustand,
    @Nullable String nummer,
    BigDecimal steuersatz,
    BigDecimal netto,
    BigDecimal steuer,
    BigDecimal brutto,
    @Nullable Integer zahlungszielTage,
    @Nullable BelegempfaengerResponse empfaenger,
    @Nullable BelegabsenderResponse absender,
    List<RechnungZeileResponse> zeilen) {

  /** Die Sicht der Oberflaeche auf eine Rechnung. */
  static RechnungResponse of(final Rechnungsansicht ansicht) {
    final Rechnung rechnung = ansicht.rechnung();
    final BigDecimal satz = ansicht.steuersatz();
    return new RechnungResponse(
        rechnung.requireId(),
        rechnung.angebotId(),
        ansicht.firmaId(),
        ansicht.firmaName(),
        rechnung.rechnungDatum(),
        rechnung.leistungszeitraum(),
        rechnung.zustand(),
        rechnung.nummer(),
        satz,
        rechnung.netto(),
        rechnung.steuer(satz),
        rechnung.brutto(satz),
        rechnung.zahlungszielTage(),
        BelegempfaengerResponse.of(rechnung.empfaenger()),
        BelegabsenderResponse.of(rechnung.absender()),
        ansicht.zeilen().stream().map(RechnungZeileResponse::of).toList());
  }
}
