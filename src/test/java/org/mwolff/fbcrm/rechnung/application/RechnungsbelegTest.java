package org.mwolff.fbcrm.rechnung.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.common.Anschrift;
import org.mwolff.fbcrm.common.Einheit;
import org.mwolff.fbcrm.common.Iban;
import org.mwolff.fbcrm.rechnung.application.ERechnungsdaten.Kaeufer;
import org.mwolff.fbcrm.rechnung.application.ERechnungsdaten.Position;
import org.mwolff.fbcrm.rechnung.application.ERechnungsdaten.Verkaeufer;
import org.mwolff.fbcrm.rechnung.application.RechnungDruckdaten.Druckposition;
import org.mwolff.fbcrm.rechnung.domain.Belegabsender;
import org.mwolff.fbcrm.rechnung.domain.Belegempfaenger;
import org.mwolff.fbcrm.rechnung.domain.Rechnung;

/**
 * Die Uebersetzung einer gestellten Rechnung in das, was auf ihrem Beleg steht (Plan #169, E7,
 * E10).
 *
 * <p>Gegenstand ist, dass jede Angabe des Dokuments aus der <b>festgeschriebenen</b> Rechnung kommt
 * und nicht aus den Einstellungen von jetzt: der Steuersatz, das Zahlungsziel und die beiden Kopien
 * von Empfaenger und Absender (#160, Kriterium 14). Netto, Steuer und Brutto rechnet die Rechnung;
 * sie gehen als fertige Zahlen hinein, damit auf dem Beleg die Zahl steht, mit der gerechnet wurde.
 *
 * <p>Dasselbe gilt fuer die Daten der E-Rechnung (Plan #461, E19): Sie entstehen aus derselben
 * festgeschriebenen Rechnung, mit der Faelligkeit als Datum und dem gedruckten Zahlungssatz (E8).
 */
class RechnungsbelegTest {

  private static final Instant GESTELLT_AM = Instant.parse("2026-09-30T10:00:00Z");
  private static final LocalDate RECHNUNGSDATUM = LocalDate.of(2026, 9, 30);
  private static final BigDecimal SIEBEN = new BigDecimal("7.00");

  private static final Belegempfaenger EMPFAENGER =
      new Belegempfaenger(
          "Adler AG", new Anschrift("Hauptstrasse 1", "28195", "Bremen", "DE"), null, null);

  private static final Belegabsender ABSENDER =
      new Belegabsender(
          "Manfred Wolff",
          "Softwarearchitekt",
          new Anschrift("Am Deich 2", "28199", "Bremen", "DE"),
          "post@example.org",
          "0421 123456",
          "75/123/45678",
          "DE123456789",
          "DE02 1203 0000 0000 2020 51",
          "https://example.org",
          null,
          null,
          null);

  /** Der Kunde einer Rechnung, die als E-Rechnung gestellt wird: mit Laenderkuerzel. */
  private static final Belegempfaenger EMPFAENGER_MIT_LAND =
      new Belegempfaenger(
          "Adler AG",
          new Anschrift("Hauptstrasse 1", "28195", "Bremen", "Deutschland"),
          "Frau Adler",
          "DE");

  /** Die eigenen Angaben mit den Bankfeldern, die eine E-Rechnung braucht. */
  private static final Belegabsender ABSENDER_MIT_BANK =
      new Belegabsender(
          "Manfred Wolff",
          "Softwarearchitekt",
          new Anschrift("Am Deich 2", "28199", "Bremen", "DE"),
          "post@example.org",
          "0421 123456",
          "75/123/45678",
          "DE123456789",
          null,
          "https://example.org",
          "DE89370400440532013000",
          "COBADEFFXXX",
          "Manfred Wolff Beratung");

  private static Rechnung gestellt() {
    return gestellt(14, EMPFAENGER, ABSENDER);
  }

  private static Rechnung gestellt(
      final int zahlungszielTage, final Belegempfaenger empfaenger, final Belegabsender absender) {
    return Rechnungsdoppel.entwurf(
            2L, List.of(Rechnungsdoppel.beratung("80.00"), Rechnungsdoppel.pauschale("1")))
        .gestellt("0001-2026", SIEBEN, zahlungszielTage, empfaenger, absender, GESTELLT_AM);
  }

  private static ERechnungsdaten erechnungsdaten(final int zahlungszielTage) {
    return Rechnungsbeleg.erechnungsdaten(
        gestellt(zahlungszielTage, EMPFAENGER_MIT_LAND, ABSENDER_MIT_BANK));
  }

  @Test
  void druckdaten_thenEveryAngabeComesFromTheFrozenRechnung() {
    // When
    final RechnungDruckdaten daten = Rechnungsbeleg.druckdaten(gestellt());

    // Then
    assertThat(daten)
        .satisfies(
            beleg -> assertThat(beleg.nummer()).isEqualTo("0001-2026"),
            beleg -> assertThat(beleg.rechnungDatum()).isEqualTo(RECHNUNGSDATUM),
            beleg -> assertThat(beleg.leistungszeitraum()).isEqualTo(Rechnungsdoppel.ZEITRAUM),
            beleg -> assertThat(beleg.empfaenger()).isEqualTo(EMPFAENGER),
            beleg -> assertThat(beleg.absender()).isEqualTo(ABSENDER),
            beleg -> assertThat(beleg.zahlungszielTage()).isEqualTo(14));
  }

