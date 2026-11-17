package db.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Modifier;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;

/**
 * Der gemeinsame Rahmen der Java-Migrationen (Issue #489).
 *
 * <p>Version und Beschreibung muessen gleich bleiben, damit Flyway die bereits angewandten
 * Migrationen 36 und 38 in der Historientabelle wiedererkennt; die Basis selbst darf als abstrakte
 * Klasse keine Migration sein.
 */
class EigeneJavaMigrationTest {

  @Test
  void landAlsKuerzel_thenKeepsVersionAndDescription() {
    // When
    final EigeneJavaMigration migration = new LandAlsKuerzelMigration();

    // Then
    assertThat(migration.getVersion()).isEqualTo(MigrationVersion.fromVersion("36"));
    assertThat(migration.getDescription()).isEqualTo("land als kuerzel");
    assertThat(migration.getChecksum()).isNull();
    assertThat(migration.canExecuteInTransaction()).isTrue();
  }

  @Test
  void bankverbindungInFeldern_thenKeepsVersionAndDescription() {
    // When
    final EigeneJavaMigration migration = new BankverbindungInFeldernMigration();

    // Then
    assertThat(migration.getVersion()).isEqualTo(MigrationVersion.fromVersion("38"));
    assertThat(migration.getDescription()).isEqualTo("bankverbindung in feldern");
    assertThat(migration.getChecksum()).isNull();
    assertThat(migration.canExecuteInTransaction()).isTrue();
  }

  @Test
  void basis_thenIsAbstractSoFlywaySkipsIt() {
    // Then — Flyways Klassenscan ueberspringt Klassen, die sich nicht instanziieren lassen.
    assertThat(Modifier.isAbstract(EigeneJavaMigration.class.getModifiers())).isTrue();
  }
}
