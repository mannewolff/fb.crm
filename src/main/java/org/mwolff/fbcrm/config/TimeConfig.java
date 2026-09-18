package org.mwolff.fbcrm.config;

import java.time.Clock;
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
    return Clock.systemUTC();
  }
}
