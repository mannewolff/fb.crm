package org.mwolff.fbcrm.common.web;

import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Haengt {@link Bedingungskopf.Leser} an die Controller an (Plan #415, E12).
 *
 * <p>Steht neben dem Leser in {@code common.web} und nicht in {@code config}: Wer den geschuetzten
 * Parameter nimmt, soll ihn an einer Stelle finden, samt dem Weg, auf dem er gefuellt wird.
 */
@Configuration
public class BedingungskopfKonfiguration implements WebMvcConfigurer {

  @Override
  public void addArgumentResolvers(final List<HandlerMethodArgumentResolver> resolvers) {
    resolvers.add(new Bedingungskopf.Leser());
  }
}
