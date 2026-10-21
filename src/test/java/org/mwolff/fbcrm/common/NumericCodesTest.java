package org.mwolff.fbcrm.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Der sechsstellige Einmalcode der Anmeldung. */
class NumericCodesTest {

  /** Liefert einen festen Wert und merkt sich jede Schranke, mit der gefragt wurde. */
  private static final class FesteQuelle extends SecureRandom {

    private static final long serialVersionUID = 1L;

    private final int wert;
    private final transient List<Integer> schranken = new ArrayList<>();

    FesteQuelle(final int wert) {
      this.wert = wert;
    }

    @Override
    public int nextInt(final int bound) {
      schranken.add(bound);
      return wert;
    }
  }

  @Test
  void newCode_thenSixDigits() {
    // Given
    final NumericCodes codes = new NumericCodes();

    // When
    final String code = codes.newCode();

    // Then
    assertThat(code).hasSize(6).containsOnlyDigits();
  }

  @Test
  void newCode_whenSourceGivesSeven_thenLeadingZerosStay() {
    assertThat(new NumericCodes(new FesteQuelle(7)).newCode()).isEqualTo("000007");
  }

  @Test
  void newCode_whenSourceGivesZero_thenAllZeros() {
    assertThat(new NumericCodes(new FesteQuelle(0)).newCode()).isEqualTo("000000");
  }

  @Test
  void newCode_whenSourceGivesUpperBound_thenAllNines() {
    assertThat(new NumericCodes(new FesteQuelle(999_999)).newCode()).isEqualTo("999999");
  }

  @Test
  void newCode_thenAsksSourceOnceOverWholeRange() {
    // Given
    final FesteQuelle quelle = new FesteQuelle(42);

    // When
    new NumericCodes(quelle).newCode();

    // Then — eine Ziehung ueber 0..999999, ohne Restklassen-Bias.
    assertThat(quelle.schranken).containsExactly(1_000_000);
  }
}
