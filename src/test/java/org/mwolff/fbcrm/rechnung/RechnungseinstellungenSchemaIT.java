package org.mwolff.fbcrm.rechnung;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Prueft die Migration {@code V14__rechnung_einstellungen.sql} gegen eine echte PostgreSQL-Instanz.
 *
 * <p>Der Gegenstand ist das Schema selbst — die eine Zeile mit ihren Vorbelegungen, der Riegel
 * gegen eine zweite und die CHECKs der drei Zahlenspalten. Deshalb laeuft der Nachweis ueber {@link
 * JdbcTemplate} mit direkten Anweisungen und nicht ueber den Adapter.
 *
 * <p>Der Ausgangszustand wird vor jeder Methode wiederhergestellt, wie ihn die Migration
 * hinterlaesst: Die Datenbank der Suite ist geteilt, und ein Test, der in dieselbe Zeile schreibt,
 * nahm der Probe „frische Instanz" sonst ihre Aussage.
 */
class RechnungseinstellungenSchemaIT extends AbstractIntegrationTest {

  private final JdbcTemplate jdbc;

  @Autowired
  RechnungseinstellungenSchemaIT(final JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @BeforeEach
  void stelleDenStandDerMigrationHer() {
    jdbc.execute("DELETE FROM rechnung_einstellungen");
    jdbc.execute("INSERT INTO rechnung_einstellungen DEFAULT VALUES");
  }

  @Test
  void migration_thenTheTableExists() {
    // When
    final Integer tabellen =
        jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public'"
                + " AND table_name = 'rechnung_einstellungen'",
            Integer.class);

    // Then
    assertThat(tabellen).isEqualTo(1);
  }

  @Test
  void migration_thenTheSingleRowCarriesTheDefaults() {
    // When — Kriterium 11: die Vorbelegungen stehen nach der Migration schon da.
    final Map<String, Object> zeile =
        jdbc.queryForMap(
            "SELECT nummer_muster, naechste_nummer, steuersatz,"
                + " zahlungsziel_tage FROM rechnung_einstellungen");

    // Then
    assertThat(zeile)
        .containsEntry("nummer_muster", "{NNNN}-{JJJJ}")
        .containsEntry("naechste_nummer", 1)
        .containsEntry("zahlungsziel_tage", 10);
    assertThat((BigDecimal) zeile.get("steuersatz")).isEqualByComparingTo("19.00");
  }

  @Test
  void migration_thenTheRowCarriesTheFixedKeyOne() {
    // When
    final Short id = jdbc.queryForObject("SELECT id FROM rechnung_einstellungen", Short.class);

    // Then
    assertThat(id).isEqualTo((short) 1);
  }

  @Test
  void migration_thenThereIsExactlyOneRow() {
    // When — der Leseweg braucht keinen Zweig „noch keine Zeile".
    final Integer zeilen =
        jdbc.queryForObject("SELECT count(*) FROM rechnung_einstellungen", Integer.class);

    // Then
    assertThat(zeilen).isEqualTo(1);
  }

  @Test
  void einstellungen_givenASecondRow_thenRejectedByTheDatabase() {
    // When / Then — die Einstellungen sind ein Stammdatum: genau ein Satz je Instanz.
    assertThatThrownBy(
            () -> jdbc.update("INSERT INTO rechnung_einstellungen (id) VALUES (?)", (short) 2))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @ParameterizedTest
  @CsvSource({
    "naechste_nummer, 0",
    "steuersatz, -0.01",
    "steuersatz, 100.01",
    "zahlungsziel_tage, -1"
  })
  void einstellungen_givenAValueOutsideItsRange_thenRejectedByTheDatabase(
      final String spalte, final String wert) {
    // When / Then — die Grenzen aus den Kriterien 7 bis 9 stehen als CHECK in der Tabelle.
    assertThatThrownBy(
            () -> jdbc.execute("UPDATE rechnung_einstellungen SET " + spalte + " = " + wert))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
