package org.mwolff.fbcrm.firma.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.firma.domain.Anschrift;
import org.mwolff.fbcrm.firma.domain.Ansprechpartner;
import org.mwolff.fbcrm.firma.domain.AnsprechpartnerRepository;
import org.mwolff.fbcrm.firma.domain.Firma;
import org.mwolff.fbcrm.firma.domain.FirmaRepository;

/**
 * Das Anlegen eines Ansprechpartners (Kriterium 9).
 *
 * <p>Die Firma kommt aus dem Pfad und aus nichts sonst (E7): Sie wird gegen den Bestand geprueft,
 * bevor irgendetwas entsteht, und landet unveraendert am neuen Ansprechpartner.
 */
@ExtendWith(MockitoExtension.class)
class AnsprechpartnerAnlegenUseCaseTest {

  private static final long FIRMA = 7L;
  private static final Instant ANGELEGT = Instant.parse("2026-09-01T08:00:00Z");
  private static final Instant JETZT = Instant.parse("2026-09-25T09:30:00Z");

  @Mock private FirmaRepository firmen;
  @Mock private AnsprechpartnerRepository ansprechpartner;
  @Captor private ArgumentCaptor<Ansprechpartner> gespeicherte;

  private AnsprechpartnerAnlegenUseCase useCase;

  @BeforeEach
  void baueDenAnwendungsfall() {
    useCase =
        new AnsprechpartnerAnlegenUseCase(
            firmen, ansprechpartner, Clock.fixed(JETZT, ZoneOffset.UTC));
  }

  private static Firma adlerAg(final boolean aktiv) {
    return new Firma(
        FIRMA,
        "Adler AG",
        new Anschrift(null, null, null, null),
        null,
        null,
        aktiv,
        ANGELEGT,
        ANGELEGT);
  }

  private static AnsprechpartnerDaten daten(final String nachname) {
    return new AnsprechpartnerDaten(
        "Max", nachname, "Einkauf", "max@firma.de", "0421 1234", "0170 1234");
  }

  private Ansprechpartner legeAnUndFange(final Firma firma, final AnsprechpartnerDaten daten) {
    when(firmen.findById(FIRMA)).thenReturn(Optional.of(firma));
    when(ansprechpartner.save(any(Ansprechpartner.class)))
        .thenAnswer(aufruf -> aufruf.getArgument(0));
    useCase.anlegen(FIRMA, daten);
    verify(ansprechpartner).save(gespeicherte.capture());
    return gespeicherte.getValue();
  }

  @Test
  void anlegen_thenStoresTheContactAsActive() {
    // When
    final Ansprechpartner gespeichert = legeAnUndFange(adlerAg(true), daten("Mueller"));

    // Then — Kriterium 9: ein neuer Ansprechpartner ist aktiv.
    assertThat(gespeichert.aktiv()).isTrue();
  }

  @Test
  void anlegen_thenTakesTheFirmaFromThePath() {
    // When — E7: die Zuordnung entsteht aus der Kennung im Pfad.
    final Ansprechpartner gespeichert = legeAnUndFange(adlerAg(true), daten("Mueller"));

    // Then
    assertThat(gespeichert.firmaId()).isEqualTo(FIRMA);
  }

  @Test
  void anlegen_thenTakesBothTimestampsFromTheClock() {
    // When
    final Ansprechpartner gespeichert = legeAnUndFange(adlerAg(true), daten("Mueller"));

    // Then
    assertThat(gespeichert)
        .extracting(Ansprechpartner::createdAt, Ansprechpartner::updatedAt)
        .containsOnly(JETZT);
  }

  @Test
  void anlegen_thenLeavesTheIdToTheDatabase() {
    // When
    final Ansprechpartner gespeichert = legeAnUndFange(adlerAg(true), daten("Mueller"));

    // Then
    assertThat(gespeichert.id()).isNull();
  }

  @Test
  void anlegen_thenStoresEveryValueOfTheRequest() {
    // When
    final Ansprechpartner gespeichert = legeAnUndFange(adlerAg(true), daten("Mueller"));

    // Then
    assertThat(gespeichert)
        .extracting(
            Ansprechpartner::vorname,
            Ansprechpartner::nachname,
            Ansprechpartner::rolle,
            Ansprechpartner::email,
            Ansprechpartner::telefonFestnetz,
            Ansprechpartner::telefonMobil)
        .containsExactly("Max", "Mueller", "Einkauf", "max@firma.de", "0421 1234", "0170 1234");
  }

