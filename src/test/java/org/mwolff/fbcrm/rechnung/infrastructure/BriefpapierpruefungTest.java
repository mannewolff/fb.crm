package org.mwolff.fbcrm.rechnung.infrastructure;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary;
import org.apache.pdfbox.pdmodel.PDEmbeddedFilesNameTreeNode;
import org.apache.pdfbox.pdmodel.PDJavascriptNameTreeNode;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionJavaScript;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.rechnung.application.BriefpapierNichtAnnehmbar;
import org.mwolff.fbcrm.rechnung.application.Briefpapierpruefer;

/**
 * Die Pruefung und Reinigung des Briefpapiers gegen die Bibliothek (Plan #363, E4 und E5).
 *
 * <p>Gegenstand sind die drei Abweisungen am Inhalt — nicht ladbar oder verschluesselt, mehr als
 * zwei Seiten, nicht A4 hochkant —, die Messung des Sichtfelds mit Toleranz und Drehung und die
 * Reinigung: Was zu sehen ist, steht nachher als Seiteninhalt im PDF, was handeln kann, ist fort.
 * Die Pruef-PDFs baut der Test selbst mit PDFBox; gelesen wird das Ergebnis mit demselben Werkzeug.
 */
class BriefpapierpruefungTest {

  /** A4 hochkant in ganzen Punkt, wie die Pruefung es misst. */
  private static final PDRectangle A4 = new PDRectangle(595, 842);

  /** A4 hochkant, wie Erzeuger es runden. */
  private static final PDRectangle A4_GERUNDET = new PDRectangle(595.276f, 841.89f);

  private static final PDRectangle A4_QUER = new PDRectangle(842, 595);

  private static final String FELDWERT = "Muster und Partner GmbH";

  private final Briefpapierpruefer pruefer = new PdfBoxBriefpapierpruefung();

  // --- Annahme und Seitenzahl ---

  @Test
  void pruefe_givenA4HochkantEinseitig_thenAngenommenMitEinerSeite() throws IOException {
    // When
    final Briefpapierpruefer.Gereinigt ergebnis = pruefer.pruefe(pdf(A4));

    // Then
    assertThat(ergebnis.seiten()).isEqualTo(1);
    assertThat(seiten(ergebnis.inhalt())).isEqualTo(1);
  }

  @Test
  void pruefe_givenA4HochkantZweiseitig_thenAngenommenMitZweiSeiten() throws IOException {
    // When
    final Briefpapierpruefer.Gereinigt ergebnis = pruefer.pruefe(pdf(A4, A4));

    // Then
    assertThat(ergebnis.seiten()).isEqualTo(2);
    assertThat(seiten(ergebnis.inhalt())).isEqualTo(2);
  }

  @Test
  void pruefe_givenGerundetesA4_thenAngenommen() throws IOException {
    // When / Then — 595,276 x 841,89 Punkt, wie Erzeuger A4 schreiben (E5).
    assertThat(pruefer.pruefe(pdf(A4_GERUNDET)).seiten()).isEqualTo(1);
  }

  @Test
  void pruefe_givenSichtbarerText_thenBleibtSichtbar() throws IOException {
    // Given
    final byte[] mitText;
    try (PDDocument dokument = new PDDocument()) {
      final PDPage seite = new PDPage(A4);
      dokument.addPage(seite);
      schreibe(dokument, seite, "Briefkopf Muster");
      mitText = gespeichert(dokument);
    }

    // When / Then
    assertThat(text(pruefer.pruefe(mitText).inhalt())).contains("Briefkopf Muster");
  }

  @Test
  void gereinigt_thenHandsOutACopyOfItsBytes() throws IOException {
    // Given
    final Briefpapierpruefer.Gereinigt ergebnis = pruefer.pruefe(pdf(A4));
    final byte[] erstes = ergebnis.inhalt();

    // When
    erstes[0] = 0;

    // Then — wer die Bytes veraendert, veraendert nicht das Ergebnis.
    assertThat(ergebnis.inhalt()[0]).isEqualTo((byte) '%');
  }

