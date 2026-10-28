package org.mwolff.fbcrm.eigeneangaben.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.common.Anschrift;
import org.mwolff.fbcrm.eigeneangaben.domain.EigeneAngaben;
import org.mwolff.fbcrm.eigeneangaben.domain.EigeneAngabenRepository;
import org.mwolff.fbcrm.eigeneangaben.domain.EigeneAngabenStand;

/**
 * Das Pflegen der Selbstauskunft (Kriterium 1).
 *
 * <p>Hier liegt die Normalisierung: Leerraum am Rand faellt weg, und was danach leer ist, wird
 * {@code null} statt Leerstring — dieselbe Regel wie E9 des Firma-Moduls. Der Zeitpunkt der
 * Aenderung kommt aus der injizierten Uhr, nie aus {@code Instant.now()}.
 *
 * <p>Davor steht der Vergleich des vorgelegten Stands (#411, Kriterium 4; Plan #415, E1, E14): Auf
 * einem alten Stand wird abgewiesen, und nichts wird geschrieben.
 */
@ExtendWith(MockitoExtension.class)
class EigeneAngabenPflegenUseCaseTest {

  private static final Instant JETZT = Instant.parse("2026-09-27T09:30:00Z");

  /** Der Stand, den die Maske vorlegt und den die Zeile traegt. */
  private static final long STAND = 3L;

  /** Das handelnde Konto. */
  private static final long KONTO = 31L;

  /** Das Konto, das zuletzt geaendert hat. */
  private static final long ANNA = 30L;

  private static final Instant ANNAS_AENDERUNG = Instant.parse("2026-09-26T08:42:00Z");

  @Mock private EigeneAngabenRepository bestand;
  @Captor private ArgumentCaptor<EigeneAngaben> gespeicherte;

  private EigeneAngabenPflegenUseCase useCase;

  @BeforeEach
  void baueDenAnwendungsfall() {
    useCase = new EigeneAngabenPflegenUseCase(bestand, Clock.fixed(JETZT, ZoneOffset.UTC));
    // Nachsichtig: Die abweisenden Faelle kommen nie zum Schreiben.
    lenient()
        .when(bestand.stand())
        .thenReturn(new EigeneAngabenStand(STAND, ANNA, ANNAS_AENDERUNG));
    lenient()
        .when(bestand.speichere(any(), any(), anyLong(), anyLong()))
        .thenReturn(new EigeneAngabenStand(STAND + 1, KONTO, JETZT));
    // Ohne bisherige Bankverbindung; die Faelle zum Freitext setzen sie mit bisher().
    lenient().when(bestand.lies()).thenReturn(angaben(null));
  }

  /* IBAN und BIC bleiben leer: Sie haben eine Form, die ein beliebiger Wert nicht erfuellt. */
  private static EigeneAngaben angaben(final @Nullable String wert) {
    return new EigeneAngaben(
        wert,
        wert,
        new Anschrift(wert, wert, wert, wert),
        wert,
        wert,
        wert,
        wert,
        wert,
        null,
        null,
        wert,
        null);
  }

  /** Dieselben Angaben mit den drei Bankfeldern, wie die Maske sie schickt. */
  private static EigeneAngaben mitBank(
      final @Nullable String iban,
      final @Nullable String bic,
      final @Nullable String kontoinhaber) {
    return new EigeneAngaben(
        "Manfred Wolff",
        null,
        new Anschrift(null, null, null, null),
        null,
        null,
        null,
        null,
        null,
        iban,
        bic,
        kontoinhaber,
        null);
  }

  /**
   * Der Bestand traegt die bisherige Bankverbindung als Freitext. Nachsichtig: Stehen IBAN und
   * Kontoinhaber, braucht der Anwendungsfall den alten Text gar nicht erst.
   */
  private void bisher(final @Nullable String freitext) {
    lenient()
        .when(bestand.lies())
        .thenReturn(
            new EigeneAngaben(
                "Manfred Wolff",
                null,
                new Anschrift(null, null, null, null),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                freitext));
  }

  private EigeneAngaben pflegeUndFange(final EigeneAngaben eingereicht) {
    useCase.pflege(eingereicht, STAND, KONTO);
    verify(bestand).speichere(gespeicherte.capture(), eq(JETZT), eq(STAND), eq(KONTO));
    return gespeicherte.getValue();
  }