  @Test
  void anlegen_thenReturnsTheContactWithTheIdFromTheRepository() {
    // Given
    final Ansprechpartner mitId =
        new Ansprechpartner(
            3L, FIRMA, "Max", "Mueller", null, null, null, null, true, JETZT, JETZT);
    when(firmen.findById(FIRMA)).thenReturn(Optional.of(adlerAg(true)));
    when(ansprechpartner.save(any(Ansprechpartner.class))).thenReturn(mitId);

    // When
    final Ansprechpartner angelegt = useCase.anlegen(FIRMA, daten("Mueller"));

    // Then
    assertThat(angelegt).isEqualTo(mitId);
  }

  @Test
  void anlegen_givenARetiredFirma_thenIsStillPossible() {
    // When — Kriterium 14: eine stillgelegte Firma bleibt pflegbar.
    final Ansprechpartner gespeichert = legeAnUndFange(adlerAg(false), daten("Mueller"));

    // Then
    assertThat(gespeichert.nachname()).isEqualTo("Mueller");
  }

  @Test
  void anlegen_givenPaddedValues_thenStoresThemTrimmed() {
    // When — E9.
    final Ansprechpartner gespeichert =
        legeAnUndFange(
            adlerAg(true),
            new AnsprechpartnerDaten(
                " Max ", " Mueller ", " Einkauf ", " max@firma.de ", " 0421 1234 ", " 0170 1234 "));

    // Then
    assertThat(gespeichert)
        .extracting(
            Ansprechpartner::vorname,
            Ansprechpartner::nachname,
            Ansprechpartner::rolle,
            Ansprechpartner::email,
            Ansprechpartner::telefonFestnetz,
            Ansprechpartner::telefonMobil)
        .containsExactly("Max", "Mueller", "Einkauf", "max@firma.de", "0421 1234", "0170 1234");
  }

  @Test
  void anlegen_givenOptionalFieldsOfWhitespaceOnly_thenStoresThemAsNull() {
    // When — E9: es gibt genau eine Schreibweise fuer „nicht angegeben".
    final Ansprechpartner gespeichert =
        legeAnUndFange(adlerAg(true), new AnsprechpartnerDaten("  ", "Mueller", "", "\t", " ", ""));

    // Then
    assertThat(gespeichert)
        .extracting(
            Ansprechpartner::vorname,
            Ansprechpartner::rolle,
            Ansprechpartner::email,
            Ansprechpartner::telefonFestnetz,
            Ansprechpartner::telefonMobil)
        .containsOnlyNulls();
  }

  @Test
  void anlegen_givenNoOptionalValuesAtAll_thenKeepsThemAbsent() {
    // When — nur der Nachname ist Pflicht.
    final Ansprechpartner gespeichert =
        legeAnUndFange(
            adlerAg(true), new AnsprechpartnerDaten(null, "Mueller", null, null, null, null));

    // Then
    assertThat(gespeichert)
        .extracting(
            Ansprechpartner::vorname,
            Ansprechpartner::rolle,
            Ansprechpartner::email,
            Ansprechpartner::telefonFestnetz,
            Ansprechpartner::telefonMobil)
        .containsOnlyNulls();
  }

  @Test
  void anlegen_givenALastNameOfWhitespaceOnly_thenIsRefused() {
    // When / Then — E9: was @NotBlank an der Schnittstelle abweist, weist auch die
    // Transaktionsgrenze ab.
    assertThatThrownBy(() -> useCase.anlegen(FIRMA, daten("   ")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void anlegen_givenALastNameOfWhitespaceOnly_thenTouchesNothing() {
    // When — die Normalisierung steht vor jedem Zugriff auf den Bestand.
    assertThatThrownBy(() -> useCase.anlegen(FIRMA, daten("   ")))
        .isInstanceOf(IllegalArgumentException.class);

    // Then
    verifyNoInteractions(firmen, ansprechpartner);
  }

  @Test
  void anlegen_givenAnUnknownFirma_thenThrowsFirmaNichtGefunden() {
    // Given
    when(firmen.findById(4711L)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> useCase.anlegen(4711L, daten("Mueller")))
        .isInstanceOf(FirmaNichtGefunden.class);
  }

  @Test
  void anlegen_givenAnUnknownFirma_thenStoresNothing() {
    // Given
    when(firmen.findById(4711L)).thenReturn(Optional.empty());

    // When
    assertThatThrownBy(() -> useCase.anlegen(4711L, daten("Mueller")))
        .isInstanceOf(FirmaNichtGefunden.class);

    // Then
    verify(ansprechpartner, never()).save(any(Ansprechpartner.class));
  }
}
