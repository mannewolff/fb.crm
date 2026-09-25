package org.mwolff.fbcrm;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Basis aller {@code *IT}: <b>eine</b> PostgreSQL- und <b>eine</b> MinIO-Instanz fuer die gesamte
 * Suite (E23).
 *
 * <p>Beide Container starten im statischen Initialisierer und werden bewusst nicht ueber
 * {@code @Container} verwaltet — die JUnit-Extension stoppte sie nach jeder Testklasse und jede
 * weitere Klasse zahlte den Start erneut. Die Engines sind dieselben wie in Produktion; H2 ist als
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

  /** Der Eimer der Suite. Die Anwendung legt ihn beim Start selbst an (S3AnhangSpeicher). */
  private static final String TEST_BUCKET = "fbcrm-test";

  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

  /**
   * Dasselbe Abbild wie {@code docker-compose.yml} Z. 19: {@code minio/minio} gibt es auf Docker
   * Hub nicht mehr, und {@link MinIOContainer} erwartet genau diesen Namen — daher die
   * Gleichsetzung.
   */
  static final MinIOContainer MINIO =
      new MinIOContainer(
          DockerImageName.parse("quay.io/minio/minio:RELEASE.2025-09-07T16-13-09Z")
              .asCompatibleSubstituteFor("minio/minio"));

  static {
    POSTGRES.start();
    MINIO.start();
  }

  /**
   * Die geteilte Datenbank der Suite.
   *
   * <p>Fuer die wenigen {@code *IT}, die den Anwendungskontext selbst hochfahren, statt von dieser
   * Klasse zu erben — etwa {@code StartupValidatorIT}, dessen Gegenstand der gescheiterte Start
   * ist. Sie brauchen dieselbe Instanz, damit nicht je Testklasse ein zweiter Container anlaeuft.
   */
  public static PostgreSQLContainer<?> datenbank() {
    return POSTGRES;
  }

  /** Der geteilte Objektspeicher der Suite, aus demselben Grund wie {@link #datenbank()}. */
  public static MinIOContainer objektspeicher() {
    return MINIO;
  }

  /**
   * Der MinIO-Zugang als Kommandozeilenargumente, fuer die {@code *IT}, die ihren Kontext selbst
   * hochfahren: Dort greift {@code @DynamicPropertySource} nicht, und ohne diese Werte scheitert
   * die Bindung von {@code MinioProperties} — die Zugangsdaten haben keinen Default.
   */
  public static String[] objektspeicherSchalter() {
    return new String[] {
      "fbcrm.minio.endpoint=" + MINIO.getS3URL(),
      "fbcrm.minio.access-key=" + MINIO.getUserName(),
      "fbcrm.minio.secret-key=" + MINIO.getPassword(),
      "fbcrm.minio.bucket=" + TEST_BUCKET
    };
  }

  @DynamicPropertySource
  static void datasourceProperties(final DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("fbcrm.auth.session-secret", () -> TEST_SESSION_SECRET);
    registry.add("fbcrm.minio.endpoint", MINIO::getS3URL);
    registry.add("fbcrm.minio.access-key", MINIO::getUserName);
    registry.add("fbcrm.minio.secret-key", MINIO::getPassword);
    registry.add("fbcrm.minio.bucket", () -> TEST_BUCKET);
  }
}
