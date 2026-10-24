package org.mwolff.fbcrm.angebot.web;

import java.time.Instant;
import org.mwolff.fbcrm.angebot.application.KommentarMitVerfasser;
import org.mwolff.fbcrm.angebot.domain.Angebotskommentar;

/**
 * Ein Kommentar, wie die Ansicht ihn zeigt (Issue #140, Kriterien 1 und 3; fachliche Quelle #409,
 * Kriterium 11).
 *
 * <p>{@code updatedAt} steht in keiner Antwort, weil es einen Hinweis „bearbeitet" nicht gibt
 * (Kriterium 9). Der Verfasser steht als sein <b>aktueller</b> Name darin, nicht als Kennung des
 * Kontos: Die Ansicht zeigt ihn nur, und nach einer Umbenennung tragen auch die bisherigen
 * Kommentare den neuen Namen (Plan #414, A7). Der Zeitpunkt geht als {@code Instant} heraus; Datum
 * und Uhrzeit setzt die Oberflaeche in der Ortszeit des Betrachters (E9).
 *
 * @param id Kennung des Kommentars — die Oberflaeche braucht sie zum Aendern und Loeschen
 * @param text der Text des Kommentars, bereinigt
 * @param createdAt Zeitpunkt der Anlage
 * @param verfasser der aktuelle Anzeigename des Kontos, das den Kommentar geschrieben hat
 * @param version der Stand des Kommentars; die Ansicht legt ihn beim Aendern und Loeschen als
 *     {@code If-Match} wieder vor (Plan #415, E2, E8)
 */
public record AngebotKommentarResponse(
    long id, String text, Instant createdAt, String verfasser, long version) {

  /** Die Sicht der Oberflaeche auf einen Kommentar. */
  static AngebotKommentarResponse of(final KommentarMitVerfasser eintrag) {
    final Angebotskommentar kommentar = eintrag.kommentar();
    return new AngebotKommentarResponse(
        kommentar.requireId(),
        kommentar.text(),
        kommentar.createdAt(),
        eintrag.verfasser(),
        kommentar.version());
  }
}
