package org.mwolff.fbcrm;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * SonarCloud misst denselben Umfang wie die lokalen Abdeckungs-Gates (Plan #221, A4 bis A7).
 *
 * <p>Die Quelle der Wahrheit sind die beiden lokalen Konfigurationen: {@code pom.xml} mit den
 * {@code excludes} des JaCoCo-Plugins fuer das Backend und {@code frontend/vite.config.ts} mit
 * {@code coverage.exclude} fuer das Frontend. {@code sonar-project.properties} spiegelt sie in
 * {@code sonar.coverage.exclusions}. Ohne diesen Abgleich wertet Sonar jede analysierte Datei ohne
 * Abdeckungsdaten als 0 % — auch die bewusst ausgenommenen — und die Gesamtzahl faellt unter den
 * echten lokalen Stand. Umgekehrt nimmt ein Eintrag, der lokal nicht ausgenommen ist, Sonar die
 * Messung von Code, der gemessen werden soll.
 *
 * <p>Muster fuer das Lesen der Dateien: {@link CiWorkflowTest}.
 */
class SonarKonfigurationTest {

  private static final Path WURZEL = Path.of("");

  /** Ein Abdeckungs-Ausschluss des JaCoCo-Plugins, als Klassenpfad unter {@code org/mwolff}. */
  private static final Pattern JACOCO_AUSSCHLUSS =
      Pattern.compile("<exclude>(org/mwolff/fbcrm/[^<]+)\\.class</exclude>");

  /** Der Block {@code exclude: [ … ]} der Abdeckung in {@code vite.config.ts}. */
  private static final Pattern VITE_BLOCK =
      Pattern.compile("coverage:\\s*\\{.*?exclude:\\s*\\[(.*?)]", Pattern.DOTALL);

  /** Ein Eintrag des Vite-Blocks — einfache Anfuehrungszeichen, wie Prettier sie setzt. */
  private static final Pattern VITE_EINTRAG = Pattern.compile("'([^']+)'");

  /** Trennzeichen der Listen-Eigenschaften von Sonar. */
  private static final Pattern KOMMA = Pattern.compile(",");

  /** Der Namensraum der bewussten Ausnahmen in {@code sonar-project.properties}. */
  private static final String MULTICRITERIA = "sonar.issue.ignore.multicriteria";

  /**
   * Die bewusst zugelassenen Ausnahmen — genau diese und keine weitere (Plan #238, A2 und A3).
   *
   * <p>Je Eintrag trifft eine Regel genau eine Datei. Wer hier etwas ergaenzt, hat sich fuer eine
   * Ausnahme entschieden und begruendet sie in {@code sonar-project.properties}; wer etwas
   * weglaesst, nimmt eine zurueck. Beides soll auffallen, darum steht die Liste hier fest.
   */
  private static final List<String> ZUGELASSENE_AUSNAHMEN =
      List.of(
          "java:S4502 an src/main/java/org/mwolff/fbcrm/auth/web/SecurityConfig.java",
          "java:S5693 an src/main/resources/application.yml");

  private static String lies(final String datei) throws IOException {
    return Files.readString(WURZEL.resolve(datei), StandardCharsets.UTF_8);
  }

  /**
   * Die Eigenschaften aus {@code sonar-project.properties}.
   *
   * <p>{@link Properties} setzt die mit {@code \} fortgesetzten Zeilen selbst zusammen und laesst
   * die Kommentarzeilen weg — genau das Format der Datei.
   */
  private static Properties sonar() throws IOException {
    final Properties eigenschaften = new Properties();
    eigenschaften.load(new StringReader(lies("sonar-project.properties")));
    return eigenschaften;
  }

  /** Eine Listen-Eigenschaft der Sonar-Konfiguration, kommagetrennt. */
  private static List<String> sonarListe(final String name) throws IOException {
    return sonarListe(sonar(), name);
  }

  /** Dieselbe Liste aus schon gelesenen Eigenschaften. */
  private static List<String> sonarListe(final Properties eigenschaften, final String name) {
    final String wert = eigenschaften.getProperty(name);
    assertThat(wert).as("sonar-project.properties traegt %s", name).isNotNull();
    final List<String> eintraege = new ArrayList<>();
    for (final String teil : KOMMA.split(wert, -1)) {
      if (!teil.isBlank()) {
        eintraege.add(teil.strip());
      }
    }
    return eintraege;
  }

  /** Die lokal von der Abdeckung ausgenommenen Dateien, in der Schreibweise der Sonar-Pfade. */
  private static List<String> lokalAusgenommen() throws IOException {
    final List<String> dateien = new ArrayList<>();
    final Matcher jacoco = JACOCO_AUSSCHLUSS.matcher(lies("pom.xml"));
    while (jacoco.find()) {
      dateien.add("src/main/java/" + jacoco.group(1) + ".java");
    }
    final Matcher block = VITE_BLOCK.matcher(lies("frontend/vite.config.ts"));
    assertThat(block.find()).as("vite.config.ts traegt coverage.exclude").isTrue();
    final Matcher eintrag = VITE_EINTRAG.matcher(block.group(1));
    while (eintrag.find()) {
      dateien.add("frontend/" + eintrag.group(1));
    }
    return dateien;
  }

  /**
   * Die bewussten Ausnahmen, je Eintrag als {@code <Regel> an <Datei>}.
   *
   * <p>Die Form macht die Meldung eines Fehlschlags lesbar: Sie nennt den Eintrag, der zu viel, zu
   * wenig oder anders in der Datei steht.
   */
  private static List<String> ausnahmen() throws IOException {
    final Properties eigenschaften = sonar();
    final List<String> beschreibungen = new ArrayList<>();
    for (final String kennung : sonarListe(eigenschaften, MULTICRITERIA)) {
      beschreibungen.add(
          feld(eigenschaften, kennung, "ruleKey")
              + " an "
              + feld(eigenschaften, kennung, "resourceKey"));
    }
    return beschreibungen;
  }

  /** Ein Feld eines Ausnahme-Eintrags; fehlt es, ist der Eintrag unvollstaendig. */
  private static String feld(
      final Properties eigenschaften, final String kennung, final String name) {
    final String schluessel = MULTICRITERIA + "." + kennung + "." + name;
    final String wert = eigenschaften.getProperty(schluessel);
    assertThat(wert).as("sonar-project.properties traegt %s", schluessel).isNotNull();
    return wert.strip();
  }

  @Test
  void multicriteria_givenTheSonarFile_thenCarriesExactlyTheDeliberateExceptions()
      throws IOException {
    // When / Then — jede zusaetzliche, fehlende oder geaenderte Ausnahme faellt hier auf.
    assertThat(ausnahmen())
        .as(
            "sonar.issue.ignore.multicriteria traegt genau die bewusst begruendeten Ausnahmen"
                + " (Plan #238, A2 und A3)")
        .containsExactlyInAnyOrderElementsOf(ZUGELASSENE_AUSNAHMEN);
  }

  @Test
  void multicriteria_givenTheSonarFile_thenNoEntryCarriesAWildcard() throws IOException {
    // When / Then — eine Ausnahme trifft genau eine Regel an genau einer Datei. Ein Muster mit *
    // deckte still mehr ab, als jemand entschieden hat, und bliebe beim naechsten Fund stumm.
    assertThat(ausnahmen())
        .as("kein ruleKey und kein resourceKey enthaelt *")
        .noneMatch(eintrag -> eintrag.contains("*"));
  }

  @Test
  void coverageExclusions_givenPomAndViteConfig_thenSonarExcludesEveryFileTheyExclude()
      throws IOException {
    // Given
    final List<String> lokal = lokalAusgenommen();

    // When / Then
    assertThat(sonarListe("sonar.coverage.exclusions"))
        .as(
            "sonar.coverage.exclusions nimmt jede Datei aus, die pom.xml (JaCoCo) oder"
                + " frontend/vite.config.ts (coverage.exclude) ausnimmt")
        .containsAll(lokal);
  }

  @Test
  void coverageExclusions_givenTheSonarFile_thenPomAndViteConfigExcludeEveryFileItExcludes()
      throws IOException {
    // Given
    final List<String> sonar = sonarListe("sonar.coverage.exclusions");

    // When / Then
    assertThat(lokalAusgenommen())
        .as(
            "pom.xml (JaCoCo) bzw. frontend/vite.config.ts (coverage.exclude) nimmt jede Datei aus,"
                + " die sonar.coverage.exclusions ausnimmt")
        .containsAll(sonar);
  }

  @Test
  void exclusions_givenTheSonarFile_thenLeavesOutTheFlywayMigrations() throws IOException {
    // When / Then — eine gelaufene Migration darf sich nicht mehr aendern: Flyway vergleicht ihre
    // Pruefsumme und bricht jede bestehende Datenbank ab, sobald die Datei abweicht. Ein Fund dort
    // ist deshalb nicht behebbar, sondern nur Rauschen.
    assertThat(sonarListe("sonar.exclusions")).contains("src/main/resources/db/migration/**");
  }

  @Test
  void sonarFile_givenTheRepository_thenNamesTheProjectOnSonarCloud() throws IOException {
    // When / Then
    assertThat(sonar())
        .containsEntry("sonar.projectKey", "mannewolff_fb.crm")
        .containsEntry("sonar.organization", "mannewolff")
        .containsEntry("sonar.host.url", "https://sonarcloud.io");
  }

  @Test
  void automaticAnalysisFile_givenTheRepository_thenIsGone() {
    // When / Then — ohne Automatic Analysis ist .sonarcloud.properties wirkungslos (Plan #221, A7);
    // die Konfiguration steht vollstaendig in sonar-project.properties.
    assertThat(WURZEL.resolve(".sonarcloud.properties")).doesNotExist();
  }
}
