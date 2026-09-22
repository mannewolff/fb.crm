package org.mwolff.fbcrm;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Die CI-Pipeline prueft dasselbe wie der lokale Pflichtcheck (CLAUDE-java.md §8 Schritt 8).
 *
 * <p>Die Quelle der Wahrheit ist {@code .claude/workflow.config.json}: {@code buildChecks} fuer den
 * lokalen Lauf, {@code mutationCommand} fuer PIT. Die Pipeline fuehrt dieselben Kommandos Zeichen
 * fuer Zeichen aus — kaeme ein Check lokal dazu und nicht in CI, liefen die beiden Gates still
 * auseinander. Welcher Schritt ein Gate ist, sagt seine {@code id} ({@code gate-…}); alles andere
 * im Workflow ist Vorbereitung.
 *
 * <p>Die Zuordnung zu den Jobs folgt den Bereichen aus {@code buildChecks}: {@code backend} laeuft
 * in {@code verify}, {@code frontend} in {@code frontend}, die Mutationstests in {@code pit}. Ein
 * neuer Bereich in der Config bricht diesen Test — genau dann muss jemand entscheiden, wo er in CI
 * laeuft.
 */
class CiWorkflowTest {

  private static final Path WURZEL = Path.of("");
  private static final ObjectMapper JSON = new ObjectMapper();

  /**
   * Der ganze Workflow als Abbildung.
   *
   * <p>SnakeYAML liefert {@code Object}; ein Workflow ist auf oberster Ebene eine Abbildung mit
   * Zeichenketten als Schluessel, und der {@code SafeConstructor} erzeugt nur Standardtypen. Der
   * ungepruefte Cast ist damit fuer jede gueltige Workflow-Datei richtig — und eine ungueltige soll
   * hier ohnehin scheitern.
   */
  @SuppressWarnings("unchecked")
  private static Map<String, Object> workflow() throws IOException {
    final Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
    return (Map<String, Object>)
        yaml.load(
            Files.readString(WURZEL.resolve(".github/workflows/ci.yml"), StandardCharsets.UTF_8));
  }

  private static JsonNode config() throws IOException {
    return JSON.readTree(WURZEL.resolve(".claude/workflow.config.json").toFile());
  }

  /**
   * Ein Abschnitt der obersten Ebene als Abbildung — {@code jobs}, {@code on}, {@code permissions}.
   */
  @SuppressWarnings("unchecked")
  private static Map<String, Object> abschnitt(final String name) throws IOException {
    return (Map<String, Object>) workflow().get(name);
  }

  private static Map<String, Object> jobs() throws IOException {
    return abschnitt("jobs");
  }

  /**
   * Die Schritte eines Jobs.
   *
   * <p>Ungepruefter Cast aus demselben Grund wie bei {@link #workflow()}: {@code steps} ist in
   * jedem gueltigen Workflow eine Liste von Abbildungen.
   */
  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> schritte(final String job) throws IOException {
    return (List<Map<String, Object>>) ((Map<String, Object>) jobs().get(job)).get("steps");
  }

  /** Die Kommandos der Gate-Schritte eines Jobs, in ihrer Reihenfolge. */
  private static List<String> gates(final String job) throws IOException {
    final List<String> kommandos = new ArrayList<>();
    for (final Map<String, Object> schritt : schritte(job)) {
      if (String.valueOf(schritt.get("id")).startsWith("gate-")) {
        kommandos.add(String.valueOf(schritt.get("run")).strip());
      }
    }
    return kommandos;
  }

  /** Die {@code buildChecks} eines Bereichs, in der Reihenfolge der Config. */
  private static List<String> buildChecks(final String bereich) throws IOException {
    final List<String> kommandos = new ArrayList<>();
    for (final JsonNode check : config().get("buildChecks")) {
      for (final JsonNode area : check.get("areas")) {
        if (bereich.equals(area.asText())) {
          kommandos.add(check.get("cmd").asText());
        }
      }
    }
    return kommandos;
  }

