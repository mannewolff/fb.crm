package org.mwolff.fbcrm.auftrag.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Die Bedingungen der Auftragsmaske an der Schnittstelle (Plan E20, E21).
 *
 * <p>Geprueft wird durch die echte Bean-Validation-Kette und nicht am Validator allein: Die Aussage
 * ist, dass jede Meldung <b>am Feld</b> haengt — nur so entsteht im {@code GlobalExceptionHandler}
 * ein Eintrag unter {@code fieldErrors.<feld>}, den die Maske an das richtige Eingabefeld schreibt.
 * Bei den Positionen ist der Pfad darum {@code positionen[0].platz} und nicht {@code positionen}.
 *
 * <p>Was hier <b>nicht</b> geprueft wird: ob es den Platz im Angebot gibt und ob die Menge die
 * vereinbarte uebersteigt. Beides braucht das Angebot, und das kennt nur die Anwendungsschicht.
 */
class AuftragAnlegenRequestTest {

  private static final LocalDate BEGINN = LocalDate.of(2026, 10, 1);
  private static final LocalDate ENDE = LocalDate.of(2026, 12, 31);

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

  private static AuftragPositionRequest position(final int platz, final String menge) {
    return new AuftragPositionRequest(platz, new BigDecimal(menge), new BigDecimal("7.50"));
  }

  private static AuftragAnlegenRequest anfrage(final List<AuftragPositionRequest> positionen) {
    return new AuftragAnlegenRequest(null, "BST-4711", null, null, positionen);
  }

  private static AuftragAnlegenRequest zeitraum(final LocalDate ab, final LocalDate bis) {
    return new AuftragAnlegenRequest(null, null, ab, bis, List.of(position(1, "1.00")));
  }

  private static Set<String> felder(final AuftragAnlegenRequest anfrage) {
    return validator.validate(anfrage).stream()
        .map(ConstraintViolation::getPropertyPath)
        .map(Object::toString)
        .collect(java.util.stream.Collectors.toUnmodifiableSet());
  }

  @Test
  void platz_givenOne_thenAccepted() {
    // When / Then — der Platz ist index + 1 der Angebotsliste und beginnt bei 1.
    assertThat(validator.validate(anfrage(List.of(position(1, "1.00"))))).isEmpty();
  }

  @ParameterizedTest(name = "Platz {0} wird abgewiesen")
  @ValueSource(ints = {0, -1})
  void platz_givenLessThanOne_thenNamesTheField(final int platz) {
    // When / Then
    assertThat(felder(anfrage(List.of(position(platz, "1.00")))))
        .containsExactly("positionen[0].platz");
  }

  @Test
  void menge_givenThreeDecimals_thenNamesTheField() {
    // When / Then — dieselbe Grenze wie numeric(12,2) im Schema.
    assertThat(felder(anfrage(List.of(position(1, "1.005")))))
        .containsExactly("positionen[0].menge");
  }

  @Test
  void menge_givenANegativeValue_thenNamesTheField() {
    // When / Then — eine negative Menge ist keine unvollstaendige Angabe, sondern eine falsche.
    assertThat(felder(anfrage(List.of(position(1, "-1.00")))))
        .containsExactly("positionen[0].menge");
  }

  @Test
  void stundenJePersonentag_givenZero_thenNamesTheField() {
    // When / Then — E10: ein Personentag ohne Stunden waere keine Umrechnung.
    assertThat(
            felder(
                anfrage(
                    List.of(
                        new AuftragPositionRequest(1, new BigDecimal("1.00"), BigDecimal.ZERO)))))
        .containsExactly("positionen[0].stundenJePersonentag");
  }

  @Test
  void stundenJePersonentag_givenNothing_thenAccepted() {
    // When / Then — die Festpreisposition traegt ihn nicht; ob das passt, weiss das Angebot.
    assertThat(
            validator.validate(
                anfrage(List.of(new AuftragPositionRequest(1, new BigDecimal("1.00"), null)))))
        .isEmpty();
  }

  @Test
  void positionen_givenAnEmptyList_thenNamesTheField() {
    // When / Then — ein Auftrag ohne Position ist kein Auftrag.
    assertThat(felder(anfrage(List.of()))).containsExactly("positionen");
  }

  @Test
  void kundenbestellnummer_givenMoreThanHundredCharacters_thenNamesTheField() {
    // When / Then — dieselbe Grenze wie varchar(100) im Schema.
    assertThat(
            felder(
                new AuftragAnlegenRequest(
                    null, "B".repeat(101), null, null, List.of(position(1, "1.00")))))
        .containsExactly("kundenbestellnummer");
  }

  @Test
  void leistungszeitraum_givenNeitherDay_thenAccepted() {
    // When / Then — E21: ganz oder gar nicht.
    assertThat(validator.validate(zeitraum(null, null))).isEmpty();
  }

  @Test
  void leistungszeitraum_givenTheSameDayTwice_thenAccepted() {
    // When / Then — E21: der gleiche Tag zaehlt noch mit.
    assertThat(validator.validate(zeitraum(BEGINN, BEGINN))).isEmpty();
  }

  @Test
  void leistungszeitraum_givenOnlyTheBeginning_thenNamesTheMissingEnd() {
    // When / Then — E21.
    assertThat(felder(zeitraum(BEGINN, null))).containsExactly("leistungBis");
  }

  @Test
  void leistungszeitraum_givenOnlyTheEnd_thenNamesTheMissingBeginning() {
    // When / Then — E21.
    assertThat(felder(zeitraum(null, ENDE))).containsExactly("leistungAb");
  }

  @Test
  void leistungszeitraum_givenAnEndBeforeTheBeginning_thenNamesTheEnd() {
    // When / Then — E21.
    assertThat(felder(zeitraum(ENDE, BEGINN))).containsExactly("leistungBis");
  }

  @Test
  void daten_thenCarriesEveryFieldIntoTheApplicationLayer() {
    // When
    final var daten =
        new AuftragAnlegenRequest(BEGINN, "BST-4711", BEGINN, ENDE, List.of(position(2, "1.00")))
            .daten();

    // Then — der Rumpf kennt kein Preisfeld; die Wahl traegt nur Platz, Menge und Stunden.
    assertThat(daten.auftragDatum()).isEqualTo(BEGINN);
    assertThat(daten.kundenbestellnummer()).isEqualTo("BST-4711");
    assertThat(daten.leistungAb()).isEqualTo(BEGINN);
    assertThat(daten.leistungBis()).isEqualTo(ENDE);
    assertThat(daten.positionen())
        .singleElement()
        .satisfies(
            wahl -> {
              assertThat(wahl.platz()).isEqualTo(2);
              assertThat(wahl.menge()).isEqualByComparingTo(new BigDecimal("1.00"));
              assertThat(wahl.stundenJePersonentag()).isEqualByComparingTo(new BigDecimal("7.50"));
            });
  }
}
