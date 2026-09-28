package org.mwolff.fbcrm.auftrag.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.auftrag.domain.Auftragsstatus;

/**
 * Die Bedingungen der Pflegemaske an der Schnittstelle (Kriterium 7, Plan E21).
 *
 * <p>Der Rumpf traegt genau die vier aenderbaren Angaben. <b>Kein Positionsfeld</b> und keines fuer
 * „Stunden je Personentag": Was sich nach der Anlage nicht mehr aendern darf, kommt gar nicht erst
 * vor (F3, R4) — das ist die staerkere Zusage als eine Pruefung, die man umgehen koennte.
 *
 * <p>Die Regel des Leistungszeitraums ist dieselbe wie beim Anlegen und steht darum in derselben
 * Constraint; gemeldet wird am fehlenden Tag, weil der Anwender dort etwas tun muss.
 */
class AuftragPflegeRequestTest {

  private static final LocalDate BEGINN = LocalDate.of(2026, 10, 1);
  private static final LocalDate ENDE = LocalDate.of(2026, 12, 31);
  private static final LocalDate TAG = LocalDate.of(2026, 9, 28);

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

  private static Set<String> felder(final AuftragPflegeRequest anfrage) {
    return validator.validate(anfrage).stream()
        .map(ConstraintViolation::getPropertyPath)
        .map(Object::toString)
        .collect(Collectors.toUnmodifiableSet());
  }

  private static AuftragPflegeRequest zeitraum(final LocalDate ab, final LocalDate bis) {
    return new AuftragPflegeRequest(TAG, null, ab, bis, Auftragsstatus.OFFEN);
  }

  @Test
  void anfrage_givenEveryField_thenAccepted() {
    // When / Then — Kriterium 7: genau diese vier Angaben.
    assertThat(
            validator.validate(
                new AuftragPflegeRequest(
                    TAG, "BST-4711", BEGINN, ENDE, Auftragsstatus.ABGESCHLOSSEN)))
        .isEmpty();
  }

  @Test
  void status_givenNothing_thenNamesTheField() {
    // When / Then — der Status ist Pflicht; ohne ihn waere die Pflege eine halbe Angabe.
    assertThat(felder(new AuftragPflegeRequest(TAG, null, null, null, null)))
        .containsExactly("status");
  }

  @Test
  void auftragDatum_givenNothing_thenNamesTheField() {
    // When / Then — anders als beim Anlegen gibt es hier keinen vorbelegten Tag.
    assertThat(felder(new AuftragPflegeRequest(null, null, null, null, Auftragsstatus.OFFEN)))
        .containsExactly("auftragDatum");
  }

  @Test
  void kundenbestellnummer_givenMoreThanHundredCharacters_thenNamesTheField() {
    // When / Then — dieselbe Grenze wie varchar(100) im Schema.
    assertThat(
            felder(
                new AuftragPflegeRequest(TAG, "B".repeat(101), null, null, Auftragsstatus.OFFEN)))
        .containsExactly("kundenbestellnummer");
  }

  @Test
  void leistungszeitraum_givenNeitherDay_thenAccepted() {
    // When / Then — E21: ganz oder gar nicht.
    assertThat(validator.validate(zeitraum(null, null))).isEmpty();
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
        new AuftragPflegeRequest(TAG, "BST-4711", BEGINN, ENDE, Auftragsstatus.IN_ARBEIT).daten();

    // Then — vier Angaben und kein Positionsfeld (F3).
    assertThat(daten.auftragDatum()).isEqualTo(TAG);
    assertThat(daten.kundenbestellnummer()).isEqualTo("BST-4711");
    assertThat(daten.leistungAb()).isEqualTo(BEGINN);
    assertThat(daten.leistungBis()).isEqualTo(ENDE);
    assertThat(daten.status()).isEqualTo(Auftragsstatus.IN_ARBEIT);
  }
}
