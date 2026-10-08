package org.mwolff.fbcrm;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Die Versionsnummer steht an mehreren Stellen — hier wird nachgezaehlt, dass es dieselbe ist.
 *
 * <p>Quelle der Wahrheit ist {@code VERSION} (RELEASING.md); {@code pom.xml}, {@code
 * frontend/package.json} und {@code frontend/package-lock.json} sind Abschriften, die {@code
 * scripts/bump-version.mjs} nachzieht. Der Test liest jede Datei einzeln ein und vergleicht sie mit
 * {@code VERSION} — gaebe es hier eine Konstante, pruefte er die Konstante gegen sich selbst.
 *
 * <p>Die Datei {@code frontend/package-lock.json} steht mit in der Reihe, obwohl sie eine reine
 * Abschrift von {@code package.json} ist: Sie ist die Stelle, an der {@code bump-version.mjs} am
 * ehesten danebengreift, und {@code npm ci} bricht ab, sobald die beiden auseinanderlaufen.
 */
class VersionConsistencyTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  /** Arbeitsverzeichnis von Surefire ist das Projektverzeichnis. */
  private static final Path WURZEL = Path.of("");

  private static String datei(final String pfad) throws Exception {
    return Files.readString(WURZEL.resolve(pfad), StandardCharsets.UTF_8);
  }

  private static String versionsdatei() throws Exception {
    return datei("VERSION").strip();
  }

  /**
   * Die Projektversion aus {@code pom.xml} — das direkte {@code <version>} unter {@code <project>}.
   *
   * <p>Ueber die Kindknoten und nicht ueber eine Textsuche: Die erste {@code <version>} in der
   * Datei gehoert zum {@code <parent>} und traegt die Spring-Boot-Version.
   */
  private static String pomVersion() throws Exception {
    final DocumentBuilderFactory fabrik = DocumentBuilderFactory.newInstance();
    fabrik.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    fabrik.setXIncludeAware(false);
    fabrik.setExpandEntityReferences(false);
    final Document dokument = fabrik.newDocumentBuilder().parse(WURZEL.resolve("pom.xml").toFile());
    final NodeList kinder = dokument.getDocumentElement().getChildNodes();
    for (int i = 0; i < kinder.getLength(); i++) {
      final Node kind = kinder.item(i);
      if (kind.getNodeType() == Node.ELEMENT_NODE && "version".equals(kind.getNodeName())) {
        return kind.getTextContent().strip();
      }
    }
    throw new IllegalStateException("pom.xml traegt kein eigenes <version> unter <project>");
  }

  private static String jsonVersion(final String pfad, final String... pfadImBaum)
      throws Exception {
    JsonNode knoten = JSON.readTree(datei(pfad));
    for (final String feld : pfadImBaum) {
      knoten = knoten.path(feld);
    }
    return knoten.asText();
  }

  @Test
  void pom_thenCarriesTheVersionOfTheVersionFile() throws Exception {
    // Then
    assertThat(pomVersion()).isEqualTo(versionsdatei());
  }

  @Test
  void packageJson_thenCarriesTheVersionOfTheVersionFile() throws Exception {
    // Then
    assertThat(jsonVersion("frontend/package.json", "version")).isEqualTo(versionsdatei());
  }

  @Test
  void packageLock_thenCarriesTheVersionOfTheVersionFile() throws Exception {
    // Then
    assertThat(jsonVersion("frontend/package-lock.json", "version")).isEqualTo(versionsdatei());
  }

  @Test
  void packageLock_thenCarriesTheVersionInItsRootPackageEntry() throws Exception {
    // Then — npm fuehrt die Version ein zweites Mal unter packages[""].
    assertThat(jsonVersion("frontend/package-lock.json", "packages", "", "version"))
        .isEqualTo(versionsdatei());
  }

  @Test
  void versionFile_thenCarriesExactlyOneThreePartVersion() throws Exception {
    // Then — X.Y.Z, eine Zeile, sonst nichts; darauf rechnet bump-version.mjs.
    assertThat(datei("VERSION")).matches("\\d+\\.\\d+\\.\\d+\\R?");
  }
}
