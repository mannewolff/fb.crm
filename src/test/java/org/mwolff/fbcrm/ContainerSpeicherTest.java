package org.mwolff.fbcrm;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Der API-Container hat eine Speichergrenze, und die JVM kennt sie.
 *
 * <p>Ohne {@code mem_limit} bemisst die JVM ihren Heap am Speicher der ganzen Maschine und nicht an
 * dem, was ihr zusteht: Am 2026-09-26 beendete das System {@code fb-crm-fbcrm-api-1} drei Sekunden
 * nach dem erfolgreichen Start mit {@code Exit 137} ({@code OOMKilled}). Beide Haelften gehoeren
 * zusammen — eine Grenze am Container ohne Prozentwert in der JVM laesst den Heap weiter zu gross
 * werden, ein Prozentwert ohne Grenze hat nichts, worauf er sich bezieht. Darum werden sie hier
 * gemeinsam festgeschrieben (Issue #85).
 */
class ContainerSpeicherTest {

  private static final Path WURZEL = Path.of("");
  private static final Pattern MAX_RAM_PERCENTAGE =
      Pattern.compile("-XX:MaxRAMPercentage=(\\d+(?:\\.\\d+)?)");

  /** Die {@code ENTRYPOINT}-Zeile des Dockerfiles — die letzte, falls es mehrere Stufen gibt. */
  static String entrypointZeile() throws IOException {
    return Files.readAllLines(WURZEL.resolve("Dockerfile"), StandardCharsets.UTF_8).stream()
        .map(String::strip)
        .filter(zeile -> zeile.startsWith("ENTRYPOINT"))
        .reduce((erste, zweite) -> zweite)
        .orElseThrow(() -> new AssertionError("Das Dockerfile hat keine ENTRYPOINT-Zeile."));
  }

  /** Die Zeilen des Dienstes {@code fbcrm-api} aus {@code docker-compose.yml}. */
  static List<String> dienstZeilen(final String dienst) throws IOException {
    final List<String> alle =
        Files.readAllLines(WURZEL.resolve("docker-compose.yml"), StandardCharsets.UTF_8);
    final int beginn = alle.indexOf("  " + dienst + ":");
    assertThat(beginn).as("Dienst %s in docker-compose.yml", dienst).isNotNegative();
    int ende = beginn + 1;
    while (ende < alle.size() && !alle.get(ende).matches("^ {2}\\S.*")) {
      ende++;
    }
    return alle.subList(beginn, ende);
  }

  @Test
  void dockerfile_givenTheEntrypoint_thenBoundsTheHeapToAShareOfTheContainer() throws IOException {
    // Given
    final Matcher prozent = MAX_RAM_PERCENTAGE.matcher(entrypointZeile());

    // When / Then — zu niedrig verschenkt Speicher, zu hoch laesst nichts fuer Metaspace,
    // Threads und Puffer neben dem Heap uebrig.
    assertThat(prozent.find()).as("-XX:MaxRAMPercentage= im ENTRYPOINT").isTrue();
    assertThat(Double.parseDouble(prozent.group(1))).isBetween(50.0, 75.0);
  }

  @Test
  void compose_givenTheApiService_thenCapsItsMemoryFromTheEnvironment() throws IOException {
    // Given
    final List<String> zeilen = dienstZeilen("fbcrm-api");

    // When
    final List<String> grenze =
        zeilen.stream()
            .map(EnvExampleTest::ohneKommentar)
            .filter(zeile -> zeile.strip().startsWith("mem_limit:"))
            .toList();

    // Then — einstellbar, damit ein staerker belasteter Server mehr bekommen kann.
    assertThat(grenze).singleElement().asString().contains("${FBCRM_API_MEM_LIMIT");
  }
}
