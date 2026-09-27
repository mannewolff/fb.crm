package org.mwolff.fbcrm.angebot.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.mwolff.fbcrm.angebot.domain.BelegnummerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Der Nummernkreis der Belege gegen eine echte PostgreSQL-Instanz.
 *
 * <p>Gegenstand ist die Zusage aus Kriterium 11: Je Jahr beginnen die Nummern bei 001 und haben
 * keine Luecke. Deshalb setzt jeder Test seine Transaktionsgrenze selbst ueber {@link
 * TransactionTemplate} — ohne sie liesse sich nicht zeigen, dass ein Ruecklauf die Nummer wieder
 * freigibt und ein verworfener Entwurf damit keine Luecke reisst.
 */
class BelegnummerIT extends AbstractIntegrationTest {

  private final BelegnummerRepository repository;
  private final TransactionTemplate transaktion;
  private final JdbcTemplate jdbc;

  @Autowired
  BelegnummerIT(
      final BelegnummerRepository repository,
      final TransactionTemplate transaktion,
      final JdbcTemplate jdbc) {
    this.repository = repository;
    this.transaktion = transaktion;
    this.jdbc = jdbc;
  }

  @BeforeEach
  void leereDenNummernkreis() {
    jdbc.execute("DELETE FROM angebot_nummernkreis");
  }

  private String zugInEigenerTransaktion(final int jahr) {
    return transaktion.execute(status -> repository.zieheNummer(jahr));
  }

  @Test
  void zieheNummer_givenAYearWithoutARow_thenStartsAtOne() {
    // When — die Jahreszeile entsteht beim ersten Zug.
    final String erste = zugInEigenerTransaktion(2026);

    // Then
    assertThat(erste).isEqualTo("A-2026-001");
  }

  @Test
  void zieheNummer_givenTwoTransactionsInARow_thenCountsUp() {
    // When
    final String erste = zugInEigenerTransaktion(2026);
    final String zweite = zugInEigenerTransaktion(2026);

    // Then
    assertThat(erste).isEqualTo("A-2026-001");
    assertThat(zweite).isEqualTo("A-2026-002");
  }

  @Test
  void zieheNummer_twiceInOneTransaction_thenCountsUpAsWell() {
    // When
    final String zweite =
        transaktion.execute(
            status -> {
              repository.zieheNummer(2026);
              return repository.zieheNummer(2026);
            });

    // Then
    assertThat(zweite).isEqualTo("A-2026-002");
  }

  @Test
  void zieheNummer_afterTheTurnOfTheYear_thenStartsAtOneAgain() {
    // Given — Kriterium 11: der Kreis ist je Jahr eigen.
    zugInEigenerTransaktion(2026);
    zugInEigenerTransaktion(2026);

    // When
    final String imNeuenJahr = zugInEigenerTransaktion(2027);

    // Then
    assertThat(imNeuenJahr).isEqualTo("A-2027-001");
  }

  @Test
  void zieheNummer_afterTheTurnOfTheYear_thenTheOldYearKeepsCountingUp() {
    // Given
    zugInEigenerTransaktion(2026);
    zugInEigenerTransaktion(2027);

    // When
    final String imAltenJahr = zugInEigenerTransaktion(2026);

    // Then
    assertThat(imAltenJahr).isEqualTo("A-2026-002");
  }

  @Test
  void zieheNummer_afterARolledBackDraw_thenHandsOutTheSameNumberAgain() {
    // Given — „ein verworfener Entwurf reisst keine Luecke" (Kriterium 11).
    transaktion.execute(
        status -> {
          repository.zieheNummer(2026);
          status.setRollbackOnly();
          return null;
        });

    // When
    final String nachDemRuecklauf = zugInEigenerTransaktion(2026);

    // Then
    assertThat(nachDemRuecklauf).isEqualTo("A-2026-001");
  }

  @Test
  void zieheNummer_thenWritesTheFollowingNumberIntoTheYearRow() {
    // When
    zugInEigenerTransaktion(2026);

    // Then
    assertThat(
            jdbc.queryForObject(
                "SELECT naechste FROM angebot_nummernkreis WHERE jahr = 2026", Long.class))
        .isEqualTo(2L);
  }
}
