package org.mwolff.fbcrm.rechnung.domain;

/**
 * Port auf den Objektspeicher des Briefpapiers; die Umsetzung liegt in {@code
 * rechnung.infrastructure} und spricht MinIO (Spezifikation R10, Plan #363, E6).
 *
 * <p>Ein eigener Port neben {@link DokumentSpeicher}: Das Briefpapier ist kein Beleg und gehoert zu
 * keiner Rechnung. Den {@code S3Client} teilt er sich mit den Belegen ({@code config.S3Config}).
 *
 * <p><b>Zusage:</b> Der Schluessel hat die Form {@code briefpapier/<uuid>.pdf} und wird im Adapter
 * gebildet — der Schluesselraum gehoert ihm, damit kein Aufrufer die Ablagestruktur in die
 * Anwendungsschicht zieht. Er trennt das Briefpapier von den Rechnungsbelegen ({@code
 * rechnung/<rechnungId>/<uuid>.pdf}) und von den Originalen nachgetragener Rechnungen ({@code
 * rechnung-nachtrag/<nachtragId>/<uuid>.pdf}). Jede Ablage bekommt ihren eigenen Schluessel; ein
 * ersetztes Briefpapier ueberschreibt das vorige also nicht.
 *
 * <p><b>Zusage:</b> Das Loeschen eines unbekannten Schluessels ist kein Fehler. Darauf stuetzt sich
 * das Entfernen des alten Objekts nach dem Commit.
 *
 * <p>Jeder Ausfall des Speichers kommt als {@link DokumentSpeicherAusfall} heraus, damit die
 * Anwendungsschicht keine Klasse des Speicher-SDK kennt (CLAUDE-java.md §6.1).
 */
public interface Briefpapierspeicher {

  /**
   * Legt ein Briefpapier ab und liefert den Schluessel, unter dem es wiederzufinden ist.
   *
   * @param inhalt das vollstaendige, gereinigte PDF
   * @return der Schluessel des abgelegten Objekts, {@code briefpapier/<uuid>.pdf}
   * @throws DokumentSpeicherAusfall wenn der Speicher die Ablage nicht annimmt
   */
  String lege(byte[] inhalt);

  /**
   * Das Briefpapier zu einem Schluessel.
   *
   * <p>Ein unbekannter Schluessel ist ein Widerspruch im Bestand — den Schluessel traegt der
   * Briefbogen selbst. Er kommt deshalb als Ausfall heraus und nicht als leere Bytes; ein leeres
   * PDF sahe wie ein gueltiges Briefpapier aus.
   *
   * @param schluessel der Schluessel aus {@link #lege}
   * @return das abgelegte Briefpapier, Byte fuer Byte
   * @throws DokumentSpeicherAusfall wenn der Speicher das Objekt nicht liefert
   */
  byte[] lies(String schluessel);

  /**
   * Entfernt das Objekt zu einem Schluessel; ein unbekannter Schluessel ist kein Fehler.
   *
   * @param schluessel der Schluessel aus {@link #lege}
   * @throws DokumentSpeicherAusfall wenn der Speicher das Loeschen abweist oder nicht antwortet
   */
  void loesche(String schluessel);
}