  @Test
  void pflege_thenStoresEveryValue() {
    // Given
    final EigeneAngaben eingereicht =
        new EigeneAngaben(
            "Manfred Wolff",
            "Freiberuflicher Softwareentwickler",
            new Anschrift("Am Wall 1", "28195", "Bremen", "DE"),
            "manne@example.org",
            "0421 1234",
            "https://mwolff.org",
            "75/123/45678",
            "DE123456789",
            "DE89370400440532013000",
            "COBADEFFXXX",
            "Manfred Wolff",
            null);

    // When
    final EigeneAngaben gespeichert = pflegeUndFange(eingereicht);

    // Then
    assertThat(gespeichert).isEqualTo(eingereicht);
  }

  @Test
  void pflege_givenAWrongCheckDigit_thenRefusesAtTheFieldAndWritesNothing() {
    // When — Kriterium 7.
    final BankverbindungUngueltig fehler =
        catchThrowableOfType(
            BankverbindungUngueltig.class,
            () ->
                useCase.pflege(
                    mitBank("DE89 3704 0044 0532 0130 01", null, "Manfred Wolff"), STAND, KONTO));

    // Then
    assertThat(fehler).isNotNull();
    assertThat(fehler.felder())
        .containsExactly(entry("iban", List.of(BankverbindungUngueltig.IBAN_UNGUELTIG)));
    verify(bestand, never()).speichere(any(), any(), anyLong(), anyLong());
  }

  @Test
  void pflege_givenAnIbanWithSpacesInLowercase_thenStoresTheElectronicForm() {
    // When — E15: gespeichert wird ohne Leerzeichen in Grossbuchstaben.
    final EigeneAngaben gespeichert =
        pflegeUndFange(mitBank("  de89 3704 0044 0532 0130 00 ", null, "Manfred Wolff"));

    // Then
    assertThat(gespeichert.iban()).isEqualTo("DE89370400440532013000");
  }

  @Test
  void pflege_givenABicInLowercase_thenStoresItInCapitals() {
    // When
    final EigeneAngaben gespeichert =
        pflegeUndFange(mitBank("DE89370400440532013000", " cobadeffxxx ", "Manfred Wolff"));

    // Then
    assertThat(gespeichert.bic()).isEqualTo("COBADEFFXXX");
  }

  @Test
  void pflege_givenABicOfTheWrongForm_thenRefusesAtTheField() {
    // When — E15: die BIC wird nur auf ihre Form geprueft.
    final BankverbindungUngueltig fehler =
        catchThrowableOfType(
            BankverbindungUngueltig.class,
            () ->
                useCase.pflege(
                    mitBank("DE89370400440532013000", "COBADEFFXX", "Manfred Wolff"),
                    STAND,
                    KONTO));

    // Then
    assertThat(fehler).isNotNull();
    assertThat(fehler.felder())
        .containsExactly(entry("bic", List.of(BankverbindungUngueltig.BIC_UNGUELTIG)));
    verify(bestand, never()).speichere(any(), any(), anyLong(), anyLong());
  }

  @Test
  void pflege_givenAnInvalidIbanAndBic_thenNamesBothFields() {
    // When — wer beides falsch eingibt, soll beides auf einmal sehen.
    final BankverbindungUngueltig fehler =
        catchThrowableOfType(
            BankverbindungUngueltig.class,
            () -> useCase.pflege(mitBank("DE00", "BIC", null), STAND, KONTO));

    // Then
    assertThat(fehler).isNotNull();
    assertThat(fehler.felder()).containsOnlyKeys("iban", "bic");
    assertThat(fehler.felder().keySet()).containsExactly("iban", "bic");
  }

  @Test
  void pflege_withoutAnIban_thenTheBisherigeBankverbindungStays() {
    // Given — E13: das erste Speichern ohne IBAN loescht den alten Text nicht.
    bisher("Sparkasse Bremen, Konto 1234567");

    // When
    final EigeneAngaben gespeichert = pflegeUndFange(mitBank(null, null, "Manfred Wolff"));

    // Then
    assertThat(gespeichert.bisherigeBankverbindung()).isEqualTo("Sparkasse Bremen, Konto 1234567");
  }

  @Test
  void pflege_withAnIbanButWithoutKontoinhaber_thenTheBisherigeBankverbindungStays() {
    // Given
    bisher("Sparkasse Bremen, Konto 1234567");

    // When
    final EigeneAngaben gespeichert =
        pflegeUndFange(mitBank("DE89370400440532013000", null, "   "));

    // Then
    assertThat(gespeichert.kontoinhaber()).isNull();
    assertThat(gespeichert.bisherigeBankverbindung()).isEqualTo("Sparkasse Bremen, Konto 1234567");
  }

