package org.mwolff.fbcrm.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/** Die Uhr der Anwendung laeuft in UTC — wie die Zeitstempel in der Datenbank. */
class TimeConfigTest {

  @Test
  void clock_thenRunsInUtc() {
    // When / Then
    assertThat(new TimeConfig().clock().getZone()).isEqualTo(ZoneOffset.UTC);
  }
}
