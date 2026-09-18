package org.mwolff.fbcrm.config;

import org.mwolff.fbcrm.common.SecureTokens;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Die eine Quelle der Einmal-Tokens.
 *
 * <p>{@link SecureTokens} ist bewusst framework-frei und traegt deshalb keine Spring-Annotation;
 * die Bean entsteht hier — wie die Uhr in {@link TimeConfig}. Eine Instanz fuer die ganze Anwendung
 * genuegt: Sie haelt nur ihre Zufallsquelle, und die ist nebenlaeufig benutzbar.
 */
@Configuration
public class TokenConfig {

  @Bean
  public SecureTokens secureTokens() {
    return new SecureTokens();
  }
}
