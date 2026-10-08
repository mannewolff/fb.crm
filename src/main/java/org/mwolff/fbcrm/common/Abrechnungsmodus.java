package org.mwolff.fbcrm.common;

/**
 * Wie eine Belegposition abgerechnet wird (Kriterium 4).
 *
 * <p>Der Modus steht an der Position und nicht am Beleg: Ein Angebot darf eine pauschale Konzeption
 * und daneben Betreuung nach Aufwand enthalten.
 */
public enum Abrechnungsmodus {

  /** Nach tatsaechlichem Aufwand; Menge und Einzelpreis sind die Schaetzung. */
  AUFWAND,

  /** Zum festen Preis, unabhaengig vom Aufwand. */
  FESTPREIS
}
