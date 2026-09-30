package org.mwolff.fbcrm.rechnung.application;

import java.math.BigDecimal;
import java.util.List;
import org.mwolff.fbcrm.rechnung.domain.Rechnung;

/**
 * Die Rechnung mit den Zeilen ihrer Maske (Plan #169, E5).
 *
 * <p>Drei Sichten auf dasselbe: Die Rechnung traegt, was sie abrechnet — Datum, Leistungszeitraum
 * und ihre Positionen mit Text, Menge und dem festgehaltenen Einzelpreis. Die Zeilen tragen den
 * Stand am Angebot, den die Maske daneben zeigt: angeboten, abgerechnet, offen. Dazu die Firma, an
 * die die Rechnung geht, und der Steuersatz, mit dem sie rechnet.
 *
 * <p>Die Firma steht nicht an der Rechnung, sondern an ihrem Angebot; der {@code steuersatz} kommt
 * aus zwei Quellen ({@link GeltenderSteuersatz}). Beides gehoert in die Ansicht und nicht in die
 * Schnittstelle: Netto, Steuer und Brutto rechnet die Rechnung daraus, und die Maske soll sie ohne
 * zweiten Aufruf zeigen koennen.
 *
 * @param rechnung die Rechnung selbst
 * @param firmaId Kennung der Firma, an die die Rechnung geht
 * @param firmaName Name dieser Firma
 * @param steuersatz der Satz in Prozent, mit dem diese Rechnung rechnet
 * @param zeilen je Position des Angebots eine Zeile, in der Reihenfolge des Angebots
 */
public record Rechnungsansicht(
    Rechnung rechnung,
    long firmaId,
    String firmaName,
    BigDecimal steuersatz,
    List<Abrechnungszeile> zeilen) {

  /** Nimmt die Zeilen als Kopie: Der Aufrufer darf seine Liste danach weiterverwenden. */
  public Rechnungsansicht {
    zeilen = List.copyOf(zeilen);
  }
}
