package org.mwolff.fbcrm.rechnung.infrastructure;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Comparator;
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
import org.mwolff.fbcrm.rechnung.application.Druckelement;
import org.springframework.stereotype.Component;

/**
 * Setzt den Port {@link Belegdrucker} auf Apache PDFBox um (E10).
 *
 * <p>Absichtlich duenn: Der Drucker entscheidet nichts ueber den Beleg, er schreibt die Elemente
 * weg, die der Satz gerechnet hat. Was hier stehen bleibt, ist das Dokument — Schriften einmal
 * laden, Seiten anlegen, die Elemente einer Seite in ihrer Tiefe ordnen. Gezeichnet wird von {@link
 * Seitenzeichner}.
 *
 * <p>Geschrieben wird mit Helvetica aus den 14 Standardschriften: keine Schriftdatei im Abbild,
 * kein Einbetten, und die Umlaute und das Eurozeichen des deutschen Belegs deckt ihre
 * WinAnsi-Kodierung ab (siehe {@link WinAnsiText}). Die Gestaltung im eigenen Erscheinungsbild ist
 * Nicht-Ziel der fachlichen Quelle.
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
  public byte[] drucke(final List<Druckelement> elemente) {
    try {
      return geschrieben(elemente);
    } catch (final IOException nichtErreichbar) {
      throw new UncheckedIOException("Das Beleg-PDF liess sich nicht schreiben.", nichtErreichbar);
    }
  }

  private static byte[] geschrieben(final List<Druckelement> elemente) throws IOException {
    try (PDDocument dokument = new PDDocument()) {
      // Einmal je Dokument und nicht je Element: Der erste Zugriff auf eine Standardschrift baut
      // den Schriftzwischenspeicher von PDFBox auf, und das dauert.
      final PDFont normal = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
      final PDFont fett = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
      final int seiten = elemente.stream().mapToInt(Druckelement::seite).max().orElse(1);
      for (int seite = 1; seite <= seiten; seite++) {
        schreibeSeite(dokument, elementeDerSeite(elemente, seite), normal, fett);
      }
      final ByteArrayOutputStream ausgabe = new ByteArrayOutputStream();
      dokument.save(ausgabe);
      return ausgabe.toByteArray();
    }
  }

  /**
   * Die Elemente der Seite in der Tiefe, in der sie gezeichnet werden: erst die Flaechen, dann die
   * Linien, dann der Text. Andersherum deckte eine Flaeche zu, was unter ihr liegt. Innerhalb einer
   * Art bleibt die Reihenfolge des Satzes erhalten, weil {@code sorted} stabil ist.
   */
  private static List<Druckelement> elementeDerSeite(
      final List<Druckelement> elemente, final int seite) {
    return elemente.stream()
        .filter(element -> element.seite() == seite)
        .sorted(Comparator.comparingInt(PdfBoxDrucker::tiefe))
        .toList();
  }

  private static int tiefe(final Druckelement element) {
    return switch (element) {
      case Druckelement.Flaeche _ -> 0;
      case Druckelement.Linie _ -> 1;
      case Druckelement.Text _ -> 2;
    };
  }

  private static void schreibeSeite(
      final PDDocument dokument,
      final List<Druckelement> elemente,
      final PDFont normal,
      final PDFont fett)
      throws IOException {
    final PDPage blatt = new PDPage(PDRectangle.A4);
    dokument.addPage(blatt);
    try (PDPageContentStream inhalt = new PDPageContentStream(dokument, blatt)) {
      final Seitenzeichner zeichner = new Seitenzeichner(inhalt, normal, fett);
      for (final Druckelement element : elemente) {
        zeichner.zeichne(element);
      }
    }
  }
}