  @Test
  void gereinigt_thenKeepsItsOwnCopyOfTheGivenBytes() {
    // Given
    final byte[] bytes = "%PDF-".getBytes(US_ASCII);
    final Briefpapierpruefer.Gereinigt ergebnis = new Briefpapierpruefer.Gereinigt(bytes, 1);

    // When
    bytes[0] = 0;

    // Then
    assertThat(ergebnis.inhalt()).isEqualTo("%PDF-".getBytes(US_ASCII));
  }

  // --- Abweisungen (Kriterium 2, E5) ---

  @Test
  void pruefe_givenQuerformat_thenFormatAbgewiesen() throws IOException {
    // When / Then
    assertAbgewiesen(pdf(A4_QUER), BriefpapierNichtAnnehmbar.MELDUNG_FORMAT);
  }

  @Test
  void pruefe_givenZweiteSeiteQuer_thenFormatAbgewiesen() throws IOException {
    // When / Then — gemessen wird jede Seite, nicht nur die erste.
    assertAbgewiesen(pdf(A4, A4_QUER), BriefpapierNichtAnnehmbar.MELDUNG_FORMAT);
  }

  @Test
  void pruefe_givenDreiSeiten_thenZuVieleSeitenAbgewiesen() throws IOException {
    // When / Then
    assertAbgewiesen(pdf(A4, A4, A4), BriefpapierNichtAnnehmbar.MELDUNG_ZU_VIELE_SEITEN);
  }

  @Test
  void pruefe_givenDreiSeitenImQuerformat_thenSeitenzahlVorFormat() throws IOException {
    // When / Then — die Reihenfolge der Abweisungen (E5): erst die Seitenzahl, dann das Format.
    assertAbgewiesen(
        pdf(A4_QUER, A4_QUER, A4_QUER), BriefpapierNichtAnnehmbar.MELDUNG_ZU_VIELE_SEITEN);
  }

  @Test
  void pruefe_givenKeineSeite_thenNichtLadbarAbgewiesen() throws IOException {
    // Given
    final byte[] ohneSeite;
    try (PDDocument dokument = new PDDocument()) {
      ohneSeite = gespeichert(dokument);
    }

    // When / Then — ein Blatt ohne Blatt ist kein Briefpapier.
    assertAbgewiesen(ohneSeite, BriefpapierNichtAnnehmbar.MELDUNG_NICHT_LADBAR);
  }

  @Test
  void pruefe_givenVerschluesseltOhneBenutzerpasswort_thenNichtLadbarAbgewiesen()
      throws IOException {
    // When / Then — es laesst sich oeffnen, ist aber verschluesselt.
    assertAbgewiesen(verschluesselt(""), BriefpapierNichtAnnehmbar.MELDUNG_NICHT_LADBAR);
  }

  @Test
  void pruefe_givenVerschluesseltMitBenutzerpasswort_thenNichtLadbarAbgewiesen()
      throws IOException {
    // When / Then — es laesst sich nicht einmal oeffnen; derselbe Satz.
    assertAbgewiesen(verschluesselt("geheim"), BriefpapierNichtAnnehmbar.MELDUNG_NICHT_LADBAR);
  }

  @Test
  void pruefe_givenKaputteBytes_thenNichtLadbarAbgewiesen() {
    // Given — die Signatur stimmt, der Rest nicht.
    final byte[] kaputt =
        "%PDF-1.7\nkein Objekt, kein Katalog, kein xref\n%%EOF".getBytes(US_ASCII);

    // When / Then
    assertAbgewiesen(kaputt, BriefpapierNichtAnnehmbar.MELDUNG_NICHT_LADBAR);
  }

  // --- Sichtfeld: Drehung, CropBox, Toleranz (E5) ---

  @Test
  void pruefe_givenQuerseiteUm90Gedreht_thenAlsHochkantAngenommen() throws IOException {
    // When / Then
    assertThat(pruefer.pruefe(gedreht(A4_QUER, 90)).seiten()).isEqualTo(1);
  }

  @Test
  void pruefe_givenQuerseiteUm270Gedreht_thenAlsHochkantAngenommen() throws IOException {
    // When / Then
    assertThat(pruefer.pruefe(gedreht(A4_QUER, 270)).seiten()).isEqualTo(1);
  }

