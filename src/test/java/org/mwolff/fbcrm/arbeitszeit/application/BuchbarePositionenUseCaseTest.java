package org.mwolff.fbcrm.arbeitszeit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
import org.mwolff.fbcrm.common.Abrechnungsmodus;
import org.mwolff.fbcrm.common.Anschrift;
import org.mwolff.fbcrm.common.Einheit;
import org.mwolff.fbcrm.firma.application.FirmaNichtGefunden;
import org.mwolff.fbcrm.firma.domain.Firma;
import org.mwolff.fbcrm.firma.domain.FirmaRepository;

/**
 * Die Positionen, auf die Arbeitszeit gebucht werden darf (Issue #193, Kriterium 1; Plan #194,
 * A15).
 *
 * <p>Gegenstand ist der Filter — nach Aufwand, in Stunden, und das Angebot bestellt oder erledigt
 * ({@link Buchbarkeit}) — und die Reihenfolge, in der die Auswahlliste gruppierbar ist: Firma
 * alphabetisch, darin das neueste Angebot zuerst, darin die Positionen in der Reihenfolge des
 * Angebots.
 *
 * <p>Die Namen der Firmen kommen in <b>einem</b> Aufruf, und ohne buchbare Position wird nach
 * keinem gefragt.
 */
@ExtendWith(MockitoExtension.class)
class BuchbarePositionenUseCaseTest {

  /** Die zweite Firma — alphabetisch vor „IT Bildungshaus". */
  private static final long ANDERE_FIRMA = 6L;

  private static final String ANDERER_FIRMENNAME = "Alpha Technik";

  private static final long ALTES_ANGEBOT = 12L;
  private static final long NEUES_ANGEBOT = 13L;

  private static final long ALTE_POSITION = 201L;
  private static final long NEUE_POSITION = 202L;

  /** Nach Aufwand, aber in Personentagen — darauf wird keine Stunde gebucht. */
  private static final long PERSONENTAG_POSITION = 203L;

  @Mock private AngebotRepository angebote;
  @Mock private FirmaRepository firmen;

  private BuchbarePositionenUseCase useCase;

  @BeforeEach
  void baueDenAnwendungsfall() {
    useCase = new BuchbarePositionenUseCase(angebote, firmen);
  }

  private static Angebotsposition position(
      final long id,
      final String bezeichnung,
      final Abrechnungsmodus modus,
      final Einheit einheit) {
    return new Angebotsposition(
        Long.valueOf(id),
        bezeichnung,
        modus,
        new BigDecimal("5.00"),
        einheit,
        new BigDecimal("100.00"));
  }

  private static Angebot angebot(
      final long id,
      final long firmaId,
      final LocalDate datum,
      final Angebotsstatus status,
      final Angebotsposition... positionen) {
    return new Angebot(
        Long.valueOf(id),
        firmaId,
        null,
        status.intern(),
        status,
        datum,
        "Wartungsvertrag",
        List.of(positionen),
        Zeitdoppel.FRUEHER,
        Zeitdoppel.FRUEHER);
  }

  private static Firma firma(final long id, final String name) {
    return new Firma(
        Long.valueOf(id),
        name,
        new Anschrift(null, null, null, null),
        null,
        null,
        true,
        Instant.EPOCH,
        Instant.EPOCH);
  }

  private void imBestandStehen(final Angebot... gefunden) {
    when(angebote.findAlle(Optional.empty())).thenReturn(List.of(gefunden));
  }

  @Test
  void positionen_thenOnlyTheBookableOnesOfAnOrderedOffer() {
    // Given — „Schulungstag" ist eine Pauschale zum Festpreis und faellt heraus (Antworten 3 und
    // 5).
    imBestandStehen(Zeitdoppel.angebot());
    when(firmen.findAllById(Set.of(Zeitdoppel.FIRMA))).thenReturn(List.of(Zeitdoppel.firma()));

    // When
    final List<Buchungsposition> gelesen = useCase.positionen();

    // Then
    assertThat(gelesen)
        .extracting(Buchungsposition::bezeichnung)
        .containsExactly("Konzeption", "Wartung");
  }

  @Test
  void positionen_givenAnEffortPositionInPersonDays_thenItIsMissing() {
    // Given — nach Aufwand, aber nicht in Stunden: eine Stundenbuchung passt dort nicht hin.
    imBestandStehen(
        angebot(
            ALTES_ANGEBOT,
            ANDERE_FIRMA,
            LocalDate.of(2026, 10, 1),
            Angebotsstatus.BESTELLT,
            position(
                PERSONENTAG_POSITION,
                "Beratungstag",
                Abrechnungsmodus.AUFWAND,
                Einheit.PERSONENTAG)));

    // When / Then
    assertThat(useCase.positionen()).isEmpty();
    verifyNoInteractions(firmen);
  }

