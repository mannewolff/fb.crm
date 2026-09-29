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
   * Alle Angebote einer Firma, jedes mit seinen Positionen.
   *
   * <p>Ohne zugesagte Reihenfolge: Welche Ordnung die Ansicht zeigt, entscheidet die
   * Anwendungsschicht — der Bestand liefert den Inhalt, nicht die Darstellung.
   *
   * @param firmaId Kennung der Firma
   */
  List<Angebot> findByFirma(long firmaId);

  /**
   * Legt das Angebot an oder schreibt es fort und liefert es mit gesetzter Id zurueck.
   *
   * <p>Ein Loeschen gibt es nicht: Ein Angebot, aus dem nichts wird, bleibt liegen (Issue #127).
   */
  Angebot save(Angebot angebot);
}