  @Test
  void pruefe_givenHochkantUm90Gedreht_thenFormatAbgewiesen() throws IOException {
    // When / Then — gedreht steht das Blatt quer.
    assertAbgewiesen(gedreht(A4, 90), BriefpapierNichtAnnehmbar.MELDUNG_FORMAT);
  }

  @Test
  void pruefe_givenHochkantUm180Gedreht_thenAngenommen() throws IOException {
    // When / Then — auf dem Kopf bleibt hochkant hochkant.
    assertThat(pruefer.pruefe(gedreht(A4, 180)).seiten()).isEqualTo(1);
  }

  @Test
  void pruefe_givenAbweichendeCropBox_thenFormatAbgewiesen() throws IOException {
    // When / Then — MediaBox A4, Sichtfeld A5: beide Kaesten muessen treffen.
    assertAbgewiesen(
        mitKaesten(A4, new PDRectangle(0, 0, 420, 595)), BriefpapierNichtAnnehmbar.MELDUNG_FORMAT);
  }

  @Test
  void pruefe_givenA4CropBoxAufGroessererMediaBox_thenFormatAbgewiesen() throws IOException {
    // When / Then — Sichtfeld A4, MediaBox A3: auch der zweite Kasten zaehlt.
    assertAbgewiesen(
        mitKaesten(new PDRectangle(842, 1191), new PDRectangle(0, 0, 595, 842)),
        BriefpapierNichtAnnehmbar.MELDUNG_FORMAT);
  }

  @Test
  void pruefe_givenGleicheCropBox_thenAngenommen() throws IOException {
    // When / Then
    assertThat(pruefer.pruefe(mitKaesten(A4, new PDRectangle(0, 0, 595, 842))).seiten())
        .isEqualTo(1);
  }

  @Test
  void pruefe_givenBreiteGeradeInnerhalbDerToleranz_thenAngenommen() throws IOException {
    // When / Then — 6 Punkt mehr und 6 Punkt weniger gehen noch.
    assertThat(pruefer.pruefe(pdf(new PDRectangle(601, 842))).seiten()).isEqualTo(1);
    assertThat(pruefer.pruefe(pdf(new PDRectangle(589, 842))).seiten()).isEqualTo(1);
  }

  @Test
  void pruefe_givenBreiteKnappJenseitsDerToleranz_thenFormatAbgewiesen() throws IOException {
    // When / Then
    assertAbgewiesen(pdf(new PDRectangle(601.5f, 842)), BriefpapierNichtAnnehmbar.MELDUNG_FORMAT);
    assertAbgewiesen(pdf(new PDRectangle(588.5f, 842)), BriefpapierNichtAnnehmbar.MELDUNG_FORMAT);
  }

  @Test
  void pruefe_givenHoeheGeradeInnerhalbDerToleranz_thenAngenommen() throws IOException {
    // When / Then
    assertThat(pruefer.pruefe(pdf(new PDRectangle(595, 848))).seiten()).isEqualTo(1);
    assertThat(pruefer.pruefe(pdf(new PDRectangle(595, 836))).seiten()).isEqualTo(1);
  }

  @Test
  void pruefe_givenHoeheKnappJenseitsDerToleranz_thenFormatAbgewiesen() throws IOException {
    // When / Then
    assertAbgewiesen(pdf(new PDRectangle(595, 848.5f)), BriefpapierNichtAnnehmbar.MELDUNG_FORMAT);
    assertAbgewiesen(pdf(new PDRectangle(595, 835.5f)), BriefpapierNichtAnnehmbar.MELDUNG_FORMAT);
  }

  // --- Reinigung (Kriterium 12, E4) ---

  @Test
  void pruefe_givenAusgefuelltesFormularfeld_thenWertBleibtAlsSeiteninhaltSichtbar()
      throws IOException {
    // Given — vorher steht der Wert nur im Feld, nicht im Seiteninhalt.
    final byte[] aktiv = mitAktivenInhalten();
    assertThat(text(aktiv)).doesNotContain(FELDWERT);

    // When
    final byte[] gereinigt = pruefer.pruefe(aktiv).inhalt();

    // Then
    assertThat(text(gereinigt)).contains(FELDWERT);
  }

