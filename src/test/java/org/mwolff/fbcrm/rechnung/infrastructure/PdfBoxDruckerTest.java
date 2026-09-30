package org.mwolff.fbcrm.rechnung.infrastructure;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.rechnung.application.Druckzeile;
import org.mwolff.fbcrm.rechnung.application.Schrift;

/**
 * Der Drucker gegen die Bibliothek: aus der Zeilenfolge wird ein lesbares PDF.
 *
 * <p>Gegenstand ist nicht der Satz — wo eine Zeile steht, entscheidet, wer die Druckzeilen rechnet
 * —, sondern dass das Ergebnis ein PDF ist und dass jede uebergebene Zeile daraus wieder
 * herauszulesen ist, auf der Seite, die sie nennt. Gelesen wird mit {@code PDFTextStripper}, also
 * mit demselben Werkzeug, mit dem ein Aussenstehender nachsehen wuerde, ob auf dem Beleg steht, was
 * darauf stehen muss.
 */
class PdfBoxDruckerTest {

  /** Eine Seite in beiden Schnitten, mit Umlaut und Eurozeichen. */
  private static final List<Druckzeile> EINE_SEITE =
      List.of(
          new Druckzeile(1, 70, 760, Schrift.FETT, 16, "Rechnung R26-0003"),
          new Druckzeile(1, 70, 730, Schrift.NORMAL, 10, "Gültig bis: 20.10.2026"),
          new Druckzeile(1, 400, 730, Schrift.NORMAL, 10, "2.500,03 €"));

  /** Zwei Seiten; die zweite traegt eine eigene Zeile. */
  private static final List<Druckzeile> ZWEI_SEITEN =
      List.of(
          new Druckzeile(1, 70, 760, Schrift.NORMAL, 10, "Posten 01"),
          new Druckzeile(2, 70, 760, Schrift.NORMAL, 10, "Posten 40"));

  private final PdfBoxDrucker drucker = new PdfBoxDrucker();

  private static String ausgelesen(final byte[] pdf) throws IOException {
    try (PDDocument dokument = Loader.loadPDF(pdf)) {
      return new PDFTextStripper().getText(dokument);
    }
  }

  private static String ausgelesen(final byte[] pdf, final int seite) throws IOException {
    try (PDDocument dokument = Loader.loadPDF(pdf)) {
      final PDFTextStripper leser = new PDFTextStripper();
      leser.setStartPage(seite);
      leser.setEndPage(seite);
      return leser.getText(dokument);
    }
  }

  private static int seiten(final byte[] pdf) throws IOException {
    try (PDDocument dokument = Loader.loadPDF(pdf)) {
      return dokument.getNumberOfPages();
    }
  }

  @Test
  void drucke_thenTheBytesAreAPdfFile() {
    // When
    final byte[] pdf = drucker.drucke(EINE_SEITE);

    // Then — die Kennung am Anfang der Datei.
    assertThat(pdf).startsWith("%PDF-".getBytes(US_ASCII));
  }

  @Test
  void drucke_thenEveryLineCanBeReadBack() throws IOException {
    // Given
    final byte[] pdf = drucker.drucke(EINE_SEITE);

    // When
    final String text = ausgelesen(pdf);

    // Then — beide Schnitte, der Umlaut und das Eurozeichen.
    assertThat(text)
        .contains("Rechnung R26-0003")
        .contains("Gültig bis: 20.10.2026")
        .contains("2.500,03 €");
  }

  @Test
  void drucke_givenLinesOnOnePage_thenTheDocumentHasExactlyOnePage() throws IOException {
    // Given
    final byte[] pdf = drucker.drucke(EINE_SEITE);

    // When / Then
    assertThat(seiten(pdf)).isOne();
  }

  @Test
  void drucke_givenNoLines_thenTheDocumentStillHasOnePage() throws IOException {
    // Given
    final byte[] pdf = drucker.drucke(List.of());

    // When / Then — ein PDF ohne Seite liesse sich nicht oeffnen.
    assertThat(seiten(pdf)).isOne();
  }

  @Test
  void drucke_givenLinesOnTwoPages_thenTheDocumentHasTwoPages() throws IOException {
    // Given
    final byte[] pdf = drucker.drucke(ZWEI_SEITEN);

    // When / Then
    assertThat(seiten(pdf)).isEqualTo(2);
  }

  @Test
  void drucke_givenLinesOnTwoPages_thenEachPageCarriesItsOwnLines() throws IOException {
    // Given
    final byte[] pdf = drucker.drucke(ZWEI_SEITEN);

    // When / Then — jede Seite wird geschrieben, und keine Zeile landet auf der falschen.
    assertThat(ausgelesen(pdf, 1)).contains("Posten 01").doesNotContain("Posten 40");
    assertThat(ausgelesen(pdf, 2)).contains("Posten 40").doesNotContain("Posten 01");
  }
}
