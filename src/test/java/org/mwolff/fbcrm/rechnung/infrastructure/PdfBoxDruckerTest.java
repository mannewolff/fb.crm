package org.mwolff.fbcrm.rechnung.infrastructure;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.rechnung.application.Ausrichtung;
import org.mwolff.fbcrm.rechnung.application.Druckelement;
import org.mwolff.fbcrm.rechnung.application.Farbe;
import org.mwolff.fbcrm.rechnung.application.Schrift;

/**
 * Der Drucker gegen die Bibliothek: aus der Elementfolge wird ein lesbares PDF.
 *
 * <p>Gegenstand ist nicht der Satz — wo ein Element steht, entscheidet, wer die Druckelemente
 * rechnet —, sondern dass das Ergebnis ein PDF ist und dass jedes uebergebene Element daraus wieder
 * herauszulesen ist, auf der Seite, die es nennt. Gelesen wird der Text mit {@code
 * PDFTextStripper}, also mit demselben Werkzeug, mit dem ein Aussenstehender nachsehen wuerde, ob
 * auf dem Beleg steht, was darauf stehen muss; Flaeche, Linie und Reihenfolge stehen nicht im Text,
 * sie werden im Inhaltsstrom der Seite gelesen.
 */
class PdfBoxDruckerTest {

  private static final Farbe SCHWARZ = new Farbe(0, 0, 0);

  private static final Farbe KUPFER = new Farbe(176, 106, 68);

  /** Eine Seite in beiden Schnitten, mit Umlaut und Eurozeichen. */
  private static final List<Druckelement> EINE_SEITE =
      List.of(
          text(1, 70, 760, Schrift.FETT, 16, "Rechnung R26-0003"),
          text(1, 70, 730, Schrift.NORMAL, 10, "Gültig bis: 20.10.2026"),
          text(1, 400, 730, Schrift.NORMAL, 10, "2.500,03 €"));

  /** Zwei Seiten; die zweite traegt ein eigenes Element. */
  private static final List<Druckelement> ZWEI_SEITEN =
      List.of(
          text(1, 70, 760, Schrift.NORMAL, 10, "Posten 01"),
          text(2, 70, 760, Schrift.NORMAL, 10, "Posten 40"));

  private final PdfBoxDrucker drucker = new PdfBoxDrucker();

  private static Druckelement text(
      final int seite,
      final int x,
      final int y,
      final Schrift schrift,
      final int groesse,
      final String inhalt) {
    return new Druckelement.Text(seite, x, y, schrift, groesse, SCHWARZ, Ausrichtung.LINKS, inhalt);
  }

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

  /** Der Inhaltsstrom der Seite, entpackt — dort stehen Flaeche, Linie und die Reihenfolge. */
  private static String inhaltsstrom(final byte[] pdf, final int seite) throws IOException {
    try (PDDocument dokument = Loader.loadPDF(pdf);
        InputStream strom = dokument.getPage(seite - 1).getContents()) {
      return new String(strom.readAllBytes(), ISO_8859_1);
    }
  }

  /** Der Name der einen Schrift, die die erste Seite benutzt. */
  private static String schriftname(final byte[] pdf) throws IOException {
    try (PDDocument dokument = Loader.loadPDF(pdf)) {
      final PDResources mittel = dokument.getPage(0).getResources();
      return mittel.getFont(mittel.getFontNames().iterator().next()).getName();
    }
  }

  private static List<TextPosition> zeichen(final byte[] pdf) throws IOException {
    try (PDDocument dokument = Loader.loadPDF(pdf)) {
      final Positionsleser leser = new Positionsleser();
      leser.getText(dokument);
      return leser.gesammelt;
    }
  }

  private static float linkeKante(final byte[] pdf) throws IOException {
    return zeichen(pdf).getFirst().getXDirAdj();
  }

  private static float rechteKante(final byte[] pdf) throws IOException {
    final TextPosition letztes = zeichen(pdf).getLast();
    return letztes.getXDirAdj() + letztes.getWidthDirAdj();
  }

  @Test
  void drucke_thenTheBytesAreAPdfFile() {
    // When
    final byte[] pdf = drucker.drucke(EINE_SEITE, null);

    // Then — die Kennung am Anfang der Datei.
    assertThat(pdf).startsWith("%PDF-".getBytes(US_ASCII));
  }

