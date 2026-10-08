package org.mwolff.fbcrm.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.mwolff.fbcrm.auth.domain.Account;
import org.mwolff.fbcrm.auth.domain.AccountRepository;
import org.mwolff.fbcrm.auth.domain.PasswordHasher;
import org.mwolff.fbcrm.auth.domain.Role;
import org.mwolff.fbcrm.mail.application.EnqueueMailUseCase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;

/**
 * Die Anforderung eines neuen Passworts gegen die echte Datenbank (K7, E7, E25).
 *
 * <p>Zwei Zusagen lassen sich nur hier einloesen. Die erste: Bei unbekannter Adresse entsteht
 * <b>kein</b> Auftrag im Postausgangsfach, bei bekannter genau einer — gezaehlt wird in der
 * Tabelle, nicht an einem Mock. Die zweite: Ein Zustellauftrag laesst sich gar nicht erst
 * ausserhalb einer Transaktion einstellen; {@code Propagation.MANDATORY} verwandelt die Zusage „in
 * derselben Transaktion" aus E7 in einen Riegel, den ein spaeterer Aufrufer nicht versehentlich
 * umgeht.
 */
class RequestPasswordResetUseCaseIT extends AbstractIntegrationTest {

  private static final String MAIL = "manne@example.org";

  private final RequestPasswordResetUseCase anforderung;
  private final EnqueueMailUseCase postausgang;
  private final AccountRepository accounts;
  private final PasswordHasher hasher;
  private final JdbcTemplate jdbc;

  @Autowired
  RequestPasswordResetUseCaseIT(
      final RequestPasswordResetUseCase anforderung,
      final EnqueueMailUseCase postausgang,
      final AccountRepository accounts,
      final PasswordHasher hasher,
      final JdbcTemplate jdbc) {
    this.anforderung = anforderung;
    this.postausgang = postausgang;
    this.accounts = accounts;
    this.hasher = hasher;
    this.jdbc = jdbc;
  }

  @BeforeEach
  void legeDasAdminKontoAn() {
    jdbc.execute("TRUNCATE outbox_message, password_reset_token, account RESTART IDENTITY CASCADE");
    final Instant jetzt = Instant.parse("2026-09-18T10:00:00Z");
    accounts.save(
        new Account(
            null, MAIL, "Manne", hasher.hash("richtiges-passwort"), Role.ADMIN, 0, jetzt, jetzt));
  }

  private List<Map<String, Object>> postausgangsfach() {
    return jdbc.queryForList("SELECT recipient, attempts, sent_at FROM outbox_message");
  }

  @Test
  void request_givenAKnownAddress_thenCreatesExactlyOneDeliveryOrder() {
    // When
    anforderung.request(MAIL);

    // Then
    assertThat(postausgangsfach()).hasSize(1);
  }

  @Test
  void request_givenAKnownAddress_thenAddressesTheOrderToTheAccountsAddress() {
    // When
    anforderung.request(MAIL);

    // Then
    assertThat(postausgangsfach().getFirst()).containsEntry("recipient", MAIL);
  }

  @Test
  void request_givenAKnownAddress_thenTheOrderIsStillWaitingWithoutAnyAttempt() {
    // When — bei FBCRM_MAIL_ENABLED=false laeuft kein Versand; der Auftrag bleibt unangetastet
    // liegen, statt seine Versuche gegen einen leeren SMTP-Host zu verbrennen.
    anforderung.request(MAIL);

    // Then
    assertThat(postausgangsfach().getFirst())
        .containsEntry("attempts", 0)
        .containsEntry("sent_at", null);
  }

  @Test
  void request_givenAKnownAddress_thenStoresTheTokenAsAHashOnly() {
    // When
    anforderung.request(MAIL);

    // Then — in der Tabelle steht der Hash, und der Rumpf der Mail enthaelt ihn nicht.
    final String hash =
        jdbc.queryForObject("SELECT token_hash FROM password_reset_token", String.class);
    final String rumpf = jdbc.queryForObject("SELECT body FROM outbox_message", String.class);
    assertThat(rumpf).isNotNull().doesNotContain(String.valueOf(hash));
  }

  @Test
  void request_givenAnUnknownAddress_thenCreatesNoDeliveryOrderAtAll() {
    // When
    anforderung.request("fremd@example.org");

    // Then
    assertThat(postausgangsfach()).isEmpty();
  }

  @Test
  void request_givenAnUnknownAddress_thenCreatesNoTokenAtAll() {
    // When
    anforderung.request("fremd@example.org");

    // Then
    assertThat(jdbc.queryForObject("SELECT count(*) FROM password_reset_token", Long.class))
        .isZero();
  }

  @Test
  void enqueue_givenNoSurroundingTransaction_thenRefusesToWriteAnything() {
    // When / Then — E7: der Auftrag gehoert in die Transaktion des Aufrufers, sonst nirgendwohin.
    assertThatExceptionOfType(IllegalTransactionStateException.class)
        .isThrownBy(() -> postausgang.enqueue(MAIL, "Betreff", "Rumpf"));
  }
}
