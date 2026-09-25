package org.mwolff.fbcrm.vorgang.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Der Nummernkreis gegen eine echte PostgreSQL-Instanz.
 *
 * <p>Gegenstand ist die Zusage aus E3: Nummern ab 1 ohne Luecke, gezogen in der Transaktion des
 * Aufrufers. Deshalb setzt jeder Test seine Transaktionsgrenze selbst ueber {@link
 * TransactionTemplate} — ohne sie liesse sich nicht zeigen, dass ein Ruecklauf die Nummer wieder
 * freigibt (Kriterium 8).
 */
class JpaNummernkreisRepositoryIT extends AbstractIntegrationTest {

  private final JpaNummernkreisRepository repository;
  private final TransactionTemplate transaktion;
  private final JdbcTemplate jdbc;
  private final DataSource dataSource;

  @Autowired
  JpaNummernkreisRepositoryIT(
      final JpaNummernkreisRepository repository,
      final TransactionTemplate transaktion,
      final JdbcTemplate jdbc,
      final DataSource dataSource) {
    this.repository = repository;
    this.transaktion = transaktion;
    this.jdbc = jdbc;
    this.dataSource = dataSource;
  }

  @BeforeEach
  void setzeDenZaehlerZurueck() {
    jdbc.update("UPDATE vorgang_nummernkreis SET naechste = 1");
  }

  private long zugInEigenerTransaktion() {
    return transaktion.execute(status -> Long.valueOf(repository.naechsteNummer()));
  }

  @Test
  void naechsteNummer_givenTwoTransactionsInARow_thenCountsUpFromOne() {
    // When
    final long erster = zugInEigenerTransaktion();
    final long zweiter = zugInEigenerTransaktion();

    // Then
    assertThat(erster).isEqualTo(1L);
    assertThat(zweiter).isEqualTo(2L);
  }

  @Test
  void naechsteNummer_twiceInOneTransaction_thenCountsUpAsWell() {
    // When
    final List<Long> gezogen =
        transaktion.execute(
            status ->
                List.of(
                    Long.valueOf(repository.naechsteNummer()),
                    Long.valueOf(repository.naechsteNummer())));

    // Then
    assertThat(gezogen).containsExactly(1L, 2L);
  }

  @Test
  void naechsteNummer_afterARolledBackDraw_thenHandsOutTheSameNumberAgain() {
    // Given
    transaktion.execute(
        status -> {
          repository.naechsteNummer();
          status.setRollbackOnly();
          return null;
        });

    // When
    final long nachDemRuecklauf = zugInEigenerTransaktion();

    // Then
    assertThat(nachDemRuecklauf).isEqualTo(1L);
  }

  @Test
  void naechsteNummer_thenWritesTheFollowingNumberIntoTheCounterRow() {
    // When
    zugInEigenerTransaktion();

    // Then
    assertThat(jdbc.queryForObject("SELECT naechste FROM vorgang_nummernkreis", Long.class))
        .isEqualTo(2L);
  }

  @Test
  void naechsteNummer_whileTheTransactionIsStillOpen_thenTheCounterRowIsLockedForOthers() {
    // When / Then
    transaktion.execute(
        status -> {
          repository.naechsteNummer();
          assertThatThrownBy(this::sperreOhneWarten)
              .isInstanceOf(SQLException.class)
              .hasMessageContaining("vorgang_nummernkreis");
          return null;
        });
  }

  /**
   * Greift die Zaehlerzeile von einer <b>zweiten</b> Verbindung aus mit {@code FOR UPDATE NOWAIT}.
   *
   * <p>{@code NOWAIT} macht den Nachweis deterministisch statt zeitabhaengig: Haelt ein anderer die
   * Zeile, meldet Postgres das sofort, statt zu warten. Scheitert dieser Griff nicht, dann hat
   * {@code sperreUndLies} keine Sperre gesetzt — und die Zusage aus E3 waere nur behauptet.
   */
  private void sperreOhneWarten() throws SQLException {
    try (Connection zweite = dataSource.getConnection();
        Statement anweisung = zweite.createStatement()) {
      zweite.setAutoCommit(false);
      anweisung.execute("SELECT naechste FROM vorgang_nummernkreis WHERE id = 1 FOR UPDATE NOWAIT");
      zweite.rollback();
    }
  }
}
