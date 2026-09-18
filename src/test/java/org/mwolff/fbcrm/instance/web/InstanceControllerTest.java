package org.mwolff.fbcrm.instance.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.info.BuildProperties;

/**
 * Die Auskunft ueber die laufende Instanz (K11, E10).
 *
 * <p>Der Controller entscheidet genau eines: was er sagt, wenn der Bau keine Version hinterlassen
 * hat. Die Version selbst kommt aus {@link BuildProperties} und damit aus dem Maven-Lauf — ein zur
 * Bauzeit ins Frontend geschriebener Wert saehe nach einem Hot-Deploy anders aus als das, was
 * wirklich laeuft.
 */
class InstanceControllerTest {

  private static InstanceController controller(final Properties eigenschaften) {
    return new InstanceController(new BuildProperties(eigenschaften));
  }

  private static Properties mitVersion(final String version) {
    final Properties eigenschaften = new Properties();
    eigenschaften.setProperty("version", version);
    return eigenschaften;
  }

  @Test
  void instance_thenAnswersWithTheVersionOfTheBuild() {
    // When
    final InstanceResponse antwort = controller(mitVersion("0.1.0")).instance();

    // Then
    assertThat(antwort).isEqualTo(new InstanceResponse("0.1.0"));
  }

  @Test
  void instance_givenAnotherBuild_thenAnswersWithThatVersion() {
    // When — der Wert wird durchgereicht, nicht hergestellt.
    final InstanceResponse antwort = controller(mitVersion("1.2.3")).instance();

    // Then
    assertThat(antwort).isEqualTo(new InstanceResponse("1.2.3"));
  }

  @Test
  void instance_givenABuildWithoutAVersion_thenSaysSoInsteadOfFailing() {
    // When — build-info.properties ohne version: der Bau ist kaputt, die Auskunft bleibt ehrlich.
    final InstanceResponse antwort = controller(new Properties()).instance();

    // Then
    assertThat(antwort).isEqualTo(new InstanceResponse("unbekannt"));
  }
}
