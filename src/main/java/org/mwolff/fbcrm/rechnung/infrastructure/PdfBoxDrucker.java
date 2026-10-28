package org.mwolff.fbcrm.rechnung.infrastructure;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.common.ExcludeFromJacocoGeneratedReport;
import org.mwolff.fbcrm.rechnung.application.Belegdrucker;
import org.mwolff.fbcrm.rechnung.application.Druckelement;
import org.springframework.stereotype.Component;

/**
 * Setzt den Port {@link Belegdrucker} auf Apache PDFBox um (E10).
 *
 * <p>Absichtlich duenn: Der Drucker entscheidet nichts ueber den Beleg, er schreibt die Elemente
 * weg, die der Satz gerechnet hat. Was hier stehen bleibt, ist das Dokument — Schriften einmal
 * laden, Seiten anlegen, den Untergrund unterlegen, die Elemente einer Seite in ihrer Tiefe ordnen.
 * Gezeichnet wird von {@link Seitenzeichner}.
 *
 * <p>Der Untergrund, das hinterlegte Briefpapier, kommt aus {@link Briefpapieruntergrund} und steht
 * als erstes im Inhaltsstrom jeder Seite (Plan #363, E3). So liegt er ohne weiteres Zutun unter
 * allem, was der Satz gerechnet hat, und der Beleg wird in einem Durchgang geschrieben.
 *
 * <p>Geschrieben wird mit Liberation Sans Regular und Bold, als Teilmenge eingebettet (Plan #461,
 * E2): PDF/A und damit die E-Rechnung verlangen eingebettete Schriften. Die Schrift ist metrisch
 * gleich zu Helvetica, deshalb bleiben Breiten und Umbrueche des Satzes, wie sie sind. Die Dateien
 * liegen mit ihrer Lizenz (SIL OFL 1.1) unter {@code beleg/}. Was die Schrift nicht schreiben soll,
 * ersetzt weiter {@link WinAnsiText}.
 */
@Component
class PdfBoxDrucker implements Belegdrucker {

  /** Der Lauftext des Belegs. */
  static final String SCHRIFT_NORMAL = "/beleg/LiberationSans-Regular.ttf";

  /** Der fette Schnitt fuer Hervorhebungen. */
  static final String SCHRIFT_FETT = "/beleg/LiberationSans-Bold.ttf";

  /*
   * Ausgenommen ist allein dieser Rahmen, nicht die Arbeit darin: Der IOException-Zweig ist mit
   * einem ByteArrayOutputStream nicht erreichbar — dort scheitert kein Schreiben —, und ein
   * Test dafuer muesste die Bibliothek verbiegen. Methodengenaue Ausnahme nach CLAUDE-java.md §5.4;
   * geschrieben() bleibt in Abdeckung und Mutationstest, geprueft von PdfBoxDruckerTest.
   */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public byte[] drucke(final List<Druckelement> elemente, final byte @Nullable [] briefpapier) {
    try {
      return geschrieben(elemente, briefpapier);
    } catch (final IOException nichtErreichbar) {
      throw new UncheckedIOException("Das Beleg-PDF liess sich nicht schreiben.", nichtErreichbar);
    }
  }

  private static byte[] geschrieben(
      final List<Druckelement> elemente, final byte @Nullable [] briefpapier) throws IOException {
    // Das Briefpapier bleibt offen, bis der Beleg geschrieben ist: Seine Blaetter sind als
    // Formulare in den Beleg uebernommen.
    try (PDDocument dokument = new PDDocument();
        @Nullable PDDocument vorlage = Briefpapieruntergrund.geoeffnet(briefpapier)) {
      final Briefpapieruntergrund untergrund = Briefpapieruntergrund.aus(dokument, vorlage);
      // Einmal je Dokument und nicht je Element: Eine eingebettete Schrift gehoert zu genau einem
      // Dokument, und eingebettet wird beim Speichern nur die Teilmenge, die der Beleg benutzt.
      final PDFont normal = geladen(dokument, SCHRIFT_NORMAL);
      final PDFont fett = geladen(dokument, SCHRIFT_FETT);
      final int seiten = elemente.stream().mapToInt(Druckelement::seite).max().orElse(1);
      for (int seite = 1; seite <= seiten; seite++) {
        schreibeSeite(
            dokument, untergrund.fuer(seite), elementeDerSeite(elemente, seite), normal, fett);
      }
      final ByteArrayOutputStream ausgabe = new ByteArrayOutputStream();
      dokument.save(ausgabe);
      return ausgabe.toByteArray();
    }
  }

  /**
   * Laedt eine Schrift aus den Ressourcen fuer dieses Dokument, als Teilmenge einzubetten.
   *
   * @param dokument das Dokument, in das die Schrift eingebettet wird
   * @param ressource der Pfad der Schriftdatei im Klassenpfad
   * @return die Schrift, gebunden an das Dokument
   * @throws IOException wenn die Schriftdatei nicht zu lesen ist
   */
  static PDFont geladen(final PDDocument dokument, final String ressource) throws IOException {
    final byte[] datei;
    try (InputStream strom =
        Objects.requireNonNull(PdfBoxDrucker.class.getResourceAsStream(ressource), ressource)) {
      datei = strom.readAllBytes();
    }
    return PDType0Font.load(dokument, new ByteArrayInputStream(datei), true);
  }

  /**
   * Die Elemente der Seite in der Tiefe, in der sie gezeichnet werden: erst die Flaechen, dann die
   * Linien, dann der Text. Andersherum deckte eine Flaeche zu, was unter ihr liegt. Zuoberst der
   * schraege Text: Das Muster eines Probedrucks soll auf jeder Seite durchgehend zu sehen sein.
   * Innerhalb einer Art bleibt die Reihenfolge des Satzes erhalten, weil {@code sorted} stabil ist.
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
      case Druckelement.Schraegtext _ -> 3;
    };
  }

  private static void schreibeSeite(
      final PDDocument dokument,
      final @Nullable PDFormXObject unterlage,
      final List<Druckelement> elemente,
      final PDFont normal,
      final PDFont fett)
      throws IOException {
    final PDPage blatt = new PDPage(PDRectangle.A4);
    dokument.addPage(blatt);
    try (PDPageContentStream inhalt = new PDPageContentStream(dokument, blatt)) {
      if (unterlage != null) {
        inhalt.drawForm(unterlage);
      }
      final Seitenzeichner zeichner = new Seitenzeichner(inhalt, normal, fett);
      for (final Druckelement element : elemente) {
        zeichner.zeichne(element);
      }
    }
  }
}
