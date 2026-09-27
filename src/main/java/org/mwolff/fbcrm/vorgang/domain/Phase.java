package org.mwolff.fbcrm.vorgang.domain;

/**
 * Der Stand eines Vorgangs in der Kette von der Anfrage bis zum Zahlungseingang.
 *
 * <p>Die Phase wird nicht gespeichert, sondern aus dem Stand der Dokumente abgeleitet (E4, R1):
 * Eine Spalte, die kein Code schreibt, laedt dazu ein, sie von Hand zu pflegen. Auftrag und
 * Rechnung kommen spaeter und bringen ihre Phasen mit.
 */
public enum Phase {

  /** Angefragt, aber noch ohne festgeschriebenes Dokument. */
  ANBAHNUNG,

  /** Mindestens ein Angebot ist festgeschrieben (Kriterium 22). */
  ANGEBOT
}
