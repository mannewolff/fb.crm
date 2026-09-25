package org.mwolff.fbcrm.vorgang.application;

import java.io.InputStream;

/**
 * Ein Anhang, so wie er wieder hinausgeht (Kriterium 17).
 *
 * <p>Name und Groesse kommen aus der Zeile in der Datenbank, die Bytes aus dem Objektspeicher (E9).
 * Der Objektschluessel bleibt drinnen — er ist die interne Adresse und hat nach aussen nichts zu
 * suchen, dieselbe Abwaegung wie bei {@link EintragAnsicht}.
 *
 * <p><b>Der Datenstrom ist offen.</b> Er wird nicht in den Speicher gezogen: Eine Datei darf 25 MiB
 * gross sein (E10), und die durch den Heap zu schleusen, waere Aufwand ohne Gewinn. Wer ihn
 * bekommt, schliesst ihn — bei der Auslieferung ueber HTTP tut das der Konverter, der ihn
 * wegschreibt.
 *
 * <p>Der hochgeladene Inhaltstyp steht hier nicht, weil er nirgends steht (E12): Ausgeliefert wird
 * immer {@code application/octet-stream}.
 *
 * @param dateiName der gesaeuberte Dateiname aus der Zeile (E13)
 * @param groesse Groesse in Byte, wie sie in der Zeile steht
 * @param inhalt der offene Datenstrom der Bytes
 */
public record AnhangInhalt(String dateiName, long groesse, InputStream inhalt) {}
