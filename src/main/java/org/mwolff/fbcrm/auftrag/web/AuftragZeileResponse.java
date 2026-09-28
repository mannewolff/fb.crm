package org.mwolff.fbcrm.auftrag.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.auftrag.domain.Auftrag;
import org.mwolff.fbcrm.auftrag.domain.Auftragsstatus;

/**
 * Eine Zeile der Auftragsliste am Vorgang (Kriterium 9).
 *
 * <p>Nur, was die Liste zeigt: Nummer, Datum, Status, Leistungszeitraum und Summe. Die Positionen
 * bleiben draussen, wie in {@code angebot.web.AngebotZeileResponse} — die Liste zeigt sie nicht,
 * und die Detailansicht holt sie ohnehin.
 *
 * <p>Auch die Nummer des Quell-Angebots fehlt hier, anders als in {@link AuftragResponse}: Die
 * Liste steht am Vorgang, und dort ist die Frage „welche Auftraege gibt es", nicht „woraus entstand
 * dieser eine". Je Zeile ein Angebot zu laden waere Gewicht ohne Nutzen.
 *
 * @param id technische Id
 * @param nummer Auftragsnummer aus dem Nummernkreis (Kriterium 3)
 * @param status der Status des Auftrags (Kriterium 7)
 * @param auftragDatum Datum des Auftrags
 * @param leistungAb erster Tag des Leistungszeitraums, oder {@code null}
 * @param leistungBis letzter Tag des Leistungszeitraums, oder {@code null}
 * @param summe die Netto-Summe, gerechnet (E11)
 */
public record AuftragZeileResponse(
    long id,
    String nummer,
    Auftragsstatus status,
    LocalDate auftragDatum,
    @Nullable LocalDate leistungAb,
    @Nullable LocalDate leistungBis,
    BigDecimal summe) {

  /** Die Sicht der Oberflaeche auf einen Auftrag in der Liste. */
  static AuftragZeileResponse of(final Auftrag auftrag) {
    return new AuftragZeileResponse(
        auftrag.requireId(),
        auftrag.nummer(),
        auftrag.status(),
        auftrag.auftragDatum(),
        auftrag.leistungAb(),
        auftrag.leistungBis(),
        auftrag.summe());
  }
}
