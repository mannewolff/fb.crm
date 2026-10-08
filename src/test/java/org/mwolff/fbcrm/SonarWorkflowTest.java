package org.mwolff.fbcrm;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Der Workflow, der SonarCloud mit Abdeckung versorgt (Plan #221, A1 bis A3).
 *
 * <p>Er laeuft getrennt von {@code ci.yml}: Ein Fund von Sonar oder ein fehlendes Token soll kein
 * CI-Gate roetfaerben, und der Scan braucht Dinge, die {@code ci.yml} nicht hat — die volle
 * Historie, die Dependency-Jars und das Token. Was er hochlaedt, muss zu dem passen, was {@code
 * sonar-project.properties} als Abdeckungsbericht erwartet; sonst scannt er, ohne zu messen. Genau
 * diese Verbindung haelt dieser Test.
 *
 * <p>Muster fuer das Lesen der Dateien: {@link CiWorkflowTest}.
 */
class SonarWorkflowTest {

  private static final Path WURZEL = Path.of("");

  /** Die Scan-Action, auf eine Commit-SHA gepinnt (Supply-Chain-Haertung wie in kanban-kit). */
  private static final String SCAN_ACTION =
      "SonarSource/sonarqube-scan-action@713881670b6b3676cda39549040e2d88c70d582e";

  /**
   * Der Workflow als Abbildung.
   *
   * <p>Ungepruefter Cast wie in {@link CiWorkflowTest}: Ein Workflow ist auf oberster Ebene eine
   * Abbildung, und der {@code SafeConstructor} erzeugt nur Standardtypen.
   */
  @SuppressWarnings("unchecked")
  private static Map<String, Object> workflow() throws IOException {
    final Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
    return (Map<String, Object>)
        yaml.load(
            Files.readString(
                WURZEL.resolve(".github/workflows/sonarqube.yml"), StandardCharsets.UTF_8));
  }

  /** Ein Abschnitt der obersten Ebene als Abbildung — {@code on}, {@code permissions}. */
  @SuppressWarnings("unchecked")
  private static Map<String, Object> abschnitt(final String name) throws IOException {
    return (Map<String, Object>) workflow().get(name);
  }

  /** Die Schritte des einen Jobs. */
  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> schritte() throws IOException {
    final Map<String, Object> jobs = (Map<String, Object>) workflow().get("jobs");
    assertThat(jobs).as("der Workflow hat genau einen Job").hasSize(1);
    final Map<String, Object> job = (Map<String, Object>) jobs.values().iterator().next();
    assertThat(job).containsEntry("runs-on", "ubuntu-latest");
    return (List<Map<String, Object>>) job.get("steps");
  }

  /** Der Schritt, der die genannte Action benutzt. */
  private static Map<String, Object> schrittMit(final String action) throws IOException {
    for (final Map<String, Object> schritt : schritte()) {
      if (String.valueOf(schritt.get("uses")).startsWith(action)) {
        return schritt;
      }
    }
    throw new AssertionError("Der Workflow benutzt " + action + " nicht.");
  }

  /** Das {@code with}-Feld des Schritts, der die genannte Action benutzt. */
  @SuppressWarnings("unchecked")
  private static Map<String, Object> mit(final String action) throws IOException {
    return (Map<String, Object>) schrittMit(action).get("with");
  }

  /** Die {@code run}-Kommandos aller Schritte, zu einem Text verbunden. */
  private static String kommandos() throws IOException {
    final StringBuilder alle = new StringBuilder();
    for (final Map<String, Object> schritt : schritte()) {
      if (schritt.get("run") != null) {
        alle.append(schritt.get("run")).append('\n');
      }
    }
    return alle.toString();
  }

  private static String sonarEigenschaft(final String name) throws IOException {
    final Properties eigenschaften = new Properties();
    eigenschaften.load(
        new StringReader(
            Files.readString(WURZEL.resolve("sonar-project.properties"), StandardCharsets.UTF_8)));
    final String wert = eigenschaften.getProperty(name);
    assertThat(wert).as("sonar-project.properties traegt %s", name).isNotNull();
    return wert.strip();
  }

  private static String pomEigenschaft(final String name) throws IOException {
    final Matcher treffer =
        Pattern.compile("<" + name + ">([^<]+)</" + name + ">")
            .matcher(Files.readString(WURZEL.resolve("pom.xml"), StandardCharsets.UTF_8));
    assertThat(treffer.find()).as("pom.xml traegt <%s>", name).isTrue();
    return treffer.group(1).strip();
  }

  @Test
  void workflow_givenTheFile_thenRunsOnMainAndOnDemand() throws IOException {
    // When / Then — "on" steht in Anfuehrungszeichen: YAML 1.1 laese es sonst als true. Der Scan
    // laeuft auf dem Branch, den die kostenlose Sonar-Stufe kennt, und auf Zuruf.
    assertThat(abschnitt("on")).containsOnlyKeys("push", "workflow_dispatch");
    assertThat(abschnitt("on").get("push")).isEqualTo(Map.of("branches", List.of("main")));
  }

  @Test
  void workflow_givenTheFile_thenReadsTheRepositoryOnly() throws IOException {
    // When / Then — der Scan liest nur; das Token geht ausschliesslich an SonarCloud (Plan #221,
    // E3).
    assertThat(abschnitt("permissions")).containsOnly(Map.entry("contents", "read"));
  }

  @Test
  void checkout_givenTheFile_thenFetchesTheFullHistory() throws IOException {
    // When / Then — ohne volle Historie kann Sonar neue Funde nicht den Commits zuordnen.
    assertThat(mit("actions/checkout@")).containsEntry("fetch-depth", 0);
  }

  @Test
  void scan_givenTheFile_thenUsesThePinnedActionWithBranchAndToken() throws IOException {
    // Given
    final Map<String, Object> scan = schrittMit(SCAN_ACTION);

    // When / Then — der Branch steht ausdruecklich, weil workflow_dispatch von jedem Branch aus
    // startbar ist (Plan #221, E1).
    assertThat(String.valueOf(scan.get("uses"))).isEqualTo(SCAN_ACTION);
    assertThat(mit(SCAN_ACTION)).containsEntry("args", "-Dsonar.branch.name=main");
    assertThat(scan.get("env")).isEqualTo(Map.of("SONAR_TOKEN", "${{ secrets.SONAR_TOKEN }}"));
  }

  @Test
  void javaSetup_givenThePom_thenUsesTheSameJdk() throws IOException {
    // When / Then
    assertThat(mit("actions/setup-java@"))
        .containsEntry("java-version", pomEigenschaft("java.version"))
        .containsEntry("cache", "maven");
  }

  @Test
  void nodeSetup_givenThePackageFile_thenUsesItsNodeVersion() throws IOException {
    // When / Then — dieselbe Quelle wie in ci.yml, damit der lcov-Bericht mit derselben
    // Node-Version entsteht wie lokal.
    assertThat(mit("actions/setup-node@"))
        .containsEntry("node-version-file", "frontend/package.json")
        .containsEntry("cache", "npm")
        .containsEntry("cache-dependency-path", "frontend/package-lock.json");
  }

  @Test
  void build_givenTheSonarConfig_thenProducesTheJavaLibrariesItExpects() throws IOException {
    // Given — sonar.java.libraries zeigt auf ein Verzeichnis, das erst der Build fuellt
    final String libraries = sonarEigenschaft("sonar.java.libraries");

    // When / Then
    assertThat(libraries).isEqualTo("target/dependency/*.jar");
    assertThat(kommandos())
        .contains("mvn -B verify dependency:copy-dependencies -DoutputDirectory=target/dependency");
  }

  @Test
  void build_givenTheSonarConfig_thenProducesBothCoverageReports() throws IOException {
    // When / Then — der Frontend-Bericht entsteht nur, wenn der Workflow die Abdeckung faehrt
    assertThat(sonarEigenschaft("sonar.javascript.lcov.reportPaths"))
        .isEqualTo("frontend/coverage/lcov.info");
    assertThat(kommandos())
        .contains("npm --prefix frontend ci")
        .contains("npm --prefix frontend run test:coverage");
  }

  @Test
  void artifact_givenTheSonarConfig_thenKeepsTheJacocoReport() throws IOException {
    // Given — bei einer Abweichung zwischen Sonar und der lokalen Messung ist der Bericht die
    // einzige Grundlage (Plan #221, E2)
    final Map<String, Object> hochladen = schrittMit("actions/upload-artifact@");

    // When / Then
    assertThat(hochladen).containsEntry("if", "always()");
    assertThat(mit("actions/upload-artifact@"))
        .containsEntry("path", sonarEigenschaft("sonar.coverage.jacoco.xmlReportPaths"))
        .containsEntry("retention-days", 14);
  }
}
