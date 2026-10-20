package org.mwolff.fbcrm.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Die Ausstellung eines Einmalcodes gegen die echte Datenbank (Kriterium 4, E12).
 *
 * <p>Geprueft wird, was der Bestand im Speicher von {@code RequestLoginCodeUseCaseTest} nicht sehen
 * kann: Hibernate schreibt eine Zeile mit {@code IDENTITY}-Id sofort, ein Loeschen dagegen erst
 * beim Flush. Entwerten und Anlegen in einer Transaktion stiessen sonst an den eindeutigen Index
 * {@code login_code_email_key}, sobald es zu der Adresse schon einen Code gibt.
 */
class RequestLoginCodeUseCaseIT extends AbstractIntegrationTest {

  private static final String MAIL = "manne@example.org";

  private final RequestLoginCodeUseCase anwendungsfall;
  private final JdbcTemplate jdbc;

  @Autowired
  RequestLoginCodeUseCaseIT(final RequestLoginCodeUseCase anwendungsfall, final JdbcTemplate jdbc) {
    this.anwendungsfall = anwendungsfall;
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
}
