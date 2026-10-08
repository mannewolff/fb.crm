package org.mwolff.fbcrm.rechnung.domain;

import java.util.List;
import java.util.Optional;

/**
 * Port auf den Bestand der nachgetragenen Rechnungen; die Umsetzung liegt in {@code
 * rechnung.infrastructure} (Plan #259).
 *
 * <p>Die beiden Nummernfragen uebergehen die Schreibweise: „RE-9" und „re-9" sind dieselbe Nummer
 * (#254, Kriterium 4; E3). Sie beantworten nur diesen Bestand — ob eine Nummer ueber beide Arten
 * von Rechnungen vergeben ist, fragt die Anwendung an einer Stelle.
 */
public interface NachgetrageneRechnungRepository {

  /** Die nachgetragene Rechnung zu einer technischen Kennung, oder leer. */
  Optional<NachgetrageneRechnung> findById(long id);

  /**
   * Alle nachgetragenen Rechnungen.
   *
   * <p>Ohne zugesagte Reihenfolge: Welche Ordnung die Ansicht zeigt, entscheidet die
   * Anwendungsschicht.
   */
  List<NachgetrageneRechnung> findAlle();

  /** Legt die Rechnung an oder schreibt sie fort und liefert sie mit gesetzter Kennung zurueck. */
  NachgetrageneRechnung save(NachgetrageneRechnung rechnung);

  /**
   * Loescht die Rechnung.
   *
   * @param id Kennung der Rechnung
   */
  void delete(long id);

  /**
   * Ob eine nachgetragene Rechnung diese Nummer schon traegt, ohne Ansehen der Schreibweise.
   *
   * <p>Die Zusage haelt trotzdem der eindeutige Index {@code
   * rechnung_nachgetragen_nummer_lower_key} — zwischen Frage und Antwort kann eine zweite Sitzung
   * dieselbe Nummer schreiben.
   *
   * @param nummer die zu pruefende Rechnungsnummer
   */
  boolean existiertNummer(String nummer);

  /**
   * Dasselbe, aber die Rechnung mit der Kennung {@code id} zaehlt nicht mit — fuer das Aendern, bei
   * dem die eigene Nummer erlaubt bleibt.
   *
   * @param nummer die zu pruefende Rechnungsnummer
   * @param id Kennung der Rechnung, die geaendert wird
   */
  boolean existiertNummerAusser(String nummer, long id);
}
