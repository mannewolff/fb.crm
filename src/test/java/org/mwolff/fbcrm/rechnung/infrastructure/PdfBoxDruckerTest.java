package org.mwolff.fbcrm.rechnung.infrastructure;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.awt.color.ColorSpace;
import java.awt.color.ICC_Profile;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary;
import org.apache.pdfbox.pdmodel.PDEmbeddedFilesNameTreeNode;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.color.PDOutputIntent;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.xmpbox.XMPMetadata;
import org.apache.xmpbox.schema.XMPSchema;
import org.apache.xmpbox.xml.DomXmpParser;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.rechnung.application.Ausrichtung;
import org.mwolff.fbcrm.rechnung.application.Druckelement;
import org.mwolff.fbcrm.rechnung.application.Farbe;
import org.mwolff.fbcrm.rechnung.application.Schrift;
import org.xml.sax.SAXParseException;

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

  /** Eine eingebettete Teilmenge traegt ein Praefix aus sechs Grossbuchstaben und „+“. */
  private static final String TEILMENGE_NORMAL = "[A-Z]{6}\\+LiberationSans";

  private static final String TEILMENGE_FETT = "[A-Z]{6}\\+LiberationSans-Bold";

  /**
   * Umlaute, Eurozeichen und Ziffern — was ein deutscher Beleg schreibt. Ohne Mittelpunkt „·“: Den
   * zeichnet Liberation Sans so breit wie Arial, nicht wie Helvetica.
   */
  private static final String MUSTERTEXT =
      "Rechnung R26-0003, Gültig bis 20.10.2026, Müller, Straße 7: 2.500,03 € ÄÖÜäöüß 0123456789";

  /** Die Abweichung der Breite, die den Umbruch eines Absatzes noch nicht verschiebt. */
  private static final double HOECHSTE_ABWEICHUNG = 0.005;

  /** Der Augenblick des Schreibens, als Aenderungsdatum der eingebetteten XML. */
  private static final Instant JETZT = Instant.parse("2026-10-29T09:30:00Z");

  /** Eine E-Rechnung, so knapp, dass der Drucker daraus Nummer und Inhalt ablesen kann. */
  private static final byte[] CII =
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <rsm:CrossIndustryInvoice \
      xmlns:rsm="urn:un:unece:uncefact:data:standard:CrossIndustryInvoice:100" \
      xmlns:ram="urn:un:unece:uncefact:data:standard:ReusableAggregateBusinessInformationEntity:100">
        <rsm:ExchangedDocument>
          <ram:ID>R26-0003</ram:ID>
        </rsm:ExchangedDocument>
      </rsm:CrossIndustryInvoice>
      """
          .getBytes(UTF_8);

  private final PdfBoxDrucker drucker = new PdfBoxDrucker(Clock.fixed(JETZT, ZoneOffset.UTC));

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

  /**
   * Die Zeichen einer eingebetteten Schrift stehen als Glyphennummern im Inhaltsstrom; gelesen wird
   * der Text darum mit {@link PDFTextStripper}, und im Strom steht statt der Zeichen ein
   * Platzhalter.
   */
  private static final Pattern TEXTOPERAND = Pattern.compile("^.* Tj$", Pattern.MULTILINE);

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

  /** Jede Schrift, die der Drucker auf eine Seite des Dokuments geschrieben hat. */
  private static List<PDFont> schriften(final byte[] pdf) throws IOException {
    final List<PDFont> gefunden = new ArrayList<>();
    try (PDDocument dokument = Loader.loadPDF(pdf)) {
      for (final PDPage seite : dokument.getPages()) {
        final PDResources mittel = seite.getResources();
        for (final COSName name : mittel.getFontNames()) {
          gefunden.add(mittel.getFont(name));
        }
      }
      return gefunden;
    }
  }

  private static String pruefsumme(final String ressource)
      throws IOException, NoSuchAlgorithmException {
    try (InputStream strom =
        Objects.requireNonNull(PdfBoxDrucker.class.getResourceAsStream(ressource), ressource)) {
      return HexFormat.of()
          .formatHex(MessageDigest.getInstance("SHA-256").digest(strom.readAllBytes()));
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
    assertThat(schriftname(pdf)).matches(TEILMENGE_FETT);
  }

  @Test
  void drucke_givenATextInNormal_thenItIsWrittenInTheRegularFont() throws IOException {
    // Given
    final byte[] pdf =
        drucker.drucke(List.of(text(1, 70, 700, Schrift.NORMAL, 12, "Posten")), null);

    // When / Then
    assertThat(schriftname(pdf)).matches(TEILMENGE_NORMAL);
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
    // bis auf die Zeichen selbst, und kein Formular in den Ressourcen der Seite. Der Versatz des
    // rechtsbuendigen Betrags ist derselbe wie mit Helvetica: Liberation Sans misst gleich breit.
    assertThat(TEXTOPERAND.matcher(inhaltsstrom(pdf, 1)).replaceAll("<Text> Tj"))
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
            <Text> Tj
            ET
            0 0 0 rg
            BT
            /F2 10 Tf
            352.73999 730 Td
            <Text> Tj
            ET
            """);
    assertThat(ausgelesen(pdf)).contains("Rechnung R26-0003").contains("2.500,03 €");
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
        .contains(" Tj");
    // Den gedrehten Text liest der Textleser in Stuecken; die Folge der Zeichen bleibt.
    assertThat(ausgelesen(pdf).replaceAll("\\s", "")).contains("Muster");
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
    assertThat(schriftname(pdf)).matches(TEILMENGE_NORMAL);
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

  @Test
  void drucke_thenEveryFontIsAnEmbeddedSubsetOfLiberationSans() throws IOException {
    // Given — beide Schnitte auf einer Seite, ohne Briefpapier.
    final byte[] pdf = drucker.drucke(EINE_SEITE, null);

    // When
    final List<PDFont> schriften = schriften(pdf);

    // Then — PDF/A verlangt eingebettete Schriften (Plan #461, E2).
    assertThat(schriften).hasSize(2).allMatch(PDFont::isEmbedded);
    assertThat(schriften)
        .extracting(PDFont::getName)
        .anyMatch(name -> name.matches(TEILMENGE_NORMAL))
        .anyMatch(name -> name.matches(TEILMENGE_FETT));
  }

  @Test
  void drucke_givenUntergrund_thenEveryFontOfTheDruckerIsAnEmbeddedSubsetOfLiberationSans()
      throws IOException {
    // Given — beide Schnitte auf zwei Seiten, jede mit einem Blatt des Briefpapiers darunter.
    final List<Druckelement> zweiSeiten =
        List.of(
            text(1, 70, 760, Schrift.FETT, 16, "Rechnung R26-0003"),
            text(1, 70, 730, Schrift.NORMAL, 10, "Gültig bis: 20.10.2026"),
            text(2, 70, 760, Schrift.FETT, 10, "Summe"),
            text(2, 70, 730, Schrift.NORMAL, 10, "2.500,03 €"));
    final byte[] pdf =
        drucker.drucke(zweiSeiten, briefpapier("Briefkopf Blatt eins", "Briefkopf Blatt zwei"));

    // When
    final List<PDFont> schriften = schriften(pdf);

    // Then — die Schriften des Briefpapiers stehen in seinen Formularen, nicht auf der Seite.
    assertThat(schriften).hasSize(4).allMatch(PDFont::isEmbedded);
    assertThat(schriften)
        .extracting(PDFont::getName)
        .allMatch(name -> name.matches(TEILMENGE_NORMAL) || name.matches(TEILMENGE_FETT));
  }

  @Test
  void schrift_givenRegular_thenItIsAsWideAsHelvetica() throws IOException {
    // Given
    try (PDDocument dokument = new PDDocument()) {
      final PDFont liberation = PdfBoxDrucker.geladen(dokument, PdfBoxDrucker.SCHRIFT_NORMAL);
      final PDFont helvetica = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

      // When
      final float breite = liberation.getStringWidth(MUSTERTEXT);
      final float vorbild = helvetica.getStringWidth(MUSTERTEXT);

      // Then — metrisch gleich: Breiten und Umbrueche des Satzes bleiben, wie sie sind.
      assertThat((double) breite).isCloseTo(vorbild, within(vorbild * HOECHSTE_ABWEICHUNG));
    }
  }

  @Test
  void schrift_givenBold_thenItIsAsWideAsHelveticaBold() throws IOException {
    // Given
    try (PDDocument dokument = new PDDocument()) {
      final PDFont liberation = PdfBoxDrucker.geladen(dokument, PdfBoxDrucker.SCHRIFT_FETT);
      final PDFont helvetica = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

      // When
      final float breite = liberation.getStringWidth(MUSTERTEXT);
      final float vorbild = helvetica.getStringWidth(MUSTERTEXT);

      // Then
      assertThat((double) breite).isCloseTo(vorbild, within(vorbild * HOECHSTE_ABWEICHUNG));
    }
  }

  @Test
  void schriftdateien_thenTheyAreTheFilesOfRelease215() throws Exception {
    // When / Then — die Pruefsummen aus der Entscheidung des Pakets #468.
    assertThat(pruefsumme(PdfBoxDrucker.SCHRIFT_NORMAL))
        .isEqualTo("76d04c18ea243f426b7de1f3ad208e927008f961dc5945e5aad352d0dfde8ee8");
    assertThat(pruefsumme(PdfBoxDrucker.SCHRIFT_FETT))
        .isEqualTo("788abee4c806d660e8aee46689dd8540cd4bb98da03dcc9d171ce3efd99a9173");
  }

  /** Die XMP-Metadaten des Dokuments, oder {@code null}, wenn es keine traegt. */
  private static XMPMetadata xmp(final byte[] pdf) throws Exception {
    try (PDDocument dokument = Loader.loadPDF(pdf)) {
      final PDMetadata metadaten = dokument.getDocumentCatalog().getMetadata();
      if (metadaten == null) {
        return XMPMetadata.createXMPMetadata();
      }
      return new DomXmpParser().parse(metadaten.toByteArray());
    }
  }

  /** Die eingebetteten Dateien unter {@code /Names/EmbeddedFiles}, nach Namen. */
  private static Map<String, PDComplexFileSpecification> eingebettete(final PDDocument dokument)
      throws IOException {
    final PDDocumentNameDictionary namen = dokument.getDocumentCatalog().getNames();
    if (namen == null) {
      return Map.of();
    }
    final PDEmbeddedFilesNameTreeNode baum = namen.getEmbeddedFiles();
    return baum == null || baum.getNames() == null ? Map.of() : baum.getNames();
  }

  /**
   * Ein Briefpapier, wie es fuer volles PDF/A taugt: Text in Liberation Sans, eingebettet, und eine
   * Flaeche in RGB (Plan #461, E5).
   */
  private static byte[] tauglichesBriefpapier() throws IOException {
    try (PDDocument dokument = new PDDocument()) {
      final PDFont schrift = PdfBoxDrucker.geladen(dokument, PdfBoxDrucker.SCHRIFT_NORMAL);
      final PDPage blatt = new PDPage(PDRectangle.A4);
      dokument.addPage(blatt);
      try (PDPageContentStream inhalt = new PDPageContentStream(dokument, blatt)) {
        inhalt.setNonStrokingColor(0.69f, 0.42f, 0.27f);
        inhalt.addRect(0, 820, 595, 22);
        inhalt.fill();
        inhalt.beginText();
        inhalt.setFont(schrift, 9);
        inhalt.newLineAtOffset(70, 30);
        inhalt.showText("Briefkopf Blatt eins");
        inhalt.endText();
      }
      final ByteArrayOutputStream ausgabe = new ByteArrayOutputStream();
      dokument.save(ausgabe);
      return ausgabe.toByteArray();
    }
  }

  @Test
  void druckeERechnung_thenTheDocumentIsPdfA3bWithoutAViolation() {
    // Given — beide Schnitte, Flaeche und Linie, ohne Briefpapier.
    final List<Druckelement> elemente = new ArrayList<>(EINE_SEITE);
    elemente.add(new Druckelement.Flaeche(1, 40, 600, 20, 160, KUPFER));
    elemente.add(new Druckelement.Linie(1, 70, 500, 525, 500, 2, KUPFER));

    // When
    final byte[] pdf = drucker.druckeERechnung(elemente, null, CII);

    // Then — veraPDF gegen das Profil PDF/A-3B.
    assertThat(PdfaPruefung.verstoesse(pdf)).isEmpty();
  }

  @Test
  void druckeERechnung_givenATauglichesBriefpapier_thenTheDocumentIsPdfA3bWithoutAViolation()
      throws IOException {
    // When
    final byte[] pdf = drucker.druckeERechnung(ZWEI_SEITEN, tauglichesBriefpapier(), CII);

    // Then — mit Schriften eingebettet und in RGB bleibt das Dokument PDF/A (E5).
    assertThat(PdfaPruefung.verstoesse(pdf)).isEmpty();
    assertThat(ausgelesen(pdf, 1)).contains("Briefkopf Blatt eins").contains("Posten 01");
  }

  @Test
  void drucke_thenTheDocumentIsNotPdfA() {
    // When
    final byte[] pdf = drucker.drucke(EINE_SEITE, null);

    // Then — ohne pdfaid und ohne OutputIntent besteht das gewoehnliche Dokument die Pruefung
    // nicht.
    assertThat(PdfaPruefung.verstoesse(pdf)).isNotEmpty();
  }

  @Test
  void druckeERechnung_thenTheXmpCarriesPdfaIdPartThreeConformanceB() throws Exception {
    // When
    final XMPMetadata xmp = xmp(drucker.druckeERechnung(EINE_SEITE, null, CII));

    // Then
    assertThat(xmp.getPDFAIdentificationSchema()).isNotNull();
    assertThat(xmp.getPDFAIdentificationSchema().getPart()).isEqualTo(3);
    assertThat(xmp.getPDFAIdentificationSchema().getConformance()).isEqualTo("B");
  }

  @Test
  void druckeERechnung_thenTheTitleIsRechnungAndItsNummer() throws Exception {
    // Given
    final byte[] pdf = drucker.druckeERechnung(EINE_SEITE, null, CII);

    // When / Then — in Dublin Core und im Info-Woerterbuch, beide gleich.
    assertThat(xmp(pdf).getDublinCoreSchema().getTitle()).isEqualTo("Rechnung R26-0003");
    try (PDDocument dokument = Loader.loadPDF(pdf)) {
      assertThat(dokument.getDocumentInformation().getTitle()).isEqualTo("Rechnung R26-0003");
    }
  }

  @Test
  void druckeERechnung_thenTheXmpCarriesTheFacturXSchema() throws Exception {
    // When
    final XMPSchema fx =
        xmp(drucker.druckeERechnung(EINE_SEITE, null, CII)).getSchema(ERechnungsmetadaten.FACTUR_X);

    // Then
    assertThat(fx).isNotNull();
    assertThat(fx.getPrefix()).isEqualTo("fx");
    assertThat(fx.getUnqualifiedTextPropertyValue("DocumentType")).isEqualTo("INVOICE");
    assertThat(fx.getUnqualifiedTextPropertyValue("DocumentFileName")).isEqualTo("factur-x.xml");
    assertThat(fx.getUnqualifiedTextPropertyValue("Version")).isEqualTo("1.0");
    assertThat(fx.getUnqualifiedTextPropertyValue("ConformanceLevel")).isEqualTo("EN 16931");
  }

  @Test
  void druckeERechnung_thenTheXmpDescribesTheFacturXSchemaAsAnExtension() throws Exception {
    // Given
    final byte[] pdf = drucker.druckeERechnung(EINE_SEITE, null, CII);

    // When
    final String metadaten;
    try (PDDocument dokument = Loader.loadPDF(pdf)) {
      metadaten = new String(dokument.getDocumentCatalog().getMetadata().toByteArray(), UTF_8);
    }

    // Then — PDF/A verlangt fuer jedes fremde Schema seine Beschreibung.
    assertThat(metadaten)
        .contains("pdfaExtension:schemas")
        .contains("urn:factur-x:pdfa:CrossIndustryDocument:invoice:1p0#")
        .contains("<pdfaSchema:prefix>fx</pdfaSchema:prefix>")
        .contains("<pdfaProperty:name>DocumentFileName</pdfaProperty:name>")
        .contains("<pdfaProperty:name>DocumentType</pdfaProperty:name>")
        .contains("<pdfaProperty:name>Version</pdfaProperty:name>")
        .contains("<pdfaProperty:name>ConformanceLevel</pdfaProperty:name>")
        .contains(
            "<pdfaSchema:namespaceURI>urn:factur-x:pdfa:CrossIndustryDocument:invoice:1p0#"
                + "</pdfaSchema:namespaceURI>")
        .contains("<pdfaSchema:schema>Factur-X PDFA Extension Schema</pdfaSchema:schema>")
        .contains("<pdfaProperty:valueType>Text</pdfaProperty:valueType>")
        .contains("<pdfaProperty:category>external</pdfaProperty:category>")
        .contains(
            "<pdfaProperty:description>The name of the embedded XML document"
                + "</pdfaProperty:description>");
  }

  @Test
  void druckeERechnung_thenTheOutputIntentIsSrgb() throws IOException {
    // When
    final byte[] pdf = drucker.druckeERechnung(EINE_SEITE, null, CII);

    // Then
    try (PDDocument dokument = Loader.loadPDF(pdf)) {
      final List<PDOutputIntent> absichten = dokument.getDocumentCatalog().getOutputIntents();
      assertThat(absichten).hasSize(1);
      final PDOutputIntent absicht = absichten.getFirst();
      assertThat(absicht.getOutputConditionIdentifier()).isEqualTo("sRGB IEC61966-2.1");
      assertThat(absicht.getOutputCondition()).isEqualTo("sRGB IEC61966-2.1");
      assertThat(absicht.getInfo()).isEqualTo("sRGB IEC61966-2.1");
      assertThat(absicht.getRegistryName()).isEqualTo("http://www.color.org");
      // Das sRGB-Profil des JDK, Byte fuer Byte (Entscheidung des Pakets #471).
      try (InputStream profil = absicht.getDestOutputIntent().createInputStream()) {
        assertThat(profil.readAllBytes())
            .isEqualTo(ICC_Profile.getInstance(ColorSpace.CS_sRGB).getData());
      }
    }
  }

  @Test
  void druckeERechnung_thenTheXmlIsEmbeddedAsFacturXWithAlternative() throws IOException {
    // When
    final byte[] pdf = drucker.druckeERechnung(EINE_SEITE, null, CII);

    // Then — genau eine Datei, unter ihrem Namen, mit Typ, Datum und Beziehung.
    try (PDDocument dokument = Loader.loadPDF(pdf)) {
      final Map<String, PDComplexFileSpecification> dateien = eingebettete(dokument);
      assertThat(dateien).containsOnlyKeys("factur-x.xml");
      final PDComplexFileSpecification beschreibung = dateien.get("factur-x.xml");
      assertThat(beschreibung.getFile()).isEqualTo("factur-x.xml");
      assertThat(beschreibung.getFileUnicode()).isEqualTo("factur-x.xml");
      assertThat(beschreibung.getCOSObject().getNameAsString("AFRelationship"))
          .isEqualTo("Alternative");
      final PDEmbeddedFile datei = beschreibung.getEmbeddedFile();
      assertThat(beschreibung.getFileDescription())
          .isEqualTo("Rechnungsdaten nach EN 16931 (ZUGFeRD, Factur-X)");
      assertThat(datei.getSubtype()).isEqualTo("text/xml");
      assertThat(datei.getSize()).isEqualTo(CII.length);
      assertThat(datei.getModDate().toInstant()).isEqualTo(JETZT);
      assertThat(datei.toByteArray()).isEqualTo(CII);
      assertThat(beschreibung.getEmbeddedFileUnicode().toByteArray()).isEqualTo(CII);
    }
  }

  @Test
  void druckeERechnung_thenTheCatalogListsTheXmlUnderAf() throws IOException {
    // When
    final byte[] pdf = drucker.druckeERechnung(EINE_SEITE, null, CII);

    // Then — /AF verweist auf dieselbe Dateibeschreibung wie der Namensbaum.
    try (PDDocument dokument = Loader.loadPDF(pdf)) {
      final PDDocumentCatalog katalog = dokument.getDocumentCatalog();
      final COSBase eintrag = katalog.getCOSObject().getDictionaryObject(COSName.AF);
      assertThat(eintrag).isInstanceOf(COSArray.class);
      final COSArray anlagen = (COSArray) eintrag;
      assertThat(anlagen.size()).isOne();
      assertThat(anlagen.getObject(0)).isInstanceOf(COSDictionary.class);
      assertThat(((COSDictionary) anlagen.getObject(0)).getString(COSName.F))
          .isEqualTo("factur-x.xml");
      assertThat(anlagen.getObject(0))
          .isSameAs(eingebettete(dokument).get("factur-x.xml").getCOSObject());
    }
  }

  @Test
  void druckeERechnung_thenTheTrailerCarriesADocumentId() throws IOException {
    // When
    final byte[] pdf = drucker.druckeERechnung(EINE_SEITE, null, CII);

    // Then — zwei Teile, keiner leer.
    try (PDDocument dokument = Loader.loadPDF(pdf)) {
      final COSArray kennung = dokument.getDocument().getDocumentID();
      assertThat(kennung).isNotNull();
      assertThat(kennung.size()).isEqualTo(2);
    }
  }

  @Test
  void druckeERechnung_thenThePagesAreTheSameAsWithDrucke() throws IOException {
    // Given — zwei Seiten auf dem Briefpapier, damit Untergrund und Folgeseite mitzaehlen.
    final byte[] papier = tauglichesBriefpapier();

    // When
    final byte[] erechnung = drucker.druckeERechnung(ZWEI_SEITEN, papier, CII);
    final byte[] gewoehnlich = drucker.drucke(ZWEI_SEITEN, papier);

    // Then — Seite fuer Seite derselbe Inhaltsstrom: Die Huelle ist nicht zu sehen.
    assertThat(seiten(erechnung)).isEqualTo(seiten(gewoehnlich)).isEqualTo(2);
    assertThat(inhaltsstrom(erechnung, 1)).isEqualTo(inhaltsstrom(gewoehnlich, 1));
    assertThat(inhaltsstrom(erechnung, 2)).isEqualTo(inhaltsstrom(gewoehnlich, 2));
    assertThat(ausgelesen(erechnung)).isEqualTo(ausgelesen(gewoehnlich));
  }

  @Test
  void drucke_thenNeitherPdfaIdNorAnEmbeddedFileNorAnOutputIntent() throws Exception {
    // When — der Weg des Probedrucks (#460, Kriterium 6).
    final byte[] pdf = drucker.drucke(EINE_SEITE, briefpapier("Briefkopf Blatt eins"));

    // Then
    assertThat(xmp(pdf).getPDFAIdentificationSchema()).isNull();
    try (PDDocument dokument = Loader.loadPDF(pdf)) {
      assertThat(eingebettete(dokument)).isEmpty();
      assertThat(dokument.getDocumentCatalog().getOutputIntents()).isEmpty();
      assertThat(dokument.getDocumentCatalog().getCOSObject().containsKey(COSName.AF)).isFalse();
    }
  }

  @Test
  void druckeERechnung_givenCiiThatIsNoXml_thenNothingIsWritten() {
    // Given
    final byte[] keinXml = "Rechnung R26-0003".getBytes(UTF_8);

    // When / Then — ohne lesbare Nummer entsteht kein Dokument; das Stellen rollt zurueck.
    assertThatThrownBy(() -> drucker.druckeERechnung(EINE_SEITE, null, keinXml))
        .isInstanceOf(UncheckedIOException.class)
        .hasRootCauseInstanceOf(SAXParseException.class);
  }

  @Test
  void druckeERechnung_givenCiiWithADoctype_thenItIsRefused() {
    // Given — eine externe Entitaet, die ein offener Parser nachladen wuerde (XXE).
    final byte[] mitDoctype =
        ("<?xml version=\"1.0\"?><!DOCTYPE x [<!ENTITY nummer SYSTEM \"file:///etc/hosts\">]>"
                + new String(CII, UTF_8).substring(new String(CII, UTF_8).indexOf("<rsm:")))
            .getBytes(UTF_8);

    // When / Then — der Parser weist die DOCTYPE-Deklaration ab, bevor er etwas nachlaedt.
    assertThatThrownBy(() -> drucker.druckeERechnung(EINE_SEITE, null, mitDoctype))
        .isInstanceOf(UncheckedIOException.class)
        .rootCause()
        .hasMessageContaining("DOCTYPE");
  }

  @Test
  void druckeERechnung_givenCiiWithoutAnXmlDeclaration_thenTheNummerIsStillRead() throws Exception {
    // Given — die Nummer steht tiefer, eine andere ID davor gehoert nicht zum Kopf.
    final byte[] cii =
        ("<rsm:CrossIndustryInvoice"
                + " xmlns:rsm=\"urn:un:unece:uncefact:data:standard:CrossIndustryInvoice:100\""
                + " xmlns:ram=\"urn:un:unece:uncefact:data:standard:"
                + "ReusableAggregateBusinessInformationEntity:100\">"
                + "<rsm:ExchangedDocumentContext><ram:GuidelineSpecifiedDocumentContextParameter>"
                + "<ram:ID>urn:cen.eu:en16931:2017</ram:ID>"
                + "</ram:GuidelineSpecifiedDocumentContextParameter></rsm:ExchangedDocumentContext>"
                + "<rsm:ExchangedDocument><ram:ID>2026-0042</ram:ID></rsm:ExchangedDocument>"
                + "</rsm:CrossIndustryInvoice>")
            .getBytes(UTF_8);

    // When / Then
    assertThat(xmp(drucker.druckeERechnung(EINE_SEITE, null, cii)).getDublinCoreSchema().getTitle())
        .isEqualTo("Rechnung 2026-0042");
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
