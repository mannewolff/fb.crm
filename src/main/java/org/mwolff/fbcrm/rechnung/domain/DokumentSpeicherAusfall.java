package org.mwolff.fbcrm.rechnung.domain;

/**
 * Der Objektspeicher der Belege war nicht zu erreichen oder hat den Zugriff abgewiesen.
 *
 * <p><b>Warum der Port einen eigenen Namen fuer seinen Ausfall braucht.</b> Beim Loeschen einer
 * nachgetragenen Rechnung oder beim Ersetzen ihres Originals soll ein Fehlschlag des
 * Objektspeichers protokolliert und <b>nicht</b> gemeldet werden (Plan #259, E14) — die Zeile ist
 * zu diesem Zeitpunkt fort oder zeigt auf ein anderes Objekt, und ein Objekt ohne Zeile ist
 * unerreichbar und schadet nicht. Um genau diesen Fehlschlag zu fangen, braucht die
 * Anwendungsschicht einen Typ, den sie benennen kann: Die Ausnahmen des AWS-SDK darf sie nicht
 * kennen (CLAUDE-java.md §6.1), und {@code catch (RuntimeException)} verbietet der Regelsatz
 * (Checkstyle {@code IllegalCatch}, PMD {@code AvoidCatchingGenericException}) mit gutem Grund: Er
 * verschluckte auch Programmierfehler.
 *
 * <p>Ungeprueft und ohne {@code @ResponseStatus}: Ein ausgefallener Speicher ist kein fachlicher
 * Fall, auf den ein Aufrufer antworten koennte. Wo er nicht ausdruecklich gefangen wird, endet er
 * als generischer 500 — so wie jeder unerwartete Fehler (CLAUDE-java.md §6.3).
 */
public final class DokumentSpeicherAusfall extends RuntimeException {

  /**
   * @param meldung was schiefging, ohne Dateinamen und ohne Inhalte
   * @param ursache die Ausnahme des Speicher-SDK
   */
  public DokumentSpeicherAusfall(final String meldung, final Throwable ursache) {
    super(meldung, ursache);
  }
}
