package org.mwolff.fbcrm.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.common.SecureTokens;

/**
 * Die Quelle der Einmal-Tokens steht als Bean bereit.
 *
 * <p>{@link SecureTokens} ist bewusst framework-frei und traegt deshalb keine Spring-Annotation;
 * die Bean entsteht hier — wie die Uhr in {@link TimeConfig}.
 */
class TokenConfigTest {

  @Test
  void secureTokens_thenProducesDistinctTokens() {
    // Given
    final SecureTokens tokens = new TokenConfig().secureTokens();

    // When / Then — die Zufallsquelle der Plattform, nicht eine feste Zeichenkette.
    assertThat(tokens.newToken()).isNotEqualTo(tokens.newToken());
  }
}
