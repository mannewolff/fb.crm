package org.mwolff.fbcrm.rechnung.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.rechnung.domain.NachgetrageneRechnung;
import org.mwolff.fbcrm.rechnung.domain.Rechnungszustand;

/**
 * Jedes Feld der nachgetragenen Rechnung kommt ueber {@link NachgetrageneRechnungEntity#aus} an
 * seiner Spalte an (Plan #259).
 *
 * <p>Gegenstand ist die Abbildung selbst: Eine vertauschte oder vergessene Zuweisung waere nur hier
 * zu sehen. Einmal mit Dokument, damit kein Feld leer bleibt, und einmal ohne — das Original ist
 * optional (#254, Kriterium 2).
 */
class NachgetrageneRechnungEntityTest {

  private static final LocalDate RECHNUNGSDATUM = LocalDate.of(2026, 2, 15);
  private static final Instant ANGELEGT = Instant.parse("2026-10-06T08:00:00Z");
  private static final Instant GEAENDERT = Instant.parse("2026-10-07T09:15:00Z");

  private static NachgetrageneRechnung rechnung(final String pdfSchluessel) {
    return new NachgetrageneRechnung(
        21L,
        4L,
        "RE-9",
        RECHNUNGSDATUM,
        new BigDecimal("1000.00"),
        new BigDecimal("1190.00"),
        Rechnungszustand.BEZAHLT,
        pdfSchluessel,
        ANGELEGT,
        GEAENDERT);
  }

  @Test
  void aus_thenCarriesEveryFieldOfTheRechnung() {
    // When
    final NachgetrageneRechnungEntity zeile =
        NachgetrageneRechnungEntity.aus(rechnung("nachgetragen/21/original.pdf"));

    // Then
    assertThat(zeile)
        .satisfies(
            gelesen -> assertThat(gelesen.getId()).isEqualTo(21L),
            gelesen -> assertThat(gelesen.getFirmaId()).isEqualTo(4L),
            gelesen -> assertThat(gelesen.getNummer()).isEqualTo("RE-9"),
            gelesen -> assertThat(gelesen.getRechnungDatum()).isEqualTo(RECHNUNGSDATUM),
            gelesen -> assertThat(gelesen.getNetto()).isEqualByComparingTo("1000.00"),
            gelesen -> assertThat(gelesen.getBrutto()).isEqualByComparingTo("1190.00"),
            gelesen -> assertThat(gelesen.getZustand()).isEqualTo(Rechnungszustand.BEZAHLT),
            gelesen ->
                assertThat(gelesen.getPdfSchluessel()).isEqualTo("nachgetragen/21/original.pdf"),
            gelesen -> assertThat(gelesen.getCreatedAt()).isEqualTo(ANGELEGT),
            gelesen -> assertThat(gelesen.getUpdatedAt()).isEqualTo(GEAENDERT));
  }

  @Test
  void aus_givenNoDocument_thenTheKeyStaysEmpty() {
    // When / Then
    assertThat(NachgetrageneRechnungEntity.aus(rechnung(null)).getPdfSchluessel()).isNull();
  }
}
