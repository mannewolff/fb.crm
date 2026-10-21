package org.mwolff.fbcrm.rechnung;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Prueft die Migration {@code V26__briefbogen.sql} gegen eine echte PostgreSQL-Instanz.
 *
 * <p>Der Gegenstand ist das Schema selbst — die eine Zeile mit den Vorgaben aus Kriterium 15 der
 * fachlichen Quelle #361, der Riegel gegen eine zweite und die CHECKs auf Raendern, Seitenzahl und
 * dem Blatt, das nur ganz oder gar nicht dasteht. Deshalb laeuft der Nachweis ueber {@link
 * JdbcTemplate} und nicht ueber den Adapter.
 *
 * <p>Der Ausgangszustand wird vor jeder Methode wiederhergestellt, wie ihn die Migration
 * hinterlaesst; die Datenbank der Suite ist geteilt.
 */
class BriefbogenSchemaIT extends AbstractIntegrationTest {

  private final JdbcTemplate jdbc;

  @Autowired
  BriefbogenSchemaIT(final JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @BeforeEach
  void stelleDenStandDerMigrationHer() {
    jdbc.execute("DELETE FROM briefbogen");
    jdbc.execute("INSERT INTO briefbogen DEFAULT VALUES");
  }

  @Test
  void migration_thenTheTableExists() {
    // When
    final Integer tabellen =
        jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public'"
                + " AND table_name = 'briefbogen'",
            Integer.class);

    // Then
    assertThat(tabellen).isEqualTo(1);
  }

  @Test
  void migration_thenThereIsExactlyOneRowWithKeyOne() {
    // When
    final Integer zeilen = jdbc.queryForObject("SELECT count(*) FROM briefbogen", Integer.class);
    final Short id = jdbc.queryForObject("SELECT id FROM briefbogen", Short.class);

    // Then
    assertThat(zeilen).isEqualTo(1);
    assertThat(id).isEqualTo((short) 1);
  }

  @Test
  void migration_thenTheRowCarriesTheDefaultsAndNoBlatt() {
    // When — Kriterium 15 und E15: Vorgaben und beide Schalter an, noch kein Briefpapier.
    final Map<String, Object> zeile = jdbc.queryForMap("SELECT * FROM briefbogen");

    // Then
    assertThat(zeile)
        .containsEntry("rand_links", 250)
        .containsEntry("rand_rechts", 200)
        .containsEntry("inhalt_oben", 985)
        .containsEntry("rand_unten", 200)
        .containsEntry("folge_oben", 200)
        .containsEntry("folge_unten", 200)
        .containsEntry("absenderzeile", true)
        .containsEntry("steuer_bank_zeile", true)
        .containsEntry("pdf_schluessel", null)
        .containsEntry("dateiname", null)
        .containsEntry("groesse", null)
        .containsEntry("seiten", null);
    assertThat(zeile.get("updated_at")).isNotNull();
  }

  @Test
  void briefbogen_givenASecondRow_thenRejectedByTheDatabase() {
    // When / Then
    assertThatThrownBy(() -> jdbc.update("INSERT INTO briefbogen (id) VALUES (?)", (short) 2))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "rand_links",
        "rand_rechts",
        "inhalt_oben",
        "rand_unten",
        "folge_oben",
        "folge_unten"
      })
  void briefbogen_givenANegativeMargin_thenRejectedByTheDatabase(final String spalte) {
    // When / Then — Kriterium 13, erster Satz.
    assertThatThrownBy(() -> jdbc.execute("UPDATE briefbogen SET " + spalte + " = -1"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "rand_links",
        "rand_rechts",
        "inhalt_oben",
        "rand_unten",
        "folge_oben",
        "folge_unten"
      })
  void briefbogen_givenAZeroMargin_thenAccepted(final String spalte) {
    // When
    jdbc.execute("UPDATE briefbogen SET " + spalte + " = 0");

    // Then
    assertThat(jdbc.queryForObject("SELECT " + spalte + " FROM briefbogen", Integer.class))
        .isZero();
  }

  @Test
  void briefbogen_givenACompleteBlatt_thenAccepted() {
    // When
    jdbc.update(
        "UPDATE briefbogen SET pdf_schluessel = ?, dateiname = ?, groesse = ?, seiten = ?",
        "briefpapier/abc.pdf",
        "kopf.pdf",
        1_024L,
        (short) 2);

    // Then
    assertThat(jdbc.queryForObject("SELECT seiten FROM briefbogen", Short.class))
        .isEqualTo((short) 2);
  }

  @Test
  void briefbogen_givenThreePages_thenRejectedByTheDatabase() {
    // When / Then — das Briefpapier hat eine oder zwei Seiten.
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE briefbogen SET pdf_schluessel = ?, dateiname = ?, groesse = ?,"
                        + " seiten = ?",
                    "briefpapier/abc.pdf",
                    "kopf.pdf",
                    1_024L,
                    (short) 3))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "pdf_schluessel = 'briefpapier/abc.pdf'",
        "dateiname = 'kopf.pdf'",
        "groesse = 1024",
        "seiten = 1"
      })
  void briefbogen_givenAPartialBlatt_thenRejectedByTheDatabase(final String zuweisung) {
    // When / Then — das Blatt steht ganz oder gar nicht da.
    assertThatThrownBy(() -> jdbc.execute("UPDATE briefbogen SET " + zuweisung))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
