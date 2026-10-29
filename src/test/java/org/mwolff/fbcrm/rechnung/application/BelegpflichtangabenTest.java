package org.mwolff.fbcrm.rechnung.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mwolff.fbcrm.eigeneangaben.domain.EigeneAngaben;

/** Was die eigenen Angaben fuer eine Rechnung tragen muessen (Plan #461, E17). */
class BelegpflichtangabenTest {

  private static EigeneAngaben mitBank(
      final @Nullable String iban,
      final @Nullable String kontoinhaber,
      final @Nullable String bisherigeBankverbindung) {
    final EigeneAngaben voll = Rechnungsdoppel.eigeneAngaben();
    return new EigeneAngaben(
        voll.name(),
        voll.berufsbezeichnung(),
        voll.anschrift(),
        voll.email(),
        voll.telefon(),
        voll.webadresse(),
        voll.steuernummer(),
        voll.umsatzsteuerId(),
        iban,
        voll.bic(),
        kontoinhaber,
        bisherigeBankverbindung);
  }

  private static EigeneAngaben mitSteuer(
      final @Nullable String steuernummer, final @Nullable String umsatzsteuerId) {
    final EigeneAngaben voll = Rechnungsdoppel.eigeneAngaben();
    return new EigeneAngaben(
        voll.name(),
        voll.berufsbezeichnung(),
        voll.anschrift(),
        voll.email(),
        voll.telefon(),
        voll.webadresse(),
        steuernummer,
        umsatzsteuerId,
        voll.iban(),
        voll.bic(),
        voll.kontoinhaber(),
        voll.bisherigeBankverbindung());
  }

  @Test
  void pruefe_givenVollstaendigeAngaben_thenNothingIsThrown() {
    assertThatCode(
            () ->
                Belegpflichtangaben.pruefe(
                    Rechnungsdoppel.eigeneAngaben(), Rechnungsdoppel.firma()))
        .doesNotThrowAnyException();
  }

  @Test
  void pruefe_withoutIbanAndKontoinhaber_thenBothFieldsAreNamed() {
    // Given — der alte Freitext steht noch, zaehlt aber nicht mehr (E17).
    final EigeneAngaben eigene = mitBank(null, " ", "MLP Bank Heidelberg");

    // When
    final PflichtangabenFehlen fehler =
        catchThrowableOfType(
            PflichtangabenFehlen.class,
            () -> Belegpflichtangaben.pruefe(eigene, Rechnungsdoppel.firma()));

    // Then
    assertThat(fehler.felder()).containsOnlyKeys("iban", "kontoinhaber");
    assertThat(fehler.felder())
        .containsEntry("iban", List.of(Belegpflichtangaben.ANGABE_FEHLT))
        .doesNotContainKey("bankverbindung");
  }

  @Test
  void pruefe_withoutKontoinhaber_thenOnlyKontoinhaberIsNamed() {
    // Given
    final EigeneAngaben eigene = mitBank("DE02120300000000202051", null, null);

    // When
    final PflichtangabenFehlen fehler =
        catchThrowableOfType(
            PflichtangabenFehlen.class,
            () -> Belegpflichtangaben.pruefe(eigene, Rechnungsdoppel.firma()));

    // Then
    assertThat(fehler.felder()).containsOnlyKeys("kontoinhaber");
  }

  @ParameterizedTest
  @CsvSource(
      value = {"75/123/45678, NULL", "NULL, DE123456789"},
      nullValues = "NULL")
  void pruefe_withOneOfBothSteuerangaben_thenNothingIsThrown(
      final @Nullable String steuernummer, final @Nullable String umsatzsteuerId) {
    assertThatCode(
            () ->
                Belegpflichtangaben.pruefe(
                    mitSteuer(steuernummer, umsatzsteuerId), Rechnungsdoppel.firma()))
        .doesNotThrowAnyException();
  }

  @Test
  void pruefe_withoutBothSteuerangaben_thenSteuernummerIsNamed() {
    // When
    final PflichtangabenFehlen fehler =
        catchThrowableOfType(
            PflichtangabenFehlen.class,
            () -> Belegpflichtangaben.pruefe(mitSteuer(null, null), Rechnungsdoppel.firma()));

    // Then
    assertThat(fehler.felder())
        .containsOnlyKeys("steuernummer")
        .containsEntry("steuernummer", List.of(Belegpflichtangaben.STEUERANGABE_FEHLT));
  }
}
