package org.mwolff.fbcrm.angebot.application;

/**
 * Ein archivierter Beleg, wie er wieder hinausgeht (Kriterium 14, E17).
 *
 * <p>Der Inhalt ist das beim Versenden abgelegte Objekt, Byte fuer Byte — nichts aus heutigen Daten
 * Nachgerechnetes. Der Dateiname entsteht aus der Angebotsnummer und steht hier und nicht in der
 * Antwortschicht: Die Nummer kennt der Anwendungsfall, der das Angebot gelesen hat, und ein zweiter
 * Lesezugriff nur fuer den Namen waere einer zu viel.
 *
 * @param dateiname Name, unter dem der Beleg im Browser erscheint, etwa {@code A-2026-001.pdf}
 * @param inhalt das vollstaendige Dokument
 */
/*
 * ArrayRecordComponent: Der Hinweis von Error Prone ist berechtigt — {@code equals} und {@code
 * hashCode} dieses Records vergleichen die Referenz der Bytefolge und nicht ihren Inhalt. Hier ist
 * das kein Mangel, sondern ohne Bedeutung: Der Record traegt den Beleg von der Anwendungsschicht in
 * die Antwort und wird nie verglichen, sortiert oder als Schluessel benutzt. Die Alternative — eine
 * Klasse mit zwei Kopien der Bytefolge je Anfrage — kostete Arbeitsspeicher fuer eine Gleichheit,
 * die niemand abfragt.
 */
@SuppressWarnings("ArrayRecordComponent")
public record Belegdokument(String dateiname, byte[] inhalt) {}
