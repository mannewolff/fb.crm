package org.mwolff.fbcrm.eigeneangaben;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Prueft {@code db.migration.BankverbindungInFeldernMigration} am Bestand: Die bisherige
 * Bankverbindung wird in IBAN, BIC und Kontoinhaber uebernommen, soweit sie eine gueltige IBAN
 * hergibt (Plan #461, E13, E14; fachliche Quelle #460, Kriterium 11; Issue #465).
 *
 * <p>Wie in {@code ArbeitszeitMigrationIT} migriert der Test ein eigenes Schema zuerst bis Version
 * 37, setzt dort den Bestand von damals — Name und Bankverbindung als Freitext — und migriert
 * danach bis 38. Im Schema der Suite gibt es den Zustand vor V38 nicht mehr.
 */
class BankverbindungMigrationIT extends AbstractIntegrationTest {

  private static final String PROBE = "migration_v38";

  private static final String NAME = "Manfred Wolff";

  private final JdbcTemplate jdbc;
  private final DataSource dataSource;

  @Autowired
  BankverbindungMigrationIT(final JdbcTemplate jdbc, final DataSource dataSource) {
    this.jdbc = jdbc;
    this.dataSource = dataSource;
  }

  private Flyway flywayBis(final String ziel) {
    return Flyway.configure()
        .dataSource(dataSource)
        .schemas(PROBE)
        .locations("classpath:db/migration")
        .target(ziel)
        .cleanDisabled(false)
        .load();
  }

  @BeforeEach
  void migriereBisSiebenunddreissig() {
    final Flyway bisSiebenunddreissig = flywayBis("37");
    bisSiebenunddreissig.clean();
    bisSiebenunddreissig.migrate();
  }

  @AfterEach
  void raeumeDasSchemaAb() {
    flywayBis("latest").clean();
  }

  /** Setzt den Bestand der einen Zeile und migriert bis 38. */
  private Map<String, @Nullable Object> umgestellt(final @Nullable String bankverbindung) {
    jdbc.update(
        "UPDATE " + PROBE + ".eigene_angaben SET name = ?, bankverbindung = ?",
        NAME,
        bankverbindung);
    flywayBis("38").migrate();
    return jdbc.queryForMap(
        "SELECT iban, bic, kontoinhaber, bankverbindung FROM " + PROBE + ".eigene_angaben");
  }

  private static Object[] felder(final Map<String, @Nullable Object> zeile) {
    return Arrays.stream(new String[] {"iban", "bic", "kontoinhaber", "bankverbindung"})
        .map(zeile::get)
        .toArray();
  }

  @Test
  void migration_givenIbanAndBicInTheFreeText_thenAllThreeFieldsAndTheTextStays() {
    // Given — Kriterium 11.
    final String freitext =
        "Max Muster, IBAN DE89 3704 0044 0532 0130 00, BIC COBADEFFXXX, Commerzbank";

    // When
    final Map<String, @Nullable Object> zeile = umgestellt(freitext);

    // Then — der Kontoinhaber ist der eigene Name, nicht der Name im Freitext.
    assertThat(felder(zeile))
        .containsExactly("DE89370400440532013000", "COBADEFFXXX", NAME, freitext);
  }

  @Test
  void migration_givenOnlyAnIban_thenNoBic() {
    // Given
    final String freitext = "DE89 3704 0044 0532 0130 00";

    // When / Then
    assertThat(felder(umgestellt(freitext)))
        .containsExactly("DE89370400440532013000", null, NAME, freitext);
  }

  @Test
  void migration_givenAWrongCheckDigit_thenAllThreeStayEmpty() {
    // Given
    final String freitext = "IBAN DE89 3704 0044 0532 0130 01, BIC COBADEFFXXX";

    // When / Then — der Mensch traegt die Felder in der Maske nach.
    assertThat(felder(umgestellt(freitext))).containsExactly(null, null, null, freitext);
  }

  @Test
  void migration_givenNoBankverbindungAtAll_thenEverythingStaysEmpty() {
    // When / Then — die frische Instanz.
    assertThat(felder(umgestellt(null))).containsExactly(null, null, null, null);
  }

  @Test
  void migration_thenTheColumnsHaveTheirLengths() {
    // When
    flywayBis("38").migrate();

    // Then
    assertThat(
            jdbc.queryForList(
                "SELECT column_name || ' ' || character_maximum_length FROM"
                    + " information_schema.columns WHERE table_schema = ? AND table_name ="
                    + " 'eigene_angaben' AND column_name IN ('iban', 'bic', 'kontoinhaber')"
                    + " ORDER BY column_name",
                String.class,
                PROBE))
        .containsExactly("bic 11", "iban 34", "kontoinhaber 200");
  }
}
