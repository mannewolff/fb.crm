package org.mwolff.fbcrm.eigeneangaben;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Prueft die Migration {@code V5__eigene_angaben.sql} gegen eine echte PostgreSQL-Instanz.
 *
 * <p>Der Gegenstand ist das Schema selbst — die eine Zeile, der Riegel gegen eine zweite und die
 * Tatsache, dass jede Fachangabe fehlen darf. Deshalb laeuft der Nachweis ueber {@link
 * JdbcTemplate} mit direkten Anweisungen und nicht ueber den Adapter.
 *
 * <p>Der Ausgangszustand wird vor jeder Methode wiederhergestellt, wie ihn die Migration
 * hinterlaesst: Die Datenbank der Suite ist geteilt, und {@code EigeneAngabenPersistenceIT}
 * schreibt in dieselbe Zeile.
 */
class EigeneAngabenSchemaIT extends AbstractIntegrationTest {

  private final JdbcTemplate jdbc;

  @Autowired
  EigeneAngabenSchemaIT(final JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @BeforeEach
  void stelleDenStandDerMigrationHer() {
    jdbc.execute("DELETE FROM eigene_angaben");
    jdbc.execute("INSERT INTO eigene_angaben DEFAULT VALUES");
  }

  @Test
  void migration_thenTheTableExists() {
    // When
    final Integer tabellen =
        jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public'"
                + " AND table_name = 'eigene_angaben'",
            Integer.class);

    // Then
    assertThat(tabellen).isEqualTo(1);
  }

  @Test
  void migration_thenTheSingleRowIsAlreadyThere() {
    // When — der Leseweg braucht keinen Zweig „noch keine Zeile".
    final Integer zeilen =
        jdbc.queryForObject("SELECT count(*) FROM eigene_angaben", Integer.class);

    // Then
    assertThat(zeilen).isEqualTo(1);
  }

  @Test
  void migration_thenEveryBusinessColumnOfTheRowIsAbsent() {
    // When
    final Integer gesetzte =
        jdbc.queryForObject(
            "SELECT count(*) FROM eigene_angaben WHERE name IS NOT NULL OR strasse IS NOT NULL"
                + " OR plz IS NOT NULL OR ort IS NOT NULL OR land IS NOT NULL"
                + " OR email IS NOT NULL OR telefon IS NOT NULL OR steuernummer IS NOT NULL"
                + " OR umsatzsteuer_id IS NOT NULL OR bankverbindung IS NOT NULL"
                + " OR zahlungsbedingungen IS NOT NULL",
            Integer.class);

    // Then
    assertThat(gesetzte).isZero();
  }

  @Test
  void migration_thenTheRowCarriesTheFixedKeyOne() {
    // When
    final Short id = jdbc.queryForObject("SELECT id FROM eigene_angaben", Short.class);

    // Then
    assertThat(id).isEqualTo((short) 1);
  }

  @Test
  void eigeneAngaben_givenASecondRow_thenRejectedByTheDatabase() {
    // When / Then — die Selbstauskunft ist ein Stammdatum: genau ein Satz je Instanz.
    assertThatThrownBy(() -> jdbc.update("INSERT INTO eigene_angaben (id) VALUES (?)", (short) 2))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void eigeneAngaben_givenASecondRowWithTheSameKey_thenRejectedByTheDatabase() {
    // When / Then — der Primaerschluessel schliesst die zweite Zeile mit derselben Kennung aus.
    assertThatThrownBy(() -> jdbc.execute("INSERT INTO eigene_angaben DEFAULT VALUES"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
