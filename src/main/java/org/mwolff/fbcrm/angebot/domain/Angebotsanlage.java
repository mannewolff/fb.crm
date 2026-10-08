package org.mwolff.fbcrm.angebot.domain;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.common.Identifiable;

/**
 * Eine Anlage am Angebot (Issue #148, Kriterium 1).
 *
 * <p>Ein eigenes Fachobjekt mit eigener Identitaet und keine Liste am {@link Angebot} (Plan #150,
 * E1), wie schon der {@link Angebotskommentar}: Das Angebot wird mit seinen Positionen als Ganzes
 * geschrieben, und eine Anlage darf bei einer Aenderung des Angebots nicht mitwandern.
 *
 * <p>Der Record traegt <b>keine Regel</b>. Der Dateiname ist bereits ueber {@link Dateiname}
 * gesaeubert, die {@link Vorschauart} bereits am Inhalt erkannt, und die Groesse hat der
 * Anwendungsfall gegen die Upload-Grenze gehalten; die letzte Grenze halten die CHECKs der Tabelle.
 * Wer den Record von Hand baut, traegt dafuer die Verantwortung.
 *
 * <p>Eine Anlage aendert sich nie — kein Umbenennen, kein Ersetzen (Nicht-Ziele der Quelle).
 * Deshalb gibt es weder eine Methode, die einen geaenderten Record liefert, noch ein {@code
 * updatedAt}.
 *
 * @param id technische Id — {@code null}, solange die Anlage nicht gespeichert ist
 * @param angebotId Kennung des Angebots, an dem die Anlage haengt
 * @param dateiName der gesaeuberte Name, wie die Ansicht ihn zeigt und die Auslieferung ihn nennt
 * @param groesse Groesse des Inhalts in Byte; zwischen 1 und der Upload-Grenze
 * @param vorschauArt die am Inhalt erkannte Art, oder {@code null} — dann gibt es keine Vorschau
 * @param objektSchluessel Schluessel des Inhalts im Objektspeicher; steht in keiner Antwort
 * @param createdAt Zeitpunkt des Hochladens — der Zeitpunkt, den die Ansicht zeigt (Kriterium 4)
 */
public record Angebotsanlage(
    @Nullable Long id,
    long angebotId,
    String dateiName,
    long groesse,
    @Nullable Vorschauart vorschauArt,
    String objektSchluessel,
    Instant createdAt)
    implements Identifiable {}
