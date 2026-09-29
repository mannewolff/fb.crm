package org.mwolff.fbcrm.angebot.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.angebot.domain.Angebotsstatus;
import org.mwolff.fbcrm.common.Anschrift;
import org.mwolff.fbcrm.firma.application.FirmaNichtGefunden;
import org.mwolff.fbcrm.firma.domain.Ansprechpartner;
import org.mwolff.fbcrm.firma.domain.AnsprechpartnerRepository;
import org.mwolff.fbcrm.firma.domain.Firma;
import org.mwolff.fbcrm.firma.domain.FirmaRepository;

/**
 * Das Anlegen eines Angebots an eine Firma (Kriterien 2, 3; Issue #126).
 *
 * <p>Zwei Zusagen stehen im Mittelpunkt. Erstens die Vorbelegung: Das Angebotsdatum ist der heutige
 * Tag <b>in der Geschaeftszone</b> und nicht in UTC (E12), der Status ist ANGELEGT (Issue #127).
 * Zweitens die Wahl des Kunden: Die Firma muss es geben und sie muss aktiv sein; ein genannter
 * Ansprechpartner muss zu ihr gehoeren und aktiv sein.
 *
 * <p>Die Uhr steht bewusst auf 22:30 UTC: In der Geschaeftszone ist da bereits der naechste Tag.
 * Eine Uhr am Mittag liesse beide Rechnungen gleich aussehen.
 */
@ExtendWith(MockitoExtension.class)
class AngebotAnlegenUseCaseTest {

  private static final Instant JETZT = Instant.parse("2026-09-27T22:30:00Z");
  private static final LocalDate HEUTE_IN_BERLIN = LocalDate.of(2026, 9, 28);
  private static final Long PERSON = Long.valueOf(Angebotsdoppel.ANSPRECHPARTNER);

  @Mock private AngebotRepository angebote;
  @Mock private FirmaRepository firmen;
  @Mock private AnsprechpartnerRepository personen;

  private AngebotAnlegenUseCase useCase;

  @BeforeEach
  void baueDenAnwendungsfall() {
    useCase =
        new AngebotAnlegenUseCase(
            angebote,
            firmen,
            new Ansprechpartnerwahl(personen),
            Clock.fixed(JETZT, ZoneOffset.UTC));
  }

  private static Firma firma(final boolean aktiv) {
    return new Firma(
        Long.valueOf(Angebotsdoppel.FIRMA),
        "Adler AG",
        new Anschrift(null, null, null, null),
        null,
        null,
        aktiv,
        Angebotsdoppel.ANGELEGT,
        Angebotsdoppel.ANGELEGT);
  }

  private static Ansprechpartner person(final long firmaId, final boolean aktiv) {
    return new Ansprechpartner(
        PERSON,
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

  private void firmaIstAktiv() {
    when(firmen.findById(Angebotsdoppel.FIRMA)).thenReturn(Optional.of(firma(true)));
  }

  private Angebot legeAn(final @Nullable Long ansprechpartnerId) {
    when(angebote.save(any())).thenAnswer(aufruf -> aufruf.getArgument(0));
    return useCase.anlegen(Angebotsdoppel.FIRMA, ansprechpartnerId);
  }

  @Test
  void anlegen_thenStartsAsCreatedForTheFirma() {
    // Given — Kriterium 2: An die Firma entsteht ein Angebot im Status ANGELEGT.
    firmaIstAktiv();

    // When
    final Angebot angelegt = legeAn(null);

    // Then
    assertThat(angelegt.status()).isEqualTo(Angebotsstatus.ANGELEGT);
    assertThat(angelegt.firmaId()).isEqualTo(Angebotsdoppel.FIRMA);
    assertThat(angelegt.ansprechpartnerId()).isNull();
    assertThat(angelegt.beschreibung()).isNull();
    assertThat(angelegt.positionen()).isEmpty();
    assertThat(angelegt.createdAt()).isEqualTo(JETZT);
    assertThat(angelegt.updatedAt()).isEqualTo(JETZT);
    verifyNoInteractions(personen);
  }

  @Test
  void anlegen_withAnActiveContactOfTheFirma_thenCarriesTheContact() {
    // Given
    firmaIstAktiv();
    when(personen.findById(PERSON)).thenReturn(Optional.of(person(Angebotsdoppel.FIRMA, true)));

    // When
    final Angebot angelegt = legeAn(PERSON);

    // Then
    assertThat(angelegt.ansprechpartnerId()).isEqualTo(PERSON);
  }

  @Test
  void anlegen_thenDatesTheAngebotInTheGeschaeftszone() {
    // Given — E12: der heutige Tag in Europe/Berlin, nicht in UTC.
    firmaIstAktiv();

    // When
    final Angebot angelegt = legeAn(null);

    // Then
    assertThat(angelegt.angebotDatum()).isEqualTo(HEUTE_IN_BERLIN);
  }

  @Test
  void anlegen_atAnUnknownFirma_thenRejects() {
    // Given
    when(firmen.findById(Angebotsdoppel.FIRMA)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> useCase.anlegen(Angebotsdoppel.FIRMA, null))
        .isInstanceOf(FirmaNichtGefunden.class);
    verify(angebote, never()).save(any());
  }

  @Test
  void anlegen_atAStillgelegteFirma_thenRejectsAndWritesNothing() {
    // Given — an eine stillgelegte Firma geht kein neues Angebot.
    when(firmen.findById(Angebotsdoppel.FIRMA)).thenReturn(Optional.of(firma(false)));

    // When / Then
    assertThatThrownBy(() -> useCase.anlegen(Angebotsdoppel.FIRMA, null))
        .isInstanceOf(FirmaStillgelegt.class);
    verify(angebote, never()).save(any());
  }

  @Test
  void anlegen_withAnUnknownContact_thenRejects() {
    // Given
    firmaIstAktiv();
    when(personen.findById(PERSON)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> useCase.anlegen(Angebotsdoppel.FIRMA, PERSON))
        .isInstanceOf(AnsprechpartnerNichtWaehlbar.class);
    verify(angebote, never()).save(any());
  }

  @Test
  void anlegen_withAContactOfAnotherFirma_thenRejects() {
    // Given
    firmaIstAktiv();
    when(personen.findById(PERSON))
        .thenReturn(Optional.of(person(Angebotsdoppel.FREMDE_FIRMA, true)));

    // When / Then
    assertThatThrownBy(() -> useCase.anlegen(Angebotsdoppel.FIRMA, PERSON))
        .isInstanceOf(AnsprechpartnerNichtWaehlbar.class);
    verify(angebote, never()).save(any());
  }

  @Test
  void anlegen_withAStillgelegterContact_thenRejects() {
    // Given
    firmaIstAktiv();
    when(personen.findById(PERSON)).thenReturn(Optional.of(person(Angebotsdoppel.FIRMA, false)));

    // When / Then
    assertThatThrownBy(() -> useCase.anlegen(Angebotsdoppel.FIRMA, PERSON))
        .isInstanceOf(AnsprechpartnerNichtWaehlbar.class);
    verify(angebote, never()).save(any());
  }
}