  @Test
  void druckdaten_thenTheSummenAreTheOnesTheRechnungCalculated() {
    // When — 80 Stunden zu 100,00 € und eine Pauschale zu 1.200,00 €, 7 Prozent.
    final RechnungDruckdaten daten = Rechnungsbeleg.druckdaten(gestellt());

    // Then
    assertThat(daten)
        .satisfies(
            beleg -> assertThat(beleg.netto()).isEqualByComparingTo("9200.00"),
            beleg -> assertThat(beleg.steuersatz()).isEqualByComparingTo(SIEBEN),
            beleg -> assertThat(beleg.steuer()).isEqualByComparingTo("644.00"),
            beleg -> assertThat(beleg.brutto()).isEqualByComparingTo("9844.00"));
  }

  @Test
  void druckdaten_thenEveryPositionBecomesAZeileInItsOrder() {
    // When
    final RechnungDruckdaten daten = Rechnungsbeleg.druckdaten(gestellt());

    // Then — der Gesamtpreis ist der gerundete Betrag der Position und nichts Nachgerechnetes.
    assertThat(daten.positionen())
        .extracting(
            Druckposition::anzahl,
            Druckposition::einheit,
            Druckposition::text,
            Druckposition::einzelpreis,
            Druckposition::gesamtpreis)
        .containsExactly(
            tuple(
                new BigDecimal("80.00"),
                Einheit.STUNDE,
                "Beratung",
                new BigDecimal("100.00"),
                new BigDecimal("8000.00")),
            tuple(
                new BigDecimal("1"),
                Einheit.PAUSCHAL,
                "Schulungstag",
                new BigDecimal("1200.00"),
                new BigDecimal("1200.00")));
  }

  @Test
  void erechnungsdaten_thenEveryAngabeComesFromTheFrozenRechnung() {
    // When
    final ERechnungsdaten daten = erechnungsdaten(14);

    // Then
    assertThat(daten)
        .satisfies(
            beleg -> assertThat(beleg.nummer()).isEqualTo("0001-2026"),
            beleg -> assertThat(beleg.rechnungDatum()).isEqualTo(RECHNUNGSDATUM),
            beleg -> assertThat(beleg.leistungszeitraum()).isEqualTo(Rechnungsdoppel.ZEITRAUM));
  }

  @Test
  void erechnungsdaten_thenTheVerkaeuferIsTheCopyOfTheEigenenAngaben() {
    // When
    final Verkaeufer verkaeufer = erechnungsdaten(14).verkaeufer();

    // Then — die IBAN in der elektronischen Form, wie sie in der Kopie steht.
    assertThat(verkaeufer)
        .isEqualTo(
            new Verkaeufer(
                "Manfred Wolff",
                new Anschrift("Am Deich 2", "28199", "Bremen", "DE"),
                "75/123/45678",
                "DE123456789",
                new Iban("DE89370400440532013000"),
                "COBADEFFXXX",
                "Manfred Wolff Beratung"));
  }

  @Test
  void erechnungsdaten_thenTheKaeuferCarriesTheLandKuerzelOfTheCopy() {
    // When
    final Kaeufer kaeufer = erechnungsdaten(14).kaeufer();

    // Then
    assertThat(kaeufer)
        .isEqualTo(
            new Kaeufer(
                "Adler AG",
                new Anschrift("Hauptstrasse 1", "28195", "Bremen", "Deutschland"),
                "DE"));
  }

  @Test
  void erechnungsdaten_thenTheFaelligkeitIsTheRechnungsdatumPlusTheZahlungsziel() {
    // When
    final ERechnungsdaten daten = erechnungsdaten(14);

    // Then — die Zahlungsbedingung ist der Satz, der gedruckt wird (E8).
    assertThat(daten)
        .satisfies(
            beleg -> assertThat(beleg.faelligkeit()).isEqualTo(LocalDate.of(2026, 10, 14)),
            beleg ->
                assertThat(beleg.zahlungsbedingung())
                    .isEqualTo("Bitte überweisen Sie den Betrag innerhalb von 14 Tagen"));
  }

  @Test
  void erechnungsdaten_whenSofortFaellig_thenTheFaelligkeitIsTheRechnungsdatum() {
    // When
    final ERechnungsdaten daten = erechnungsdaten(0);

    // Then
    assertThat(daten)
        .satisfies(
            beleg -> assertThat(beleg.faelligkeit()).isEqualTo(RECHNUNGSDATUM),
            beleg ->
                assertThat(beleg.zahlungsbedingung())
                    .isEqualTo("Bitte überweisen Sie den Betrag sofort"));
  }

  @Test
  void erechnungsdaten_thenTheSummenAreTheOnesTheRechnungCalculated() {
    // When — 80 Stunden zu 100,00 € und eine Pauschale zu 1.200,00 €, 7 Prozent.
    final ERechnungsdaten daten = erechnungsdaten(14);

    // Then
    assertThat(daten)
        .satisfies(
            beleg -> assertThat(beleg.netto()).isEqualByComparingTo("9200.00"),
            beleg -> assertThat(beleg.steuersatz()).isEqualByComparingTo(SIEBEN),
            beleg -> assertThat(beleg.steuer()).isEqualByComparingTo("644.00"),
            beleg -> assertThat(beleg.brutto()).isEqualByComparingTo("9844.00"));
  }

  @Test
  void erechnungsdaten_thenEveryPositionIsCarriedOverInItsOrder() {
    // When
    final ERechnungsdaten daten = erechnungsdaten(14);

    // Then
    assertThat(daten.positionen())
        .containsExactly(
            new Position(
                "Beratung",
                new BigDecimal("80.00"),
                Einheit.STUNDE,
                new BigDecimal("100.00"),
                new BigDecimal("8000.00")),
            new Position(
                "Schulungstag",
                new BigDecimal("1"),
                Einheit.PAUSCHAL,
                new BigDecimal("1200.00"),
                new BigDecimal("1200.00")));
  }
}
