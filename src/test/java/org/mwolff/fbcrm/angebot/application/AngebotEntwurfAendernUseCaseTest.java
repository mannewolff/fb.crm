package org.mwolff.fbcrm.angebot.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.angebot.domain.Angebotsposition;
import org.mwolff.fbcrm.angebot.domain.Angebotszustand;

/**
 * Das Fortschreiben eines Entwurfs (Kriterien 6, 13).
 *
 * <p>Der Entwurf wird als Ganzes geschrieben (E8): Texte, Gueltigkeit und die vollstaendige
 * Positionsliste in der gewuenschten Reihenfolge. Was hier ankommt, ersetzt den bisherigen Stand —
 * ein Weg zum Verschieben einer einzelnen Position gibt es deshalb nicht.
 *
 * <p>Der zweite Gegenstand ist die Grenze aus Kriterium 13: In jedem der vier festgeschriebenen
 * Zustaende antwortet der Anwendungsfall mit {@link AngebotNichtAenderbar} — und er <b>schreibt
 * nichts</b>. Den Nachweis fuehrt {@code verifyNoMoreInteractions} nach dem einen Lesezugriff: Ohne
 * ihn waere der Bestand gefragt, aber nicht belegt, dass er unberuehrt blieb.
 */
@ExtendWith(MockitoExtension.class)
class AngebotEntwurfAendernUseCaseTest {

  private static final long ANGEBOT = 11L;
  private static final Instant JETZT = Instant.parse("2026-09-28T09:30:00Z");
  private static final LocalDate NEUE_GUELTIGKEIT = LocalDate.of(2026, 11, 30);
  private static final String NEUER_TEXT = "Ueberarbeitete Leistungsbeschreibung";

  @Mock private AngebotRepository angebote;

  private AngebotEntwurfAendernUseCase useCase;

  @BeforeEach
  void baueDenAnwendungsfall() {
    useCase = new AngebotEntwurfAendernUseCase(angebote, Clock.fixed(JETZT, ZoneOffset.UTC));
  }

  private static EntwurfDaten daten(final List<Angebotsposition> positionen) {
    return new EntwurfDaten(NEUE_GUELTIGKEIT, NEUER_TEXT, null, positionen);
  }

  private Angebot aendere(final List<Angebotsposition> positionen) {
    when(angebote.findById(ANGEBOT)).thenReturn(Optional.of(Angebotsdoppel.entwurf(ANGEBOT)));
    when(angebote.save(any())).thenAnswer(aufruf -> aufruf.getArgument(0));
    return useCase.aendere(ANGEBOT, daten(positionen)).angebot();
  }

  @Test
  void aendere_thenWritesTextsAndValidity() {
    // Given — Kriterium 6: der Entwurf ist frei aenderbar.

    // When
    final Angebot geaendert = aendere(List.of(Angebotsdoppel.KONZEPTION));

    // Then
    assertThat(geaendert.gueltigBis()).isEqualTo(NEUE_GUELTIGKEIT);
    assertThat(geaendert.leistungsbeschreibung()).isEqualTo(NEUER_TEXT);
    assertThat(geaendert.zahlungsbedingungen()).isNull();
  }

  @Test
  void aendere_thenTakesTheSubmittedOrderOfPositionen() {
    // Given — E8: die Liste ist die Reihenfolge.

    // When
    final Angebot geaendert = aendere(List.of(Angebotsdoppel.SCHULUNG, Angebotsdoppel.KONZEPTION));

    // Then
    assertThat(geaendert.positionen())
        .containsExactly(Angebotsdoppel.SCHULUNG, Angebotsdoppel.KONZEPTION);
  }

  @Test
  void aendere_thenKeepsIdentityAndStampsTheChange() {
    // Given — Kennung, Firma, Zustand und Anlagezeitpunkt bleiben.

    // When
    final Angebot geaendert = aendere(List.of());

    // Then
    assertThat(geaendert.requireId()).isEqualTo(ANGEBOT);
    assertThat(geaendert.firmaId()).isEqualTo(Angebotsdoppel.FIRMA);
    assertThat(geaendert.zustand()).isEqualTo(Angebotszustand.ENTWURF);
    assertThat(geaendert.createdAt()).isEqualTo(Angebotsdoppel.ANGELEGT);
    assertThat(geaendert.updatedAt()).isEqualTo(JETZT);
  }

  @ParameterizedTest
  @EnumSource(
      value = Angebotszustand.class,
      names = {"VERSENDET", "ANGENOMMEN", "ABGELEHNT", "ABGELOEST"})
  void aendere_whenTheAngebotIsFestgeschrieben_thenRejectsAndWritesNothing(
      final Angebotszustand zustand) {
    // Given — Kriterium 13: ab dem Versenden aendert sich nichts mehr.
    when(angebote.findById(ANGEBOT))
        .thenReturn(Optional.of(Angebotsdoppel.festgeschrieben(ANGEBOT, zustand)));

    // When / Then
    assertThatThrownBy(() -> useCase.aendere(ANGEBOT, daten(List.of())))
        .isInstanceOf(AngebotNichtAenderbar.class);
    verify(angebote).findById(ANGEBOT);
    verifyNoMoreInteractions(angebote);
  }

  @Test
  void aendere_whenTheAngebotIsUnknown_thenRejects() {
    // Given
    when(angebote.findById(ANGEBOT)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> useCase.aendere(ANGEBOT, daten(List.of())))
        .isInstanceOf(AngebotNichtGefunden.class);
    verify(angebote).findById(ANGEBOT);
    verifyNoMoreInteractions(angebote);
  }
}
