package org.mwolff.fbcrm.rechnung;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.mwolff.fbcrm.rechnung.domain.Nummernmuster;
import org.mwolff.fbcrm.rechnung.domain.Rechnungseinstellungen;
import org.mwolff.fbcrm.rechnung.domain.RechnungseinstellungenRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Der Port auf den Bestand gegen eine echte PostgreSQL-Instanz.
 *
 * <p>Zwei Zusagen sind nur hier pruefbar, weil sie am Zusammenspiel von Abbildung und Schema
 * haengen und nicht an der Abbildung allein ({@code JpaRechnungseinstellungenRepositoryTest} gegen
 * ein gemocktes Spring-Data-Repository). Erstens: Auf der frischen Datenbank liest der Port ohne
 * Sonderzweig die Vorbelegungen — die Migration hat die eine Zeile schon angelegt. Zweitens: {@code
 * speichere} schreibt die bestehende Zeile fort und legt keine zweite an; dass die Kennung von
 * Anfang an steht, macht aus dem {@code save} ein UPDATE.
 *
 * <p>Der Ausgangszustand wird vor jeder Methode wiederhergestellt, wie ihn die Migration
 * hinterlaesst; die Datenbank der Suite ist geteilt.
 */
class RechnungseinstellungenPersistenceIT extends AbstractIntegrationTest {

  private static final Instant GEAENDERT = Instant.parse("2026-09-30T12:00:00Z");

  private final RechnungseinstellungenRepository repository;
  private final JdbcTemplate jdbc;

  @Autowired
  RechnungseinstellungenPersistenceIT(
      final RechnungseinstellungenRepository repository, final JdbcTemplate jdbc) {
    this.repository = repository;
    this.jdbc = jdbc;
  }

  @BeforeEach
  void stelleDenStandDerMigrationHer() {
    jdbc.execute("DELETE FROM rechnung_einstellungen");
    jdbc.execute("INSERT INTO rechnung_einstellungen DEFAULT VALUES");
  }

  @Test
  void lies_givenTheFreshDatabase_thenAnswersWithTheDefaults() {
    // When — Kriterium 11: die Vorbelegungen sind von Anfang an da.
    final Rechnungseinstellungen gelesen = repository.lies();

    // Then
    assertThat(gelesen)
        .isEqualTo(
            new Rechnungseinstellungen(
                new Nummernmuster("{NNNN}-{JJJJ}"), 1, new BigDecimal("19.00"), 10));
  }

  @Test
  void speichere_thenLiesAnswersWithTheStoredValues() {
    // Given
    final Rechnungseinstellungen neu =
        new Rechnungseinstellungen(
            new Nummernmuster("R{JJ}-{NNNN}"), 4, new BigDecimal("19.50"), 14);

    // When
    repository.speichere(neu, GEAENDERT);

    // Then
    assertThat(repository.lies()).isEqualTo(neu);
  }

  @Test
  void speichere_thenUpdatesTheOneRowAndAddsNoSecond() {
    // When — die Kennung steht von Anfang an, das save ist ein UPDATE.
    repository.speichere(
        new Rechnungseinstellungen(new Nummernmuster("{N}"), 7, new BigDecimal("7.00"), 0),
        GEAENDERT);

    // Then
    final Integer zeilen =
        jdbc.queryForObject("SELECT count(*) FROM rechnung_einstellungen", Integer.class);
    final OffsetDateTime zuletzt =
        jdbc.queryForObject("SELECT updated_at FROM rechnung_einstellungen", OffsetDateTime.class);
    assertThat(zeilen).isEqualTo(1);
    assertThat(zuletzt).isNotNull();
    assertThat(zuletzt.toInstant()).isEqualTo(GEAENDERT);
  }
}
