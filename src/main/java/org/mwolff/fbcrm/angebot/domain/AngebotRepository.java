package org.mwolff.fbcrm.angebot.domain;

import java.util.List;
import java.util.Optional;

/**
 * Port auf den Bestand der Angebote; die Umsetzung liegt in {@code angebot.infrastructure}.
 *
 * <p>Das Angebot ist mit seinen Positionen <b>eine</b> Einheit: Gelesen wird es mit ihnen in ihrer
 * Reihenfolge, geschrieben wird es mit der vollstaendigen Liste, und die Plaetze vergibt der
 * Adapter lueckenlos ab 1 aus der Reihenfolge der Liste (E24). Einzelne Positionen anzulegen, zu
 * aendern oder zu verschieben gibt es deshalb nicht.
 */
public interface AngebotRepository {

  /** Das Angebot zu einer technischen Id samt seinen Positionen, oder leer. */
  Optional<Angebot> findById(long id);

  /**
   * Alle Angebote eines Vorgangs, jedes mit seinen Positionen.
   *
   * <p>Ohne zugesagte Reihenfolge: Welche Ordnung die Ansicht zeigt, entscheidet Kriterium 20 und
   * damit die Anwendungsschicht (E25) — der Bestand liefert den Inhalt, nicht die Darstellung.
   *
   * @param vorgangId Kennung des Vorgangs
   */
  List<Angebot> findByVorgang(long vorgangId);

  /** Legt das Angebot an oder schreibt es fort und liefert es mit gesetzter Id zurueck. */
  Angebot save(Angebot angebot);

  /**
   * Loescht das Angebot samt seinen Positionen (Kriterium 7).
   *
   * <p>Der einzige Weg, auf dem im Modul etwas verschwindet — und er gilt nur dem Entwurf. Ob das
   * Angebot einer ist, entscheidet der Anwendungsfall vor dem Aufruf; der Bestand kennt die
   * Zustandsmaschine nicht.
   *
   * @param id Kennung des zu loeschenden Angebots
   */
  void loesche(long id);
}
