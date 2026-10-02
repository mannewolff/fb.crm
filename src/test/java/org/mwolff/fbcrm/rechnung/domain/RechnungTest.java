package org.mwolff.fbcrm.rechnung.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.common.Anschrift;
import org.mwolff.fbcrm.common.Einheit;
import org.mwolff.fbcrm.rechnung.application.RechnungszustandPasstNicht;

/**
 * Die Rechnung als Fachobjekt: ihre Betraege und ihre beiden Zustaende.
 *
 * <p>Nichts Gerechnetes wird gespeichert (E5 am Angebot, E3 des Plans #169). {@link
 * Rechnung#netto()} ist die Summe der gerundeten Positionsbetraege, die Steuer entsteht aus dieser
 * Summe und nicht je Position, und das Brutto ist beides zusammen.
 *
 * <p>Unveraenderlich: Jeder Uebergang liefert eine neue Rechnung, und der Zeitpunkt kommt von
 * aussen (CLAUDE-java.md §6.2). Aendern geht nur im Entwurf, gestellt wird nur ein Entwurf, und
 * einen Dokumentschluessel traegt nur eine gestellte Rechnung — dieselbe Grenze, die der CHECK der
 * Migration in der Datenbank zieht.
 */
class RechnungTest {

  private static final LocalDate RECHNUNGSDATUM = LocalDate.of(2026, 9, 30);
  private static final Instant ANGELEGT = Instant.parse("2026-09-30T08:00:00Z");
  private static final Instant GESTELLT_AM = Instant.parse("2026-10-01T09:15:00Z");
  private static final String ZEITRAUM = "September 2026";
  private static final BigDecimal NEUNZEHN = new BigDecimal("19.00");

  private static final Rechnungsposition BERATUNG =
      new Rechnungsposition(
          7L, "Beratung", new BigDecimal("3.00"), Einheit.STUNDE, new BigDecimal("120.00"));

  private static final Rechnungsposition HALBER_CENT =
      new Rechnungsposition(
          8L, "Konzeption", new BigDecimal("2.50"), Einheit.PERSONENTAG, new BigDecimal("99.99"));

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

  private static Rechnung entwurf(final List<Rechnungsposition> positionen) {
    return new Rechnung(
        11L,
        3L,
        Rechnungszustand.ENTWURF,
        RECHNUNGSDATUM,
        ZEITRAUM,
        positionen,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        ANGELEGT,
        ANGELEGT);
  }

  private static Rechnung gestellt() {
    return entwurf(List.of(BERATUNG))
        .gestellt("R26-0004", NEUNZEHN, 10, EMPFAENGER, ABSENDER, GESTELLT_AM);
  }

  @Test
  void netto_thenTheSumOfTheRoundedPositionAmounts() {
    // Given — 3 Stunden zu 120,00.
    final Rechnung rechnung = entwurf(List.of(BERATUNG));

    // When / Then
    assertThat(rechnung.netto()).isEqualByComparingTo("360.00").hasScaleOf(2);
  }

  @Test
  void netto_givenNoPositions_thenZero() {
    // Given
    final Rechnung rechnung = entwurf(List.of());

    // When / Then
    assertThat(rechnung.netto()).isEqualByComparingTo("0.00").hasScaleOf(2);
  }

  @Test
  void steuer_given19Percent_thenTheTaxOnTheNetSum() {
    // Given
    final Rechnung rechnung = entwurf(List.of(BERATUNG));

    // When / Then — 360,00 zu 19 Prozent.
    assertThat(rechnung.steuer(NEUNZEHN)).isEqualByComparingTo("68.40").hasScaleOf(2);
  }

  @Test
  void brutto_given19Percent_thenNetPlusTax() {
    // Given
    final Rechnung rechnung = entwurf(List.of(BERATUNG));

    // When / Then
    assertThat(rechnung.brutto(NEUNZEHN)).isEqualByComparingTo("428.40").hasScaleOf(2);
  }

  @Test
  void steuer_givenTwoPositions_thenComputedFromTheNetSumAndNotPerPosition() {
    // Given — zweimal 2,5 mal 99,99: je Position 249,98, zusammen 499,96.
    final Rechnung rechnung = entwurf(List.of(HALBER_CENT, HALBER_CENT));

    // When / Then — 499,96 zu 19 Prozent sind 94,9924 und damit 94,99; je Position gerechnet
    // waeren es zweimal 47,50 und damit 95,00.
    assertThat(rechnung.steuer(NEUNZEHN)).isEqualByComparingTo("94.99");
  }

  @Test
  void netto_givenTwoHalfCentPositions_thenEachAmountIsRoundedBeforeAdding() {
    // Given
    final Rechnung rechnung = entwurf(List.of(HALBER_CENT, HALBER_CENT));

    // When / Then
    assertThat(rechnung.netto()).isEqualByComparingTo("499.96");
  }

  @Test
  void geaendert_givenADraft_thenCarriesTheNewDateTimeframeAndPositions() {
    // Given
    final Rechnung rechnung = entwurf(List.of(BERATUNG));
    final LocalDate neuesDatum = LocalDate.of(2026, 10, 2);
    final Instant zeitpunkt = Instant.parse("2026-10-02T11:00:00Z");

    // When
    final Rechnung geaendert =
        rechnung.geaendert(neuesDatum, "Oktober 2026", List.of(HALBER_CENT), zeitpunkt);

    // Then
    assertThat(geaendert)
        .satisfies(
            neu -> assertThat(neu.rechnungDatum()).isEqualTo(neuesDatum),
            neu -> assertThat(neu.leistungszeitraum()).isEqualTo("Oktober 2026"),
            neu -> assertThat(neu.positionen()).containsExactly(HALBER_CENT),
            neu -> assertThat(neu.updatedAt()).isEqualTo(zeitpunkt),
            neu -> assertThat(neu.createdAt()).isEqualTo(ANGELEGT),
            neu -> assertThat(neu.zustand()).isEqualTo(Rechnungszustand.ENTWURF));
  }

