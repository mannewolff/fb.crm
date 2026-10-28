package org.mwolff.fbcrm.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.text.Collator;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Die Liste der Laender und die Zuordnung eines Freitexts zu ihrem Kuerzel (Plan #461, E11, E12;
 * fachliche Quelle #460, Kriterium 10).
 *
 * <p>Die Zuordnung ist dieselbe, mit der die Migration den Bestand umstellt. Sie bekommt dort die
 * eingefrorenen Namen und hier eine kleine eigene Liste, damit jeder Zweig der Regel an einem Wert
 * steht, den man sieht.
 */
class LaenderlisteTest {

  private static final List<Laenderliste.Land> NAMEN =
      List.of(
          new Laenderliste.Land("AT", "Österreich", "Austria"),
          new Laenderliste.Land("DE", "Deutschland", "Germany"),
          new Laenderliste.Land("FR", "Frankreich", "France"));

  @ParameterizedTest(name = "„{0}\" wird {1}")
  @CsvSource(
      delimiter = '|',
      ignoreLeadingAndTrailingWhitespace = false,
      value = {
        "Österreich|AT",
        "Austria|AT",
        "AT|AT",
        "österreich|AT",
        "AUSTRIA|AT",
        "at|AT",
        "  Frankreich\t|FR",
        " FR |FR",
        "france|FR",
        "Germany|DE"
      })
  void kuerzel_givenANameOrCodeOfACountry_thenThatCountry(
      final String freitext, final String kuerzel) {
    // When / Then
    assertThat(Laenderliste.kuerzel(freitext, NAMEN)).isEqualTo(kuerzel);
  }

  @ParameterizedTest(name = "„{0}\" wird DE")
  @ValueSource(strings = {"Ostereich", "Frankreich und Belgien", "A", "AUT", "XX", "", "   "})
  @NullSource
  void kuerzel_givenNoExactNameOrCode_thenGermany(final String freitext) {
    // When / Then — Kriterium 10: jedes andere Land, auch ein leeres, wird Deutschland.
    assertThat(Laenderliste.kuerzel(freitext, NAMEN)).isEqualTo("DE");
  }

  @Test
  void kuerzel_givenTheListOfTheRuntime_thenFindsGermanAndEnglishNames() {
    // When / Then — die Namen des JDK, wie sie die eingefrorene Liste einmal uebernommen hat.
    assertThat(Laenderliste.kuerzel("austria", Laenderliste.alle())).isEqualTo("AT");
    assertThat(Laenderliste.kuerzel("Vereinigte Staaten", Laenderliste.alle())).isEqualTo("US");
  }

  @Test
  void alle_thenEveryIsoCountryOnceSortedByTheGermanName() {
    // When
    final List<Laenderliste.Land> alle = Laenderliste.alle();

    // Then
    assertThat(alle)
        .extracting(Laenderliste.Land::kuerzel)
        .containsExactlyInAnyOrder(Locale.getISOCountries());
    assertThat(alle)
        .extracting(Laenderliste.Land::name)
        .isSortedAccordingTo(Collator.getInstance(Locale.GERMAN));
    assertThat(alle)
        .contains(
            new Laenderliste.Land("DE", "Deutschland", "Germany"),
            new Laenderliste.Land("AT", "Österreich", "Austria"));
  }

  @Test
  void alle_thenSortsUmlautsLikeTheirBaseLetter() {
    // Given
    final List<String> namen = Laenderliste.alle().stream().map(Laenderliste.Land::name).toList();

    // Then — nach dem Alphabet und nicht nach Unicode: Österreich steht vor Pakistan, nicht am
    // Ende.
    assertThat(namen.indexOf("Österreich")).isLessThan(namen.indexOf("Pakistan"));
    assertThat(namen.indexOf("Oman")).isLessThan(namen.indexOf("Österreich"));
  }

  @Test
  void name_givenAKnownCode_thenTheGermanName() {
    // When / Then
    assertThat(Laenderliste.name("AT")).isEqualTo("Österreich");
    assertThat(Laenderliste.name("DE")).isEqualTo("Deutschland");
  }

  @Test
  void name_givenAnUnknownCodeOrNone_thenNothing() {
    // When / Then
    assertThat(Laenderliste.name("XX")).isNull();
    assertThat(Laenderliste.name("de")).isNull();
    assertThat(Laenderliste.name(null)).isNull();
  }

  @Test
  void bekannt_givenACodeOfTheList_thenTrue() {
    // When / Then
    assertThat(Laenderliste.bekannt("DE")).isTrue();
    assertThat(Laenderliste.bekannt("AT")).isTrue();
  }

  @ParameterizedTest(name = "„{0}\" ist kein bekanntes Kuerzel")
  @ValueSource(strings = {"XX", "de", "DEU", "Deutschland", ""})
  @NullSource
  void bekannt_givenAnythingElse_thenFalse(final String kuerzel) {
    // When / Then — nur das Kuerzel in Grossbuchstaben, wie es die Spalte fuehrt.
    assertThat(Laenderliste.bekannt(kuerzel)).isFalse();
  }
}
