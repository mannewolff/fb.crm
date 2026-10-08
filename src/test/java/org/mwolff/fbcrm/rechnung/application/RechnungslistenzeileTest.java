package org.mwolff.fbcrm.rechnung.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.rechnung.domain.Rechnungszustand;

/**
 * Eine Zeile der gemischten Rechnungsliste (Plan #259, E18; #254, Kriterien 6 und 7).
 *
 * <p>Gegenstand sind die beiden Wege in die Zeile: aus einer von fb.crm geschriebenen Rechnung und
 * aus einer nachgetragenen. Beide tragen die Art und ob ein Dokument hinterlegt ist — fuer beide
 * Arten genau dann, wenn ein {@code pdfSchluessel} da ist.
 */
class RechnungslistenzeileTest {

  private static final LocalDate DATUM = LocalDate.of(2026, 9, 30);

  @Test
  void vonRechnung_thenCarriesItsAngabenAndIsNotNachgetragen() {
    // Given — eine gestellte Rechnung ohne archiviertes Dokument.
    final var rechnung =
        Rechnungsdoppel.gestellt(
            4L, Rechnungsdoppel.ANGEBOT, "R26-0004", List.of(Rechnungsdoppel.beratung("1")), DATUM);

    // When
    final Rechnungslistenzeile zeile =
        Rechnungslistenzeile.vonRechnung(
            rechnung, Rechnungsdoppel.FIRMA, "Adler AG", new BigDecimal("107.00"));

    // Then
    assertThat(zeile)
        .isEqualTo(
            new Rechnungslistenzeile(
                false,
                4L,
                "R26-0004",
                Rechnungsdoppel.FIRMA,
                "Adler AG",
                DATUM,
                new BigDecimal("107.00"),
                Rechnungszustand.GESTELLT,
                false));
  }

  @Test
  void vonRechnung_withAnArchiviertesDokument_thenHatDokument() {
    // Given
    final var rechnung =
        Rechnungsdoppel.gestellt(4L, "R26-0004", List.of(Rechnungsdoppel.beratung("1")))
            .mitDokument("rechnung/4.pdf");

    // When / Then
    assertThat(
            Rechnungslistenzeile.vonRechnung(
                    rechnung, Rechnungsdoppel.FIRMA, "Adler AG", BigDecimal.ONE)
                .hatDokument())
        .isTrue();
  }

  @Test
  void vonRechnung_forAnEntwurf_thenTheNummerIsMissing() {
    // Given
    final var entwurf = Rechnungsdoppel.entwurf(2L, List.of(Rechnungsdoppel.beratung("1")));

    // When
    final Rechnungslistenzeile zeile =
        Rechnungslistenzeile.vonRechnung(
            entwurf, Rechnungsdoppel.FIRMA, "Adler AG", BigDecimal.ONE);

    // Then
    assertThat(zeile.nummer()).isNull();
    assertThat(zeile.zustand()).isEqualTo(Rechnungszustand.ENTWURF);
  }

  @Test
  void vonNachtrag_thenCarriesTheBruttoAsErfasstAndIsNachgetragen() {
    // Given — eine nachgetragene Rechnung ohne Original.
    final var nachtrag = Rechnungsdoppel.nachgetragen(4L, "AR-17", DATUM, "100.00", "119.00", null);

    // When
    final Rechnungslistenzeile zeile = Rechnungslistenzeile.vonNachtrag(nachtrag, "Adler AG");

    // Then
    assertThat(zeile)
        .isEqualTo(
            new Rechnungslistenzeile(
                true,
                4L,
                "AR-17",
                Rechnungsdoppel.FIRMA,
                "Adler AG",
                DATUM,
                new BigDecimal("119.00"),
                Rechnungszustand.GESTELLT,
                false));
  }

  @Test
  void vonNachtrag_withAnOriginal_thenHatDokument() {
    // Given
    final var nachtrag =
        Rechnungsdoppel.nachgetragen(4L, "AR-17", DATUM, "100.00", "119.00", "nachtrag/4.pdf");

    // When / Then
    assertThat(Rechnungslistenzeile.vonNachtrag(nachtrag, "Adler AG").hatDokument()).isTrue();
  }
}