  /** Das {@code with}-Feld des Schritts, der die genannte Action benutzt. */
  @SuppressWarnings("unchecked")
  private static Map<String, Object> mit(final String job, final String action) throws IOException {
    for (final Map<String, Object> schritt : schritte(job)) {
      if (String.valueOf(schritt.get("uses")).startsWith(action + "@")) {
        return (Map<String, Object>) schritt.get("with");
      }
    }
    throw new AssertionError("Job " + job + " benutzt " + action + " nicht.");
  }

  private static String pomEigenschaft(final String name) throws IOException {
    final Matcher treffer =
        Pattern.compile("<" + name + ">([^<]+)</" + name + ">")
            .matcher(Files.readString(WURZEL.resolve("pom.xml"), StandardCharsets.UTF_8));
    assertThat(treffer.find()).as("pom.xml traegt <%s>", name).isTrue();
    return treffer.group(1).strip();
  }

  @Test
  void workflow_givenTheFile_thenHasExactlyTheThreeJobs() throws IOException {
    // When / Then
    assertThat(jobs().keySet()).containsExactlyInAnyOrder("verify", "pit", "frontend");
  }

  @Test
  void workflow_givenTheFile_thenRunsOnPushAndPullRequest() throws IOException {
    // When / Then — "on" steht in Anfuehrungszeichen: YAML 1.1 laese es sonst als true.
    assertThat(abschnitt("on")).containsOnlyKeys("push", "pull_request");
  }

  @Test
  void verify_givenTheConfig_thenRunsExactlyTheBackendChecks() throws IOException {
    // When / Then
    assertThat(gates("verify")).isNotEmpty().isEqualTo(buildChecks("backend"));
  }

  @Test
  void frontend_givenTheConfig_thenRunsExactlyTheFrontendChecks() throws IOException {
    // When / Then
    assertThat(gates("frontend")).isNotEmpty().isEqualTo(buildChecks("frontend"));
  }

  @Test
  void pit_givenTheConfig_thenRunsExactlyTheMutationCommand() throws IOException {
    // When / Then
    assertThat(gates("pit")).containsExactly(config().get("mutationCommand").asText());
  }

  @Test
  void workflow_givenTheConfig_thenEveryLocalCheckRunsInCi() throws IOException {
    // Given — alle Kommandos, die lokal Pflicht sind
    final List<String> lokal = new ArrayList<>();
    for (final JsonNode check : config().get("buildChecks")) {
      lokal.add(check.get("cmd").asText());
    }
    lokal.add(config().get("mutationCommand").asText());

    // When
    final List<String> inCi = new ArrayList<>();
    for (final String job : jobs().keySet()) {
      inCi.addAll(gates(job));
    }

    // Then — kein lokaler Check fehlt in CI, und CI prueft nichts, was lokal nicht Pflicht ist
    assertThat(inCi).containsExactlyInAnyOrderElementsOf(lokal);
  }

  @Test
  void javaJobs_givenThePom_thenUseTheSameJdk() throws IOException {
    // Given
    final String jdk = pomEigenschaft("java.version");

    // When / Then
    for (final String job : List.of("verify", "pit")) {
      assertThat(String.valueOf(mit(job, "actions/setup-java").get("java-version"))).isEqualTo(jdk);
      assertThat(mit(job, "actions/setup-java")).containsEntry("cache", "maven");
    }
  }

  @Test
  void frontend_givenThePackageFile_thenUsesItsNodeVersionAndTheMavenOne() throws IOException {
    // Given
    final JsonNode paket = JSON.readTree(WURZEL.resolve("frontend/package.json").toFile());

    // When / Then — dieselbe Node-Version wie das frontend-maven-plugin in mvn verify
    assertThat(mit("frontend", "actions/setup-node"))
        .containsEntry("node-version-file", "frontend/package.json")
        .containsEntry("cache", "npm");
    assertThat("v" + paket.get("engines").get("node").asText())
        .isEqualTo(pomEigenschaft("node.version"));
  }

  @Test
  void workflow_givenTheFile_thenReadsTheRepositoryOnly() throws IOException {
    // When / Then — der Workflow braucht nur Lesezugriff; ein Token mit mehr Rechten waere
    // Angriffsflaeche.
    assertThat(abschnitt("permissions")).containsOnly(Map.entry("contents", "read"));
  }
}
