package org.mwolff.fbcrm.angebot.domain;

/**
 * Wie eine Angebotsposition abgerechnet wird (Kriterium 4).
 *
 * <p>Der Modus steht an der Position und nicht am Angebot: Ein Angebot darf eine pauschale
 * Konzeption und daneben Betreuung nach Aufwand enthalten.
 */
public enum Abrechnungsmodus {

  /** Nach tatsaechlichem Aufwand; Menge und Einzelpreis sind die Schaetzung. */
  AUFWAND,

  /** Zum festen Preis, unabhaengig vom Aufwand. */
  FESTPREIS
}
