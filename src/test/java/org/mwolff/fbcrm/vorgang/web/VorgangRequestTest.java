package org.mwolff.fbcrm.vorgang.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Die Bedingungen der Vorgangsmaske an der Schnittstelle — vor allem die
 * Abschlusswahrscheinlichkeit (Kriterium 21).
 *
 * <p>Sie steht in Zehnerschritten von 0 bis 100 und ist optional. Geprueft wird hier durch die
 * echte Bean-Validation-Kette und nicht am Validator allein: Die Aussage ist, dass die Bedingung am
 * Feld haengt — nur so entsteht im {@code GlobalExceptionHandler} ein Eintrag unter {@code
 * fieldErrors.abschlusswahrscheinlichkeit}, den die Maske an das richtige Eingabefeld schreibt.
 */
class VorgangRequestTest {

  private static final LocalDate ENTSCHEIDUNG = LocalDate.of(2026, 10, 15);

  private static ValidatorFactory fabrik;
  private static Validator validator;

  @BeforeAll
  static void baueDiePruefkette() {
    fabrik = Validation.buildDefaultValidatorFactory();
    validator = fabrik.getValidator();
  }

  @AfterAll
  static void gibDiePruefketteFrei() {
    fabrik.close();
  }

  private static VorgangRequest anfrage(final Integer wahrscheinlichkeit) {
    return new VorgangRequest(
        "Website-Relaunch", Long.valueOf(7L), null, wahrscheinlichkeit, ENTSCHEIDUNG);
  }

  @ParameterizedTest(name = "{0} ist gueltig")
  @ValueSource(ints = {0, 10, 30, 90, 100})
  void abschlusswahrscheinlichkeit_givenATenStep_thenAccepted(final int wahrscheinlichkeit) {
    // When / Then — Kriterium 21: die elf Werte der Auswahl.
    assertThat(validator.validate(anfrage(Integer.valueOf(wahrscheinlichkeit)))).isEmpty();
  }

  @ParameterizedTest(name = "{0} wird abgewiesen")
  @ValueSource(ints = {35, -10, 110})
  void abschlusswahrscheinlichkeit_givenAnythingElse_thenNamesTheField(
      final int wahrscheinlichkeit) {
    // When
    final Set<ConstraintViolation<VorgangRequest>> verletzungen =
        validator.validate(anfrage(Integer.valueOf(wahrscheinlichkeit)));

    // Then — die Meldung steht am Feld, nicht am ganzen Rumpf.
    assertThat(verletzungen)
        .singleElement()
        .extracting(verletzung -> verletzung.getPropertyPath().toString())
        .isEqualTo("abschlusswahrscheinlichkeit");
  }

  @Test
  void abschlusswahrscheinlichkeit_givenNothing_thenAccepted() {
    // When / Then — Kriterium 21: beide Felder sind optional.
    assertThat(validator.validate(anfrage(null))).isEmpty();
  }

  @Test
  void entscheidungErwartetAm_givenNothing_thenAccepted() {
    // When / Then
    assertThat(
            validator.validate(
                new VorgangRequest(
                    "Website-Relaunch", Long.valueOf(7L), null, Integer.valueOf(30), null)))
        .isEmpty();
  }

  @Test
  void daten_thenCarriesBothPipelineFieldsIntoTheApplicationLayer() {
    // When
    final var daten = anfrage(Integer.valueOf(30)).daten();

    // Then
    assertThat(daten)
        .extracting(d -> d.abschlusswahrscheinlichkeit(), d -> d.entscheidungErwartetAm())
        .containsExactly(Integer.valueOf(30), ENTSCHEIDUNG);
  }
}
