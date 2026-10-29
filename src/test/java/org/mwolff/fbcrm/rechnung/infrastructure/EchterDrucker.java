package org.mwolff.fbcrm.rechnung.infrastructure;

import java.time.Clock;
import org.mwolff.fbcrm.rechnung.application.Belegdrucker;

/**
 * Der echte {@link PdfBoxDrucker} fuer Tests der Anwendungsschicht (Plan #363, Issue #374).
 *
 * <p>Der Adapter bleibt paketprivat: Kein Anwendungsfall soll ihn beim Namen nennen. Ein Test, der
 * am fertigen PDF ablesen muss, welches Blatt des Briefpapiers unter welcher Seite liegt, holt ihn
 * sich hier — ueber den Port, nicht ueber die Klasse.
 */
public final class EchterDrucker {

  private EchterDrucker() {}

  /**
   * Ein frischer Drucker.
   *
   * @return der PDFBox-Drucker hinter seinem Port
   */
  public static Belegdrucker pdfBox() {
    return new PdfBoxDrucker(Clock.systemUTC());
  }
}
