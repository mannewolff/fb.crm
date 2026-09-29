package org.mwolff.fbcrm.angebot.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
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
import org.mwolff.fbcrm.angebot.domain.Angebotsstatus;
import org.mwolff.fbcrm.firma.domain.Ansprechpartner;
import org.mwolff.fbcrm.firma.domain.AnsprechpartnerRepository;

/**
 * Das Aendern eines Angebots (Issue #127, Kriterium 5).
 *
 * <p>Das Angebot wird als Ganzes geschrieben (E8): Datum, Ansprechpartner, Beschreibung und die
 * vollstaendige Positionsliste in der gewuenschten Reihenfolge — in jedem Status.
 *
 * <p>Der zweite Gegenstand ist die Wahl des Ansprechpartners: Ein <em>neu</em> gewaehlter muss zur
 * Firma gehoeren und aktiv sein. Der bereits gespeicherte bleibt waehlbar, auch wenn er inzwischen
 * stillgelegt ist — sonst liesse sich ein Angebot nach dem Stilllegen seines Ansprechpartners gar
 * nicht mehr speichern. Eine abgewiesene Wahl schreibt nichts; den Nachweis fuehrt {@code
 * verifyNoMoreInteractions} nach dem einen Lesezugriff.
 */
@ExtendWith(MockitoExtension.class)
class AngebotAendernUseCaseTest {

  private static final long ANGEBOT = 11L;
  private static final long ANDERE_PERSON = 9L;
  private static final Instant JETZT = Instant.parse("2026-09-28T09:30:00Z");
  private static final LocalDate NEUES_DATUM = LocalDate.of(2026, 9, 25);
  private static final String NEUER_TEXT = "Ueberarbeitete Beschreibung";

  @Mock private AngebotRepository angebote;
  @Mock private AnsprechpartnerRepository personen;

  private AngebotAendernUseCase useCase;

  @BeforeEach
  void baueDenAnwendungsfall() {
    useCase =
        new AngebotAendernUseCase(
            angebote, new Ansprechpartnerwahl(personen), Clock.fixed(JETZT, ZoneOffset.UTC));
  }

  private static AngebotDaten daten(
      final @Nullable Long ansprechpartnerId, final List<Angebotsposition> positionen) {
    return new AngebotDaten(NEUES_DATUM, ansprechpartnerId, NEUER_TEXT, positionen);
  }

  private static Ansprechpartner person(final long id, final long firmaId, final boolean aktiv) {
    return new Ansprechpartner(
        Long.valueOf(id),
        firmaId,
        "Eva",
        "Adler",
        null,
        null,
        null,
        null,
        aktiv,
        Angebotsdoppel.ANGELEGT,
        Angebotsdoppel.ANGELEGT);
  }

  private void angebotIst(final Angebot angebot) {
    when(angebote.findById(ANGEBOT)).thenReturn(Optional.of(angebot));
  }

  private Angebot aendere(final AngebotDaten daten) {
    when(angebote.save(any())).thenAnswer(aufruf -> aufruf.getArgument(0));
    return useCase.aendere(ANGEBOT, daten);
  }

  @ParameterizedTest
  @EnumSource(Angebotsstatus.class)
  void aendere_givenAnyStatus_thenWritesTheNewValues(final Angebotsstatus status) {
    // Given — Kriterium 5: das Angebot bleibt in jedem Status aenderbar.
    angebotIst(Angebotsdoppel.ohneAnsprechpartner(Angebotsdoppel.angebot(ANGEBOT, status)));

    // When
    final Angebot geaendert = aendere(daten(null, List.of(Angebotsdoppel.KONZEPTION)));

    // Then
    assertThat(geaendert)
        .satisfies(
            a -> assertThat(a.status()).isEqualTo(status),
            a -> assertThat(a.angebotDatum()).isEqualTo(NEUES_DATUM),
            a -> assertThat(a.beschreibung()).isEqualTo(NEUER_TEXT),
            a -> assertThat(a.ansprechpartnerId()).isNull(),
            a -> assertThat(a.positionen()).containsExactly(Angebotsdoppel.KONZEPTION));
    verifyNoInteractions(personen);
  }

  @Test
  void aendere_thenTakesTheSubmittedOrderOfPositionen() {
    // Given — E8: die Liste ist die Reihenfolge.
    angebotIst(Angebotsdoppel.ohneAnsprechpartner(Angebotsdoppel.angebot(ANGEBOT)));

    // When
    final Angebot geaendert =
        aendere(daten(null, List.of(Angebotsdoppel.SCHULUNG, Angebotsdoppel.KONZEPTION)));

    // Then
    assertThat(geaendert.positionen())
        .containsExactly(Angebotsdoppel.SCHULUNG, Angebotsdoppel.KONZEPTION);
  }

