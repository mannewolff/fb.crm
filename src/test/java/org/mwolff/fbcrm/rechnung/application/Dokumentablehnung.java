package org.mwolff.fbcrm.rechnung.application;

/**
 * Ein Zugang zu {@link DokumentNichtAnnehmbar} fuer die Tests der Web-Schicht (Issue #268).
 *
 * <p>Die Ausnahme entsteht nur ueber ihre paketinternen Fabriken, weil allein der Anwendungsfall
 * entscheidet, wann eine Datei nicht geht. Der Slice-Test des Controllers liegt in einem anderen
 * Paket und braucht sie trotzdem, um die Abbildung auf 422 mit dem Feld {@code datei} zu zeigen;
 * diese Klasse oeffnet dafuer genau eine Fabrik, und nur im Testbaum.
 */
public final class Dokumentablehnung {

  private Dokumentablehnung() {}

  /** Die Ablehnung einer Datei, die kein PDF ist. */
  public static DokumentNichtAnnehmbar keinPdf() {
    return DokumentNichtAnnehmbar.keinPdf();
  }
}