  @Test
  void drucke_thenEveryTextCanBeReadBack() throws IOException {
    // Given
    final byte[] pdf = drucker.drucke(EINE_SEITE, null);

    // When
    final String text = ausgelesen(pdf);

    // Then — beide Schnitte, der Umlaut und das Eurozeichen.
    assertThat(text)
        .contains("Rechnung R26-0003")
        .contains("Gültig bis: 20.10.2026")
        .contains("2.500,03 €");
  }

  @Test
  void drucke_givenElementsOnOnePage_thenTheDocumentHasExactlyOnePage() throws IOException {
    // Given
    final byte[] pdf = drucker.drucke(EINE_SEITE, null);

    // When / Then
    assertThat(seiten(pdf)).isOne();
  }

  @Test
  void drucke_givenNoElements_thenTheDocumentStillHasOnePage() throws IOException {
    // Given
    final byte[] pdf = drucker.drucke(List.of(), null);

    // When / Then — ein PDF ohne Seite liesse sich nicht oeffnen.
    assertThat(seiten(pdf)).isOne();
  }

  @Test
  void drucke_givenElementsOnTwoPages_thenTheDocumentHasTwoPages() throws IOException {
    // Given
    final byte[] pdf = drucker.drucke(ZWEI_SEITEN, null);

    // When / Then
    assertThat(seiten(pdf)).isEqualTo(2);
  }

  @Test
  void drucke_givenElementsOnTwoPages_thenEachPageCarriesItsOwnElements() throws IOException {
    // Given
    final byte[] pdf = drucker.drucke(ZWEI_SEITEN, null);

    // When / Then — jede Seite wird geschrieben, und kein Element landet auf der falschen.
    assertThat(ausgelesen(pdf, 1)).contains("Posten 01").doesNotContain("Posten 40");
    assertThat(ausgelesen(pdf, 2)).contains("Posten 40").doesNotContain("Posten 01");
  }

  @Test
  void drucke_givenATextAlignedLeft_thenItStartsAtItsX() throws IOException {
    // Given
    final byte[] pdf =
        drucker.drucke(List.of(text(1, 300, 700, Schrift.NORMAL, 10, "2.500,03")), null);

    // When / Then — das erste Zeichen steht auf dem x des Elements.
    assertThat(linkeKante(pdf)).isCloseTo(300f, within(1f));
  }

  @Test
  void drucke_givenATextAlignedRight_thenItEndsAtItsX() throws IOException {
    // Given
    final byte[] pdf =
        drucker.drucke(
            List.of(
                new Druckelement.Text(
                    1, 300, 700, Schrift.NORMAL, 10, SCHWARZ, Ausrichtung.RECHTS, "2.500,03")),
            null);

    // When / Then — das x ist die rechte Kante, die gemessene Breite liegt links davon.
    assertThat(rechteKante(pdf)).isCloseTo(300f, within(1f));
    assertThat(linkeKante(pdf)).isLessThan(300f);
  }

  @Test
  void drucke_givenACharacterOutsideWinAnsi_thenItIsReplacedByAQuestionMark() throws IOException {
    // Given — ein chinesisches Zeichen und ein Emoji, beide ausserhalb der Kodierung.
    final byte[] pdf =
        drucker.drucke(List.of(text(1, 70, 700, Schrift.NORMAL, 10, "Preis 中 🙂 €")), null);

    // When / Then — geschrieben wird ohne Ausnahme, an ihrer Stelle steht das Fragezeichen.
    assertThat(ausgelesen(pdf)).contains("Preis ? ? €");
  }

  @Test
  void drucke_givenAControlCharacter_thenItIsReplacedByAQuestionMark() throws IOException {
    // Given — ein Steuerzeichen hat auf dem Blatt keine Gestalt.
    final byte[] pdf =
        drucker.drucke(List.of(text(1, 70, 700, Schrift.NORMAL, 10, "A\u0007B")), null);

    // When / Then
    assertThat(ausgelesen(pdf)).contains("A?B");
  }

  @Test
  void drucke_givenAnArea_thenItIsFilledInItsColour() throws IOException {
    // Given
    final byte[] pdf =
        drucker.drucke(List.of(new Druckelement.Flaeche(1, 40, 600, 20, 160, KUPFER)), null);

    // When
    final String strom = inhaltsstrom(pdf, 1);

    // Then — Rechteck, Fuellfarbe und Fuellen stehen im Inhaltsstrom.
    assertThat(strom)
        .contains("40 600 20 160 re")
        .contains("0.6902 0.41569 0.26667 rg")
        .contains("\nf\n");
  }

