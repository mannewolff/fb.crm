package org.mwolff.fbcrm.rechnung.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.common.Uploadgrenze;

/**
 * Die eigene Grenze des Briefpapiers und ihre Meldung (Plan #363, E17).
 *
 * <p>Gegenstand sind Wert und Einheit: 5 MB binaer gerechnet, genannt in MB wie bei {@link
 * Uploadgrenze}, und eine andere Zahl als dort — die eine Grenze der uebrigen Uploads bleibt, wie
 * sie ist.
 */
class BriefpapiergrenzeTest {

  @Test
  void maxByte_thenIsFiveMegabyteBinary() {
    // When / Then — 5 * 1024 * 1024 (E17).
    assertThat(Briefpapiergrenze.MAX_BYTE).isEqualTo(5_242_880L);
  }

  @Test
  void meldung_thenNamesTheLimitInMbAndNotInMib() {
    // When / Then
    assertThat(Briefpapiergrenze.MELDUNG)
        .isEqualTo("Das Briefpapier darf höchstens 5242880 Byte (5 MB) groß sein.")
        .doesNotContain("MiB");
  }

  @Test
  void maxByte_thenLeavesTheCommonUploadLimitAlone() {
    // When / Then — zwei Grenzen, zwei Werte (E17).
    assertThat(Briefpapiergrenze.MAX_BYTE).isLessThan(Uploadgrenze.MAX_BYTE);
    assertThat(Uploadgrenze.MAX_BYTE).isEqualTo(26_214_400L);
  }
}
