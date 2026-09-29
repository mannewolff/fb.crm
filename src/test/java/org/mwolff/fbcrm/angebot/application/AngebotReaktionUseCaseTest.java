package org.mwolff.fbcrm.angebot.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.angebot.domain.Angebotsstand;
import org.mwolff.fbcrm.angebot.domain.Angebotszustand;

/**
 * Die Reaktion des Kunden auf ein Angebot (Kriterien 17, 18).
 *
 * <p><b>Aus welchen Zustaenden reagiert werden darf.</b> Aus {@code VERSENDET} und aus {@code
 * ABGELOEST}, jeweils unabhaengig vom Datum — der Fall aus F13: Nach einer Nachverhandlung sagt der
 * Kunde „wir nehmen das erste". Aus {@code ENTWURF}, {@code ANGENOMMEN} und {@code ABGELEHNT} ist
 * es 409, und dann wird nichts geschrieben; geprueft mit {@code never()} am Bestand.
 *
 * <p><b>Die Reaktion betrifft genau dieses eine Angebot</b> (Issue #126): Der Anwendungsfall liest
 * keine weiteren Angebote und schreibt nur das eine.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AngebotReaktionUseCaseTest {

  private static final long ANGEBOT = 11L;
  private static final Instant JETZT = Instant.parse("2026-09-28T09:30:00Z");

  /** Lange nach dem Ende der Gueltigkeit (20.10.2026) aus {@link Angebotsdoppel}. */
  private static final Instant NACH_ABLAUF = Instant.parse("2026-12-01T09:00:00Z");

  @Mock private AngebotRepository angebote;

  private AngebotReaktionUseCase useCase(final Instant zeitpunkt) {
    return new AngebotReaktionUseCase(angebote, Clock.fixed(zeitpunkt, ZoneOffset.UTC));
  }

  private AngebotReaktionUseCase useCase() {
    return useCase(JETZT);
  }

  /** Ein Angebot im gegebenen Zustand. */
  private void liegtVor(final Angebotszustand zustand) {
    final Angebot angebot = Angebotsdoppel.festgeschrieben(ANGEBOT, zustand);
    when(angebote.findById(ANGEBOT)).thenReturn(Optional.of(angebot));
    when(angebote.save(any())).thenAnswer(aufruf -> aufruf.getArgument(0));
  }

  @ParameterizedTest
  @EnumSource(
      value = Angebotszustand.class,
      names = {"VERSENDET", "ABGELOEST"})
  void nimmAn_fromVersendetOrAbgeloest_thenWritesTheAngebotAsAngenommen(
      final Angebotszustand zustand) {
    // Given — Kriterium 17, F13: annehmbar bleibt auch, was ein spaeterer Versand abgeloest hat.
    liegtVor(zustand);

    // When
    final Angebot angenommen = useCase().nimmAn(ANGEBOT).angebot();

    // Then
    assertThat(angenommen.zustand()).isEqualTo(Angebotszustand.ANGENOMMEN);
    assertThat(angenommen.reaktionAm()).isEqualTo(JETZT);
    verify(angebote).save(angenommen);
  }

  @ParameterizedTest
  @EnumSource(
      value = Angebotszustand.class,
      names = {"VERSENDET", "ABGELOEST"})
  void lehneAb_fromVersendetOrAbgeloest_thenWritesTheAngebotAsAbgelehnt(
      final Angebotszustand zustand) {
    // Given — Kriterium 17.
    liegtVor(zustand);

    // When
    final Angebot abgelehnt = useCase().lehneAb(ANGEBOT).angebot();

    // Then
    assertThat(abgelehnt.zustand()).isEqualTo(Angebotszustand.ABGELEHNT);
    assertThat(abgelehnt.reaktionAm()).isEqualTo(JETZT);
    verify(angebote).save(abgelehnt);
  }

  @Test
  void nimmAn_atAnExpiredAngebot_thenSucceeds() {
    // Given — Kriterium 18, F5: die verstrichene Gueltigkeit schliesst ein Angebot nicht.
    liegtVor(Angebotszustand.VERSENDET);

    // When
    final AngebotAnsicht ansicht = useCase(NACH_ABLAUF).nimmAn(ANGEBOT);

    // Then
    assertThat(ansicht.angebot().zustand()).isEqualTo(Angebotszustand.ANGENOMMEN);
    assertThat(ansicht.stand()).isEqualTo(Angebotsstand.ANGENOMMEN);
  }

  @Test
  void lehneAb_atAnExpiredAngebot_thenSucceeds() {
    // Given — Kriterium 18, F5.
    liegtVor(Angebotszustand.VERSENDET);

    // When
    final AngebotAnsicht ansicht = useCase(NACH_ABLAUF).lehneAb(ANGEBOT);

    // Then
    assertThat(ansicht.angebot().zustand()).isEqualTo(Angebotszustand.ABGELEHNT);
    assertThat(ansicht.stand()).isEqualTo(Angebotsstand.ABGELEHNT);
  }

  @ParameterizedTest
  @EnumSource(
      value = Angebotszustand.class,
      names = {"ENTWURF", "ANGENOMMEN", "ABGELEHNT"})
  void nimmAn_fromAnUnreachableZustand_thenRejectsAndWritesNothing(final Angebotszustand zustand) {
    // Given — Kriterium 17: beide Reaktionen sind endgueltig, ein Entwurf war nie beim Kunden.
    liegtVor(zustand);

    // When / Then
    assertThatThrownBy(() -> useCase().nimmAn(ANGEBOT)).isInstanceOf(AngebotNichtAenderbar.class);
    verify(angebote, never()).save(any());
  }

  @ParameterizedTest
  @EnumSource(
      value = Angebotszustand.class,
      names = {"ENTWURF", "ANGENOMMEN", "ABGELEHNT"})
  void lehneAb_fromAnUnreachableZustand_thenRejectsAndWritesNothing(final Angebotszustand zustand) {
    // Given — Kriterium 17.
    liegtVor(zustand);

    // When / Then
    assertThatThrownBy(() -> useCase().lehneAb(ANGEBOT)).isInstanceOf(AngebotNichtAenderbar.class);
    verify(angebote, never()).save(any());
  }

  @Test
  void nimmAn_thenWritesOnlyThisAngebotAndReadsNoOther() {
    // Given — Issue #126: Die Zusage beendet keines der anderen Angebote der Firma.
    liegtVor(Angebotszustand.VERSENDET);

    // When
    useCase().nimmAn(ANGEBOT);

    // Then
    verify(angebote, times(1)).save(any());
    verify(angebote, never()).findByFirma(anyLong());
  }

  @Test
  void nimmAn_whenTheAngebotIsUnknown_thenRejects() {
    // Given
    when(angebote.findById(ANGEBOT)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> useCase().nimmAn(ANGEBOT)).isInstanceOf(AngebotNichtGefunden.class);
    verify(angebote, never()).save(any());
  }

  @Test
  void lehneAb_whenTheAngebotIsUnknown_thenRejects() {
    // Given
    when(angebote.findById(ANGEBOT)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> useCase().lehneAb(ANGEBOT)).isInstanceOf(AngebotNichtGefunden.class);
    verify(angebote, never()).save(any());
  }
}
