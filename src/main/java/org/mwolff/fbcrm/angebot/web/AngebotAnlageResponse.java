package org.mwolff.fbcrm.angebot.web;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.angebot.domain.Angebotsanlage;
import org.mwolff.fbcrm.angebot.domain.Vorschauart;

/**
 * Eine Anlage, wie die Ansicht sie zeigt (Issue #148, Kriterien 1 und 4).
 *
 * <p>Fuenf Felder und nicht mehr. Der <b>Objektschluessel</b> bleibt drinnen — er ist die interne
 * Adresse im Speicher und hat nach aussen nichts zu suchen; ein <b>Verfasser</b> steht hier nicht,
 * weil die Anwendung keinen kennt (Plan #150, E13); und ein {@code updatedAt} gibt es nicht, weil
 * eine Anlage sich nie aendert (E2).
 *
 * <p>Die {@link Vorschauart} ist {@code null}, wenn es keine Vorschau gibt. Daran — und nur daran —
 * entscheidet die Oberflaeche, ob sie die Taste „Anzeigen" anbietet; sie ist dieselbe Angabe, der
 * auch der {@code Content-Type} des Inhaltswegs folgt (E5).
 *
 * <p>Der Zeitpunkt geht als {@code Instant} heraus; Datum und Uhrzeit setzt die Oberflaeche in der
 * Ortszeit des Betrachters.
 *
 * @param id Kennung der Anlage — die Oberflaeche braucht sie zum Abrufen und Loeschen
 * @param dateiName der gesaeuberte Name
 * @param groesse Groesse des Inhalts in Byte
 * @param vorschauArt die am Inhalt erkannte Art, oder {@code null}
 * @param createdAt Zeitpunkt des Hochladens
 */
public record AngebotAnlageResponse(
    long id, String dateiName, long groesse, @Nullable Vorschauart vorschauArt, Instant createdAt) {

  /** Die Sicht der Oberflaeche auf eine Anlage. */
  static AngebotAnlageResponse of(final Angebotsanlage anlage) {
    return new AngebotAnlageResponse(
        anlage.requireId(),
        anlage.dateiName(),
        anlage.groesse(),
        anlage.vorschauArt(),
        anlage.createdAt());
  }
}
