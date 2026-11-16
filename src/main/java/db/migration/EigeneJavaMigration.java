package db.migration;

import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.migration.JavaMigration;

/**
 * Der gemeinsame Rahmen der Java-Migrationen des Projekts: Version und Beschreibung aus dem
 * Konstruktor, keine Pruefsumme, Lauf in der Transaktion (Issue #489).
 *
 * <p>Sie implementiert {@link JavaMigration} direkt und erweitert nicht {@code BaseJavaMigration}:
 * Deren Konstruktor liest Version und Beschreibung aus dem Klassennamen ({@code V36__…}) und weist
 * jeden anderen Namen ab, auch wenn {@link #getVersion()} ueberschrieben ist. Die Namen der
 * Migrationen folgen so der Checkstyle-Regel {@code TypeName}, und die Version steht in der
 * Unterklasse.
 *
 * <p>Sie liegt in {@code db.migration}, weil die Migrations-ITs Flyway ohne Spring ueber {@code
 * classpath:db/migration} bauen. Als abstrakte Klasse greift Flyway sie dort nicht selbst als
 * Migration auf: Sein Klassenscan ueberspringt Klassen, die sich nicht instanziieren lassen.
 */
public abstract class EigeneJavaMigration implements JavaMigration {

  private final MigrationVersion version;

  private final String beschreibung;

  /**
   * Der Rahmen einer Migration.
   *
   * @param version die Version, unter der Flyway die Migration fuehrt, etwa {@code "36"}
   * @param beschreibung die Beschreibung in der Historientabelle
   */
  protected EigeneJavaMigration(final String version, final String beschreibung) {
    this.version = MigrationVersion.fromVersion(version);
    this.beschreibung = beschreibung;
  }

  @Override
  public final MigrationVersion getVersion() {
    return version;
  }

  @Override
  public final String getDescription() {
    return beschreibung;
  }

  /** Ohne Pruefsumme, wie bei {@code BaseJavaMigration}: Flyway prueft den Code nicht nach. */
  @Override
  public final Integer getChecksum() {
    return null;
  }

  @Override
  public final boolean canExecuteInTransaction() {
    return true;
  }
}