  @Test
  void pruefe_givenAktiveInhalte_thenKeinFormularUndKeinXfaMehr() throws IOException {
    // Given
    final byte[] aktiv = mitAktivenInhalten();
    try (PDDocument vorher = Loader.loadPDF(aktiv)) {
      assertThat(vorher.getDocumentCatalog().getAcroForm(null).hasXFA()).isTrue();
    }

    // When
    final byte[] gereinigt = pruefer.pruefe(aktiv).inhalt();

    // Then — XFA steht nur im AcroForm; mit ihm ist es fort.
    try (PDDocument nachher = Loader.loadPDF(gereinigt)) {
      final PDDocumentCatalog katalog = nachher.getDocumentCatalog();
      assertThat(katalog.getCOSObject().containsKey(COSName.ACRO_FORM)).isFalse();
      assertThat(katalog.getAcroForm(null)).isNull();
    }
  }

  @Test
  void pruefe_givenAktiveInhalte_thenKeineAnnotationAufKeinerSeite() throws IOException {
    // Given
    final byte[] aktiv = mitAktivenInhalten();
    try (PDDocument vorher = Loader.loadPDF(aktiv)) {
      assertThat(vorher.getPage(0).getAnnotations()).isNotEmpty();
      assertThat(vorher.getPage(1).getAnnotations()).isNotEmpty();
    }

    // When
    final byte[] gereinigt = pruefer.pruefe(aktiv).inhalt();

    // Then
    try (PDDocument nachher = Loader.loadPDF(gereinigt)) {
      for (final PDPage seite : nachher.getPages()) {
        assertThat(seite.getCOSObject().containsKey(COSName.ANNOTS)).isFalse();
      }
    }
  }

  @Test
  void pruefe_givenAktiveInhalte_thenKeineDokumentOderSeitenaktionMehr() throws IOException {
    // Given
    final byte[] aktiv = mitAktivenInhalten();
    try (PDDocument vorher = Loader.loadPDF(aktiv)) {
      final COSDictionary katalog = vorher.getDocumentCatalog().getCOSObject();
      assertThat(katalog.containsKey(COSName.OPEN_ACTION)).isTrue();
      assertThat(katalog.containsKey(COSName.AA)).isTrue();
      assertThat(vorher.getPage(1).getCOSObject().containsKey(COSName.AA)).isTrue();
      assertThat(katalog.containsKey(COSName.OUTLINES)).isTrue();
    }

    // When
    final byte[] gereinigt = pruefer.pruefe(aktiv).inhalt();

    // Then
    try (PDDocument nachher = Loader.loadPDF(gereinigt)) {
      final COSDictionary katalog = nachher.getDocumentCatalog().getCOSObject();
      assertThat(katalog.containsKey(COSName.OPEN_ACTION)).isFalse();
      assertThat(katalog.containsKey(COSName.AA)).isFalse();
      assertThat(katalog.containsKey(COSName.OUTLINES)).isFalse();
      for (final PDPage seite : nachher.getPages()) {
        assertThat(seite.getCOSObject().containsKey(COSName.AA)).isFalse();
      }
    }
  }

  @Test
  void pruefe_givenAktiveInhalte_thenKeinJavaScriptUndKeineEingebetteteDateiMehr()
      throws IOException {
    // Given
    final byte[] aktiv = mitAktivenInhalten();
    try (PDDocument vorher = Loader.loadPDF(aktiv)) {
      final PDDocumentCatalog katalog = vorher.getDocumentCatalog();
      assertThat(katalog.getNames().getJavaScript()).isNotNull();
      assertThat(katalog.getNames().getEmbeddedFiles()).isNotNull();
      assertThat(katalog.getCOSObject().containsKey(COSName.AF)).isTrue();
    }

    // When
    final byte[] gereinigt = pruefer.pruefe(aktiv).inhalt();

    // Then
    try (PDDocument nachher = Loader.loadPDF(gereinigt)) {
      final PDDocumentCatalog katalog = nachher.getDocumentCatalog();
      final COSDictionary namen = katalog.getCOSObject().getCOSDictionary(COSName.NAMES);
      assertThat(namen).isNotNull();
      assertThat(namen.containsKey(COSName.JAVA_SCRIPT)).isFalse();
      assertThat(namen.containsKey(COSName.EMBEDDED_FILES)).isFalse();
      assertThat(katalog.getCOSObject().containsKey(COSName.AF)).isFalse();
    }
  }