  @Test
  void drucke_givenALine_thenItIsStrokedFromStartToEnd() throws IOException {
    // Given
    final byte[] pdf =
        drucker.drucke(List.of(new Druckelement.Linie(1, 70, 500, 525, 500, 2, KUPFER)), null);

    // When
    final String strom = inhaltsstrom(pdf, 1);

    // Then — Staerke, Strichfarbe und der Weg von Anfang zu Ende.
    assertThat(strom)
        .contains("2 w")
        .contains("0.6902 0.41569 0.26667 RG")
        .contains("70 500 m")
        .contains("525 500 l")
        .contains("\nS\n");
  }

  @Test
  void drucke_givenAreaLineAndText_thenTheyAreDrawnInThatOrder() throws IOException {
    // Given — bewusst in der umgekehrten Reihenfolge uebergeben.
    final byte[] pdf =
        drucker.drucke(
            List.of(
                text(1, 70, 700, Schrift.NORMAL, 10, "Posten"),
                new Druckelement.Linie(1, 70, 690, 525, 690, 1, SCHWARZ),
                new Druckelement.Flaeche(1, 40, 600, 20, 160, KUPFER)),
            null);

    // When
    final String strom = inhaltsstrom(pdf, 1);

    // Then — erst die Flaeche, dann die Linie, dann der Text; sonst deckte die Flaeche beides zu.
    assertThat(strom.indexOf(" re")).isLessThan(strom.indexOf(" m"));
    assertThat(strom.indexOf(" m")).isLessThan(strom.indexOf("BT"));
    assertThat(strom.indexOf("BT")).isLessThan(strom.indexOf("ET"));
  }

  @Test
  void drucke_givenAnAreaAndALine_thenTheDocumentCanBeOpenedAgain() throws IOException {
    // Given
    final byte[] pdf =
        drucker.drucke(
            List.of(
                new Druckelement.Flaeche(1, 40, 600, 20, 160, KUPFER),
                new Druckelement.Linie(1, 70, 500, 525, 500, 2, SCHWARZ)),
            null);

    // When / Then — ohne Ausnahme geschrieben und wieder als PDF zu oeffnen.
    assertThat(seiten(pdf)).isOne();
  }

  @Test
  void drucke_givenATextInColour_thenTheColourIsSetForTheGlyphs() throws IOException {
    // Given
    final byte[] pdf =
        drucker.drucke(
            List.of(
                new Druckelement.Text(
                    1, 70, 700, Schrift.FETT, 12, KUPFER, Ausrichtung.LINKS, "Rechnung")),
            null);

    // When
    final String strom = inhaltsstrom(pdf, 1);

    // Then
    assertThat(strom).contains("0.6902 0.41569 0.26667 rg");
  }

  @Test
  void drucke_givenATextInBold_thenItIsWrittenInTheBoldFont() throws IOException {
    // Given
    final byte[] pdf = drucker.drucke(List.of(text(1, 70, 700, Schrift.FETT, 12, "Summe")), null);

    // When / Then — die Hervorhebung ist der fette Schnitt und nicht der Lauftext.
    assertThat(schriftname(pdf)).isEqualTo("Helvetica-Bold");
  }

  @Test
  void drucke_givenATextInNormal_thenItIsWrittenInTheRegularFont() throws IOException {
    // Given
    final byte[] pdf =
        drucker.drucke(List.of(text(1, 70, 700, Schrift.NORMAL, 12, "Posten")), null);

    // When / Then
    assertThat(schriftname(pdf)).isEqualTo("Helvetica");
  }

  /**
   * Ein Briefpapier mit so vielen Blaettern, wie Aufschriften genannt sind; jedes Blatt traegt
   * seine Aufschrift als Text, damit sich ablesen laesst, welches Blatt unter welcher Seite liegt.
   */
  private static byte[] briefpapier(final String... aufschriften) throws IOException {
    try (PDDocument dokument = new PDDocument()) {
      final PDFont schrift = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
      for (final String aufschrift : aufschriften) {
        final PDPage blatt = new PDPage(PDRectangle.A4);
        dokument.addPage(blatt);
        try (PDPageContentStream inhalt = new PDPageContentStream(dokument, blatt)) {
          inhalt.beginText();
          inhalt.setFont(schrift, 9);
          inhalt.newLineAtOffset(70, 30);
          inhalt.showText(aufschrift);
          inhalt.endText();
        }
      }
      final ByteArrayOutputStream ausgabe = new ByteArrayOutputStream();
      dokument.save(ausgabe);
      return ausgabe.toByteArray();
    }
  }