  @Test
  void aendere_thenKeepsIdentityAndStampsTheChange() {
    // Given — Kennung, Firma und Anlagezeitpunkt bleiben.
    angebotIst(Angebotsdoppel.ohneAnsprechpartner(Angebotsdoppel.angebot(ANGEBOT)));

    // When
    final Angebot geaendert = aendere(daten(null, List.of()));

    // Then
    assertThat(geaendert.requireId()).isEqualTo(ANGEBOT);
    assertThat(geaendert.firmaId()).isEqualTo(Angebotsdoppel.FIRMA);
    assertThat(geaendert.createdAt()).isEqualTo(Angebotsdoppel.ANGELEGT);
    assertThat(geaendert.updatedAt()).isEqualTo(JETZT);
  }

  @Test
  void aendere_withANewActiveContactOfTheFirma_thenCarriesIt() {
    // Given
    angebotIst(Angebotsdoppel.angebot(ANGEBOT));
    when(personen.findById(ANDERE_PERSON))
        .thenReturn(Optional.of(person(ANDERE_PERSON, Angebotsdoppel.FIRMA, true)));

    // When
    final Angebot geaendert = aendere(daten(ANDERE_PERSON, List.of()));

    // Then
    assertThat(geaendert.ansprechpartnerId()).isEqualTo(ANDERE_PERSON);
  }

  @Test
  void aendere_keepingTheStoredContactThatIsNowStillgelegt_thenAllowed() {
    // Given — der gespeicherte Ansprechpartner wurde nach der Anlage stillgelegt.
    angebotIst(Angebotsdoppel.angebot(ANGEBOT));
    when(personen.findById(Angebotsdoppel.ANSPRECHPARTNER))
        .thenReturn(
            Optional.of(person(Angebotsdoppel.ANSPRECHPARTNER, Angebotsdoppel.FIRMA, false)));

    // When
    final Angebot geaendert = aendere(daten(Angebotsdoppel.ANSPRECHPARTNER, List.of()));

    // Then
    assertThat(geaendert.ansprechpartnerId()).isEqualTo(Angebotsdoppel.ANSPRECHPARTNER);
  }

  @Test
  void aendere_withANewStillgelegterContact_thenRejectsAndWritesNothing() {
    // Given
    angebotIst(Angebotsdoppel.angebot(ANGEBOT));
    when(personen.findById(ANDERE_PERSON))
        .thenReturn(Optional.of(person(ANDERE_PERSON, Angebotsdoppel.FIRMA, false)));

    // When / Then
    assertThatThrownBy(() -> useCase.aendere(ANGEBOT, daten(ANDERE_PERSON, List.of())))
        .isInstanceOf(AnsprechpartnerNichtWaehlbar.class);
    verify(angebote).findById(ANGEBOT);
    verifyNoMoreInteractions(angebote);
  }

  @Test
  void aendere_withAContactOfAnotherFirma_thenRejectsAndWritesNothing() {
    // Given
    angebotIst(Angebotsdoppel.angebot(ANGEBOT));
    when(personen.findById(ANDERE_PERSON))
        .thenReturn(Optional.of(person(ANDERE_PERSON, Angebotsdoppel.FREMDE_FIRMA, true)));

    // When / Then
    assertThatThrownBy(() -> useCase.aendere(ANGEBOT, daten(ANDERE_PERSON, List.of())))
        .isInstanceOf(AnsprechpartnerNichtWaehlbar.class);
    verify(angebote).findById(ANGEBOT);
    verifyNoMoreInteractions(angebote);
  }

  @Test
  void aendere_keepingTheStoredContactButItBelongsToAnotherFirma_thenRejects() {
    // Given — auch der unveraenderte muss zur Firma gehoeren; nur das Stilllegen ist ihm erlaubt.
    angebotIst(Angebotsdoppel.angebot(ANGEBOT));
    when(personen.findById(Angebotsdoppel.ANSPRECHPARTNER))
        .thenReturn(
            Optional.of(person(Angebotsdoppel.ANSPRECHPARTNER, Angebotsdoppel.FREMDE_FIRMA, true)));

    // When / Then
    assertThatThrownBy(
            () -> useCase.aendere(ANGEBOT, daten(Angebotsdoppel.ANSPRECHPARTNER, List.of())))
        .isInstanceOf(AnsprechpartnerNichtWaehlbar.class);
  }

  @Test
  void aendere_withAnUnknownContact_thenRejects() {
    // Given
    angebotIst(Angebotsdoppel.angebot(ANGEBOT));
    when(personen.findById(ANDERE_PERSON)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> useCase.aendere(ANGEBOT, daten(ANDERE_PERSON, List.of())))
        .isInstanceOf(AnsprechpartnerNichtWaehlbar.class);
  }

  @Test
  void aendere_whenTheAngebotIsUnknown_thenRejects() {
    // Given
    when(angebote.findById(ANGEBOT)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> useCase.aendere(ANGEBOT, daten(null, List.of())))
        .isInstanceOf(AngebotNichtGefunden.class);
    verify(angebote).findById(ANGEBOT);
    verifyNoMoreInteractions(angebote);
  }
}
