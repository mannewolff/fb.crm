package org.mwolff.fbcrm.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Die Zerlegung der bisherigen Bankverbindung (Plan #461, E14; fachliche Quelle #460, Kriterium
 * 11).
 *
 * <p>Gesucht werden die erste gueltige IBAN und eine BIC aus demselben Land. Was keine gueltige
 * IBAN ist, bleibt leer — die Felder fuellt dann der Mensch.
 */
class BankverbindungszerlegungTest {

  private static final Iban MUSTER = Iban.aus("DE89370400440532013000");

  private static Bankverbindungszerlegung.Zerlegt zerlegt(final String freitext) {
    return Bankverbindungszerlegung.zerlege(freitext);
  }

  @Test
  void zerlege_givenTheUsualFreeText_thenIbanAndBic() {
    // When — der Freitext aus dem Akzeptanzkriterium des Pakets.
    final Bankverbindungszerlegung.Zerlegt zerlegt =
        zerlegt("Max Muster, IBAN DE89 3704 0044 0532 0130 00, BIC COBADEFFXXX, Commerzbank");

    // Then
    assertThat(zerlegt).isEqualTo(new Bankverbindungszerlegung.Zerlegt(MUSTER, "COBADEFFXXX"));
  }

  @Test
  void zerlege_givenOnlyAnIban_thenNoBic() {
    // When / Then
    assertThat(zerlegt("DE89 3704 0044 0532 0130 00"))
        .isEqualTo(new Bankverbindungszerlegung.Zerlegt(MUSTER, null));
  }

  @Test
  void zerlege_givenAWrongCheckDigit_thenNeitherIbanNorBic() {
    // When / Then — ohne gueltige IBAN bleiben alle Felder leer, auch wenn eine BIC dasteht.
    assertThat(zerlegt("IBAN DE89 3704 0044 0532 0130 01, BIC COBADEFFXXX"))
        .isEqualTo(new Bankverbindungszerlegung.Zerlegt(null, null));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   ", "Sparkasse Bremen, Konto 1234567, BLZ 29050101", "COBADEFFXXX"})
  void zerlege_givenNoIbanInTheText_thenNothing(final String freitext) {
    // When / Then
    assertThat(Bankverbindungszerlegung.zerlege(freitext))
        .isEqualTo(new Bankverbindungszerlegung.Zerlegt(null, null));
  }

  @Test
  void zerlege_givenAnIbanInOnePieceAndLowercase_thenFindsIt() {
    // When / Then — Gross- und Kleinschreibung zaehlt bei der IBAN nicht.
    assertThat(zerlegt("Konto: de89370400440532013000").iban()).isEqualTo(MUSTER);
  }

  @Test
  void zerlege_givenAnIbanFollowedByWordsWithoutAComma_thenTheWholeIban() {
    // When — die IBAN endet nicht an einem Satzzeichen; die laengste gueltige Folge gilt.
    final Bankverbindungszerlegung.Zerlegt zerlegt =
        zerlegt("IBAN DE89 3704 0044 0532 0130 00 BIC COBADEFF bei der Commerzbank");

    // Then
    assertThat(zerlegt).isEqualTo(new Bankverbindungszerlegung.Zerlegt(MUSTER, "COBADEFF"));
  }

  @Test
  void zerlege_givenTwoIbans_thenTheFirst() {
    // When
    final Bankverbindungszerlegung.Zerlegt zerlegt =
        zerlegt("Geschaeft BE68 5390 0754 7034; privat DE89 3704 0044 0532 0130 00");

    // Then
    assertThat(zerlegt.iban()).isEqualTo(Iban.aus("BE68539007547034"));
  }

  @Test
  void zerlege_givenAnInvalidIbanBeforeAValidOne_thenTheValidOne() {
    // When / Then — gueltig ist die erste Folge, die die Pruefung besteht.
    assertThat(zerlegt("alt DE89 3704 0044 0532 0130 01, neu DE89 3704 0044 0532 0130 00").iban())
        .isEqualTo(MUSTER);
  }

  @Test
  void zerlege_givenCommerzbankInCapitals_thenNoBic() {
    // When — „COMMERZBANK" hat elf Grossbuchstaben, und ER ist das Kuerzel Eritreas. Aus demselben
    // Land wie die IBAN ist es nicht.
    final Bankverbindungszerlegung.Zerlegt zerlegt =
        zerlegt("IBAN DE89 3704 0044 0532 0130 00, COMMERZBANK");

    // Then
    assertThat(zerlegt).isEqualTo(new Bankverbindungszerlegung.Zerlegt(MUSTER, null));
  }

  @Test
  void zerlege_givenAWordWithAWrongCountryBeforeTheBic_thenTheBic() {
    // When / Then
    assertThat(zerlegt("COMMERZBANK, BNPAFRPPXXX, COBADEFFXXX, DE89 3704 0044 0532 0130 00").bic())
        .isEqualTo("COBADEFFXXX");
  }

  @Test
  void zerlege_givenABicInLowercase_thenNoBic() {
    // When / Then — eine BIC besteht aus Grossbuchstaben und Ziffern.
    assertThat(zerlegt("DE89 3704 0044 0532 0130 00, cobadeffxxx").bic()).isNull();
  }

  @Test
  void zerlege_givenAnOverlongSequence_thenTheIbanAlone() {
    // When — mit dem Wort danach waere die Folge laenger als 34 Zeichen.
    final Bankverbindungszerlegung.Zerlegt zerlegt =
        zerlegt("DE89 3704 0044 0532 0130 00 ABCDEFGHIJKLMNOPQRSTUVWXYZ");

    // Then
    assertThat(zerlegt.iban()).isEqualTo(MUSTER);
  }

  @ParameterizedTest
  @ValueSource(strings = {"COBADEFF", "COBADEFFXXX", "DEUTDE2H", "DEUTDE2H123"})
  void istBic_givenEightOrElevenCharactersWithAKnownCountry_thenTrue(final String wort) {
    // When / Then
    assertThat(Bankverbindungszerlegung.istBic(wort)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "COBADEF",
        "COBADEFFX",
        "COBADEFFXX",
        "COBADEFFXXXX",
        "COBAQQFFXXX",
        "cobadeffxxx",
        "COBA-DEFFXX",
        "COBAdeFFXXX"
      })
  void istBic_givenAWrongLengthCountryOrCharacter_thenFalse(final String wort) {
    // When / Then — QQ ist kein Kuerzel der Laenderliste.
    assertThat(Bankverbindungszerlegung.istBic(wort)).isFalse();
  }

  @Test
  void istBic_givenCommerzbank_thenTheFormAloneDoesNotRuleItOut() {
    // When / Then — darum braucht die Zerlegung zusaetzlich das Land der IBAN.
    assertThat(Bankverbindungszerlegung.istBic("COMMERZBANK")).isTrue();
  }
}
