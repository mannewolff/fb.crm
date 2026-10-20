package org.mwolff.fbcrm.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

/**
 * Die Vorgaben der Zaehlbremse und der Sitzungsdauer, gelesen aus der echten {@code
 * application.yml} (Kriterium 5, Kriterium 9, E21, E27).
 *
 * <p>{@code AuthPropertiesTest} kann das nicht: Er faehrt einen Kontext mit ausschliesslich selbst
 * gesetzten Werten hoch und liest die {@code application.yml} nie. Gilt eine Vorgabe dort nicht
 * mehr, faellt das erst hier auf.
 *
 * <p>Die Umgebung ist eine {@link MockEnvironment} und nicht die des Rechners: Sie kennt keine
 * Umgebungsvariablen, und damit greift in jedem Platzhalter {@code ${FBCRM_…:vorgabe}} die Vorgabe
 * — unabhaengig davon, was auf dem Rechner gesetzt ist, der die Suite faehrt.
 */
class AuthDefaultsIT {

  private static Binder vorgaben() throws IOException {
    final MockEnvironment umgebung = new MockEnvironment();
    new YamlPropertySourceLoader()
        .load("application.yml", new ClassPathResource("application.yml"))
        .forEach(umgebung.getPropertySources()::addLast);
    return Binder.get(umgebung);
  }

  @Test
  void defaults_thenASessionLasts30Days() throws IOException {
    // When / Then — Kriterium 9: 30 Tage ab der letzten Nutzung (E21).
    assertThat(vorgaben().bind("fbcrm.auth.session-ttl", Duration.class).get())
        .isEqualTo(Duration.ofDays(30));
  }

  @Test
  void defaults_thenFiveCodeRequestsPerAddress() throws IOException {
    // When / Then
    assertThat(vorgaben().bind("fbcrm.auth.login-code-per-address", Integer.class).get())
        .isEqualTo(5);
  }

  @Test
  void defaults_thenTheAddressWindowIsOneHour() throws IOException {
    // When / Then
    assertThat(vorgaben().bind("fbcrm.auth.login-code-address-window", Duration.class).get())
        .isEqualTo(Duration.ofHours(1));
  }

  @Test
  void defaults_thenTenCodeRequestsPerAddressAndDay() throws IOException {
    // When / Then
    assertThat(vorgaben().bind("fbcrm.auth.login-code-per-address-daily", Integer.class).get())
        .isEqualTo(10);
  }

  @Test
  void defaults_thenTheDailyWindowIs24Hours() throws IOException {
    // When / Then
    assertThat(vorgaben().bind("fbcrm.auth.login-code-daily-window", Duration.class).get())
        .isEqualTo(Duration.ofHours(24));
  }
}