  @Test
  void pflege_withIbanAndKontoinhaber_thenTheBisherigeBankverbindungIsCleared() {
    // Given — E13: stehen beide, hat der Freitext ausgedient.
    bisher("Sparkasse Bremen, Konto 1234567");

    // When
    final EigeneAngaben gespeichert =
        pflegeUndFange(mitBank("DE89370400440532013000", null, "Manfred Wolff"));

    // Then
    assertThat(gespeichert.bisherigeBankverbindung()).isNull();
  }

  @Test
  void pflege_givenABisherigeBankverbindungInTheRequest_thenOnlyTheStoredOneCounts() {
    // Given — der Freitext ist nur noch zum Lesen; was hereinkommt, schreibt ihn nicht.
    bisher(null);
    final EigeneAngaben eingereicht =
        new EigeneAngaben(
            null,
            null,
            new Anschrift(null, null, null, null),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            "untergeschoben");

    // When
    final EigeneAngaben gespeichert = pflegeUndFange(eingereicht);

    // Then
    assertThat(gespeichert.bisherigeBankverbindung()).isNull();
  }

  @Test
  void pflege_givenPaddedValues_thenStoresThemTrimmed() {
    // When — E9.
    final EigeneAngaben gespeichert = pflegeUndFange(angaben("  Wert  "));

    // Then
    assertThat(gespeichert).isEqualTo(angaben("Wert"));
  }

  @Test
  void pflege_givenEmptyStrings_thenStoresThemAsAbsent() {
    // When — E9: „nicht angegeben" ist NULL und nicht der Leerstring.
    final EigeneAngaben gespeichert = pflegeUndFange(angaben(""));

    // Then
    assertThat(gespeichert).isEqualTo(angaben(null));
  }

  @Test
  void pflege_givenValuesOfWhitespaceOnly_thenStoresThemAsAbsent() {
    // When — E9.
    final EigeneAngaben gespeichert = pflegeUndFange(angaben("   "));

    // Then
    assertThat(gespeichert).isEqualTo(angaben(null));
  }

  @Test
  void pflege_givenAbsentValues_thenKeepsThemAbsent() {
    // When
    final EigeneAngaben gespeichert = pflegeUndFange(angaben(null));

    // Then
    assertThat(gespeichert).isEqualTo(angaben(null));
  }

  @Test
  void pflege_thenTakesTheChangeTimeFromTheClock() {
    // When
    useCase.pflege(angaben("Wert"), STAND, KONTO);

    // Then — nie Instant.now(): die Uhr kommt von aussen.
    verify(bestand).speichere(any(EigeneAngaben.class), eq(JETZT), anyLong(), anyLong());
  }

  @Test
  void pflege_thenAnswersWithTheNormalisedValuesAndTheRaisedVersion() {
    // When
    final GespeicherteEigeneAngaben neu = useCase.pflege(angaben("  Wert  "), STAND, KONTO);

    // Then — die Maske legt die erhoehte Version beim naechsten Speichern vor (Plan #415, E2).
    assertThat(neu).isEqualTo(new GespeicherteEigeneAngaben(angaben("Wert"), STAND + 1));
  }

  @Test
  void pflege_givenAnOutdatedStand_thenRefusesAndWritesNothing() {
    // When — #411, Kriterium 4: Anna hat die Angaben inzwischen gespeichert.
    final EigeneAngabenGeaendert konflikt =
        catchThrowableOfType(
            EigeneAngabenGeaendert.class, () -> useCase.pflege(angaben("Wert"), STAND - 1, KONTO));

    // Then — Urheber, Handelnder und Zeitpunkt fuer die Meldung.
    assertThat(konflikt).isNotNull();
    assertThat(konflikt.urheberKonto()).hasValue(ANNA);
    assertThat(konflikt.handelndesKonto()).isEqualTo(KONTO);
    assertThat(konflikt.zeitpunkt()).hasValue(ANNAS_AENDERUNG);
    verify(bestand, never()).speichere(any(), any(), anyLong(), anyLong());
  }

  @Test
  void pflege_givenAStandThatNeverExisted_thenRefusesAsWell() {
    // When / Then — abgewiesen wird jede Abweichung, nicht nur ein aelterer Stand.
    assertThatThrownBy(() -> useCase.pflege(angaben("Wert"), STAND + 1, KONTO))
        .isInstanceOf(EigeneAngabenGeaendert.class);
    verify(bestand, never()).speichere(any(), any(), anyLong(), anyLong());
  }
}
