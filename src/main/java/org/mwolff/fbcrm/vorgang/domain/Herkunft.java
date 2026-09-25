package org.mwolff.fbcrm.vorgang.domain;

/**
 * Woher ein Eintrag der Historie stammt.
 *
 * <p>In diesem Stand gibt es genau eine Herkunft: Jeder Eintrag wird von Hand erfasst. Ein
 * Postfach-Import ist ausdruecklich Nicht-Ziel; kaeme er, traete er hier als zweiter Wert hinzu,
 * ohne dass ein Bestandssatz umgeschrieben werden muesste.
 */
public enum Herkunft {

  /** Von Hand in der Anwendung erfasst. */
  VON_HAND
}
