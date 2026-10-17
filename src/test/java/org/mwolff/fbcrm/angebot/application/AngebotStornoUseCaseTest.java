package org.mwolff.fbcrm.angebot.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.angebot.domain.Angebotsstatus;

/**
 * Storno und Ruecknahme am Angebot (Issue #334, Plan #332 E5, E16, E17).
 *
 * <p>Beide Richtungen lesen das Angebot, rufen die Domaene und speichern. Ein zweites Storno ist
 * kein Uebergang (E17): Es weist mit {@link StornoNichtMoeglich} ab, damit der vorhandene Grund
 * nicht stillschweigend ueberschrieben wird; die Ruecknahme am nicht stornierten Angebot weist mit
 * {@link KeinStornoVorhanden} ab. Eine Abweisung schreibt nichts — den Nachweis fuehrt {@code
 * verifyNoMoreInteractions} nach dem einen Lesezugriff.
 */
@ExtendWith(MockitoExtension.class)
class AngebotStornoUseCaseTest {

  private static final long ANGEBOT = 11L;
  private static final Instant JETZT = Instant.parse("2026-10-17T09:30:00Z");
  private static final String GRUND = "vom Kunden abgelehnt";

  @Mock private AngebotRepository angebote;

  private AngebotStornoUseCase useCase;

  @BeforeEach
  void baueDenAnwendungsfall() {
    useCase = new AngebotStornoUseCase(angebote, Clock.fixed(JETZT, ZoneOffset.UTC));
  }

  private void angebotIst(final Angebot angebot) {
    when(angebote.findById(ANGEBOT)).thenReturn(Optional.of(angebot));
  }

  private static Angebot storniert(final Angebotsstatus status) {
    return Angebotsdoppel.angebot(ANGEBOT, status).storniert("Auftrag zurückgezogen", JETZT);
  }

  private void speichernGibtZurueck() {
    when(angebote.save(any())).thenAnswer(aufruf -> aufruf.getArgument(0));
  }

  @ParameterizedTest
  @EnumSource(
      value = Angebotsstatus.class,
      names = {"ANGELEGT", "ABGEGEBEN", "BESTELLT"})
  void storniere_thenSavesFlagAndGrundAndKeepsTheStatus(final Angebotsstatus status) {
    // Given — #330, Kriterium 1 und 5; der Status bleibt fuer die Ruecknahme stehen (E1).
    angebotIst(Angebotsdoppel.angebot(ANGEBOT, status));
    speichernGibtZurueck();

    // When
    final Angebot storniert = useCase.storniere(ANGEBOT, GRUND);

    // Then
    assertThat(storniert.storniert()).isTrue();
    assertThat(storniert.stornoGrund()).isEqualTo(GRUND);
    assertThat(storniert.status()).isEqualTo(status);
    assertThat(storniert.updatedAt()).isEqualTo(JETZT);
    verify(angebote).save(storniert);
  }

  @Test
  void storniere_withoutGrund_thenSavesTheStornoWithoutGrund() {
    // Given — der Grund ist freiwillig (E4).
    angebotIst(Angebotsdoppel.angebot(ANGEBOT, Angebotsstatus.ABGEGEBEN));
    speichernGibtZurueck();

    // When
    final Angebot storniert = useCase.storniere(ANGEBOT, null);

    // Then
    assertThat(storniert.storniert()).isTrue();
    assertThat(storniert.stornoGrund()).isNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   ", " \n\t "})
  void storniere_withAGrundOfOnlyWhitespace_thenSavesTheStornoWithoutGrund(final String grund) {
    // Given — ein leer gelassenes Feld ist kein Grund; die Domaene wiese ihn als leer ab.
    angebotIst(Angebotsdoppel.angebot(ANGEBOT, Angebotsstatus.ABGEGEBEN));
    speichernGibtZurueck();

    // When
    final Angebot storniert = useCase.storniere(ANGEBOT, grund);

    // Then
    assertThat(storniert.storniert()).isTrue();
    assertThat(storniert.stornoGrund()).isNull();
  }

  @Test
  void storniere_givenAnAlreadyStornoedAngebot_thenRejectsAndWritesNothing() {
    // Given — E17: der vorhandene Grund bleibt, wie er ist.
    angebotIst(storniert(Angebotsstatus.BESTELLT));

    // When / Then
    assertThatThrownBy(() -> useCase.storniere(ANGEBOT, GRUND))
        .isInstanceOf(StornoNichtMoeglich.class);
    verify(angebote).findById(ANGEBOT);
    verifyNoMoreInteractions(angebote);
  }

  @ParameterizedTest
  @EnumSource(
      value = Angebotsstatus.class,
      names = {"ERLEDIGT", "ABGERECHNET", "LAEUFT", "ABGESCHLOSSEN"})
  void storniere_givenAStatusWithoutStorno_thenRejectsAndWritesNothing(
      final Angebotsstatus status) {
    // Given — #330, Kriterium 1; E3: kein Storno aus „erledigt" und keines an interner Arbeit.
    angebotIst(Angebotsdoppel.angebot(ANGEBOT, status));

    // When / Then
    assertThatThrownBy(() -> useCase.storniere(ANGEBOT, GRUND))
        .isInstanceOf(StornoNichtMoeglich.class);
    verify(angebote).findById(ANGEBOT);
    verifyNoMoreInteractions(angebote);
  }

  @Test
  void storniere_whenTheAngebotIsUnknown_thenRejects() {
    // Given
    when(angebote.findById(ANGEBOT)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> useCase.storniere(ANGEBOT, GRUND))
        .isInstanceOf(AngebotNichtGefunden.class);
    verify(angebote).findById(ANGEBOT);
    verifyNoMoreInteractions(angebote);
  }

  @Test
  void nimmStornoZurueck_thenSavesWithoutFlagAndGrundInThePreviousStatus() {
    // Given — #330, Kriterium 9: zurueck in genau den Zustand von vorher.
    angebotIst(storniert(Angebotsstatus.BESTELLT));
    speichernGibtZurueck();

    // When
    final Angebot zurueck = useCase.nimmStornoZurueck(ANGEBOT);

    // Then
    assertThat(zurueck.storniert()).isFalse();
    assertThat(zurueck.stornoGrund()).isNull();
    assertThat(zurueck.status()).isEqualTo(Angebotsstatus.BESTELLT);
    assertThat(zurueck.updatedAt()).isEqualTo(JETZT);
    verify(angebote).save(zurueck);
  }

  @Test
  void nimmStornoZurueck_givenAnAngebotWithoutStorno_thenRejectsAndWritesNothing() {
    // Given — E17.
    angebotIst(Angebotsdoppel.angebot(ANGEBOT, Angebotsstatus.BESTELLT));

    // When / Then
    assertThatThrownBy(() -> useCase.nimmStornoZurueck(ANGEBOT))
        .isInstanceOf(KeinStornoVorhanden.class);
    verify(angebote).findById(ANGEBOT);
    verifyNoMoreInteractions(angebote);
  }

  @Test
  void nimmStornoZurueck_whenTheAngebotIsUnknown_thenRejects() {
    // Given
    when(angebote.findById(ANGEBOT)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> useCase.nimmStornoZurueck(ANGEBOT))
        .isInstanceOf(AngebotNichtGefunden.class);
    verify(angebote).findById(ANGEBOT);
    verifyNoMoreInteractions(angebote);
  }
}