  @ParameterizedTest
  @EnumSource(
      value = Angebotsstatus.class,
      names = {"BESTELLT", "ERLEDIGT"},
      mode = EnumSource.Mode.EXCLUDE)
  void positionen_givenAnOfferInAnotherStatus_thenNone(final Angebotsstatus status) {
    // Given — Antwort 2: gebucht wird nur auf bestellte und erledigte Angebote; „abgegeben" und
    // „abgerechnet" fehlen damit in der Auswahl.
    imBestandStehen(Zeitdoppel.angebot(status));

    // When / Then
    assertThat(useCase.positionen()).isEmpty();
    verifyNoInteractions(firmen);
  }

  @Test
  void positionen_thenCarryTheOfferAndTheCompanyName() {
    // Given — A15: gruppierbar nach Firma und Angebot, also tragen beide ihre Angaben mit.
    imBestandStehen(Zeitdoppel.angebot());
    when(firmen.findAllById(Set.of(Zeitdoppel.FIRMA))).thenReturn(List.of(Zeitdoppel.firma()));

    // When
    final Buchungsposition erste = useCase.positionen().get(0);

    // Then
    assertThat(erste.id()).isEqualTo(Zeitdoppel.KONZEPTION_ID);
    assertThat(erste.angebotId()).isEqualTo(Zeitdoppel.ANGEBOT);
    assertThat(erste.angebotDatum()).isEqualTo(LocalDate.of(2026, 11, 1));
    assertThat(erste.firmaName()).isEqualTo(Zeitdoppel.FIRMENNAME);
  }

  @Test
  void positionen_thenOrderedByCompanyThenNewestOffer() {
    // Given — zwei Firmen, bei der zweiten zwei Angebote verschiedenen Datums.
    imBestandStehen(
        Zeitdoppel.angebot(),
        angebot(
            ALTES_ANGEBOT,
            ANDERE_FIRMA,
            LocalDate.of(2026, 9, 1),
            Angebotsstatus.ERLEDIGT,
            position(ALTE_POSITION, "Altvertrag", Abrechnungsmodus.AUFWAND, Einheit.STUNDE)),
        angebot(
            NEUES_ANGEBOT,
            ANDERE_FIRMA,
            LocalDate.of(2026, 10, 1),
            Angebotsstatus.BESTELLT,
            position(NEUE_POSITION, "Neuvertrag", Abrechnungsmodus.AUFWAND, Einheit.STUNDE)));
    when(firmen.findAllById(Set.of(Zeitdoppel.FIRMA, ANDERE_FIRMA)))
        .thenReturn(List.of(firma(ANDERE_FIRMA, ANDERER_FIRMENNAME), Zeitdoppel.firma()));

    // When
    final List<Buchungsposition> gelesen = useCase.positionen();

    // Then — „Alpha Technik" vor „IT Bildungshaus", darin das Oktober-Angebot vor dem September.
    assertThat(gelesen)
        .extracting(Buchungsposition::bezeichnung)
        .containsExactly("Neuvertrag", "Altvertrag", "Konzeption", "Wartung");
  }

  @Test
  void positionen_whenNothingIsBookable_thenEmptyWithoutAskingForCompanies() {
    // Given — kein Angebot im Bestand.
    imBestandStehen();

    // When / Then
    assertThat(useCase.positionen()).isEmpty();
    verifyNoInteractions(firmen);
  }

  @Test
  void positionen_thenAsksForTheCompanyNamesInOneCall() {
    // Given
    imBestandStehen(Zeitdoppel.angebot());
    when(firmen.findAllById(Set.of(Zeitdoppel.FIRMA))).thenReturn(List.of(Zeitdoppel.firma()));

    // When
    useCase.positionen();

    // Then
    verify(firmen).findAllById(Set.of(Zeitdoppel.FIRMA));
  }

  @Test
  void positionen_whenTheCompanyOfAnOfferIsGone_thenFails() {
    // Given — Firmen werden nie geloescht; eine fehlende waere ein Widerspruch im Bestand.
    imBestandStehen(Zeitdoppel.angebot());
    when(firmen.findAllById(Set.of(Zeitdoppel.FIRMA))).thenReturn(List.of());

    // When / Then
    assertThatExceptionOfType(FirmaNichtGefunden.class).isThrownBy(useCase::positionen);
  }
}
