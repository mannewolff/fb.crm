package org.mwolff.fbcrm.mail.domain;

import java.time.Instant;
import java.util.List;

/**
 * Port auf den Bestand der Zustellauftraege; die Umsetzung liegt in {@code mail.infrastructure}.
 */
public interface OutboxRepository {

  /** Legt den Auftrag an oder schreibt ihn fort und liefert ihn mit gesetzter Id zurueck. */
  OutboxMessage save(OutboxMessage nachricht);

  /**
   * Die offenen Auftraege, die jetzt an der Reihe sind — aeltester zuerst.
   *
   * @param jetzt Zeitpunkt des Durchgangs
   * @param maxAttempts Zahl der erlaubten Versuche; wer sie aufgebraucht hat, bleibt liegen
   * @param limit Obergrenze der zurueckgegebenen Auftraege
   */
  List<OutboxMessage> findDue(Instant jetzt, int maxAttempts, int limit);

  /**
   * Loescht <b>zugestellte</b> Auftraege, die vor dem Zeitpunkt hinausgingen.
   *
   * <p>Was nie zugestellt wurde, bleibt — es ist der einzige Beleg dafuer, dass der Mailweg dieser
   * Instanz nicht funktioniert.
   *
   * @param grenze Zeitpunkt, vor dem Zugestelltes entfaellt
   * @return Zahl der geloeschten Zeilen
   */
  int deleteSentBefore(Instant grenze);
}
