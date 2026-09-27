package org.mwolff.fbcrm.vorgang.domain;

/**
 * Woher ein Eintrag der Historie stammt.
 *
 * <p>Von Hand erfasst oder von der Anwendung vermerkt — daran unterscheidet die Ansicht einen
 * eigenen Eintrag von einem Ereignis (Kriterium 19). Ein Postfach-Import ist ausdruecklich
 * Nicht-Ziel; kaeme er, traete er hier als dritter Wert hinzu, ohne dass ein Bestandssatz
 * umgeschrieben werden muesste.
 */
public enum Herkunft {

  /** Von Hand in der Anwendung erfasst. */
  VON_HAND,

  /** Von der Anwendung selbst vermerkt — die Herkunft jedes Ereignisses. */
  AUTOMATISCH
}
