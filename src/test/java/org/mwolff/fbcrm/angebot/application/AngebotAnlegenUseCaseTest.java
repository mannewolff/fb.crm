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
import org.mwolff.fbcrm.angebot.domain.Angebotsstand;
import org.mwolff.fbcrm.angebot.domain.Angebotszustand;
import org.mwolff.fbcrm.common.Anschrift;
import org.mwolff.fbcrm.eigeneangaben.domain.EigeneAngaben;
import org.mwolff.fbcrm.eigeneangaben.domain.EigeneAngabenRepository;
import org.mwolff.fbcrm.firma.application.FirmaNichtGefunden;
import org.mwolff.fbcrm.firma.domain.Ansprechpartner;
import org.mwolff.fbcrm.firma.domain.AnsprechpartnerRepository;
import org.mwolff.fbcrm.firma.domain.Firma;
import org.mwolff.fbcrm.firma.domain.FirmaRepository;

/**
 * Das Anlegen eines Angebots an eine Firma (Kriterien 2, 3; Issue #126).
 *
 * <p>Zwei Zusagen stehen im Mittelpunkt. Erstens die Vorbelegung aus Kriterium 3: Das Angebotsdatum
 * ist der heutige Tag <b>in der Geschaeftszone</b> und nicht in UTC (E12), die Gueltigkeit laeuft
 * dreissig Tage, und die Zahlungsbedingungen kommen aus „Eigene Angaben" (F8). Zweitens die Wahl
 * des Kunden: Die Firma muss es geben und sie muss aktiv sein; ein genannter Ansprechpartner muss
 * zu ihr gehoeren und aktiv sein.
 *
 * <p>Die Uhr steht bewusst auf 22:30 UTC: In der Geschaeftszone ist da bereits der naechste Tag.
 * Eine Uhr am Mittag liesse beide Rechnungen gleich aussehen.
 */
@ExtendWith(MockitoExtension.class)
class AngebotAnlegenUseCaseTest {

  private static final Instant JETZT = Instant.parse("2026-09-27T22:30:00Z");
  private static final LocalDate HEUTE_IN_BERLIN = LocalDate.of(2026, 9, 28);
  private static final String STANDARDBEDINGUNGEN = "Zahlbar innerhalb von 30 Tagen netto.";
  private static final Long PERSON = Long.valueOf(Angebotsdoppel.ANSPRECHPARTNER);

  @Mock private AngebotRepository angebote;
  @Mock private FirmaRepository firmen;
  @Mock private AnsprechpartnerRepository personen;
  @Mock private EigeneAngabenRepository eigeneAngaben;

  private AngebotAnlegenUseCase useCase;

  @BeforeEach
  void baueDenAnwendungsfall() {
    useCase =
        new AngebotAnlegenUseCase(
            angebote, firmen, personen, eigeneAngaben, Clock.fixed(JETZT, ZoneOffset.UTC));
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

  private static EigeneAngaben angabenMit(final @Nullable String zahlungsbedingungen) {
    return new EigeneAngaben(
        "Manfred Wolff",
        new Anschrift(null, null, null, null),
        null,
        null,
        null,
        null,
        null,
        zahlungsbedingungen);
  }

  private void firmaIstAktiv() {
    when(firmen.findById(Angebotsdoppel.FIRMA)).thenReturn(Optional.of(firma(true)));
  }

  private Angebot legeAn(final @Nullable Long ansprechpartnerId) {
    when(eigeneAngaben.lies()).thenReturn(angabenMit(STANDARDBEDINGUNGEN));
    when(angebote.save(any())).thenAnswer(aufruf -> aufruf.getArgument(0));
    return useCase.anlegen(Angebotsdoppel.FIRMA, ansprechpartnerId).angebot();
  }

  @Test
  void anlegen_thenStartsAsDraftForTheFirma() {
    // Given — Kriterium 2: An die Firma entsteht ein Angebot als Entwurf.
    firmaIstAktiv();

    // When
    final Angebot angelegt = legeAn(null);

    // Then
    assertThat(angelegt.zustand()).isEqualTo(Angebotszustand.ENTWURF);
    assertThat(angelegt.firmaId()).isEqualTo(Angebotsdoppel.FIRMA);
    assertThat(angelegt.ansprechpartnerId()).isNull();
    assertThat(angelegt.nummer()).isNull();
    assertThat(angelegt.positionen()).isEmpty();
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
  void anlegen_thenDatesTheAngebotInTheGeschaeftszoneAndGivesItThirtyDays() {
    // Given — Kriterium 3 und E12: der heutige Tag in Europe/Berlin, nicht in UTC.
    firmaIstAktiv();

    // When
    final Angebot angelegt = legeAn(null);

    // Then
    assertThat(angelegt.angebotDatum()).isEqualTo(HEUTE_IN_BERLIN);
    assertThat(angelegt.gueltigBis()).isEqualTo(HEUTE_IN_BERLIN.plusDays(30));
  }

  @Test
  void anlegen_thenPrefillsTheZahlungsbedingungenFromEigeneAngaben() {
    // Given — Kriterium 3, F8: der Standardtext der eigenen Angaben.
    firmaIstAktiv();

    // When
    final Angebot angelegt = legeAn(null);

    // Then
    assertThat(angelegt.zahlungsbedingungen()).isEqualTo(STANDARDBEDINGUNGEN);
    assertThat(angelegt.leistungsbeschreibung()).isNull();
  }

  @Test
  void anlegen_whenEigeneAngabenCarryNoText_thenLeavesTheZahlungsbedingungenEmpty() {
    // Given — auf einer frischen Instanz ist der Standardtext leer.
    firmaIstAktiv();
    when(eigeneAngaben.lies()).thenReturn(angabenMit(null));
    when(angebote.save(any())).thenAnswer(aufruf -> aufruf.getArgument(0));

    // When
    final Angebot angelegt = useCase.anlegen(Angebotsdoppel.FIRMA, null).angebot();

    // Then
    assertThat(angelegt.zahlungsbedingungen()).isNull();
  }

  @Test
  void anlegen_thenAnswersWithTheStandOfTheNewDraft() {
    // Given
    firmaIstAktiv();
    when(eigeneAngaben.lies()).thenReturn(angabenMit(STANDARDBEDINGUNGEN));
    when(angebote.save(any())).thenAnswer(aufruf -> aufruf.getArgument(0));

    // When
    final AngebotAnsicht ansicht = useCase.anlegen(Angebotsdoppel.FIRMA, null);

    // Then
    assertThat(ansicht.stand()).isEqualTo(Angebotsstand.ENTWURF);
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
