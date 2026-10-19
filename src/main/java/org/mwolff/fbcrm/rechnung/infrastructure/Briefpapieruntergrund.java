package org.mwolff.fbcrm.rechnung.infrastructure;

import java.io.IOException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.multipdf.LayerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.jspecify.annotations.Nullable;

/**
 * Die Blaetter des hinterlegten Briefpapiers als Formulare im Beleg (Plan #363, E3).
 *
 * <p>Seite 1 des Briefpapiers und, falls vorhanden, Seite 2 werden einmal je Beleg als Form-XObject
 * in das Zieldokument uebernommen; jede Seite des Belegs zeichnet danach nur noch einen Verweis
 * darauf. Seite 1 liegt unter Seite 1, Seite 2 unter jeder Folgeseite. Ohne Seite 2 bleibt die
 * Folgeseite ohne Unterlage, ohne Briefpapier jede Seite.
 *
 * <p>Uebernommen wird nur, was auf dem Blatt zu sehen ist: {@code importPageAsForm} nimmt den
 * Inhaltsstrom und die Ressourcen, nicht die Anmerkungen der Seite.
 */
final class Briefpapieruntergrund {

  private final @Nullable PDFormXObject ersteSeite;

  private final @Nullable PDFormXObject folgeseite;

  private Briefpapieruntergrund(
      final @Nullable PDFormXObject ersteSeite, final @Nullable PDFormXObject folgeseite) {
    this.ersteSeite = ersteSeite;
    this.folgeseite = folgeseite;
  }

  /**
   * Oeffnet das hinterlegte Briefpapier.
   *
   * @param briefpapier das Briefpapier als PDF oder {@code null}
   * @return das geoeffnete Dokument, das der Aufrufer schliesst, oder {@code null} ohne Briefpapier
   * @throws IOException wenn die Bytefolge kein lesbares PDF ist
   */
  static @Nullable PDDocument geoeffnet(final byte @Nullable [] briefpapier) throws IOException {
    return briefpapier == null ? null : Loader.loadPDF(briefpapier);
  }

  /**
   * Uebernimmt die Blaetter des Briefpapiers in den Beleg.
   *
   * @param beleg das Dokument, in das gedruckt wird
   * @param briefpapier das geoeffnete Briefpapier; es muss offen bleiben, bis der Beleg geschrieben
   *     ist, oder {@code null} fuer einen Beleg ohne Untergrund
   * @return die Unterlagen je Seite
   * @throws IOException wenn ein Blatt des Briefpapiers nicht zu lesen ist
   */
  static Briefpapieruntergrund aus(final PDDocument beleg, final @Nullable PDDocument briefpapier)
      throws IOException {
    if (briefpapier == null) {
      return new Briefpapieruntergrund(null, null);
    }
    final LayerUtility ebenen = new LayerUtility(beleg);
    final PDFormXObject erste = ebenen.importPageAsForm(briefpapier, 0);
    final PDFormXObject zweite =
        briefpapier.getNumberOfPages() > 1 ? ebenen.importPageAsForm(briefpapier, 1) : null;
    return new Briefpapieruntergrund(erste, zweite);
  }

  /**
   * Die Unterlage einer Seite des Belegs.
   *
   * @param seite Nummer der Seite, ab 1
   * @return das Blatt, das unter dieser Seite liegt, oder {@code null}, wenn sie ohne Unterlage
   *     bleibt
   */
  @Nullable PDFormXObject fuer(final int seite) {
    return seite == 1 ? ersteSeite : folgeseite;
  }
}
