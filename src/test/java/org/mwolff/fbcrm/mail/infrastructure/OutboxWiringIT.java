package org.mwolff.fbcrm.mail.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;

/**
 * Der Schalter {@code FBCRM_OUTBOX_ENABLED} wirkt auf die beiden Hintergrundjobs (E7).
 *
 * <p>Ein Schalter, der nichts schaltet, ist die unangenehmste Art von Konfiguration: Er steht in
 * der {@code .env}, jemand stellt ihn um, und nichts geschieht. Hier steht beides nebeneinander —
 * mit {@code true} gibt es die Jobs, mit dem Default nicht.
 *
 * <p>Der Anwendungskontext dieser Klasse fuehrt ausserdem vor, dass die Jobs sich ueberhaupt
 * verdrahten lassen: Sie haengen an {@code JavaMailSender}, und der entsteht nur, weil {@code
 * application.yml} {@code spring.mail.host} setzt.
 */
class OutboxWiringIT extends AbstractIntegrationTest {

  private final ApplicationContext kontext;

  @Autowired
  OutboxWiringIT(final ApplicationContext kontext) {
    this.kontext = kontext;
  }

  @Test
  void dispatcher_givenTheDefaultConfiguration_thenDoesNotExist() {
    // When / Then — ohne ausdrueckliches true laeuft kein Hintergrundjob.
    assertThat(kontext.getBeanNamesForType(OutboxDispatcher.class)).isEmpty();
  }

  @Test
  void cleanup_givenTheDefaultConfiguration_thenDoesNotExist() {
    // When / Then
    assertThat(kontext.getBeanNamesForType(OutboxCleanup.class)).isEmpty();
  }

  /** Dieselbe Anwendung mit eingeschaltetem Postausgangsfach. */
  @Nested
  @TestPropertySource(properties = "fbcrm.outbox.enabled=true")
  class Eingeschaltet {

    private final ApplicationContext eingeschalteterKontext;

    @Autowired
    Eingeschaltet(final ApplicationContext eingeschalteterKontext) {
      this.eingeschalteterKontext = eingeschalteterKontext;
    }

    @Test
    void dispatcher_givenTheSwitchIsOn_thenExists() {
      // When / Then
      assertThat(eingeschalteterKontext.getBeanNamesForType(OutboxDispatcher.class)).hasSize(1);
    }

    @Test
    void cleanup_givenTheSwitchIsOn_thenExists() {
      // When / Then
      assertThat(eingeschalteterKontext.getBeanNamesForType(OutboxCleanup.class)).hasSize(1);
    }

    @Test
    void scheduling_givenTheSwitchIsOn_thenIsEnabled() {
      // When / Then — ohne Zeitsteuerung liefe der Takt der Jobs nie an.
      assertThat(eingeschalteterKontext.getBeanNamesForType(OutboxSchedulingConfig.class))
          .hasSize(1);
    }
  }
}
