package org.mwolff.fbcrm.angebot.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Saeuberung des Dateinamens, der mit einer Anlage aus der Anfrage kommt (Plan #150, E8). */
class DateinameTest {

  @Test
  void gesaeubert_givenAPlainName_thenKeepsIt() {
    // When
    final Optional<String> gesaeubert = Dateiname.gesaeubert("Angebot 2026.pdf");

    // Then
    assertThat(gesaeubert).contains("Angebot 2026.pdf");
  }

  @Test
  void gesaeubert_givenWindowsPathTraversal_thenKeepsOnlyTheLastSegment() {
    // When
    final Optional<String> gesaeubert = Dateiname.gesaeubert("..\\..\\etc\\passwd");

    // Then
    assertThat(gesaeubert).contains("passwd");
  }

  @Test
  void gesaeubert_givenAnAbsoluteUnixPath_thenKeepsOnlyTheLastSegment() {
    // When
    final Optional<String> gesaeubert = Dateiname.gesaeubert("/tmp/a.pdf");

    // Then
    assertThat(gesaeubert).contains("a.pdf");
  }

  @Test
  void gesaeubert_givenControlCharacters_thenRemovesThem() {
    // When
    final Optional<String> gesaeubert = Dateiname.gesaeubert("Re\u0007port\u0000.pdf");

    // Then
    assertThat(gesaeubert).contains("Report.pdf");
  }

  @Test
  void gesaeubert_givenMoreThan255Characters_thenCutsToTheLimit() {
    // Given
    final String zuLang = "a".repeat(300) + ".pdf";

    // When
    final Optional<String> gesaeubert = Dateiname.gesaeubert(zuLang);

    // Then
    assertThat(gesaeubert).contains("a".repeat(255));
  }

  @Test
  void gesaeubert_givenExactly255Characters_thenKeepsThemAll() {
    // Given
    final String genauLang = "b".repeat(255);

    // When
    final Optional<String> gesaeubert = Dateiname.gesaeubert(genauLang);

    // Then
    assertThat(gesaeubert).contains(genauLang);
  }

  @Test
  void gesaeubert_givenAPathThatEndsInASeparator_thenReportsNothingLeft() {
    // When
    final Optional<String> gesaeubert = Dateiname.gesaeubert("/tmp/");

    // Then
    assertThat(gesaeubert).isEmpty();
  }

  @Test
  void gesaeubert_givenOnlyControlCharactersAndBlanks_thenReportsNothingLeft() {
    // When
    final Optional<String> gesaeubert = Dateiname.gesaeubert("  \u0001\u0002  ");

    // Then
    assertThat(gesaeubert).isEmpty();
  }

  @Test
  void gesaeubert_givenSurroundingBlanks_thenStripsThem() {
    // When
    final Optional<String> gesaeubert = Dateiname.gesaeubert("  Bericht.pdf  ");

    // Then
    assertThat(gesaeubert).contains("Bericht.pdf");
  }
}
