package db.migration;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Objects;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.migration.Context;
import org.flywaydb.core.api.migration.JavaMigration;
import org.mwolff.fbcrm.common.Laenderliste;

/**
 * Stellt das Land der Firma vom Freitext auf das Kuerzel nach ISO 3166-1 um (Plan #461, E11, E12;
 * Issue #462).
 *
 * <p>Jede Firma bekommt das Kuerzel, das {@link Laenderliste#kuerzel} ihrem bisherigen Freitext
 * zuordnet (fachliche Quelle #460, Kriterium 10): genauer deutscher oder englischer Name oder
 * Kuerzel, sonst Deutschland. Danach wird die Spalte zum Kuerzel: {@code varchar(2)}, Pflicht,
 * Vorgabe {@code DE} und ein CHECK auf zwei Grossbuchstaben.
 *
 * <p>Zugeordnet wird gegen die eingefrorene Liste {@code laender-v36.csv} und nicht gegen das
 * laufende JDK, damit das Ergebnis nicht vom JDK des Abbilds abhaengt (E12). Eine Sicherungsspalte
 * fuer den alten Freitext gibt es nicht; {@code docs/betrieb.md} empfiehlt vor jedem Update eine
 * Sicherung.
 *
 * <p>Eine Java-Migration im Paket {@code db.migration} und keine Spring-Bean: So findet Flyway sie
 * ueber {@code classpath:db/migration} auch in den Migrations-ITs, die ein eigenes Flyway ohne
 * Spring bauen.
 *
 * <p>Sie implementiert {@link JavaMigration} direkt und erweitert nicht {@code BaseJavaMigration}:
 * Deren Konstruktor liest Version und Beschreibung aus dem Klassennamen ({@code V36__…}) und weist
 * jeden anderen Namen ab, auch wenn {@link #getVersion()} ueberschrieben ist. Der Name folgt so der
 * Checkstyle-Regel {@code TypeName}, und die Version steht hier.
 */
public class LandAlsKuerzelMigration implements JavaMigration {

  private static final String NAMENSLISTE = "/db/migration/laender-v36.csv";

  @Override
  public MigrationVersion getVersion() {
    return MigrationVersion.fromVersion("36");
  }

  @Override
  public String getDescription() {
    return "land als kuerzel";
  }

  /** Ohne Pruefsumme, wie bei {@code BaseJavaMigration}: Flyway prueft den Code nicht nach. */
  @Override
  public Integer getChecksum() {
    return null;
  }

  @Override
  public boolean canExecuteInTransaction() {
    return true;
  }

  @Override
  public void migrate(final Context context) throws SQLException, IOException {
    final List<Laenderliste.Land> namen = eingefroreneNamen();
    final Connection verbindung = context.getConnection();
    try (Statement lesen = verbindung.createStatement();
        ResultSet firmen = lesen.executeQuery("SELECT id, land FROM firma");
        PreparedStatement schreiben =
            verbindung.prepareStatement("UPDATE firma SET land = ? WHERE id = ?")) {
      while (firmen.next()) {
        schreiben.setString(1, Laenderliste.kuerzel(firmen.getString("land"), namen));
        schreiben.setLong(2, firmen.getLong("id"));
        schreiben.addBatch();
      }
      schreiben.executeBatch();
    }
    try (Statement aendern = verbindung.createStatement()) {
      aendern.execute(
          "ALTER TABLE firma"
              + " ALTER COLUMN land TYPE varchar(2),"
              + " ALTER COLUMN land SET NOT NULL,"
              + " ALTER COLUMN land SET DEFAULT 'DE',"
              + " ADD CONSTRAINT firma_land_kuerzel CHECK (land ~ '^[A-Z]{2}$')");
    }
  }

  /** Die Namen, wie sie bei dieser Migration galten: Kuerzel; deutscher Name; englischer Name. */
  private static List<Laenderliste.Land> eingefroreneNamen() throws IOException {
    try (InputStream datei =
            Objects.requireNonNull(
                LandAlsKuerzelMigration.class.getResourceAsStream(NAMENSLISTE), NAMENSLISTE);
        BufferedReader zeilen =
            new BufferedReader(new InputStreamReader(datei, StandardCharsets.UTF_8))) {
      return zeilen
          .lines()
          .skip(1)
          .map(zeile -> zeile.split(";", -1))
          .map(teile -> new Laenderliste.Land(teile[0], teile[1], teile[2]))
          .toList();
    }
  }
}
