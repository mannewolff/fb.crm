package org.mwolff.fbcrm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Einstiegspunkt der Anwendung.
 *
 * <p>{@code @ConfigurationPropertiesScan} bindet und <b>validiert</b> die Schalter beim Start —
 * eine fehlende oder zu schwache Einstellung (etwa {@code FBCRM_SESSION_SECRET}) laesst die
 * Anwendung erst gar nicht hochkommen, statt sie unsicher laufen zu lassen.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class FbCrmApplication {

  public static void main(final String[] args) {
    SpringApplication.run(FbCrmApplication.class, args);
  }
}
