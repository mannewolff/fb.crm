package org.mwolff.fbcrm.angebot.web;

import java.time.Instant;
import org.mwolff.fbcrm.angebot.domain.Angebotskommentar;

/**
 * Ein Kommentar, wie die Ansicht ihn zeigt (Issue #140, Kriterien 1 und 3).
 *
 * <p>Drei Felder und nicht mehr: {@code updatedAt} steht in keiner Antwort, weil es einen Hinweis
 * „bearbeitet" nicht gibt (Kriterium 9), und einen Verfasser kennt die Anwendung nicht (Plan #141,
 * E11). Der Zeitpunkt geht als {@code Instant} heraus; Datum und Uhrzeit setzt die Oberflaeche in
 * der Ortszeit des Betrachters (E9).
 *
 * @param id Kennung des Kommentars — die Oberflaeche braucht sie zum Aendern und Loeschen
 * @param text der Text des Kommentars, bereinigt
 * @param createdAt Zeitpunkt der Anlage
 */
public record AngebotKommentarResponse(long id, String text, Instant createdAt) {

  /** Die Sicht der Oberflaeche auf einen Kommentar. */
  static AngebotKommentarResponse of(final Angebotskommentar kommentar) {
    return new AngebotKommentarResponse(
        kommentar.requireId(), kommentar.text(), kommentar.createdAt());
  }
}