  @Test
  void pruefe_givenAktiveInhalte_thenSeitenzahlBleibt() throws IOException {
    // When / Then
    assertThat(pruefer.pruefe(mitAktivenInhalten()).seiten()).isEqualTo(2);
  }

  @Test
  void pruefe_givenOhneNamensbaum_thenAngenommen() throws IOException {
    // Given — ein Katalog ohne /Names, nur mit einer Startaktion.
    final byte[] nurStartaktion;
    try (PDDocument dokument = new PDDocument()) {
      dokument.addPage(new PDPage(A4));
      dokument.getDocumentCatalog().setOpenAction(new PDActionJavaScript("app.alert(1)"));
      nurStartaktion = gespeichert(dokument);
    }

    // When
    final byte[] gereinigt = pruefer.pruefe(nurStartaktion).inhalt();

    // Then
    try (PDDocument nachher = Loader.loadPDF(gereinigt)) {
      final COSDictionary katalog = nachher.getDocumentCatalog().getCOSObject();
      assertThat(katalog.containsKey(COSName.OPEN_ACTION)).isFalse();
      assertThat(katalog.containsKey(COSName.NAMES)).isFalse();
    }
  }

  // --- Pruef-PDFs ---

  private void assertAbgewiesen(final byte[] pdf, final String meldung) {
    assertThatThrownBy(() -> pruefer.pruefe(pdf))
        .isInstanceOf(BriefpapierNichtAnnehmbar.class)
        .hasMessage(meldung);
  }

  /** Ein leeres PDF mit einer Seite je uebergebenem Format. */
  private static byte[] pdf(final PDRectangle... formate) throws IOException {
    try (PDDocument dokument = new PDDocument()) {
      for (final PDRectangle format : formate) {
        dokument.addPage(new PDPage(format));
      }
      return gespeichert(dokument);
    }
  }

  private static byte[] gedreht(final PDRectangle format, final int grad) throws IOException {
    try (PDDocument dokument = new PDDocument()) {
      final PDPage seite = new PDPage(format);
      seite.setRotation(grad);
      dokument.addPage(seite);
      return gespeichert(dokument);
    }
  }

  private static byte[] mitKaesten(final PDRectangle mediaBox, final PDRectangle cropBox)
      throws IOException {
    try (PDDocument dokument = new PDDocument()) {
      final PDPage seite = new PDPage(mediaBox);
      seite.setCropBox(cropBox);
      dokument.addPage(seite);
      return gespeichert(dokument);
    }
  }

  private static byte[] verschluesselt(final String benutzerpasswort) throws IOException {
    try (PDDocument dokument = new PDDocument()) {
      dokument.addPage(new PDPage(A4));
      final StandardProtectionPolicy schutz =
          new StandardProtectionPolicy("eigentuemer", benutzerpasswort, new AccessPermission());
      schutz.setEncryptionKeyLength(128);
      dokument.protect(schutz);
      return gespeichert(dokument);
    }
  }

