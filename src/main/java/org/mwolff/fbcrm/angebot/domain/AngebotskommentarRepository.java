package org.mwolff.fbcrm.angebot.domain;

import java.util.List;
import java.util.Optional;

/**
 * Port auf den Bestand der Kommentare am Angebot; die Umsetzung liegt in {@code
 * angebot.infrastructure}.
 *
 * <p>Anders als die Positionen sind Kommentare <b>keine</b> Einheit mit dem Angebot (Plan #141,
 * E2): Jeder traegt seine eigene Kennung und wird einzeln angelegt, geaendert und geloescht.
 *
 * <p>Geloescht wird mit Kennzeichen (Plan #415, E17): {@link #save(Angebotskommentar)} schreibt es
 * wie jede andere Aenderung. Die Liste laesst gekennzeichnete Kommentare aus; {@link #findById}
 * liefert sie dagegen mit, damit der Anwendungsfall Urheber und Zeitpunkt der Loeschung nennen kann
 * (E16).
 */
public interface AngebotskommentarRepository {

  /**
   * Alle nicht geloeschten Kommentare eines Angebots (E17).
   *
   * <p>Ohne zugesagte Reihenfolge, wie {@link AngebotRepository#findByFirma(long)}: Welche Ordnung
   * die Ansicht zeigt, entscheidet die Anwendungsschicht — der Bestand liefert den Inhalt, nicht
   * die Darstellung (E8).
   *
   * @param angebotId Kennung des Angebots
   */
  List<Angebotskommentar> findByAngebot(long angebotId);

  /**
   * Der Kommentar zu einer technischen Id, oder leer — auch ein geloeschter, samt Kennzeichen.
   *
   * <p>Ob er zu dem Angebot gehoert, nach dem gefragt wurde, prueft der Anwendungsfall — der Port
   * kennt nur die Kennung (E5).
   */
  Optional<Angebotskommentar> findById(long id);

  /**
   * Legt den Kommentar an oder schreibt ihn fort und liefert ihn mit gesetzter Id und dem neuen
   * Stand zurueck (Plan #415, E1).
   *
   * <p>Beruht der Kommentar auf einem Stand, den die Zeile nicht mehr traegt, scheitert das
   * Schreiben, statt still zu ueberschreiben.
   */
  Angebotskommentar save(Angebotskommentar kommentar);
}
