package org.mwolff.fbcrm;

import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Legt in einem Integrationstest den Stand vor, den eine Zeile gerade traegt (Plan #415, E2, E3).
 *
 * <p>Ein geschuetzter Weg weist ohne {@code If-Match} mit 428 ab. Ein Test, dessen Gegenstand nicht
 * der Schutz selbst ist, soll so schreiben, wie es die Maske tut: auf dem aktuellen Stand. Den
 * liest dieser Helfer aus der Spalte {@code version}, statt ihn in jedem Test mitzuzaehlen — ein
 * falsch gezaehlter Stand liesse einen Test am Schutz scheitern, der etwas ganz anderes pruefen
 * will.
 */
public final class Stand {

  private Stand() {}

  /**
   * Die Kopfzeilen der Sitzung samt {@code If-Match} auf den aktuellen Stand der Zeile.
   *
   * <p>Gibt es die Zeile nicht, wird Stand 0 vorgelegt: Der Weg soll dann an der fehlenden Zeile
   * scheitern und nicht am Kopf.
   *
   * @param sitzung die Kopfzeilen der angemeldeten Sitzung; sie bleiben unveraendert
   * @param jdbc der Zugang zur Datenbank des Tests
   * @param tabelle die geschuetzte Tabelle, etwa {@code angebot}
   * @param id die Kennung der Zeile
   */
  public static HttpHeaders aktuell(
      final HttpHeaders sitzung, final JdbcTemplate jdbc, final String tabelle, final long id) {
    final List<Long> versionen =
        jdbc.queryForList(
            "SELECT version FROM " + tabelle + " WHERE id = ?", Long.class, Long.valueOf(id));
    final long version = versionen.isEmpty() ? 0L : versionen.getFirst().longValue();
    final HttpHeaders kopf = new HttpHeaders();
    kopf.putAll(sitzung);
    kopf.setIfMatch("\"" + version + "\"");
    return kopf;
  }
}