  @Test
  void drucke_givenUntergrundNurSeiteEins_thenFolgeseiteOhneUnterlage() throws IOException {
    // Given — ein zweiseitiger Beleg, ein einseitiges Briefpapier.
    final byte[] pdf = drucker.drucke(ZWEI_SEITEN, briefpapier("Briefkopf Blatt eins"));

    // When / Then — Seite 1 traegt das Blatt, Seite 2 nicht, und ihre Elemente stehen darauf.
    assertThat(seiten(pdf)).isEqualTo(2);
    assertThat(ausgelesen(pdf, 1)).contains("Briefkopf Blatt eins").contains("Posten 01");
    assertThat(ausgelesen(pdf, 2)).doesNotContain("Briefkopf").contains("Posten 40");
    assertThat(inhaltsstrom(pdf, 2)).doesNotContain(" Do");
  }

  @Test
  void drucke_givenUntergrundZweiSeiten_thenJedeSeiteMitUnterlage() throws IOException {
    // Given — drei Seiten, damit auch die zweite Folgeseite ihr Blatt zeigt.
    final List<Druckelement> dreiSeiten =
        List.of(
            text(1, 70, 760, Schrift.NORMAL, 10, "Posten 01"),
            text(2, 70, 760, Schrift.NORMAL, 10, "Posten 40"),
            text(3, 70, 760, Schrift.NORMAL, 10, "Posten 80"));

    // When
    final byte[] pdf =
        drucker.drucke(dreiSeiten, briefpapier("Briefkopf Blatt eins", "Briefkopf Blatt zwei"));

    // Then — Seite 1 auf dem ersten Blatt, jede Folgeseite auf dem zweiten.
    assertThat(ausgelesen(pdf, 1))
        .contains("Briefkopf Blatt eins")
        .doesNotContain("Blatt zwei")
        .contains("Posten 01");
    assertThat(ausgelesen(pdf, 2))
        .contains("Briefkopf Blatt zwei")
        .doesNotContain("Blatt eins")
        .contains("Posten 40");
    assertThat(ausgelesen(pdf, 3))
        .contains("Briefkopf Blatt zwei")
        .doesNotContain("Blatt eins")
        .contains("Posten 80");
  }

  @Test
  void drucke_givenUntergrund_thenItIsDrawnBeforeEveryElement() throws IOException {
    // Given
    final byte[] pdf =
        drucker.drucke(
            List.of(
                new Druckelement.Flaeche(1, 40, 600, 20, 160, KUPFER),
                text(1, 70, 700, Schrift.NORMAL, 10, "Posten")),
            briefpapier("Briefkopf Blatt eins"));

    // When
    final String strom = inhaltsstrom(pdf, 1);

    // Then — das Formular steht zuerst im Inhaltsstrom und liegt damit unter allem.
    assertThat(strom).contains(" Do");
    assertThat(strom.indexOf(" Do")).isLessThan(strom.indexOf(" re"));
  }

  @Test
  void drucke_givenOhneUntergrund_thenUnveraendert() throws IOException {
    // Given — Flaeche, Linie und zwei Texte in beiden Ausrichtungen.
    final byte[] pdf =
        drucker.drucke(
            List.of(
                new Druckelement.Text(
                    1, 70, 760, Schrift.FETT, 16, SCHWARZ, Ausrichtung.LINKS, "Rechnung R26-0003"),
                new Druckelement.Text(
                    1, 400, 730, Schrift.NORMAL, 10, SCHWARZ, Ausrichtung.RECHTS, "2.500,03 €"),
                new Druckelement.Linie(1, 70, 690, 525, 690, 1, SCHWARZ),
                new Druckelement.Flaeche(1, 40, 600, 20, 160, KUPFER)),
            null);

    // When / Then — Wort fuer Wort der Inhaltsstrom, den der Drucker vor dem Untergrund schrieb,
    // und kein Formular in den Ressourcen der Seite.
    assertThat(inhaltsstrom(pdf, 1))
        .isEqualTo(
            """
            0.6902 0.41569 0.26667 rg
            40 600 20 160 re
            f
            0 0 0 RG
            1 w
            70 690 m
            525 690 l
            S
            0 0 0 rg
            BT
            /F1 16 Tf
            70 760 Td
            (Rechnung R26-0003) Tj
            ET
            0 0 0 rg
            BT
            /F2 10 Tf
            352.73999 730 Td
            <322E3530302C30332080> Tj
            ET
            """);
    assertThat(seiten(pdf)).isOne();
    try (PDDocument dokument = Loader.loadPDF(pdf)) {
      assertThat(dokument.getPage(0).getResources().getXObjectNames()).isEmpty();
    }
  }

