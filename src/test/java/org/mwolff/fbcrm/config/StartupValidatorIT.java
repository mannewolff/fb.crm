package org.mwolff.fbcrm.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.mwolff.fbcrm.FbCrmApplication;
import org.slf4j.LoggerFactory;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.event.ApplicationPreparedEvent;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Die Start-Validierung gegen die echte Anwendung (E27).
 *
 * <p>Dieser Test faehrt den Anwendungskontext <b>selbst</b> hoch, statt von {@link
 * AbstractIntegrationTest} zu erben: Sein Gegenstand ist der Fall, in dem der Kontext gar nicht
 * erst hochkommt — den kann {@code @SpringBootTest} nicht ausdruecken, weil dort ein gescheiterter
 * Start den Test selbst scheitern liesse. Die Datenbank kommt trotzdem aus dem geteilten Container
 * der Suite; ohne sie liefe der Start in einen Fehler, der nichts mit dem Validator zu tun haette.
 *
 * <p>Die Mitschrift des Logs haengt an {@link ApplicationPreparedEvent} und nicht am Testaufbau:
 * Spring Boot richtet das Logging beim Start neu ein und wuerde einen vorher angehaengten Appender
 * wieder abraeumen. Das Ereignis liegt nach dieser Einrichtung und vor der Erzeugung der Beans —
 * und damit vor der Pruefung selbst.
 *
 * <p>Zum zu kurzen Geheimnis: Dort greifen <b>zwei</b> Riegel, die Bindung von {@code
 * AuthProperties} ({@code @Size(min = 32)} auf Zeichen) und {@link StartupValidator} (32 Byte). Was
 * dieser Test nachweist, ist die Zusage nach aussen — die Anwendung startet nicht. Dass der
 * Validator sie fuer sich genommen ebenfalls einloest, weist {@code StartupValidatorTest} nach.
 */
class StartupValidatorIT {

  private static final String EIGENES_GEHEIMNIS = "ein-eigenes-geheimnis-mit-genug-laenge";
  private static final String DEV_DEFAULT =
      "fbcrm.auth.session-secret=" + StartupValidator.DEV_DEFAULT_SESSION_SECRET;

  private final ListAppender<ILoggingEvent> mitschrift = new ListAppender<>();

  private ConfigurableApplicationContext starte(final String... schalter) {
    mitschrift.start();
    return new SpringApplicationBuilder(FbCrmApplication.class)
        .listeners(
            (ApplicationPreparedEvent ereignis) ->
                ((Logger) LoggerFactory.getLogger(StartupValidator.class)).addAppender(mitschrift))
        .run(alsArgumente(schalter));
  }

  /**
   * Die Schalter als Kommandozeilenargumente, nicht als {@code properties(...)}.
   *
   * <p>{@code SpringApplicationBuilder.properties(...)} legt seine Werte als
   * <b>defaultProperties</b> ab — die unterste Stufe der Rangfolge. Darueber liegt {@code
   * application.yml} mit {@code spring.datasource.url:
   * ${FBCRM_DB_URL:jdbc:postgresql://localhost:5432/fbcrm}}: Der Container waere damit uebergangen
   * und jeder Start liefe gegen ein Postgres, das es auf dem Host nicht gibt.
   * Kommandozeilenargumente stehen in der Rangfolge ueber der YAML und gewinnen.
   */
  private static String[] alsArgumente(final String[] schalter) {
    return Stream.concat(Stream.of(datenbankSchalter()), Stream.of(schalter))
        .map(eintrag -> "--" + eintrag)
        .toArray(String[]::new);
  }

  private static String[] datenbankSchalter() {
    return new String[] {
      "server.port=0",
      "spring.datasource.url=" + AbstractIntegrationTest.datenbank().getJdbcUrl(),
      "spring.datasource.username=" + AbstractIntegrationTest.datenbank().getUsername(),
      "spring.datasource.password=" + AbstractIntegrationTest.datenbank().getPassword()
    };
  }

  private List<String> warnungen() {
    return mitschrift.list.stream()
        .filter(eintrag -> eintrag.getLevel() == Level.WARN)
        .map(ILoggingEvent::getFormattedMessage)
        .toList();
  }

  /** Faehrt hoch, prueft dass der Kontext laeuft, und schliesst ihn wieder. */
  private void starteUndSchliesse(final String... schalter) {
    try (ConfigurableApplicationContext kontext = starte(schalter)) {
      assertThat(kontext.isRunning()).isTrue();
    }
  }

  @Test
  void start_givenProductionAndTheDevDefaultSecret_thenTheApplicationRefusesToStart() {
    // When / Then — docker-compose.prod.yml setzt FBCRM_DEV_MODE fest auf false. Geprueft wird die
    // Ursache, nicht nur das Scheitern: Ein Start bricht aus vielerlei Gruenden ab, und ein Test,
    // dem jede RuntimeException genuegt, bestaetigte den Riegel auch dann, wenn es ihn nicht gaebe.
    assertThatExceptionOfType(RuntimeException.class)
        .isThrownBy(() -> starteUndSchliesse("fbcrm.dev-mode=false", DEV_DEFAULT))
        .withRootCauseInstanceOf(IllegalStateException.class);
  }

  @Test
  void start_givenProductionAndTheDevDefaultSecret_thenSaysWhichVariableToSet() {
    // When / Then — wer den Start abbricht, sagt auch, was zu tun ist.
    assertThatExceptionOfType(RuntimeException.class)
        .isThrownBy(() -> starteUndSchliesse("fbcrm.dev-mode=false", DEV_DEFAULT))
        .withStackTraceContaining("FBCRM_SESSION_SECRET");
  }

  @Test
  void start_givenProductionAndASecretShorterThan32Bytes_thenTheApplicationRefusesToStart() {
    // When / Then
    assertThatExceptionOfType(RuntimeException.class)
        .isThrownBy(
            () ->
                starteUndSchliesse(
                    "fbcrm.dev-mode=false",
                    "fbcrm.auth.session-secret=1234567890123456789012345678901"))
        .withStackTraceContaining("session-secret");
  }

  @Test
  void start_givenDevModeAndTheDevDefaultSecret_thenTheApplicationStartsWithAWarning() {
    // When — docker-compose.yml Z. 40 ff.: lokal erlaubt, aber nicht stillschweigend.
    starteUndSchliesse("fbcrm.dev-mode=true", DEV_DEFAULT);

    // Then
    assertThat(warnungen()).hasSize(1);
  }

  @Test
  void start_givenDevModeAndTheDevDefaultSecret_thenTheWarningNeverCarriesTheSecret() {
    // When
    starteUndSchliesse("fbcrm.dev-mode=true", DEV_DEFAULT);

    // Then — weder ganz noch in Teilen; ein Log-Archiv ist kein Ort fuer Schluesselmaterial.
    assertThat(warnungen())
        .singleElement()
        .asString()
        .doesNotContain(StartupValidator.DEV_DEFAULT_SESSION_SECRET)
        .doesNotContain(StartupValidator.DEV_DEFAULT_SESSION_SECRET.substring(0, 8));
  }

  @Test
  void start_givenProductionAndAProperSecret_thenTheApplicationStartsWithoutAWarning() {
    // When — so soll eine Produktionsinstanz hochkommen.
    starteUndSchliesse("fbcrm.dev-mode=false", "fbcrm.auth.session-secret=" + EIGENES_GEHEIMNIS);

    // Then
    assertThat(warnungen()).isEmpty();
  }
}
