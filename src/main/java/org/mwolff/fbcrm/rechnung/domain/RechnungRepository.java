package org.mwolff.fbcrm.rechnung.domain;

import java.util.List;
import java.util.Optional;

/**
 * Port auf den Bestand der Rechnungen; die Umsetzung liegt in {@code rechnung.infrastructure}.
 *
 * <p>Die Rechnung ist mit ihren Positionen <b>eine</b> Einheit — wie das Angebot mit seinen:
 * Gelesen wird sie mit ihnen in ihrer Reihenfolge, geschrieben wird sie mit der vollstaendigen
 * Liste, und die Plaetze vergibt der Adapter lueckenlos ab 1 aus der Reihenfolge der Liste (E24).
 * Einzelne Positionen anzulegen, zu aendern oder zu verschieben gibt es deshalb nicht.
 *
 * <p>Anders als das Angebot laesst sich eine Rechnung loeschen: Ein Entwurf, aus dem nichts wird,
 * verschwindet, und weil er nie eine Nummer getragen hat, reisst er keine Luecke (#160, Kriterium
 * 12). Dass nur ein Entwurf geloescht werden darf, haelt der Anwendungsfall fest — der Bestand
 * fuehrt aus.
 */
public interface RechnungRepository {

  /** Die Rechnung zu einer technischen Kennung samt ihren Positionen, oder leer. */
  Optional<Rechnung> findById(long id);

  /**
   * Alle Rechnungen, jede mit ihren Positionen.
   *
   * <p>Ohne zugesagte Reihenfolge: Welche Ordnung die Ansicht zeigt, entscheidet die
   * Anwendungsschicht — der Bestand liefert den Inhalt, nicht die Darstellung.
   */
  List<Rechnung> findAlle();

  /**
   * Alle Rechnungen eines Angebots, jede mit ihren Positionen.
   *
   * <p>Ohne zugesagte Reihenfolge, aus demselben Grund wie {@link #findAlle()}.
   *
   * @param angebotId Kennung des Angebots
   */
  List<Rechnung> findByAngebot(long angebotId);

  /** Legt die Rechnung an oder schreibt sie fort und liefert sie mit gesetzter Kennung zurueck. */
  Rechnung save(Rechnung rechnung);

  /**
   * Loescht die Rechnung samt ihren Positionen.
   *
   * @param id Kennung der Rechnung; eine unbekannte Kennung ist kein Fehler
   */
  void delete(long id);

  /**
   * Ob eine Rechnung diese Nummer schon traegt (#160, Kriterium 18).
   *
   * <p>Die Vorabpruefung des Stellens: Sie erspart dem Anwender einen Datenbankfehler und laesst
   * den Anwendungsfall mit 409 antworten. Die Zusage haelt trotzdem {@code UNIQUE} in der Datenbank
   * — zwischen Frage und Antwort kann eine zweite Sitzung dieselbe Nummer schreiben.
   *
   * @param nummer die zu pruefende Rechnungsnummer
   */
  boolean existiertNummer(String nummer);
}
