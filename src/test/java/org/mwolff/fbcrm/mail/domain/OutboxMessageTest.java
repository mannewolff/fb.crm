package org.mwolff.fbcrm.mail.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Der Zustellauftrag als Fachobjekt (E7).
 *
 * <p>Zwei Aussagen tragen dieses Objekt. Die erste: Ein fehlgeschlagener Versuch zaehlt {@code
 * attempts} hoch <b>und</b> schiebt den naechsten Termin weiter — wuerde nur gezaehlt, liefe der
 * Auftrag im Takt des Pollings gegen einen Mailserver, der ohnehin nicht antwortet. Die zweite: Der
 * Abstand waechst, bis er an der Deckelung haengenbleibt; ohne Deckelung liefe er in eine
 * Verdopplung, die keine Zustellung mehr erlebt.
 */
class OutboxMessageTest {

  private static final Instant ANGELEGT = Instant.parse("2026-09-18T10:00:00Z");
  private static final Instant JETZT = Instant.parse("2026-09-18T10:05:00Z");
  private static final Duration BASIS = Duration.ofSeconds(5);

  private static OutboxMessage mitVersuchen(final int versuche) {
    return new OutboxMessage(
        7L, "manne@example.org", "Betreff", "Rumpf", versuche, ANGELEGT, null, ANGELEGT);
  }

  @Test
  void pending_thenStartsWithoutAnyAttempt() {
    // When
    final OutboxMessage auftrag =
        OutboxMessage.pending("manne@example.org", "Betreff", "Rumpf", ANGELEGT);

    // Then
    assertThat(auftrag.attempts()).isZero();
  }

  @Test
  void pending_thenIsDueImmediately() {
    // When — der Auftrag soll im naechsten Durchgang mitgenommen werden, nicht spaeter.
    final OutboxMessage auftrag =
        OutboxMessage.pending("manne@example.org", "Betreff", "Rumpf", ANGELEGT);

    // Then
    assertThat(auftrag.nextAttemptAt()).isEqualTo(ANGELEGT);
  }

  @Test
  void pending_thenCarriesRecipientSubjectAndBody() {
    // When
    final OutboxMessage auftrag =
        OutboxMessage.pending("manne@example.org", "Betreff", "Rumpf", ANGELEGT);

    // Then
    assertThat(auftrag)
        .extracting(OutboxMessage::recipient, OutboxMessage::subject, OutboxMessage::body)
        .containsExactly("manne@example.org", "Betreff", "Rumpf");
  }

  @Test
  void pending_thenHasNoIdAndNoDeliveryYet() {
    // When
    final OutboxMessage auftrag =
        OutboxMessage.pending("manne@example.org", "Betreff", "Rumpf", ANGELEGT);

    // Then
    assertThat(auftrag)
        .extracting(OutboxMessage::id, OutboxMessage::sentAt, OutboxMessage::createdAt)
        .containsExactly(null, null, ANGELEGT);
  }

  @Test
  void delivered_thenStampsTheDeliveryTime() {
    // When
    final OutboxMessage zugestellt = mitVersuchen(1).delivered(JETZT);

    // Then
    assertThat(zugestellt.sentAt()).isEqualTo(JETZT);
  }

  @Test
  void delivered_thenLeavesEverythingElseUntouched() {
    // Given
    final OutboxMessage offen = mitVersuchen(1);

    // When
    final OutboxMessage zugestellt = offen.delivered(JETZT);

    // Then
    assertThat(zugestellt)
        .isEqualTo(
            new OutboxMessage(
                offen.id(),
                offen.recipient(),
                offen.subject(),
                offen.body(),
                1,
                offen.nextAttemptAt(),
                JETZT,
                offen.createdAt()));
  }

  @Test
  void failed_thenCountsTheAttempt() {
    // When
    final OutboxMessage gescheitert = mitVersuchen(2).failed(JETZT, BASIS);

    // Then
    assertThat(gescheitert.attempts()).isEqualTo(3);
  }

  @Test
  void failed_givenTheFirstAttempt_thenWaitsOneBaseInterval() {
    // When
    final OutboxMessage gescheitert = mitVersuchen(0).failed(JETZT, BASIS);

    // Then
    assertThat(gescheitert.nextAttemptAt()).isEqualTo(JETZT.plusSeconds(5));
  }

  @Test
  void failed_givenTheSecondAttempt_thenDoublesTheInterval() {
    // When
    final OutboxMessage gescheitert = mitVersuchen(1).failed(JETZT, BASIS);

    // Then
    assertThat(gescheitert.nextAttemptAt()).isEqualTo(JETZT.plusSeconds(10));
  }

  @Test
  void failed_givenTheAttemptAtTheCap_thenStillDoubles() {
    // Given — der siebte Versuch ist der letzte, der verdoppelt: 2^6 = 64.
    // When
    final OutboxMessage gescheitert = mitVersuchen(6).failed(JETZT, BASIS);

    // Then
    assertThat(gescheitert.nextAttemptAt()).isEqualTo(JETZT.plusSeconds(5 * 64));
  }

  @Test
  void failed_givenAnAttemptBeyondTheCap_thenKeepsTheCappedInterval() {
    // Given — ohne Deckelung waere der Abstand hier bereits bei 2^9 Basisintervallen.
    // When
    final OutboxMessage gescheitert = mitVersuchen(9).failed(JETZT, BASIS);

    // Then
    assertThat(gescheitert.nextAttemptAt()).isEqualTo(JETZT.plusSeconds(5 * 64));
  }

  @Test
  void failed_thenNeverMarksTheMessageAsDelivered() {
    // When
    final OutboxMessage gescheitert = mitVersuchen(0).failed(JETZT, BASIS);

    // Then
    assertThat(gescheitert.sentAt()).isNull();
  }

  @Test
  void isExhausted_givenFewerAttemptsThanAllowed_thenFalse() {
    // When / Then
    assertThat(mitVersuchen(7).isExhausted(8)).isFalse();
  }

  @Test
  void isExhausted_givenExactlyTheAllowedAttempts_thenTrue() {
    // When / Then — „nach maxAttempts endet der Versuch" heisst: der achte war der letzte.
    assertThat(mitVersuchen(8).isExhausted(8)).isTrue();
  }

  @Test
  void isExhausted_givenMoreAttemptsThanAllowed_thenTrue() {
    // When / Then
    assertThat(mitVersuchen(9).isExhausted(8)).isTrue();
  }
}
