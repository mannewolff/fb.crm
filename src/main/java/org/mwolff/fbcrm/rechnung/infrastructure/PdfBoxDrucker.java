package org.mwolff.fbcrm.rechnung.infrastructure;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.mwolff.fbcrm.common.ExcludeFromJacocoGeneratedReport;
import org.mwolff.fbcrm.rechnung.application.Belegdrucker;
import org.mwolff.fbcrm.rechnung.application.Druckzeile;
import org.mwolff.fbcrm.rechnung.application.Schrift;
import org.springframework.stereotype.Component;

/**
 * Setzt den Port {@link Belegdrucker} auf Apache PDFBox um (E10).
 *
 * <p>Absichtlich duenn: Der Drucker entscheidet nichts ueber den Beleg, er schreibt die Zeilen weg,
 * die der Satz gerechnet hat. Was hier stehen bleibt, ist der Umgang mit der Bibliothek — Seiten
 * anlegen, Schrift setzen, Text ausgeben.
 *
 * <p>Geschrieben wird mit Helvetica aus den 14 Standardschriften: keine Schriftdatei im Abbild,
 * kein Einbetten, und die Umlaute und das Eurozeichen des deutschen Belegs deckt ihre
 * WinAnsi-Kodierung ab. Die Gestaltung im eigenen Erscheinungsbild ist Nicht-Ziel der fachlichen
 * Quelle.
 */
@Component
class PdfBoxDrucker implements Belegdrucker {

  /*
   * Ausgenommen ist allein dieser Rahmen, nicht die Arbeit darin: Der IOException-Zweig ist mit
   * einem ByteArrayOutputStream nicht erreichbar — dort scheitert kein Schreiben —, und ein
   * Test dafuer muesste die Bibliothek verbiegen. Methodengenaue Ausnahme nach CLAUDE-java.md §5.4;
   * geschrieben() bleibt in Abdeckung und Mutationstest, geprueft von PdfBoxDruckerTest.
   */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public byte[] drucke(final List<Druckzeile> zeilen) {
    try {
      return geschrieben(zeilen);
    } catch (final IOException nichtErreichbar) {
      throw new UncheckedIOException("Das Beleg-PDF liess sich nicht schreiben.", nichtErreichbar);
    }
  }

  private static byte[] geschrieben(final List<Druckzeile> zeilen) throws IOException {
    try (PDDocument dokument = new PDDocument()) {
      // Einmal je Dokument und nicht je Zeile: Der erste Zugriff auf eine Standardschrift baut den
      // Schriftzwischenspeicher von PDFBox auf, und das dauert.
      final PDFont normal = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
      final PDFont fett = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
      final int seiten = zeilen.stream().mapToInt(Druckzeile::seite).max().orElse(1);
      for (int seite = 1; seite <= seiten; seite++) {
        schreibeSeite(dokument, zeilenDerSeite(zeilen, seite), normal, fett);
      }
      final ByteArrayOutputStream ausgabe = new ByteArrayOutputStream();
      dokument.save(ausgabe);
      return ausgabe.toByteArray();
    }
  }

  private static List<Druckzeile> zeilenDerSeite(final List<Druckzeile> zeilen, final int seite) {
    return zeilen.stream().filter(zeile -> zeile.seite() == seite).toList();
  }

  private static void schreibeSeite(
      final PDDocument dokument,
      final List<Druckzeile> zeilen,
      final PDFont normal,
      final PDFont fett)
      throws IOException {
    final PDPage blatt = new PDPage(PDRectangle.A4);
    dokument.addPage(blatt);
    try (PDPageContentStream inhalt = new PDPageContentStream(dokument, blatt)) {
      for (final Druckzeile zeile : zeilen) {
        inhalt.beginText();
        inhalt.setFont(zeile.schrift() == Schrift.FETT ? fett : normal, zeile.groesse());
        inhalt.newLineAtOffset(zeile.x(), zeile.y());
        inhalt.showText(zeile.text());
        inhalt.endText();
      }
    }
  }
}
