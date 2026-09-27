package org.mwolff.fbcrm.angebot.infrastructure;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.angebot.application.Beleglayout;
import org.mwolff.fbcrm.angebot.application.Druckdatendoppel;

/**
 * Der Drucker gegen die Bibliothek: aus der Zeilenfolge wird ein lesbares PDF.
 *
 * <p>Gegenstand ist nicht der Satz — den prueft {@code BeleglayoutTest} ohne PDFBox —, sondern dass
 * das Ergebnis ein PDF ist und dass jeder Pflichtinhalt aus Kriterium 15 daraus wieder
 * herauszulesen ist. Gelesen wird mit {@code PDFTextStripper}, also mit demselben Werkzeug, mit dem
 * ein Aussenstehender nachsehen wuerde, ob auf dem Beleg steht, was darauf stehen muss.
 */
class PdfBoxDruckerTest {

  private final PdfBoxDrucker drucker = new PdfBoxDrucker();

  private static String ausgelesen(final byte[] pdf) throws IOException {
    try (PDDocument dokument = Loader.loadPDF(pdf)) {
      return new PDFTextStripper().getText(dokument);
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
    final byte[] pdf = drucker.drucke(Beleglayout.zeilen(Druckdatendoppel.standard()));

    // Then — die Kennung am Anfang der Datei.
    assertThat(pdf).startsWith("%PDF-".getBytes(US_ASCII));
  }

  @Test
  void drucke_thenEveryMandatoryContentOfCriterion15CanBeReadBack() throws IOException {
    // Given
    final byte[] pdf = drucker.drucke(Beleglayout.zeilen(Druckdatendoppel.standard()));

    // When
    final String text = ausgelesen(pdf);

    // Then — Absender, Empfaenger, Nummer, Datum, Gueltigkeit, Leistungsbeschreibung, Positionen
    // mit Menge, Einheit, Einzelpreis und Betrag, Summe und Zahlungsbedingungen.
    assertThat(text)
        .contains("Manfred Wolff, Am Deich 2, 28199 Hansestadt")
        .contains("Adler AG")
        .contains("Frau Dr. Adler")
        .contains("Hauptstrasse 1")
        .contains("28195 Bremen")
        .contains("Angebot A-2026-001")
        .contains("Angebotsdatum: 20.09.2026")
        .contains("Gültig bis: 20.10.2026")
        .contains("wort wort")
        .contains("Konzeption")
        .contains("2,5")
        .contains("Personentag")
        .contains("1.000,01 €")
        .contains("2.500,03 €")
        .contains("Angebotssumme")
        .contains("3.700,03 €")
        .contains("Zahlungsbedingungen")
        .contains(Druckdatendoppel.BEDINGUNGEN);
  }

  @Test
  void drucke_thenTheNetHintIsOnThePaper() throws IOException {
    // Given
    final byte[] pdf = drucker.drucke(Beleglayout.zeilen(Druckdatendoppel.standard()));

    // When / Then — E10: ohne ihn liest sich ein Nettobetrag als Bruttopreis.
    assertThat(ausgelesen(pdf)).contains(Druckdatendoppel.NETTO_HINWEIS);
  }

  @Test
  void drucke_thenTheSenderDetailsAreOnThePaper() throws IOException {
    // Given
    final byte[] pdf = drucker.drucke(Beleglayout.zeilen(Druckdatendoppel.standard()));

    // When / Then
    assertThat(ausgelesen(pdf))
        .contains("E-Mail: manne@example.org")
        .contains("Telefon: 0421 123456")
        .contains("Steuernummer: 12/345/67890")
        .contains("USt-IdNr.: DE123456789")
        .contains("Bankverbindung: Sparkasse, IBAN DE02 1203 0000 0000 2020 51");
  }

  @Test
  void drucke_givenAMultiPageOffer_thenTheDocumentHasMoreThanOnePage() throws IOException {
    // Given
    final byte[] pdf = drucker.drucke(Beleglayout.zeilen(Druckdatendoppel.mehrseitig()));

    // When / Then
    assertThat(seiten(pdf)).isGreaterThan(1);
  }

  @Test
  void drucke_givenASingleLineOffer_thenTheDocumentHasExactlyOnePage() throws IOException {
    // Given
    final byte[] pdf = drucker.drucke(Beleglayout.zeilen(Druckdatendoppel.standard()));

    // When / Then
    assertThat(seiten(pdf)).isOne();
  }

  @Test
  void drucke_givenAMultiPageOffer_thenTheSecondPageCarriesItsOwnRows() throws IOException {
    // Given
    final byte[] pdf = drucker.drucke(Beleglayout.zeilen(Druckdatendoppel.mehrseitig()));

    // When — die letzte Position liegt nach dem Seitenumbruch.
    final String text = ausgelesen(pdf);

    // Then — jede Seite wird geschrieben, nicht nur die erste.
    assertThat(text).contains("Posten 29").contains("Posten 40");
  }
}
