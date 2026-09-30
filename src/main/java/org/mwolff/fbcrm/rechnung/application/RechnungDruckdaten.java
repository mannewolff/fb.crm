package org.mwolff.fbcrm.rechnung.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.common.Einheit;
import org.mwolff.fbcrm.rechnung.domain.Belegabsender;
import org.mwolff.fbcrm.rechnung.domain.Belegempfaenger;

/**
 * Alles, was auf dem Rechnungsdokument steht (E10, Vorlage {@code docs/vorlage-rechnung.pdf}).
 *
 * <p>Der Record kennt weder eine {@code Rechnung} noch eine Uhr: Was ein Beleg zeigt, ist beim
 * Stellen entschieden worden, und wer den Satz rechnet, soll dafuer nichts nachschlagen und nichts
 * ableiten muessen. Deshalb stehen hier auch Netto, Steuer und Brutto als fertige Zahlen — sie
 * kommen aus {@code Rechnung}, das sie nach der einen Regel in {@code Geldrechnung} rechnet, und
 * eine zweite Rechnung an dieser Stelle koennte davon abweichen.
 *
 * @param absender eigene Angaben, wie sie beim Stellen galten
 * @param empfaenger Firma und Anschrift, wie sie beim Stellen galten
 * @param nummer die Rechnungsnummer, wie sie im Titel steht
 * @param rechnungDatum Datum der Rechnung
 * @param leistungszeitraum Zeitraum der Leistung als Text, oder {@code null}
 * @param positionen die Positionen in ihrer Reihenfolge
 * @param netto Netto-Summe
 * @param steuersatz Steuersatz in Prozent
 * @param steuer Steuer auf die Netto-Summe
 * @param brutto Brutto-Summe
 * @param zahlungszielTage Zahlungsziel in Tagen; 0 bedeutet sofort faellig
 */
public record RechnungDruckdaten(
    Belegabsender absender,
    Belegempfaenger empfaenger,
    String nummer,
    LocalDate rechnungDatum,
    @Nullable String leistungszeitraum,
    List<Druckposition> positionen,
    BigDecimal netto,
    BigDecimal steuersatz,
    BigDecimal steuer,
    BigDecimal brutto,
    int zahlungszielTage) {

  /** Nimmt die Positionen als Kopie: Der Aufrufer darf seine Liste danach weiterverwenden. */
  public RechnungDruckdaten {
    positionen = List.copyOf(positionen);
  }

  /**
   * Eine Zeile der Tabelle, wie sie auf dem Dokument steht.
   *
   * <p>Der Gesamtpreis ist mitgegeben und nicht gerechnet, aus demselben Grund wie die Summen des
   * Belegs: Auf dem Dokument steht die Zahl, die die Rechnung gerechnet hat.
   *
   * @param anzahl abgerechnete Menge
   * @param einheit Einheit der Menge; sie steht vor dem Text, ausser bei einer Pauschale
   * @param text die Leistung, wie sie auf der Rechnung steht
   * @param einzelpreis Netto-Preis je Einheit
   * @param gesamtpreis Netto-Betrag der Position
   */
  public record Druckposition(
      BigDecimal anzahl,
      Einheit einheit,
      String text,
      BigDecimal einzelpreis,
      BigDecimal gesamtpreis) {}
}
