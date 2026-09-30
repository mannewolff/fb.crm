package org.mwolff.fbcrm.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Die eine Grenze fuer hochgeladene Dateien und die eine Meldung dazu (Issue #148, Kriterium 6;
 * Plan #150, E8 und E12).
 *
 * <p>Gegenstand ist die Einheit im Meldungssatz. Sie ist keine Formsache: Die Oberflaeche rechnet
 * Groessen binaer und beschriftet sie mit KB und MB (E12), und eine Datei der Grenzgroesse steht
 * dort als „25 MB". Nennte der Satz „25 MiB", stuende an derselben Datei zweimal dieselbe Zahl mit
 * zwei Einheiten.
 */
class UploadgrenzeTest {

  @Test
  void meldung_thenNamesTheLimitInMbAndNotInMib() {
    // When / Then — 26.214.400 Byte sind binaer gerechnet genau 25 MB (E12).
    assertThat(Uploadgrenze.MELDUNG).contains("25 MB").doesNotContain("MiB");
  }

  @Test
  void maxByte_thenIsTheLimitOfTheSpecification() {
    // When / Then
    assertThat(Uploadgrenze.MAX_BYTE).isEqualTo(26_214_400L);
  }
}
