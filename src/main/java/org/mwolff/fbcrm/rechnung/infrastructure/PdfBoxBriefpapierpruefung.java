package org.mwolff.fbcrm.rechnung.infrastructure;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.mwolff.fbcrm.rechnung.application.BriefpapierNichtAnnehmbar;
import org.mwolff.fbcrm.rechnung.application.Briefpapierpruefer;
import org.springframework.stereotype.Component;

/**
 * Setzt den Port {@link Briefpapierpruefer} auf Apache PDFBox um (Plan #363, E4 und E5).
 *
 * <p><b>Laden mit Schranke.</b> Geladen wird ueber {@code Loader.loadPDF} auf den gelesenen Bytes,
 * mit einem Zwischenspeicher allein im Hauptspeicher und mit fester Obergrenze. Schlaegt das Parsen
 * fehl, bricht es ab oder ueberschreitet es die Grenze, gilt das PDF als nicht ladbar und wird mit
 * demselben Satz abgewiesen wie ein verschluesseltes — jede {@link IOException} zwischen Laden und
 * Schreiben endet dort (Sicherheit zuerst, CLAUDE.md).
 *
 * <p><b>Abweisungen in fester Reihenfolge:</b> nicht ladbar oder verschluesselt, keine Seite, mehr
 * als zwei Seiten, eine Seite nicht A4 hochkant. A4 hochkant heisst: Das Sichtfeld jeder Seite —
 * die CropBox, wo sie steht, sonst die MediaBox — misst nach Anwendung von {@code /Rotate} 595 x
 * 842 Punkt mit einer Toleranz von 6 Punkt (rund 2 mm), und <b>beide Kaesten muessen treffen</b>.
 * Der Untergrund des Belegs wird am Sichtfeld importiert ({@link Briefpapieruntergrund}); eine
 * abweichende CropBox legte das Blatt in falscher Groesse oder Lage unter die Rechnung. Toleranz
 * statt Gleichheit, weil Erzeuger A4 unterschiedlich runden (595,276 x 841,89 Punkt).
 *
 * <p><b>Gereinigt wird einmal, hier</b> (E4): Formularfelder werden flach gemacht, damit
 * ausgefuellte Werte als Seiteninhalt stehen bleiben; danach gehen AcroForm samt XFA, die
 * Anmerkungen und Aktionen jeder Seite, Start- und Dokumentaktionen, Lesezeichen, der Namensbaum
 * {@code JavaScript} und die eingebetteten Dateien. Was zu sehen ist, bleibt zu sehen; was handelt,
 * ist schon im Objektspeicher nicht mehr da.
 */
@Component
class PdfBoxBriefpapierpruefung implements Briefpapierpruefer {

  /** A4 hochkant, Breite in Punkt. */
  private static final float A4_BREITE = 595f;

  /** A4 hochkant, Hoehe in Punkt. */
  private static final float A4_HOEHE = 842f;

  /**
   * Rund 2 mm in Punkt — genug fuer jede Rundung eines Erzeugers, zu wenig fuer ein anderes Format.
   */
  private static final float TOLERANZ = 6f;

  /** Seite 1 fuer die erste Seite der Rechnung, Seite 2 fuer die Folgeseiten. */
  private static final int MAX_SEITEN = 2;

  /*
   * Die Schranke des Zwischenspeichers, den PDFBox fuer die beim Verarbeiten entstehenden Stroeme
   * anlegt. Ein Briefpapier hat hoechstens 5 MB; entpackte Bilder und Schriften sind ein Vielfaches
   * davon, aber kein beliebiges. 64 MB lassen jedes ernstgemeinte Briefpapier durch und halten eine
   * Datei auf, die sich beim Entpacken aufblaeht.
   */
  private static final long ZWISCHENSPEICHER_MAX_BYTE = 64L * 1024 * 1024;

  @Override
  public Gereinigt pruefe(final byte[] pdf) {
    try (PDDocument dokument =
        Loader.loadPDF(
            pdf,
            "",
            null,
            null,
            MemoryUsageSetting.setupMainMemoryOnly(ZWISCHENSPEICHER_MAX_BYTE).streamCache)) {
      final int seiten = geprueft(dokument);
      reinige(dokument);
      return new Gereinigt(gespeichert(dokument), seiten);
    } catch (final IOException nichtLadbar) {
      throw BriefpapierNichtAnnehmbar.nichtLadbar(nichtLadbar);
    }
  }

  /** Prueft das geladene Dokument und liefert seine Seitenzahl. */
  private static int geprueft(final PDDocument dokument) {
    final int seiten = dokument.getNumberOfPages();
    // Ein PDF ohne Seite ist kein Briefpapier; es laesst sich so wenig verwenden wie ein kaputtes.
    if (dokument.isEncrypted() || seiten == 0) {
      throw BriefpapierNichtAnnehmbar.nichtLadbar();
    }
    if (seiten > MAX_SEITEN) {
      throw BriefpapierNichtAnnehmbar.zuVieleSeiten();
    }
    for (final PDPage seite : dokument.getPages()) {
      if (!istA4Hochkant(seite)) {
        throw BriefpapierNichtAnnehmbar.falschesFormat();
      }
    }
    return seiten;
  }

  /*
   * getCropBox() liefert die CropBox auf die MediaBox beschnitten und ohne CropBox die MediaBox
   * selbst; getRotation() liefert die Drehung auf 0, 90, 180 oder 270 gebracht.
   */
  private static boolean istA4Hochkant(final PDPage seite) {
    final boolean gedreht = seite.getRotation() % 180 != 0;
    return misstA4Hochkant(seite.getMediaBox(), gedreht)
        && misstA4Hochkant(seite.getCropBox(), gedreht);
  }

  private static boolean misstA4Hochkant(final PDRectangle kasten, final boolean gedreht) {
    final float breite = gedreht ? kasten.getHeight() : kasten.getWidth();
    final float hoehe = gedreht ? kasten.getWidth() : kasten.getHeight();
    return Math.abs(breite - A4_BREITE) <= TOLERANZ && Math.abs(hoehe - A4_HOEHE) <= TOLERANZ;
  }

  /*
   * Erst flach machen, dann entfernen: flatten() schreibt die Erscheinung jedes Feldes in den
   * Seiteninhalt und nimmt die Widgets von der Seite. Das XFA steht im AcroForm-Verzeichnis und geht
   * mit ihm.
   */
  private static void reinige(final PDDocument dokument) throws IOException {
    final PDAcroForm formular = dokument.getDocumentCatalog().getAcroForm();
    if (formular != null) {
      formular.flatten();
    }
    final COSDictionary katalog = dokument.getDocumentCatalog().getCOSObject();
    katalog.removeItem(COSName.ACRO_FORM);
    katalog.removeItem(COSName.OPEN_ACTION);
    katalog.removeItem(COSName.AA);
    katalog.removeItem(COSName.OUTLINES);
    katalog.removeItem(COSName.AF);
    final COSDictionary namen = katalog.getCOSDictionary(COSName.NAMES);
    if (namen != null) {
      namen.removeItem(COSName.JAVA_SCRIPT);
      namen.removeItem(COSName.EMBEDDED_FILES);
    }
    for (final PDPage seite : dokument.getPages()) {
      seite.getCOSObject().removeItem(COSName.ANNOTS);
      seite.getCOSObject().removeItem(COSName.AA);
    }
  }

  private static byte[] gespeichert(final PDDocument dokument) throws IOException {
    final ByteArrayOutputStream ausgabe = new ByteArrayOutputStream();
    dokument.save(ausgabe);
    return ausgabe.toByteArray();
  }
}
