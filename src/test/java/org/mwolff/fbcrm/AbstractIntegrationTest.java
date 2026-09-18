package org.mwolff.fbcrm;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Basis aller {@code *IT}: <b>eine</b> PostgreSQL-Instanz fuer die gesamte Suite.
 *
 * <p>Der Container startet im statischen Initialisierer und wird bewusst nicht ueber
 * {@code @Container} verwaltet — die JUnit-Extension stoppte ihn nach jeder Testklasse und jede
 * weitere Klasse zahlte den Start erneut. Die Engine ist dieselbe wie in Produktion; H2 ist als
 * Postgres-Ersatz ausgeschlossen (CLAUDE-java.md §3.1).
 *
 * <p>Oeffentlich, damit auch {@code *IT} in den Fachpaketen davon erben koennen.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

  /**
   * Nur fuer den Test. Das echte Geheimnis kommt aus {@code FBCRM_SESSION_SECRET} und hat in der
   * Anwendung keinen Default — ohne gesetzte Variable startet sie nicht.
   */
  private static final String TEST_SESSION_SECRET =
      "test-geheimnis-mit-mindestens-32-zeichen-laenge";

  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

  static {
    POSTGRES.start();
  }

  @DynamicPropertySource
  static void datasourceProperties(final DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("fbcrm.auth.session-secret", () -> TEST_SESSION_SECRET);
  }
}
