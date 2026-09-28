package org.mwolff.fbcrm.auftrag.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.auftrag.application.AuftragAnsicht;
import org.mwolff.fbcrm.auftrag.domain.Auftrag;
import org.mwolff.fbcrm.auftrag.domain.Auftragsstatus;

/**
 * Ein Auftrag mit seinen Positionen, wie die Ansicht ihn zeigt (Kriterien 3 bis 7).
 *
 * <p>Die {@code summe} kommt gerechnet und steht in keiner Spalte (E11): die Addition der
 * gerundeten Positionsbetraege.
 *
 * <p><b>{@code angebotNummer} steht immer hier</b>, auch wenn eine Ansicht sie gerade nicht zeigt:
 * Kriterium 3 verlangt, am Auftrag zu sehen, aus welchem Angebot er entstand, und eine Kennung
 * allein zeigt kein Wort. Ein Feld, das je Weg mal da und mal nicht da ist, zwingt die Oberflaeche
 * zu zwei Formen fuer dieselbe Antwort.
 *
 * @param id technische Id
 * @param vorgangId Kennung des Vorgangs, an dem der Auftrag haengt
 * @param angebotId Kennung des Angebots, aus dem er entstand
 * @param angebotNummer Nummer dieses Angebots
 * @param nummer Auftragsnummer aus dem Nummernkreis (Kriterium 3)
 * @param status der Status des Auftrags (Kriterium 7)
 * @param auftragDatum Datum des Auftrags
 * @param kundenbestellnummer Bestellnummer des Kunden, oder {@code null}
 * @param leistungAb erster Tag des Leistungszeitraums, oder {@code null}
 * @param leistungBis letzter Tag des Leistungszeitraums, oder {@code null}
 * @param positionen die Positionen in ihrer Reihenfolge
 * @param summe die Netto-Summe, gerechnet
 */
public record AuftragResponse(
    long id,
    long vorgangId,
    long angebotId,
    @Nullable String angebotNummer,
    String nummer,
    Auftragsstatus status,
    LocalDate auftragDatum,
    @Nullable String kundenbestellnummer,
    @Nullable LocalDate leistungAb,
    @Nullable LocalDate leistungBis,
    List<AuftragPositionResponse> positionen,
    BigDecimal summe) {

  /** Die Sicht der Oberflaeche auf einen Auftrag. */
  static AuftragResponse of(final AuftragAnsicht ansicht) {
    final Auftrag auftrag = ansicht.auftrag();
    return new AuftragResponse(
        auftrag.requireId(),
        auftrag.vorgangId(),
        auftrag.angebotId(),
        ansicht.angebotNummer(),
        auftrag.nummer(),
        auftrag.status(),
        auftrag.auftragDatum(),
        auftrag.kundenbestellnummer(),
        auftrag.leistungAb(),
        auftrag.leistungBis(),
        auftrag.positionen().stream().map(AuftragPositionResponse::of).toList(),
        auftrag.summe());
  }
}
