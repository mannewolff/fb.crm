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
 */
public interface AngebotsanlageRepository {

  /**
   * Alle Anlagen eines Angebots.
   *
   * <p>Ohne zugesagte Reihenfolge, wie {@link AngebotskommentarRepository#findByAngebot(long)}:
   * Welche Ordnung die Ansicht zeigt, entscheidet die Anwendungsschicht — der Bestand liefert den
   * Inhalt, nicht die Darstellung (E10).
   *
   * @param angebotId Kennung des Angebots
   */
  List<Angebotsanlage> findByAngebot(long angebotId);

  /**
   * Die Anlage zu einer technischen Id, oder leer.
   *
   * <p>Ob sie zu dem Angebot gehoert, nach dem gefragt wurde, prueft der Anwendungsfall — der Port
   * kennt nur die Kennung.
   */
  Optional<Angebotsanlage> findById(long id);

  /** Legt die Anlage an oder schreibt sie fort und liefert sie mit gesetzter Id zurueck. */
  Angebotsanlage save(Angebotsanlage anlage);

  /**
   * Loescht die Anlage — die Zeile ist danach fort.
   *
   * <p>Das Objekt im Speicher raeumt der Anwendungsfall weg, und zwar erst nach dem Commit (E9).
   *
   * @param id Kennung der Anlage
   */
  void deleteById(long id);
}
