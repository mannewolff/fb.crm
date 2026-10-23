package org.mwolff.fbcrm.angebot.domain;

import java.util.List;
import java.util.Optional;

/**
 * Port auf den Bestand der Anlagen am Angebot; die Umsetzung liegt in {@code
 * angebot.infrastructure}.
 *
 * <p>Der Port fuehrt nur die Zeile. Die Bytes der Anlage stehen im Objektspeicher und gehen ueber
 * einen eigenen Port (Plan #150, E3); {@link Angebotsanlage#objektSchluessel()} verbindet beide. In
 * welcher Reihenfolge beide beschrieben werden, entscheidet der Anwendungsfall (E9).
 *
 * <p>Geloescht wird mit Kennzeichen (Plan #415, E7, E17): {@link #save(Angebotsanlage)} schreibt
 * es. Die Liste laesst gekennzeichnete Anlagen aus; {@link #findById} liefert sie dagegen mit,
 * damit der Anwendungsfall Urheber und Zeitpunkt der Loeschung nennen kann (E16).
 */
public interface AngebotsanlageRepository {

  /**
   * Alle nicht geloeschten Anlagen eines Angebots (E7).
   *
   * <p>Ohne zugesagte Reihenfolge, wie {@link AngebotskommentarRepository#findByAngebot(long)}:
   * Welche Ordnung die Ansicht zeigt, entscheidet die Anwendungsschicht — der Bestand liefert den
   * Inhalt, nicht die Darstellung (E10).
   *
   * @param angebotId Kennung des Angebots
   */
  List<Angebotsanlage> findByAngebot(long angebotId);

  /**
   * Die Anlage zu einer technischen Id, oder leer — auch eine geloeschte, samt Kennzeichen.
   *
   * <p>Ob sie zu dem Angebot gehoert, nach dem gefragt wurde, prueft der Anwendungsfall — der Port
   * kennt nur die Kennung.
   */
  Optional<Angebotsanlage> findById(long id);

  /** Legt die Anlage an oder schreibt sie fort und liefert sie mit gesetzter Id zurueck. */
  Angebotsanlage save(Angebotsanlage anlage);
}
