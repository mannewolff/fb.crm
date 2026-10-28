package db.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.migration.Context;
import org.flywaydb.core.api.migration.JavaMigration;
import org.mwolff.fbcrm.common.Bankverbindungszerlegung;
import org.mwolff.fbcrm.common.Iban;

/**
 * Uebernimmt die bisherige Bankverbindung in die Felder IBAN, BIC und Kontoinhaber (Plan #461, E13,
 * E14; fachliche Quelle #460, Kriterium 11; Issue #465).
 *
 * <p>Die Zerlegung des Freitexts steht in {@link Bankverbindungszerlegung}, hier wird sie nur
 * aufgerufen. Findet sie eine gueltige IBAN, bekommt die Zeile die IBAN ohne Leerzeichen, die BIC,
 * wenn es eine gibt, und den eigenen Namen als Kontoinhaber. Ohne gueltige IBAN bleiben alle drei
 * leer, und der Mensch traegt sie in der Maske nach. Die Spalte {@code bankverbindung} bleibt in
 * jedem Fall unveraendert: Sie ist die „bisherige Bankverbindung", die die Maske zum Abschreiben
 * zeigt (E13).
 *
 * <p>Wie {@code LandAlsKuerzelMigration} implementiert sie {@link JavaMigration} direkt und
 * erweitert nicht {@code BaseJavaMigration}: Deren Konstruktor verlangt einen Klassennamen der Form
 * {@code V38__…}, den die Checkstyle-Regel {@code TypeName} abweist.
 */
public class BankverbindungInFeldernMigration implements JavaMigration {

  @Override
  public MigrationVersion getVersion() {
    return MigrationVersion.fromVersion("38");
  }

  @Override
  public String getDescription() {
    return "bankverbindung in feldern";
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
  public void migrate(final Context context) throws SQLException {
    final Connection verbindung = context.getConnection();
    try (Statement lesen = verbindung.createStatement();
        ResultSet zeilen =
            lesen.executeQuery("SELECT id, name, bankverbindung FROM eigene_angaben");
        PreparedStatement schreiben =
            verbindung.prepareStatement(
                "UPDATE eigene_angaben SET iban = ?, bic = ?, kontoinhaber = ? WHERE id = ?")) {
      while (zeilen.next()) {
        final Bankverbindungszerlegung.Zerlegt zerlegt =
            Bankverbindungszerlegung.zerlege(zeilen.getString("bankverbindung"));
        final Iban iban = zerlegt.iban();
        if (iban != null) {
          schreiben.setString(1, iban.elektronisch());
          schreiben.setString(2, zerlegt.bic());
          schreiben.setString(3, zeilen.getString("name"));
          schreiben.setShort(4, zeilen.getShort("id"));
          schreiben.addBatch();
        }
      }
      schreiben.executeBatch();
    }
  }
}
