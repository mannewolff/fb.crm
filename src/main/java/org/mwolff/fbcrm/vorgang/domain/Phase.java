package org.mwolff.fbcrm.vorgang.domain;

/**
 * Der Stand eines Vorgangs in der Kette von der Anfrage bis zum Zahlungseingang.
 *
 * <p>Die Phase wird nicht gespeichert, sondern aus dem Stand der Dokumente abgeleitet (E4, R1):
 * Eine Spalte, die kein Code schreibt, laedt dazu ein, sie von Hand zu pflegen. Rechnung und
 * Zahlung kommen spaeter und bringen ihre Phasen mit.
 *
 * <p><b>Die Deklarationsreihenfolge ist die Ordnung der Kette.</b> An einem Vorgang haengen oft
 * mehrere Belegarten gleichzeitig — ein Auftrag loescht das Angebot nicht, auf das er folgt. Welche
 * Phase dann gilt, entscheidet {@link Vorgang#phase(java.util.Collection)} als <b>Maximum</b>, und
 * das Maximum eines Aufzaehlungstyps ist seine Reihenfolge im Quelltext. Eine zweite Ordnungszahl
 * an jedem Wert waere ein zweiter Wahrheitsort fuer dieselbe Ordnung (Plan E2).
 *
 * <p>Wer hier einen Wert <b>einschiebt</b>, verschiebt damit die Ordnung. Ein neuer Wert gehoert an
 * die Stelle, an der sein Beleg in der Kette steht — fuer Rechnung und Zahlung also ans Ende.
 */
public enum Phase {

  /** Angefragt, aber noch ohne festgeschriebenes Dokument. */
  ANBAHNUNG,

  /** Mindestens ein Angebot ist festgeschrieben (Kriterium 22). */
  ANGEBOT,

  /** Mindestens ein Auftrag haengt am Vorgang (Kriterium 10). */
  AUFTRAG
}
