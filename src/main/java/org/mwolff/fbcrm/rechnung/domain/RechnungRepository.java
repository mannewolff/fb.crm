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

  /**
   * Die Rechnung zu einer technischen Kennung, mit <b>Sperre</b> auf ihrer Zeile, oder leer.
   *
   * <p>Fuer den einen nicht umkehrbaren Schritt: das Stellen. Die Sperre haelt bis zum Ende der
   * Transaktion des Aufrufers, und erst sie macht aus „lesen, pruefen, schreiben" einen Schritt.
   * Ohne sie laesen zwei gleichzeitige Aufrufe fuer denselben Entwurf beide {@code ENTWURF} und
   * stellten beide — eine Nummer waere verbraucht und verschwunden. Der zweite Aufruf wartet
   * stattdessen und sieht danach die gestellte Rechnung.
   *
   * <p>Nicht der Standardweg: Jedes Lesen zu sperren machte aus jeder Ansicht einen Engpass.
   * Gelesen wird darum sonst ueber {@link #findById(long)}.
   *
   * @param id Kennung der Rechnung
   */
  Optional<Rechnung> findByIdMitSperre(long id);

  /** Legt die Rechnung an oder schreibt sie fort und liefert sie mit gesetzter Kennung zurueck. */
  Rechnung save(Rechnung rechnung);

  /**
   * Dasselbe, aber die Zeile geht <b>sofort</b> in die Datenbank.
   *
   * <p>Fuer das Stellen: Die Nummer ist in {@code V18__rechnung.sql} eindeutig, und eine Verletzung
   * dieser Zusage soll dort auffallen, wo der Anwendungsfall sie noch in ein 409 uebersetzen kann.
   * Erst beim Commit gemeldet waere sie ein Serverfehler, obwohl die Lage fachlich benannt ist
   * (#160, Kriterium 18).
   *
   * @param rechnung die zu schreibende Rechnung
   */
  Rechnung saveAndFlush(Rechnung rechnung);

  /**
   * Loescht die Rechnung samt ihren Positionen.
   *
   * @param id Kennung der Rechnung; eine unbekannte Kennung ist kein Fehler
   */
  void delete(long id);

  /**
   * Ob eine Rechnung diese Nummer schon traegt, ohne Ansehen der Schreibweise (#160, Kriterium 18;
   * #254, Kriterium 4): „RE-1" und „re-1" sind dieselbe Nummer.
   *
   * <p>Die Vorabpruefung des Stellens: Sie erspart dem Anwender einen Datenbankfehler und laesst
   * den Anwendungsfall mit 409 antworten. Die Zusage haelt trotzdem der eindeutige Index ueber
   * {@code lower(nummer)} in der Datenbank — zwischen Frage und Antwort kann eine zweite Sitzung
   * dieselbe Nummer schreiben.
   *
   * @param nummer die zu pruefende Rechnungsnummer
   */
  boolean existiertNummer(String nummer);
}
