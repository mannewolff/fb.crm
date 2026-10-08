package org.mwolff.fbcrm.angebot.domain;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.common.Identifiable;

/**
 * Ein Kommentar am Angebot (Issue #140, Kriterium 1).
 *
 * <p>Ein eigenes Fachobjekt mit eigener Identitaet und keine Liste am {@link Angebot} (Plan #141,
 * E2): Das Angebot wird mit seinen Positionen als Ganzes geschrieben, und ein Kommentar darf bei
 * einer Aenderung des Angebots nicht mitwandern.
 *
 * <p>Der Record <b>bereinigt den Text nicht</b> und prueft seine Laenge nicht. Beides tut der
 * Anwendungsfall, nach dem Muster von {@code FirmaDaten} und {@code AnsprechpartnerDaten} (E7);
 * kein {@code domain}-Paket des Bestands schneidet Leerraum ab. Wer den Record von Hand baut,
 * traegt darum die Verantwortung fuer den Text — die letzte Grenze halten die CHECKs der Tabelle.
 *
 * <p>Unveraenderlich: {@link #geaendert(String, Instant)} liefert einen neuen Kommentar. Der
 * Zeitpunkt kommt von aussen, weil die Domaene keine Uhr kennt (CLAUDE-java.md §6.2).
 *
 * @param id technische Id — {@code null}, solange der Kommentar nicht gespeichert ist
 * @param angebotId Kennung des Angebots, an dem der Kommentar haengt
 * @param text der Text des Kommentars; nie leer, das prueft der Anwendungsfall
 * @param createdAt Zeitpunkt der Anlage — der Zeitpunkt, den die Ansicht zeigt (Kriterium 3)
 * @param updatedAt Zeitpunkt der letzten Aenderung; keine Antwort traegt ihn (Kriterium 9)
 */
public record Angebotskommentar(
    @Nullable Long id, long angebotId, String text, Instant createdAt, Instant updatedAt)
    implements Identifiable {

  /**
   * Der Kommentar mit neuem Text (Kriterium 9).
   *
   * <p>Wechselt genau zwei Felder: den Text und {@code updatedAt}. Kennung, Angebot und {@code
   * createdAt} bleiben, damit der Zeitpunkt in der Ansicht nach dem Bearbeiten derselbe ist (E8).
   *
   * @param neuerText der neue Text; bereits bereinigt
   * @param zeitpunkt Zeitpunkt der Aenderung
   */
  public Angebotskommentar geaendert(final String neuerText, final Instant zeitpunkt) {
    return new Angebotskommentar(id, angebotId, neuerText, createdAt, zeitpunkt);
  }
}
