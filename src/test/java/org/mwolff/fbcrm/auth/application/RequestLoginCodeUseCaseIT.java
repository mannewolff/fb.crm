package org.mwolff.fbcrm.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.mwolff.fbcrm.mail.application.EnqueueMailUseCase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;

/**
 * Die Ausstellung eines Einmalcodes gegen die echte Datenbank (Kriterium 4, E12).
 *
 * <p>Geprueft wird, was der Bestand im Speicher von {@code RequestLoginCodeUseCaseTest} nicht sehen
 * kann: Hibernate schreibt eine Zeile mit {@code IDENTITY}-Id sofort, ein Loeschen dagegen erst
 * beim Flush. Entwerten und Anlegen in einer Transaktion stiessen sonst an den eindeutigen Index
 * {@code login_code_email_key}, sobald es zu der Adresse schon einen Code gibt.
 *
 * <p>Ebenso nur hier: Ein Zustellauftrag laesst sich gar nicht erst ausserhalb einer Transaktion
 * einstellen. {@code Propagation.MANDATORY} verwandelt die Zusage „in einer Transaktion" aus E12 in
 * einen Riegel, den ein spaeterer Aufrufer nicht versehentlich umgeht.
 */
class RequestLoginCodeUseCaseIT extends AbstractIntegrationTest {

  private static final String MAIL = "manne@example.org";

  private final RequestLoginCodeUseCase anwendungsfall;
  private final EnqueueMailUseCase postausgang;
  private final JdbcTemplate jdbc;

  @Autowired
  RequestLoginCodeUseCaseIT(
      final RequestLoginCodeUseCase anwendungsfall,
      final EnqueueMailUseCase postausgang,
      final JdbcTemplate jdbc) {
    this.anwendungsfall = anwendungsfall;
    this.postausgang = postausgang;
    this.jdbc = jdbc;
  }

  @BeforeEach
  void leereTabelle() {
    jdbc.execute("TRUNCATE login_code RESTART IDENTITY");
  }

  @Test
  void ausstellen_givenAPreviousCodeInAnotherCase_thenLeavesExactlyOneRowForTheAddress() {
    // Given
    anwendungsfall.ausstellen(MAIL, "Manne");

    // When
    anwendungsfall.ausstellen("Manne@Example.ORG", "Manne");

    // Then
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM login_code WHERE lower(email) = ?", Long.class, MAIL))
        .isEqualTo(1L);
  }

  @Test
  void enqueue_givenNoSurroundingTransaction_thenRefusesToWriteAnything() {
    // When / Then — der Auftrag gehoert in die Transaktion des Aufrufers, sonst nirgendwohin.
    assertThatExceptionOfType(IllegalTransactionStateException.class)
        .isThrownBy(() -> postausgang.enqueue(MAIL, "Betreff", "Rumpf"));
  }
}
