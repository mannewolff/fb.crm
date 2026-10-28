package org.mwolff.fbcrm.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Die IBAN als Wert (Plan #461, E15): Schreibweise, Format und Pruefung nach Modulo 97.
 *
 * <p>Die Beispiel-IBAN {@code DE89 3704 0044 0532 0130 00} ist die bekannte Muster-IBAN; mit der
 * Endung {@code 01} besteht sie die Pruefziffer nicht.
 */
class IbanTest {

  private static final String GUELTIG = "DE89370400440532013000";

  @Test
  void aus_givenAPrintedIban_thenRemovesTheSpaces() {
    // When
    final Iban iban = Iban.aus("DE89 3704 0044 0532 0130 00");

    // Then
    assertThat(iban.elektronisch()).isEqualTo(GUELTIG);
  }

  @Test
  void aus_givenLowercaseAndSurroundingWhitespace_thenWritesItInCapitals() {
    // When
    final Iban iban = Iban.aus("  de89 3704\t0044 0532 0130 00 ");

    // Then
    assertThat(iban.elektronisch()).isEqualTo(GUELTIG);
  }

  @Test
  void gedruckt_thenGroupsOfFourWithAShorterRest() {
    // When / Then
    assertThat(Iban.aus(GUELTIG).gedruckt()).isEqualTo("DE89 3704 0044 0532 0130 00");
  }

  @Test
  void gedruckt_givenALengthDivisibleByFour_thenNoTrailingSpace() {
    // When — die norwegische Muster-IBAN hat fuenfzehn Zeichen, die belgische sechzehn.
    final Iban belgisch = Iban.aus("BE68539007547034");

    // Then
    assertThat(belgisch.gedruckt()).isEqualTo("BE68 5390 0754 7034");
    assertThat(Iban.aus("NO9386011117947").gedruckt()).isEqualTo("NO93 8601 1117 947");
  }

  @Test
  void aus_givenAWrongCheckDigit_thenRefuses() {
    // When / Then — Kriterium 7: eine ungueltige IBAN wird abgewiesen.
    assertThatThrownBy(() -> Iban.aus("DE89 3704 0044 0532 0130 01"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "   ",
        "DE89",
        "D189370400440532013000",
        "1E89370400440532013000",
        "DEX9370400440532013000",
        "DE8X370400440532013000",
        "DE89-3704-0044-0532-0130-00",
        "DE8937040044053201300000000000000000"
      })
  void aus_givenAWrongFormat_thenRefuses(final String text) {
    // When / Then — zwei Buchstaben, zwei Ziffern und eins bis dreissig Zeichen aus Buchstaben und
    // Ziffern; das letzte Beispiel hat 36 Zeichen.
    assertThatThrownBy(() -> Iban.aus(text)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void gueltig_givenTheLongestAllowedForm_thenOnlyTheCheckDigitDecides() {
    // When / Then — 34 Zeichen sind die Grenze des Formats; die maltesische Muster-IBAN hat 31.
    assertThat(Iban.gueltig("MT84MALT011000012345MTLCAST001S")).isTrue();
    assertThat(Iban.gueltig("MT84MALT011000012345MTLCAST001T")).isFalse();
  }

  @Test
  void gueltig_givenLowercase_thenNotTheElectronicForm() {
    // When / Then — gueltig prueft die Schreibweise ohne Leerzeichen in Grossbuchstaben.
    assertThat(Iban.gueltig("de89370400440532013000")).isFalse();
    assertThat(Iban.gueltig(GUELTIG)).isTrue();
  }

  @Test
  void neu_givenAnInvalidValue_thenRefusesAsWell() {
    // When / Then — auch der Konstruktor laesst keine ungueltige IBAN entstehen.
    assertThatThrownBy(() -> new Iban("DE89 3704 0044 0532 0130 00"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void equals_thenComparesTheElectronicForm() {
    // When / Then
    assertThat(Iban.aus("DE89 3704 0044 0532 0130 00")).isEqualTo(Iban.aus(GUELTIG));
  }
}
