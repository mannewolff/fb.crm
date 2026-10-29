package org.mwolff.fbcrm.rechnung.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.common.Anschrift;
import org.mwolff.fbcrm.common.Einheit;
import org.mwolff.fbcrm.common.Iban;

/**
 * Alles, was in der E-Rechnung einer gestellten Rechnung steht (Plan #461, E1, E6 bis E9, E19).
 *
 * <p>Das Gegenstueck zu {@link RechnungDruckdaten} fuer die maschinenlesbare Ausgabe: ein Record je
 * Ausgabe, beide aus derselben festgeschriebenen Rechnung. Der Record kennt weder eine {@code
 * Rechnung} noch eine Uhr, und Netto, Steuer und Brutto stehen als fertige Zahlen darin — in der
 * XML steht die Zahl, die die Rechnung gerechnet hat, wie auf dem Dokument.
 *
 * <p>Was die E-Rechnung verlangt, steht hier ohne {@code null}: IBAN, Kontoinhaber und das
 * Laenderkuerzel des Kaeufers. Aus „kann fehlen" wird „steht fest" beim Bilden in {@code
 * Rechnungsbeleg}; der Schreiber muss nichts mehr pruefen.
 *
 * @param nummer die Rechnungsnummer (BT-1)
 * @param rechnungDatum Datum der Rechnung (BT-2)
 * @param leistungszeitraum Zeitraum der Leistung als Text, oder {@code null}; er steht als Hinweis
 *     (BT-22, E7)
 * @param verkaeufer die eigenen Angaben, wie sie beim Stellen galten
 * @param kaeufer der Kunde, wie er beim Stellen galt
 * @param positionen die Positionen in ihrer Reihenfolge
 * @param netto Netto-Summe
 * @param steuersatz Steuersatz in Prozent
 * @param steuer Steuer auf die Netto-Summe
 * @param brutto Brutto-Summe
 * @param faelligkeit Rechnungsdatum plus Zahlungsziel (BT-9, E8)
 * @param zahlungsbedingung der Satz ueber das Zahlungsziel, wie er gedruckt wird (BT-20, E8)
 */
public record ERechnungsdaten(
    String nummer,
    LocalDate rechnungDatum,
    @Nullable String leistungszeitraum,
    Verkaeufer verkaeufer,
    Kaeufer kaeufer,
    List<Position> positionen,
    BigDecimal netto,
    BigDecimal steuersatz,
    BigDecimal steuer,
    BigDecimal brutto,
    LocalDate faelligkeit,
    String zahlungsbedingung) {

  /** Nimmt die Positionen als Kopie: Der Aufrufer darf seine Liste danach weiterverwenden. */
  public ERechnungsdaten {
    positionen = List.copyOf(positionen);
  }

  /**
   * Der Verkaeufer und zugleich der Zahlungsempfaenger; sein Land ist fest Deutschland (E9).
   *
   * @param name Name, unter dem die Rechnung hinausgeht (BT-27)
   * @param anschrift eigene Postanschrift; Strasse, Postleitzahl und Ort duerfen fehlen
   * @param steuernummer Steuernummer (BT-32), oder {@code null}
   * @param umsatzsteuerId Umsatzsteuer-Identifikationsnummer (BT-31), oder {@code null}
   * @param iban das Konto fuer die Ueberweisung (BT-84)
   * @param bic BIC (BT-86), oder {@code null}
   * @param kontoinhaber Inhaber des Kontos (BT-85)
   */
  public record Verkaeufer(
      String name,
      Anschrift anschrift,
      @Nullable String steuernummer,
      @Nullable String umsatzsteuerId,
      Iban iban,
      @Nullable String bic,
      String kontoinhaber) {}

  /**
   * Der Kaeufer.
   *
   * @param name Name der Firma (BT-44)
   * @param anschrift Postanschrift; Strasse, Postleitzahl und Ort duerfen fehlen, das gedruckte
   *     Land zaehlt hier nicht
   * @param landKuerzel Kuerzel des Landes nach ISO 3166-1 alpha-2 (BT-55)
   */
  public record Kaeufer(String name, Anschrift anschrift, String landKuerzel) {}

  /**
   * Eine Position der Rechnung.
   *
   * <p>Der Betrag ist mitgegeben und nicht gerechnet, aus demselben Grund wie die Summen.
   *
   * @param bezeichnung die Leistung (BT-153)
   * @param menge abgerechnete Menge (BT-129)
   * @param einheit Einheit der Menge; sie wird zum Code nach UN/ECE Rec. 20 (BT-130, E6)
   * @param einzelpreis Netto-Preis je Einheit (BT-146)
   * @param betrag Netto-Betrag der Position (BT-131)
   */
  public record Position(
      String bezeichnung,
      BigDecimal menge,
      Einheit einheit,
      BigDecimal einzelpreis,
      BigDecimal betrag) {}
}
