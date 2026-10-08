package org.mwolff.fbcrm.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Haengt {@link SpaResourceResolver} vor die Auslieferung statischer Dateien (E20).
 *
 * <p>Reines Wiring: Die Entscheidung, welcher Pfad zur Oberflaeche und welcher zur API gehoert,
 * steht im Resolver und ist dort geprueft ({@code SpaForwardingTest}). Diese Klasse ist deshalb von
 * Abdeckung und Mutationstest ausgenommen (CLAUDE-java.md §5.2); dass sie greift, weist {@code
 * AccessRuleIT} gegen den laufenden Server nach.
 */
@Configuration
public class SpaForwardingConfig implements WebMvcConfigurer {

  private static final String STATIC_LOCATION = "classpath:/static/";
  private static final String INDEX = "static/index.html";

  @Override
  public void addResourceHandlers(final ResourceHandlerRegistry registry) {
    registry
        .addResourceHandler("/**")
        .addResourceLocations(STATIC_LOCATION)
        .resourceChain(true)
        .addResolver(new SpaResourceResolver(new ClassPathResource(INDEX)));
  }
}
