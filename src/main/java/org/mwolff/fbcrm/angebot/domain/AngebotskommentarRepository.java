package org.mwolff.fbcrm.angebot.domain;

import java.util.List;
import java.util.Optional;

/**
 * Port auf den Bestand der Kommentare am Angebot; die Umsetzung liegt in {@code
 * angebot.infrastructure}.
 *
 * <p>Anders als die Positionen sind Kommentare <b>keine</b> Einheit mit dem Angebot (Plan #141,
 * E2): Jeder traegt seine eigene Kennung und wird einzeln angelegt, geaendert und geloescht.
 */
public interface AngebotskommentarRepository {

  /**
   * Alle Kommentare eines Angebots.
   *
   * <p>Ohne zugesagte Reihenfolge, wie {@link AngebotRepository#findByFirma(long)}: Welche Ordnung
   * die Ansicht zeigt, entscheidet die Anwendungsschicht — der Bestand liefert den Inhalt, nicht
   * die Darstellung (E8).
   *
   * @param angebotId Kennung des Angebots
   */
  List<Angebotskommentar> findByAngebot(long angebotId);

  /**
   * Der Kommentar zu einer technischen Id, oder leer.
   *
   * <p>Ob er zu dem Angebot gehoert, nach dem gefragt wurde, prueft der Anwendungsfall — der Port
   * kennt nur die Kennung (E5).
   */
  Optional<Angebotskommentar> findById(long id);

  /** Legt den Kommentar an oder schreibt ihn fort und liefert ihn mit gesetzter Id zurueck. */
  Angebotskommentar save(Angebotskommentar kommentar);

  /**
   * Loescht den Kommentar — die Zeile ist danach fort (E6).
   *
   * <p>Ein Stilllegen gibt es nicht: Ein stillgelegter Kommentar haette keine Ansicht.
   *
   * @param id Kennung des Kommentars
   */
  void deleteById(long id);
}
