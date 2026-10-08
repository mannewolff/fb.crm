package org.mwolff.fbcrm.angebot.application;

import java.io.InputStream;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.angebot.domain.Vorschauart;

/**
 * Eine Anlage, so wie sie wieder hinausgeht (Issue #148, Kriterium 9; Plan #150, E5).
 *
 * <p>Name, Groesse und Vorschauart kommen aus der Zeile in der Datenbank, die Bytes aus dem
 * Objektspeicher. Der Objektschluessel bleibt drinnen — er ist die interne Adresse und hat nach
 * aussen nichts zu suchen.
 *
 * <p><b>Der Datenstrom ist offen.</b> Er wird nicht in den Speicher gezogen: Eine Anlage darf bis
 * zur Upload-Grenze gross sein, und die durch den Heap zu schleusen, waere Aufwand ohne Gewinn. Wer
 * ihn bekommt, schliesst ihn — bei der Auslieferung ueber HTTP tut das der Konverter, der ihn
 * wegschreibt.
 *
 * <p>Die beim Hochladen vom Browser <b>gemeldete</b> Art steht hier nicht, weil sie nirgends steht
 * (E4): Was ausgeliefert wird, folgt allein der gespeicherten {@link Vorschauart}, und ohne sie
 * geht die Anlage als {@code application/octet-stream} hinaus.
 *
 * @param dateiName der gesaeuberte Dateiname aus der Zeile
 * @param groesse Groesse in Byte, wie sie in der Zeile steht
 * @param vorschauArt die beim Hochladen am Inhalt erkannte Art, oder {@code null}
 * @param inhalt der offene Datenstrom der Bytes
 */
public record Anlageninhalt(
    String dateiName, long groesse, @Nullable Vorschauart vorschauArt, InputStream inhalt) {}
