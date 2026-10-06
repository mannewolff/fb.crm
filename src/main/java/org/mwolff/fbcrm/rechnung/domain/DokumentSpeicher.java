package org.mwolff.fbcrm.rechnung.domain;

/**
 * Port auf den Objektspeicher der archivierten Belege; die Umsetzung liegt in {@code
 * rechnung.infrastructure} und spricht MinIO (Spezifikation R10, E9).
 *
 * <p>Den {@code S3Client} baut {@code config.S3Config} (E9); jeder weitere Beleg teilt sich diesen
 * Zugang.
 *
 * <p>Der Speicher kennt nur Schluessel und Bytes. Wem ein Beleg gehoert und wann er entstand, steht
 * in der Datenbank.
 */
public interface DokumentSpeicher {

  /**
   * Legt ein Belegdokument ab und liefert den Schluessel, unter dem es wiederzufinden ist.
   *
   * <p><b>Zusage:</b> Der Schluessel hat die Form {@code rechnung/<rechnungId>/<uuid>.pdf} und wird
   * im Adapter gebildet — der Schluesselraum gehoert ihm, damit kein Aufrufer die Ablagestruktur in
   * die Anwendungsschicht zieht (E9). Jede Ablage bekommt ihren eigenen Schluessel; eine zweite
   * Ablage zur selben Rechnung ueberschreibt die erste nicht.
   *
   * @param rechnungId Kennung der Rechnung, zu der der Beleg gehoert
   * @param inhalt das vollstaendige Dokument
   * @return der Schluessel des abgelegten Objekts
   */
  String lege(long rechnungId, byte[] inhalt);

  /**
   * Das Dokument zu einem Schluessel.
   *
   * <p>Ein unbekannter Schluessel ist hier kein erwarteter Fall, sondern ein Widerspruch im
   * Bestand: Den Schluessel traegt die gestellte Rechnung selbst. Der Adapter gibt den Fehler des
   * Speichers deshalb weiter, statt leere Bytes zu liefern; ein leeres PDF sahe wie ein gueltiger
   * Beleg aus.
   *
   * @param schluessel der Schluessel aus {@link #lege}
   * @return das abgelegte Dokument, Byte fuer Byte
   */
  byte[] lies(String schluessel);

  /**
   * Legt das hochgeladene Original einer nachgetragenen Rechnung ab und liefert den Schluessel,
   * unter dem es wiederzufinden ist (Plan #259, E13).
   *
   * <p><b>Zusage:</b> Der Schluessel hat die Form {@code rechnung-nachtrag/<nachtragId>/<uuid>.pdf}
   * und wird im Adapter gebildet, wie bei {@link #lege}. Der eigene Schluesselraum trennt die
   * Originale, die von aussen kommen, von den Belegen, die fb.crm selbst erzeugt; jede Ablage
   * bekommt ihren eigenen Schluessel, ein ersetztes Original ueberschreibt das vorige also nicht.
   * Gelesen wird mit {@link #lies}.
   *
   * @param nachtragId Kennung der nachgetragenen Rechnung, zu der das Original gehoert
   * @param inhalt das vollstaendige Dokument
   * @return der Schluessel des abgelegten Objekts
   * @throws DokumentSpeicherAusfall wenn der Speicher die Ablage nicht annimmt
   */
  String legeHochgeladenes(long nachtragId, byte[] inhalt);

  /**
   * Entfernt das Objekt zu einem Schluessel.
   *
   * <p>Ein unbekannter Schluessel ist kein Fehler: Was nicht da ist, muss nicht entfernt werden.
   * Der Aufrufer ruft diese Methode erst nach dem Commit (Plan #259, E14) und darf ihren Ausfall
   * fangen, weil ein Objekt ohne Zeile unerreichbar ist und nicht schadet.
   *
   * @param schluessel der Schluessel aus {@link #legeHochgeladenes}
   * @throws DokumentSpeicherAusfall wenn der Speicher das Loeschen abweist oder nicht antwortet
   */
  void loesche(String schluessel);
}
