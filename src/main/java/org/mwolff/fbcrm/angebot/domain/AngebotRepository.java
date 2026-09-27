package org.mwolff.fbcrm.angebot.domain;

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

  /** Legt das Angebot an oder schreibt es fort und liefert es mit gesetzter Id zurueck. */
  Angebot save(Angebot angebot);
}
