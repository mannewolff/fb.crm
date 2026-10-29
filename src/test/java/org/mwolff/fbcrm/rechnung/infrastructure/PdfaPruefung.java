package org.mwolff.fbcrm.rechnung.infrastructure;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import org.verapdf.core.VeraPDFException;
import org.verapdf.gf.foundry.VeraGreenfieldFoundryProvider;
import org.verapdf.pdfa.Foundries;
import org.verapdf.pdfa.PDFAParser;
import org.verapdf.pdfa.PDFAValidator;
import org.verapdf.pdfa.flavours.PDFAFlavour;
import org.verapdf.pdfa.results.TestAssertion;
import org.verapdf.pdfa.results.ValidationResult;

/**
 * Die Pruefung auf PDF/A-3B mit veraPDF, als Testhilfe (Plan #461, E4, E20).
 *
 * <p>veraPDF und nicht PDFBox Preflight: Preflight kennt nur PDF/A-1b, die E-Rechnung ist PDF/A-3b.
 * Geprueft wird fest gegen das Profil PDF/A-3B, nicht gegen das, was das Dokument von sich
 * behauptet — ein Dokument ohne {@code pdfaid} besteht darum nicht.
 *
 * <p>Ein Verstoss ist jede gescheiterte Pruefung des Profils, als „Klausel-Test: Text". Eine
 * Pruefung, die nicht zu Ende kam, ist ebenfalls ein Verstoss: Was nicht geprueft wurde, darf nicht
 * als bestanden gelten.
 */
public final class PdfaPruefung {

  private static final PDFAFlavour PDFA_3B = PDFAFlavour.PDFA_3_B;

  static {
    VeraGreenfieldFoundryProvider.initialise();
  }

  private PdfaPruefung() {}

  /**
   * Die Verstoesse eines Dokuments gegen PDF/A-3B.
   *
   * @param pdf das Dokument als Bytefolge
   * @return je gescheiterter Pruefung eine Zeile; leer, wenn das Dokument PDF/A-3B ist
   */
  public static List<String> verstoesse(final byte[] pdf) {
    try (PDFAParser leser =
            Foundries.defaultInstance().createParser(new ByteArrayInputStream(pdf), PDFA_3B);
        PDFAValidator pruefer = Foundries.defaultInstance().createValidator(PDFA_3B, false)) {
      final ValidationResult ergebnis = pruefer.validate(leser);
      final List<String> verstoesse =
          ergebnis.getTestAssertions().stream()
              .filter(pruefung -> pruefung.getStatus() == TestAssertion.Status.FAILED)
              .map(
                  pruefung ->
                      pruefung.getRuleId().getClause()
                          + "-"
                          + pruefung.getRuleId().getTestNumber()
                          + ": "
                          + pruefung.getMessage())
              .distinct()
              .toList();
      if (verstoesse.isEmpty() && !ergebnis.isCompliant()) {
        return List.of("nicht konform ohne Meldung: " + ergebnis.getJobEndStatus());
      }
      return verstoesse;
    } catch (final VeraPDFException | IOException unpruefbar) {
      throw new IllegalStateException("Das Dokument liess sich nicht pruefen.", unpruefbar);
    }
  }
}
