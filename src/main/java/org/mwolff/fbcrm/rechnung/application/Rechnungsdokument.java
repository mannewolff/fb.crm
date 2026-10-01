package org.mwolff.fbcrm.rechnung.application;

import java.io.InputStream;

/**
 * Das archivierte Dokument einer gestellten Rechnung, so wie es wieder hinausgeht (#160, Kriterium
 * 24; Plan #169, E11).
 *
 * <p>Die Nummer steht daneben, weil der Dateiname aus ihr entsteht — der Empfaenger soll die Datei
 * unter einem Namen speichern, der die Rechnung benennt. Der Objektschluessel bleibt drinnen: Er
 * ist die interne Adresse im Speicher und hat nach aussen nichts zu suchen.
 *
 * <p><b>Die Bytes als Datenstrom und die Groesse daneben</b> — dieselbe Form wie bei {@code
 * Anlageninhalt} am Angebot, damit beide Auslieferungswege gleich aussehen. Der Strom liegt hier
 * ueber einem Feld im Speicher, weil {@code DokumentSpeicher.lies} das Dokument vollstaendig
 * liefert; ein Beleg ist ein selbst gedrucktes PDF von wenigen Kilobyte. Ein {@code byte[]} als
 * Komponente waere die geradere Form, scheitert aber an den Leitplanken des Projekts: Error Prone
 * ({@code ArrayRecordComponent}) und PMD ({@code MethodReturnsInternalArray}) weisen ein Feld
 * zurueck, dessen Accessor eine veraenderliche Referenz herausgibt.
 *
 * <p>Wer den Strom bekommt, schliesst ihn — bei der Auslieferung ueber HTTP tut das der Konverter,
 * der ihn wegschreibt.
 *
 * @param nummer die Rechnungsnummer, die der Beleg traegt
 * @param groesse Groesse des Dokuments in Byte
 * @param inhalt der offene Datenstrom der Bytes
 */
public record Rechnungsdokument(String nummer, long groesse, InputStream inhalt) {}
