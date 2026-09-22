package org.mwolff.fbcrm;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * {@code .env.example} und die Compose-Dateien sagen dasselbe.
 *
 * <p>Die Vorlage ist die einzige Stelle, an der ein Betreiber erfaehrt, was er setzen kann. Fehlt
 * dort eine Variable, die {@code docker-compose.yml} oder {@code docker-compose.prod.yml} liest,
 * kennt er sie nicht; fuehrt sie eine, die niemand liest, setzt er einen Wert ohne Wirkung — und
 * nichts schlaegt an. Beide Richtungen werden deshalb geprueft.
 *
 * <p>Gesammelt wird jede {@code ${NAME}}-Referenz, nicht nur die mit {@code FBCRM_}: Auch {@code
 * POSTGRES_PASSWORD} und {@code MINIO_ROOT_PASSWORD} kommen aus der {@code .env}. Kommentare in den
 * Compose-Dateien bleiben aussen vor — sie nennen Variablen zur Erklaerung, lesen sie aber nicht.
 */
class EnvExampleTest {

  private static final Path WURZEL = Path.of("");
  private static final Pattern REFERENZ = Pattern.compile("\\$\\{([A-Z][A-Z0-9_]*)");
  private static final Pattern ZUWEISUNG =
      Pattern.compile("^([A-Z][A-Z0-9_]*)=", Pattern.MULTILINE);

  static Set<String> gelesenVonCompose() throws IOException {
    final Set<String> namen = new TreeSet<>();
    for (final String datei : new String[] {"docker-compose.yml", "docker-compose.prod.yml"}) {
      for (final String zeile : Files.readAllLines(WURZEL.resolve(datei), StandardCharsets.UTF_8)) {
        final Matcher treffer = REFERENZ.matcher(ohneKommentar(zeile));
        while (treffer.find()) {
          namen.add(treffer.group(1));
        }
      }
    }
    return namen;
  }

  static Set<String> gefuehrtInEnvExample() throws IOException {
    final Set<String> namen = new TreeSet<>();
    final Matcher treffer =
        ZUWEISUNG.matcher(Files.readString(WURZEL.resolve(".env.example"), StandardCharsets.UTF_8));
    while (treffer.find()) {
      namen.add(treffer.group(1));
    }
    return namen;
  }

  /** Schneidet einen YAML-Kommentar ab: {@code #} am Zeilenanfang oder nach Leerraum. */
  static String ohneKommentar(final String zeile) {
    final Matcher kommentar = Pattern.compile("(^|\\s)#").matcher(zeile);
    return kommentar.find() ? zeile.substring(0, kommentar.start()) : zeile;
  }

  @Test
  void envExample_givenTheComposeFiles_thenListsEveryVariableTheyRead() throws IOException {
    // Given
    final Set<String> gelesen = gelesenVonCompose();

    // When / Then
    assertThat(gelesen).isNotEmpty();
    assertThat(gefuehrtInEnvExample()).containsAll(gelesen);
  }

  @Test
  void envExample_givenTheComposeFiles_thenListsNoVariableNobodyReads() throws IOException {
    // When / Then
    assertThat(gelesenVonCompose()).containsAll(gefuehrtInEnvExample());
  }

  @Test
  void ohneKommentar_givenAnExplanatoryComment_thenTheReferenceInsideItIsNotRead() {
    // When / Then — die Compose-Datei erklaert in Kommentaren die Form ${NAME:-}.
    assertThat(ohneKommentar("      # der Job fiele bei der Form ${NAME:-} also")).isBlank();
    assertThat(ohneKommentar("      FBCRM_X: ${FBCRM_X:-a} # Erklaerung")).contains("${FBCRM_X");
  }

  @Test
  void ohneKommentar_givenAHashInsideAValue_thenKeepsTheValue() {
    // When / Then — nur ein # nach Leerraum beginnt einen Kommentar.
    assertThat(ohneKommentar("  X: ${A:-farbe#1}")).isEqualTo("  X: ${A:-farbe#1}");
  }
}
