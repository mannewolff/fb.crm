package org.mwolff.fbcrm.vorgang.domain;

/**
 * Der Stand eines Vorgangs in der Kette von der Anfrage bis zum Zahlungseingang.
 *
 * <p>In diesem Stand gibt es genau eine Phase: Solange keine Dokumente am Vorgang haengen, ist er
 * in der Anbahnung. Angebot, Auftrag und Rechnung kommen spaeter und bringen ihre Phasen mit. Die
 * Phase wird nicht gespeichert, sondern abgeleitet (E4).
 */
public enum Phase {

  /** Angefragt, aber noch ohne Dokument. */
  ANBAHNUNG
}
