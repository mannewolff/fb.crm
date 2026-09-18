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
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class AbstractIntegrationTest {

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
  }
}
