package org.mwolff.fbcrm.mail.domain;

import java.time.Duration;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.common.Identifiable;

/**
 * Ein Zustellauftrag im Postausgangsfach (E7).
 *
 * <p>Unveraenderlich: {@link #delivered} und {@link #failed} liefern einen neuen Auftrag, statt
 * diesen zu aendern. Der Zeitpunkt kommt von aussen, weil die Domaene keine Uhr kennt
 * (CLAUDE-java.md §6.2).
 *
 * <p><b>Der wachsende Abstand.</b> Jeder Fehlversuch verdoppelt die Wartezeit bis zum naechsten —
 * ein Mailserver, der gerade neu startet, ist nach zwei Minuten wieder da, einer mit falscher
 * Zugangsangabe nie. Waere der Abstand konstant, liefe der zweite Fall im Takt des Pollings und
 * haette seine Versuche in einer halben Minute verbraucht. Ab {@link #MAX_BACKOFF_SHIFT}
 * Verdopplungen bleibt der Abstand stehen; die Verdopplung liefe sonst in Abstaende, die keine
 * Zustellung mehr erleben.
 *
 * @param id technische Id — {@code null}, solange der Auftrag nicht gespeichert ist
 * @param recipient Empfaengeradresse
 * @param subject Betreff
 * @param body Rumpf der Nachricht
 * @param attempts Zahl der bereits unternommenen Zustellversuche
 * @param nextAttemptAt Zeitpunkt, ab dem der naechste Versuch faellig ist
 * @param sentAt Zeitpunkt der Zustellung — {@code null}, solange der Auftrag offen ist
 * @param createdAt Zeitpunkt der Anlage
 */
public record OutboxMessage(
    @Nullable Long id,
    String recipient,
    String subject,
    String body,
    int attempts,
    Instant nextAttemptAt,
    @Nullable Instant sentAt,
    Instant createdAt)
    implements Identifiable {

  /** Zahl der Verdopplungen, ab der der Wiederholungsabstand stehen bleibt. */
  public static final int MAX_BACKOFF_SHIFT = 6;

  /**
   * Ein frischer Auftrag, sofort faellig und ohne Versuch.
   *
   * @param recipient Empfaengeradresse
   * @param subject Betreff
   * @param body Rumpf der Nachricht
   * @param jetzt Zeitpunkt der Anlage
   */
  public static OutboxMessage pending(
      final String recipient, final String subject, final String body, final Instant jetzt) {
    return new OutboxMessage(null, recipient, subject, body, 0, jetzt, null, jetzt);
  }

  /**
   * Der Auftrag als zugestellt.
   *
   * @param zeitpunkt Zeitpunkt der Zustellung
   */
  public OutboxMessage delivered(final Instant zeitpunkt) {
    return new OutboxMessage(
        id, recipient, subject, body, attempts, nextAttemptAt, zeitpunkt, createdAt);
  }

  /**
   * Der Auftrag nach einem vergeblichen Versuch: einer mehr, und der naechste liegt weiter weg.
   *
   * @param jetzt Zeitpunkt des Fehlversuchs
   * @param basisAbstand Abstand bis zum ersten Wiederholungsversuch
   */
  public OutboxMessage failed(final Instant jetzt, final Duration basisAbstand) {
    final int versuche = attempts + 1;
    final long vielfaches = 1L << Math.min(versuche - 1, MAX_BACKOFF_SHIFT);
    return new OutboxMessage(
        id,
        recipient,
        subject,
        body,
        versuche,
        jetzt.plus(basisAbstand.multipliedBy(vielfaches)),
        sentAt,
        createdAt);
  }

  /**
   * Ob der Auftrag seine Versuche aufgebraucht hat und liegen bleibt.
   *
   * @param maxAttempts Zahl der erlaubten Versuche
   */
  public boolean isExhausted(final int maxAttempts) {
    return attempts >= maxAttempts;
  }
}