  @Test
  void geaendert_givenAnIssuedRechnung_thenRejected() {
    // Given
    final Rechnung rechnung = gestellt();

    final List<Rechnungsposition> positionen = List.of(HALBER_CENT);

    // When / Then
    assertThatThrownBy(() -> rechnung.geaendert(RECHNUNGSDATUM, ZEITRAUM, positionen, GESTELLT_AM))
        .isInstanceOf(RechnungszustandPasstNicht.class);
  }

  @Test
  void gestellt_thenLeavesPositionsAndDateUntouched() {
    // Given
    final Rechnung rechnung = entwurf(List.of(BERATUNG, HALBER_CENT));

    // When
    final Rechnung neu =
        rechnung.gestellt("R26-0004", NEUNZEHN, 10, EMPFAENGER, ABSENDER, GESTELLT_AM);

    // Then
    assertThat(neu)
        .satisfies(
            gestellt -> assertThat(gestellt.positionen()).containsExactly(BERATUNG, HALBER_CENT),
            gestellt -> assertThat(gestellt.rechnungDatum()).isEqualTo(RECHNUNGSDATUM),
            gestellt -> assertThat(gestellt.leistungszeitraum()).isEqualTo(ZEITRAUM),
            gestellt -> assertThat(gestellt.id()).isEqualTo(11L),
            gestellt -> assertThat(gestellt.angebotId()).isEqualTo(3L),
            gestellt -> assertThat(gestellt.createdAt()).isEqualTo(ANGELEGT));
  }

  @Test
  void gestellt_thenCarriesNumberTaxRatePaymentTermCopiesAndMoment() {
    // When
    final Rechnung neu = gestellt();

    // Then
    assertThat(neu)
        .satisfies(
            r -> assertThat(r.zustand()).isEqualTo(Rechnungszustand.GESTELLT),
            r -> assertThat(r.nummer()).isEqualTo("R26-0004"),
            r -> assertThat(r.steuersatz()).isEqualByComparingTo(NEUNZEHN),
            r -> assertThat(r.zahlungszielTage()).isEqualTo(10),
            r -> assertThat(r.gestelltAm()).isEqualTo(GESTELLT_AM),
            r -> assertThat(r.updatedAt()).isEqualTo(GESTELLT_AM),
            r -> assertThat(r.empfaenger()).isEqualTo(EMPFAENGER),
            r -> assertThat(r.absender()).isEqualTo(ABSENDER),
            r -> assertThat(r.pdfSchluessel()).isNull());
  }

  @Test
  void gestellt_givenAnAlreadyIssuedRechnung_thenRejected() {
    // Given — eine zweite Nummer auf derselben Rechnung waere ein Loch im Nummernkreis.
    final Rechnung rechnung = gestellt();

    // When / Then
    assertThatThrownBy(
            () -> rechnung.gestellt("R26-0005", NEUNZEHN, 10, EMPFAENGER, ABSENDER, GESTELLT_AM))
        .isInstanceOf(RechnungszustandPasstNicht.class);
  }

  @Test
  void mitDokument_givenAnIssuedRechnung_thenCarriesTheKeyAndKeepsTheRest() {
    // Given
    final Rechnung rechnung = gestellt();

    // When
    final Rechnung mitPdf = rechnung.mitDokument("rechnung/11/abc.pdf");

    // Then
    assertThat(mitPdf)
        .satisfies(
            r -> assertThat(r.pdfSchluessel()).isEqualTo("rechnung/11/abc.pdf"),
            r -> assertThat(r.nummer()).isEqualTo("R26-0004"),
            r -> assertThat(r.zustand()).isEqualTo(Rechnungszustand.GESTELLT),
            r -> assertThat(r.gestelltAm()).isEqualTo(GESTELLT_AM),
            r -> assertThat(r.steuersatz()).isEqualByComparingTo(NEUNZEHN),
            r -> assertThat(r.zahlungszielTage()).isEqualTo(10),
            r -> assertThat(r.empfaenger()).isEqualTo(EMPFAENGER),
            r -> assertThat(r.absender()).isEqualTo(ABSENDER),
            r -> assertThat(r.positionen()).containsExactly(BERATUNG),
            r -> assertThat(r.leistungszeitraum()).isEqualTo(ZEITRAUM),
            r -> assertThat(r.createdAt()).isEqualTo(ANGELEGT),
            r -> assertThat(r.updatedAt()).isEqualTo(GESTELLT_AM));
  }

  @Test
  void mitDokument_givenADraft_thenRejected() {
    // Given — ein Entwurf traegt kein Dokument; das haelt auch der CHECK der Migration.
    final Rechnung rechnung = entwurf(List.of(BERATUNG));

    // When / Then
    assertThatThrownBy(() -> rechnung.mitDokument("rechnung/11/abc.pdf"))
        .isInstanceOf(RechnungszustandPasstNicht.class);
  }

  @Test
  void positionen_whenTheCallerChangesItsListAfterwards_thenTheRechnungIsUnaffected() {
    // Given
    final List<Rechnungsposition> eingereicht = new java.util.ArrayList<>(List.of(BERATUNG));
    final Rechnung rechnung = entwurf(eingereicht);

    // When
    eingereicht.add(HALBER_CENT);

    // Then
    assertThat(rechnung.positionen()).containsExactly(BERATUNG);
  }
}
