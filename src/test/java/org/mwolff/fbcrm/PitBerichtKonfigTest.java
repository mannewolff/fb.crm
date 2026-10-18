package org.mwolff.fbcrm;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Das pit-Profil schreibt seinen Bericht maschinenlesbar an einen festen Pfad.
 *
 * <p>{@code scripts/mutationsgate.mjs} liest {@code target/pit-reports/mutations.xml} an genau
 * diesem Pfad (Plan #348, E5). Dafuer braucht es {@code XML} unter {@code outputFormats} und {@code
 * timestampedReports=false} — ohne Letzteres wechselt der Ordnername mit jedem Lauf. {@code mvn
 * verify} faehrt PIT nicht; eine entfernte Zeile fiele ohne diesen Test erst beim naechsten
 * Veroeffentlichen auf. Die Schwelle bleibt bei 100 % (E3): Der Lauf von Hand misst weiter dagegen.
 */
class PitBerichtKonfigTest {

  private static Element pitKonfiguration() throws Exception {
    final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    factory.setExpandEntityReferences(false);
    factory.setXIncludeAware(false);
    final Document pom = factory.newDocumentBuilder().parse(Path.of("pom.xml").toFile());
    final NodeList profile = pom.getElementsByTagName("profile");
    for (int i = 0; i < profile.getLength(); i++) {
      final Element profil = (Element) profile.item(i);
      if ("pit".equals(kind(profil, "id").getTextContent().trim())) {
        return (Element) profil.getElementsByTagName("configuration").item(0);
      }
    }
    throw new AssertionError("Profil pit fehlt in pom.xml");
  }

  private static Element kind(final Element eltern, final String name) {
    for (Node n = eltern.getFirstChild(); n != null; n = n.getNextSibling()) {
      if (n instanceof Element e && name.equals(e.getTagName())) {
        return e;
      }
    }
    throw new AssertionError("<" + name + "> fehlt unter <" + eltern.getTagName() + ">");
  }

  @Test
  void schreibtXmlUndHtml() throws Exception {
    final Element formate = kind(pitKonfiguration(), "outputFormats");
    final List<String> werte = new ArrayList<>();
    final NodeList params = formate.getElementsByTagName("param");
    for (int i = 0; i < params.getLength(); i++) {
      werte.add(params.item(i).getTextContent().trim());
    }
    assertThat(werte).containsExactlyInAnyOrder("XML", "HTML");
  }

  @Test
  void schreibtOhneZeitstempelOrdner() throws Exception {
    assertThat(kind(pitKonfiguration(), "timestampedReports").getTextContent().trim())
        .isEqualTo("false");
  }

  @Test
  void schwelleBleibtBeiHundert() throws Exception {
    assertThat(kind(pitKonfiguration(), "mutationThreshold").getTextContent().trim())
        .isEqualTo("100");
  }
}
