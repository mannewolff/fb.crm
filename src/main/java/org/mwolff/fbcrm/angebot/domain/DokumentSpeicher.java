package org.mwolff.fbcrm.angebot.domain;

/**
 * Port auf den Objektspeicher der archivierten Belege; die Umsetzung liegt in {@code
 * angebot.infrastructure} und spricht MinIO (Spezifikation R10, E9).
 *
 * <p>Ein eigener Port neben {@code vorgang.domain.AnhangSpeicher} und nicht derselbe: Die Anhaenge
 * eines Vorgangs und die festgeschriebenen Belege sind verschiedene Bestaende mit verschiedenen
 * Schluesselraeumen. Ein gemeinsamer Port loege ueber den Inhalt — der Schluessel begaenne mit
 * {@code vorgang/}, obwohl ein Beleg am Angebot haengt. Den Zugang selbst teilen beide: Den {@code
 * S3Client} baut {@code config.S3Config} (E9).
 *
 * <p>Der Speicher kennt nur Schluessel und Bytes. Wem ein Beleg gehoert und wann er entstand, steht
 * in der Datenbank.
 */
public interface DokumentSpeicher {

  /**
   * Legt ein Belegdokument ab und liefert den Schluessel, unter dem es wiederzufinden ist.
   *
   * <p><b>Zusage:</b> Der Schluessel hat die Form {@code angebot/<angebotId>/<uuid>.pdf} und wird
   * im Adapter gebildet — der Schluesselraum gehoert ihm, damit kein Aufrufer die Ablagestruktur in
   * die Anwendungsschicht zieht (E9). Jede Ablage bekommt ihren eigenen Schluessel; ein zweiter
   * Versand ueberschreibt den ersten Beleg nicht.
   *
   * @param angebotId Kennung des Angebots, zu dem der Beleg gehoert
   * @param inhalt das vollstaendige Dokument
   * @return der Schluessel des abgelegten Objekts
   */
  String lege(long angebotId, byte[] inhalt);

  /**
   * Das Dokument zu einem Schluessel.
   *
   * <p>Ein unbekannter Schluessel ist hier — anders als beim Anhang eines Vorgangs — kein
   * erwarteter Fall, sondern ein Widerspruch im Bestand: Den Schluessel traegt das festgeschriebene
   * Angebot selbst. Der Adapter gibt den Fehler des Speichers deshalb weiter, statt leere Bytes zu
   * liefern; ein leeres PDF sahe wie ein gueltiger Beleg aus.
   *
   * @param schluessel der Schluessel aus {@link #lege}
   * @return das abgelegte Dokument, Byte fuer Byte
   */
  byte[] lies(String schluessel);
}
