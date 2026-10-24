package org.mwolff.fbcrm;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Liest die Spalten des Schutzes gegen gleichzeitiges Bearbeiten aus dem Katalog der Datenbank
 * (Issue #439, Plan #415).
 *
 * <p>{@code V33} gibt elf Tabellen {@code version} und {@code geaendert_von}, {@code V34} fuenf
 * Tabellen {@code geloescht_am} und {@code geloescht_von}. Jede {@code *SchemaIT} prueft ihre
 * Tabellen mit denselben zwei Fragen; die Antwort steht als eine Zeile, damit eine Abweichung in
 * der Meldung der Assertion vollstaendig zu lesen ist.
 */
public final class Schutzspalten {

  private Schutzspalten() {}

  /**
   * Typ, Pflicht und Vorbelegung einer Spalte, etwa {@code "bigint NO 0"}; ohne Vorbelegung steht
   * am Ende {@code "-"}.
   */
  public static String spalte(final JdbcTemplate jdbc, final String tabelle, final String spalte) {
    return jdbc.queryForObject(
        "SELECT data_type || ' ' || is_nullable || ' ' || coalesce(column_default, '-')"
            + " FROM information_schema.columns WHERE table_schema = 'public'"
            + " AND table_name = ? AND column_name = ?",
        String.class,
        tabelle,
        spalte);
  }

  /**
   * Ziel und Loeschregel des Fremdschluessels einer Spalte, etwa {@code "account.id NO ACTION"}.
   */
  public static String verweis(final JdbcTemplate jdbc, final String tabelle, final String spalte) {
    return jdbc.queryForObject(
        "SELECT ccu.table_name || '.' || ccu.column_name || ' ' || rc.delete_rule"
            + " FROM information_schema.key_column_usage kcu"
            + " JOIN information_schema.referential_constraints rc"
            + " ON rc.constraint_name = kcu.constraint_name"
            + " AND rc.constraint_schema = kcu.constraint_schema"
            + " JOIN information_schema.constraint_column_usage ccu"
            + " ON ccu.constraint_name = rc.constraint_name"
            + " AND ccu.constraint_schema = rc.constraint_schema"
            + " WHERE kcu.table_schema = 'public' AND kcu.table_name = ? AND kcu.column_name = ?",
        String.class,
        tabelle,
        spalte);
  }

  /** Legt ein Konto an und liefert seine Nummer — vorher leert {@code leereKonten} die Konten. */
  public static long konto(final JdbcTemplate jdbc) {
    return jdbc.queryForObject(
            "INSERT INTO account (email, display_name, role)"
                + " VALUES ('loescherin@example.org', 'Loescherin', 'EMPLOYEE') RETURNING id",
            Long.class)
        .longValue();
  }
}