  /**
   * Zwei A4-Seiten mit allem, was handeln kann: ein ausgefuelltes Textfeld samt XFA auf Seite 1,
   * ein Link und eine Seitenaktion auf Seite 2, Startaktion, Dokumentaktion, Lesezeichen mit
   * Skript, JavaScript-Namensbaum und eine eingebettete Datei, auch als zugeordnete Datei des
   * Dokuments.
   */
  private static byte[] mitAktivenInhalten() throws IOException {
    try (PDDocument dokument = new PDDocument()) {
      final PDPage erste = new PDPage(A4);
      final PDPage zweite = new PDPage(A4);
      dokument.addPage(erste);
      dokument.addPage(zweite);
      final PDDocumentCatalog katalog = dokument.getDocumentCatalog();

      fuelleFormular(dokument, erste);

      final PDAnnotationLink link = new PDAnnotationLink();
      link.setRectangle(new PDRectangle(50, 50, 200, 20));
      final PDActionURI ziel = new PDActionURI();
      ziel.setURI("https://example.org");
      link.setAction(ziel);
      zweite.getAnnotations().add(link);
      zweite.getActions().setO(new PDActionJavaScript("app.alert('Seite')"));

      katalog.setOpenAction(new PDActionJavaScript("app.alert('Start')"));
      katalog.getActions().setWC(new PDActionJavaScript("app.alert('Schliessen')"));

      final PDDocumentOutline lesezeichen = new PDDocumentOutline();
      final PDOutlineItem eintrag = new PDOutlineItem();
      eintrag.setTitle("Skript");
      eintrag.setAction(new PDActionJavaScript("app.alert('Lesezeichen')"));
      lesezeichen.addLast(eintrag);
      katalog.setDocumentOutline(lesezeichen);

      final PDDocumentNameDictionary namen = new PDDocumentNameDictionary(katalog);
      final PDJavascriptNameTreeNode skripte = new PDJavascriptNameTreeNode();
      skripte.setNames(Map.of("start", new PDActionJavaScript("app.alert('Namensbaum')")));
      namen.setJavascript(skripte);
      final PDComplexFileSpecification datei = eingebettet(dokument);
      final PDEmbeddedFilesNameTreeNode dateien = new PDEmbeddedFilesNameTreeNode();
      dateien.setNames(Map.of("beilage.txt", datei));
      namen.setEmbeddedFiles(dateien);
      katalog.setNames(namen);
      final COSArray zugeordnet = new COSArray();
      zugeordnet.add(datei);
      katalog.getCOSObject().setItem(COSName.AF, zugeordnet);

      return gespeichert(dokument);
    }
  }

  private static void fuelleFormular(final PDDocument dokument, final PDPage seite)
      throws IOException {
    final PDAcroForm formular = new PDAcroForm(dokument);
    dokument.getDocumentCatalog().setAcroForm(formular);
    final PDResources mittel = new PDResources();
    mittel.put(COSName.HELV, new PDType1Font(Standard14Fonts.FontName.HELVETICA));
    formular.setDefaultResources(mittel);
    formular.setDefaultAppearance("/Helv 12 Tf 0 g");

    final PDTextField feld = new PDTextField(formular);
    feld.setPartialName("absender");
    final PDAnnotationWidget widget = feld.getWidgets().getFirst();
    widget.setRectangle(new PDRectangle(50, 700, 300, 20));
    widget.setPage(seite);
    widget.setPrinted(true);
    seite.getAnnotations().add(widget);
    formular.getFields().add(feld);
    feld.setValue(FELDWERT);

    final COSStream xfa = dokument.getDocument().createCOSStream();
    try (OutputStream aus = xfa.createOutputStream()) {
      aus.write("<xdp:xdp xmlns:xdp=\"http://ns.adobe.com/xdp/\"/>".getBytes(US_ASCII));
    }
    formular.getCOSObject().setItem(COSName.XFA, xfa);
  }

  private static PDComplexFileSpecification eingebettet(final PDDocument dokument)
      throws IOException {
    final PDEmbeddedFile inhalt =
        new PDEmbeddedFile(dokument, new ByteArrayInputStream("Beilage".getBytes(US_ASCII)));
    final PDComplexFileSpecification datei = new PDComplexFileSpecification();
    datei.setFile("beilage.txt");
    datei.setEmbeddedFile(inhalt);
    return datei;
  }

  private static void schreibe(final PDDocument dokument, final PDPage seite, final String zeile)
      throws IOException {
    final PDFont schrift = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    try (PDPageContentStream strom = new PDPageContentStream(dokument, seite)) {
      strom.beginText();
      strom.setFont(schrift, 12);
      strom.newLineAtOffset(70, 760);
      strom.showText(zeile);
      strom.endText();
    }
  }

  private static byte[] gespeichert(final PDDocument dokument) throws IOException {
    final ByteArrayOutputStream ausgabe = new ByteArrayOutputStream();
    dokument.save(ausgabe);
    return ausgabe.toByteArray();
  }

  private static int seiten(final byte[] pdf) throws IOException {
    try (PDDocument dokument = Loader.loadPDF(pdf)) {
      return dokument.getNumberOfPages();
    }
  }

  private static String text(final byte[] pdf) throws IOException {
    try (PDDocument dokument = Loader.loadPDF(pdf)) {
      return new PDFTextStripper().getText(dokument);
    }
  }
}
