package org.mwolff.fbcrm.config;

import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Die eine Uhr der Anwendung.
 *
 * <p>Sie steht als Bean zur Verfuegung, damit niemand {@code Instant.now()} aufruft und damit eine
 * Zeitlogik untestbar macht (CLAUDE-java.md §6.2). UTC, weil die Datenbank ihre Zeitstempel
 * ebenfalls in UTC fuehrt ({@code hibernate.jdbc.time_zone}).
 */
@Configuration
public class TimeConfig {

  @Bean
  public Clock clock() {
    return mikrosekundengenau(Clock.systemUTC());
  }

  /**
   * Kuerzt eine Uhr auf Mikrosekunden.
   *
   * <p>Postgres speichert {@code timestamptz} nur mikrosekundengenau. Eine Uhr, die feiner tickt —
   * unter Linux liefert {@link Clock#systemUTC()} Nanosekunden —, gibt der Anwendung einen
   * Zeitstempel, den die Datenbank so nie zurueckgibt: Die Antwort nach dem Anlegen traegt dann
   * einen anderen Wert als derselbe Datensatz nach dem Lesen. Kuerzen an dieser einen Stelle gilt
   * fuer jeden Zeitstempel der Anwendung, und die Antwort entspricht dem gespeicherten Wert.
   *
   * @param basis die zugrunde liegende Uhr
   * @return eine Uhr mit derselben Zone, die nur in Mikrosekunden tickt
   */
  static Clock mikrosekundengenau(final Clock basis) {
    return Clock.tick(basis, Duration.ofNanos(1_000));
  }
}
