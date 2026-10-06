package org.mwolff.fbcrm.rechnung.application;

import java.util.List;
import java.util.Map;
import org.mwolff.fbcrm.common.Feldfehler;
import org.mwolff.fbcrm.common.Uploadgrenze;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Das hochgeladene Original einer nachgetragenen Rechnung wird nicht angenommen (#254, Kriterium 2;
 * Plan #259, E15).
 *
 * <p><b>Eine Ausnahme fuer drei Gruende</b> — leer, zu gross, kein PDF —, weil alle drei dasselbe
 * an derselben Stelle mit demselben Statuscode sagen: Die Datei am Feld {@code datei} geht nicht.
 * Drei Klassen nach dem Muster der Anlagen am Angebot waeren Form ohne Unterschied. Welcher Grund
 * vorliegt, sagt allein der Satz; darum entsteht die Meldung zur Laufzeit und steht nicht in {@code
 * reason}.
 *
 * <p>422 wie die uebrigen Feldfehler dieses Projekts (E23): Der Rumpf ist wohlgeformt, abgewiesen
 * wird er fachlich.
 */
@ResponseStatus(code = HttpStatus.UNPROCESSABLE_ENTITY)
public final class DokumentNichtAnnehmbar extends Feldfehler {

  /** Das Feld, unter dem Maske und Schnittstelle das Original fuehren. */
  public static final String FELD = "datei";

  /** Der Inhalt ist kein PDF — erkannt an den ersten Bytes, nicht am Namen (E12). */
  public static final String MELDUNG_KEIN_PDF = "Die Datei ist kein PDF.";

  /** Die Datei traegt kein einziges Byte. */
  public static final String MELDUNG_LEER = "Die Datei ist leer.";

  /** Die Datei ueberschreitet die eine Grenze fuer hochgeladene Dateien. */
  public static final String MELDUNG_ZU_GROSS = Uploadgrenze.MELDUNG;

  private DokumentNichtAnnehmbar(final String meldung) {
    super(meldung);
  }

  /** Der Inhalt ist kein PDF. */
  static DokumentNichtAnnehmbar keinPdf() {
    return new DokumentNichtAnnehmbar(MELDUNG_KEIN_PDF);
  }

  /** Die Datei ist leer. */
  static DokumentNichtAnnehmbar leer() {
    return new DokumentNichtAnnehmbar(MELDUNG_LEER);
  }

  /** Die Datei ist groesser als {@link Uploadgrenze#MAX_BYTE}. */
  static DokumentNichtAnnehmbar zuGross() {
    return new DokumentNichtAnnehmbar(MELDUNG_ZU_GROSS);
  }

  @Override
  public Map<String, List<String>> felder() {
    return Map.of(FELD, List.of(getMessage()));
  }
}
