package org.mwolff.fbcrm.vorgang.application;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.vorgang.domain.Eintrag;
import org.mwolff.fbcrm.vorgang.domain.Eintragsart;
import org.mwolff.fbcrm.vorgang.domain.Herkunft;

/**
 * Ein Eintrag der Historie, so wie die Detailansicht ihn zeigt (Kriterien 15, 16, 19).
 *
 * <p>Ein eigenes Lesemodell und nicht der {@link Eintrag} selbst, aus genau einem Grund: Der
 * Objektschluessel ist die interne Adresse der Datei im Objektspeicher (E9) und hat nach aussen
 * nichts zu suchen. Wer die Datei will, geht ueber den eigenen Weg dafuer.
 *
 * @param id technische Id des Eintrags
 * @param art Kommentar oder Anhang
 * @param text der Text; bei einem Anhang die Beschreibung, oder {@code null}
 * @param geschehenAm Zeitpunkt des Geschehens — nicht der der Erfassung
 * @param herkunft woher der Eintrag stammt (Kriterium 16)
 * @param dateiName Dateiname — nur beim Anhang gesetzt
 * @param dateiGroesse Groesse in Byte — nur beim Anhang gesetzt
 * @param geaendertAm Zeitpunkt der letzten Aenderung, oder {@code null} — daran haengt der Vermerk
 *     „geaendert" (Kriterium 19)
 */
public record EintragAnsicht(
    long id,
    Eintragsart art,
    @Nullable String text,
    Instant geschehenAm,
    Herkunft herkunft,
    @Nullable String dateiName,
    @Nullable Long dateiGroesse,
    @Nullable Instant geaendertAm) {

  /** Die Sicht der Detailansicht auf einen Eintrag — ohne seinen Objektschluessel. */
  static EintragAnsicht of(final Eintrag eintrag) {
    return new EintragAnsicht(
        eintrag.requireId(),
        eintrag.art(),
        eintrag.text(),
        eintrag.geschehenAm(),
        eintrag.herkunft(),
        eintrag.dateiName(),
        eintrag.dateiGroesse(),
        eintrag.geaendertAm());
  }
}