  @Test
  void drucke_givenASlantedText_thenItIsRotatedInItsColourAboveAreaLineAndText()
      throws IOException {
    // Given — das Muster zuerst uebergeben, damit die Tiefe nicht aus der Reihenfolge folgt.
    final Farbe hellgrau = new Farbe(210, 210, 210);
    final byte[] pdf =
        drucker.drucke(
            List.of(
                new Druckelement.Schraegtext(1, 150, 300, 45, Schrift.FETT, 96, hellgrau, "Muster"),
                text(1, 70, 700, Schrift.NORMAL, 10, "Posten"),
                new Druckelement.Linie(1, 70, 690, 525, 690, 1, SCHWARZ),
                new Druckelement.Flaeche(1, 40, 600, 20, 160, KUPFER)),
            null);

    // When
    final String strom = inhaltsstrom(pdf, 1);

    // Then — Drehmatrix um 45 Grad am Ankerpunkt, die Farbe, und zuletzt gezeichnet.
    assertThat(strom)
        .contains("0.70711 0.70711 -0.70711 0.70711 150 300 Tm")
        .contains("0.82353 0.82353 0.82353 rg")
        .contains("(Muster) Tj");
    assertThat(strom.indexOf(" Tm")).isGreaterThan(strom.indexOf(" re"));
    assertThat(strom.indexOf(" Tm")).isGreaterThan(strom.indexOf(" m"));
    assertThat(strom.indexOf(" Tm")).isGreaterThan(strom.indexOf(" Td"));
  }

  @Test
  void drucke_givenASlantedTextInNormal_thenItIsWrittenInTheRegularFont() throws IOException {
    // Given
    final byte[] pdf =
        drucker.drucke(
            List.of(
                new Druckelement.Schraegtext(
                    1, 150, 300, 30, Schrift.NORMAL, 60, SCHWARZ, "Muster")),
            null);

    // When / Then
    assertThat(schriftname(pdf)).isEqualTo("Helvetica");
  }

  @Test
  void drucke_givenASkewedText_thenItsTextObjectIsClosed() throws IOException {
    // Given — der schraege Text als letztes Element, damit kein weiteres BT den Block bemerkt.
    final byte[] pdf =
        drucker.drucke(
            List.of(
                new Druckelement.Schraegtext(1, 150, 300, 45, Schrift.FETT, 96, SCHWARZ, "Muster")),
            null);

    // When
    final List<String> operatoren = new ArrayList<>();
    try (PDDocument dokument = Loader.loadPDF(pdf)) {
      final PDPage seite = dokument.getPage(0);
      for (final Object token : new PDFStreamParser(seite).parse()) {
        if (token instanceof Operator operator) {
          operatoren.add(operator.getName());
        }
      }
    }

    // Then — nach dem Tj folgt ET, und jedes BT hat sein ET.
    final int zeige = operatoren.lastIndexOf("Tj");
    assertThat(zeige).isNotNegative();
    assertThat(operatoren.subList(zeige + 1, operatoren.size())).contains("ET");
    assertThat(operatoren.stream().filter("BT"::equals).count())
        .isEqualTo(operatoren.stream().filter("ET"::equals).count());
  }

  /** Sammelt die Zeichen mit ihrer Lage, um Ausrichtung nachzumessen. */
  private static final class Positionsleser extends PDFTextStripper {

    private final List<TextPosition> gesammelt = new ArrayList<>();

    @Override
    protected void writeString(final String text, final List<TextPosition> positionen) {
      gesammelt.addAll(positionen);
    }
  }
}
