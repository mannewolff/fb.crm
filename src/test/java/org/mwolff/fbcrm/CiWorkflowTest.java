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
import org.assertj.core.api.InstanceOfAssertFactories;
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
 * <p>Die Zuordnung zu den Jobs folgt den Bereichen aus {@code buildChecks}: {@code betrieb} und
 * {@code backend} laufen in {@code verify}, {@code betrieb} zuerst, weil es nur die Workflow-Config
 * liest und vor dem langen Maven-Lauf scheitern soll; {@code frontend} laeuft in {@code frontend},
 * die Mutationstests in {@code pit}. Ein neuer Bereich in der Config bricht diesen Test — genau
 * dann muss jemand entscheiden, wo er in CI laeuft.
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
   * Ein Job als Abbildung.
   *
   * <p>Ungepruefter Cast aus demselben Grund wie bei {@link #workflow()}: Jeder Job ist in einem
   * gueltigen Workflow eine Abbildung mit Zeichenketten als Schluessel.
   */
  @SuppressWarnings("unchecked")
  private static Map<String, Object> job(final String name) throws IOException {
    return (Map<String, Object>) jobs().get(name);
  }

  /**
   * Die Schritte eines Jobs.
   *
   * <p>Ungepruefter Cast aus demselben Grund wie bei {@link #workflow()}: {@code steps} ist in
   * jedem gueltigen Workflow eine Liste von Abbildungen.
   */
  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> schritte(final String job) throws IOException {
    return (List<Map<String, Object>>) job(job).get("steps");
  }

  /** Die Positionen der Schritte eines Jobs, die die genannte {@code id} tragen. */
  private static List<Integer> positionen(final String job, final String id) throws IOException {
    final List<Integer> treffer = new ArrayList<>();
    final List<Map<String, Object>> alle = schritte(job);
    for (int i = 0; i < alle.size(); i++) {
      if (id.equals(alle.get(i).get("id"))) {
        treffer.add(i);
      }
    }
    return treffer;
  }

  /** Der einzige Schritt eines Jobs mit der genannten {@code id}. */
  private static Map<String, Object> schritt(final String job, final String id) throws IOException {
    final List<Integer> treffer = positionen(job, id);
    assertThat(treffer).as("Job %s traegt genau einen Schritt %s", job, id).hasSize(1);
    return schritte(job).get(treffer.getFirst());
  }

  /** Die Schluessel des {@code env}-Felds eines Jobs oder Schritts; leer ohne {@code env}. */
  @SuppressWarnings("unchecked")
  private static List<String> envSchluessel(final Map<String, Object> traeger) {
    final Object env = traeger.get("env");
    return env == null ? List.of() : new ArrayList<>(((Map<String, Object>) env).keySet());
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

  /** Die {@code buildChecks} eines Bereichs, die in CI laufen, in der Reihenfolge der Config. */
  private static List<String> buildChecks(final String bereich) throws IOException {
    final List<String> kommandos = new ArrayList<>();
    for (final JsonNode check : ciChecks()) {
      for (final JsonNode area : check.get("areas")) {
        if (bereich.equals(area.asText())) {
          kommandos.add(check.get("cmd").asText());
        }
      }
    }
    return kommandos;
  }

  /**
   * Die {@code buildChecks}, die CI faehrt.
   *
   * <p>CI prueft den Stand, der veroeffentlicht wird. Fuehrt ein Bereich neben seinem
   * Paketstufen-Eintrag einen Eintrag spaeterer Stufe ({@code push}, {@code merge}), ist der
   * Paketstufen-Eintrag dessen lokale Abkuerzung (etwa {@code -DskipITs}) und laeuft in CI nicht
   * mit — der spaetere Eintrag deckt ihn ab.
   */
  private static List<JsonNode> ciChecks() throws IOException {
    final List<JsonNode> alle = new ArrayList<>();
    config().get("buildChecks").forEach(alle::add);
    final List<String> spaeterGeprueft = new ArrayList<>();
    for (final JsonNode check : alle) {
      if (!istPaketstufe(check)) {
        check.get("areas").forEach(area -> spaeterGeprueft.add(area.asText()));
      }
    }
    final List<JsonNode> inCi = new ArrayList<>();
    for (final JsonNode check : alle) {
      boolean abgedeckt = false;
      for (final JsonNode area : check.get("areas")) {
        abgedeckt |= spaeterGeprueft.contains(area.asText());
      }
      if (!(istPaketstufe(check) && abgedeckt)) {
        inCi.add(check);
      }
    }
    return inCi;
  }

  /** Ein Eintrag ohne {@code stufe} gilt als Paketstufe (checks.mjs, Issue #758). */
  private static boolean istPaketstufe(final JsonNode check) {
    return !check.has("stufe") || "paket".equals(check.get("stufe").asText());
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
  void workflow_givenTheFile_thenHasExactlyTheFourJobs() throws IOException {
    // When / Then
    assertThat(jobs().keySet())
        .containsExactlyInAnyOrder("verify", "pit", "frontend", "mutationskarten");
  }

  @Test
  void workflow_givenTheFile_thenRunsOnPushAndPullRequest() throws IOException {
    // When / Then — "on" steht in Anfuehrungszeichen: YAML 1.1 laese es sonst als true.
    assertThat(abschnitt("on")).containsOnlyKeys("push", "pull_request");
  }

  @Test
  void verify_givenTheConfig_thenRunsExactlyTheBetriebAndBackendChecks() throws IOException {
    // Given — erst betrieb, dann backend, jeweils in der Reihenfolge der Config
    final List<String> erwartet = new ArrayList<>(buildChecks("betrieb"));
    erwartet.addAll(buildChecks("backend"));

    // When / Then
    assertThat(gates("verify")).isNotEmpty().isEqualTo(erwartet);
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
    for (final JsonNode check : ciChecks()) {
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

  @Test
  void pit_givenTheShorterJobs_thenRunsOnlyAfterThem() throws IOException {
    // When / Then — ein Stand, der wegen eines roten kuerzeren Jobs nicht hinausgeht, startet
    // keine Liegezeit (Plan #348, E20).
    assertThat(job("pit").get("needs"))
        .asInstanceOf(InstanceOfAssertFactories.LIST)
        .containsExactlyInAnyOrder("verify", "frontend");
  }

  @Test
  void pit_givenAPushRun_thenTheMutationCommandDoesNotDecideByItsExitCode() throws IOException {
    // When / Then — die Schwelle 100 bleibt im Kommando, entschieden wird am Bericht (E3, E17)
    assertThat(schritt("pit", "gate-pit"))
        .containsEntry("continue-on-error", "${{ github.event_name == 'push' }}");
  }

  @Test
  void pit_givenAPushRun_thenTheQuotaStepFollowsTheMutationCommandWithoutToken()
      throws IOException {
    // Given
    final int gate = positionen("pit", "gate-pit").getFirst();

    // When
    final Map<String, Object> quote = schritt("pit", "mutationsquote");

    // Then — auf jedem push-Lauf, ohne Board und ohne Geheimnis (E2, E15)
    assertThat(positionen("pit", "mutationsquote").getFirst()).isGreaterThan(gate);
    assertThat(String.valueOf(quote.get("if"))).contains("github.event_name == 'push'");
    assertThat(quote).doesNotContainKey("env");
  }

  @Test
  void mutationskarten_givenTheConfig_thenRunsOnlyOnPushToTheCheckBranch() throws IOException {
    // Given
    final String zweig = config().get("pushPruefung").get("zweig").asText();

    // When
    final Map<String, Object> karten = job("mutationskarten");

    // Then — die Umgebung mit dem Board-Zugang ist auf den Pruefzweig begrenzt (E2, E21)
    assertThat(zweig).isNotBlank();
    assertThat(karten.get("needs"))
        .asInstanceOf(InstanceOfAssertFactories.LIST)
        .containsExactly("pit");
    assertThat(karten).containsEntry("environment", "board-schreiben");
    assertThat(String.valueOf(karten.get("if")))
        .contains("github.event_name == 'push'")
        .contains("'" + zweig + "'");
  }

  @Test
  void mutationskarten_givenTheBoardStep_thenItCarriesTheTokenAsItsOnlyEnv() throws IOException {
    // When / Then — das Token steht allein am schreibenden Schritt (E15)
    assertThat(envSchluessel(schritt("mutationskarten", "mutationskarten")))
        .containsExactly("TBX_TOKEN");
    assertThat(job("mutationskarten")).doesNotContainKey("env");
  }

  @Test
  void workflow_givenEveryOtherJobAndStep_thenNoneCarriesTheTokenOrAnEnvironment()
      throws IOException {
    // When / Then — der eine erlaubte Traeger ist der Schritt mutationskarten im gleichnamigen Job
    assertThat(workflow()).doesNotContainKeys("env", "environment");
    for (final String name : jobs().keySet()) {
      assertThat(envSchluessel(job(name))).as("env am Job %s", name).doesNotContain("TBX_TOKEN");
      if (!"mutationskarten".equals(name)) {
        assertThat(job(name)).as("Job %s", name).doesNotContainKey("environment");
      }
      for (final Map<String, Object> schritt : schritte(name)) {
        final boolean erlaubt =
            "mutationskarten".equals(name) && "mutationskarten".equals(schritt.get("id"));
        if (!erlaubt) {
          assertThat(envSchluessel(schritt))
              .as("env eines Schritts in %s", name)
              .doesNotContain("TBX_TOKEN");
        }
      }
    }
  }
}
