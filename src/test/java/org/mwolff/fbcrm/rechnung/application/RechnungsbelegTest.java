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
 */
class RechnungsbelegTest {

  private static final Instant GESTELLT_AM = Instant.parse("2026-09-30T10:00:00Z");
  private static final LocalDate RECHNUNGSDATUM = LocalDate.of(2026, 9, 30);
  private static final BigDecimal SIEBEN = new BigDecimal("7.00");

  private static final Belegempfaenger EMPFAENGER =
      new Belegempfaenger(
          "Adler AG", new Anschrift("Hauptstrasse 1", "28195", "Bremen", "Deutschland"), null);

  private static final Belegabsender ABSENDER =
      new Belegabsender(
          "Manfred Wolff",
          "Softwarearchitekt",
          new Anschrift("Am Deich 2", "28199", "Bremen", "Deutschland"),
          "post@example.org",
          "0421 123456",
          "75/123/45678",
          "DE123456789",
          "DE02 1203 0000 0000 2020 51",
          "https://example.org");

  private static Rechnung gestellt() {
    return Rechnungsdoppel.entwurf(
            2L, List.of(Rechnungsdoppel.beratung("80.00"), Rechnungsdoppel.pauschale("1")))
        .gestellt("0001-2026", SIEBEN, 14, EMPFAENGER, ABSENDER, GESTELLT_AM);
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
}
