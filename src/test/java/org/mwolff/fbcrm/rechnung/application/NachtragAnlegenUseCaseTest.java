package org.mwolff.fbcrm.rechnung.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.firma.application.FirmaNichtGefunden;
import org.mwolff.fbcrm.firma.domain.FirmaRepository;
import org.mwolff.fbcrm.rechnung.domain.NachgetrageneRechnung;
import org.mwolff.fbcrm.rechnung.domain.NachgetrageneRechnungRepository;
import org.mwolff.fbcrm.rechnung.domain.Rechnungszustand;

/**
 * Das Nachtragen einer Rechnung (#254, Kriterien 2, 3, 4, 8; Plan #259, E7, E9, E10, E16).
 *
 * <p>Die Uhr steht am 6. Oktober 2026 um 22:30 UTC — in der Geschaeftszone ist das schon der 7.
 * Oktober. „Heute" ist damit der Tag in Europe/Berlin und nicht in UTC (E7).
 */
@ExtendWith(MockitoExtension.class)
class NachtragAnlegenUseCaseTest {

  private static final long FIRMA = 4L;
  private static final Instant JETZT = Instant.parse("2026-10-06T22:30:00Z");
  private static final LocalDate HEUTE = LocalDate.of(2026, 10, 7);
  private static final BigDecimal NETTO = new BigDecimal("1000.00");
  private static final BigDecimal BRUTTO = new BigDecimal("1190.00");

  @Mock private NachgetrageneRechnungRepository nachgetragene;
  @Mock private FirmaRepository firmen;
  @Mock private Rechnungsnummern rechnungsnummern;

  @Captor private ArgumentCaptor<NachgetrageneRechnung> gespeicherte;

  private NachtragAnlegenUseCase useCase;

  @BeforeEach
  void baueDenAnwendungsfall() {
    useCase =
        new NachtragAnlegenUseCase(
            nachgetragene, firmen, rechnungsnummern, Clock.fixed(JETZT, ZoneOffset.UTC));
    lenient()
        .when(firmen.findById(FIRMA))
        .thenReturn(Optional.of(Rechnungsdoppel.firma(FIRMA, "Kunde GmbH")));
    lenient().when(nachgetragene.save(any())).thenAnswer(aufruf -> aufruf.getArgument(0));
  }

  private static Nachtragsdaten daten(final LocalDate datum) {
    return new Nachtragsdaten(FIRMA, "RE-1", datum, NETTO, BRUTTO);
  }

  @Test
  void anlege_thenWrittenAsGestelltWithTheEingabenAndTheMomentFromTheClock() {
    // When
    final NachgetrageneRechnung angelegt = useCase.anlege(daten(LocalDate.of(2026, 3, 1)));

    // Then
    verify(nachgetragene).save(gespeicherte.capture());
    assertThat(gespeicherte.getValue())
        .satisfies(
            r -> assertThat(r.id()).isNull(),
            r -> assertThat(r.firmaId()).isEqualTo(FIRMA),
            r -> assertThat(r.nummer()).isEqualTo("RE-1"),
            r -> assertThat(r.rechnungDatum()).isEqualTo(LocalDate.of(2026, 3, 1)),
            r -> assertThat(r.netto()).isEqualTo(NETTO),
            r -> assertThat(r.brutto()).isEqualTo(BRUTTO),
            r -> assertThat(r.zustand()).isEqualTo(Rechnungszustand.GESTELLT),
            r -> assertThat(r.pdfSchluessel()).isNull(),
            r -> assertThat(r.createdAt()).isEqualTo(JETZT),
            r -> assertThat(r.updatedAt()).isEqualTo(JETZT));
    assertThat(angelegt).isEqualTo(gespeicherte.getValue());
  }

  @Test
  void anlege_withBlanksAroundTheNummer_thenTrimmedBeforeCheckAndSave() {
    // When
    useCase.anlege(new Nachtragsdaten(FIRMA, " RE-1 ", HEUTE, NETTO, BRUTTO));

    // Then
    verify(rechnungsnummern).vergeben("RE-1");
    verify(nachgetragene).save(gespeicherte.capture());
    assertThat(gespeicherte.getValue().nummer()).isEqualTo("RE-1");
  }

  @Test
  void anlege_onTheFirstOfJanuaryOfTheRunningYear_thenAccepted() {
    // When / Then
    assertThat(useCase.anlege(daten(LocalDate.of(2026, 1, 1))).rechnungDatum())
        .isEqualTo(LocalDate.of(2026, 1, 1));
  }

  @Test
  void anlege_todayInTheGeschaeftszone_thenAccepted() {
    // When / Then — in UTC waere der 7. Oktober noch morgen
    assertThat(useCase.anlege(daten(HEUTE)).rechnungDatum()).isEqualTo(HEUTE);
  }

  @ParameterizedTest
  @ValueSource(strings = {"2025-12-31", "2026-10-08"})
  void anlege_outsideTheRunningYearUntilToday_thenRechnungsdatumAusserhalb(final String datum) {
    // When / Then
    assertThatThrownBy(() -> useCase.anlege(daten(LocalDate.parse(datum))))
        .isInstanceOfSatisfying(
            RechnungsdatumAusserhalb.class,
            fehler -> assertThat(fehler.felder()).containsOnlyKeys(RechnungsdatumAusserhalb.FELD));
    verify(nachgetragene, never()).save(any());
  }

  @Test
  void anlege_withANummerAlreadyTaken_thenNummerSchonVergebenAtTheFieldNummer() {
    // Given
    when(rechnungsnummern.vergeben("RE-1")).thenReturn(true);

    // When / Then
    assertThatThrownBy(() -> useCase.anlege(daten(HEUTE)))
        .isInstanceOfSatisfying(
            NummerSchonVergeben.class,
            fehler ->
                assertThat(fehler.felder())
                    .isEqualTo(Map.of("nummer", List.of(NummerSchonVergeben.MELDUNG))));
    verify(nachgetragene, never()).save(any());
  }

  @Test
  void anlege_withBruttoBelowNetto_thenBetraegePassenNichtAtTheFieldBrutto() {
    // When / Then
    assertThatThrownBy(
            () ->
                useCase.anlege(
                    new Nachtragsdaten(FIRMA, "RE-1", HEUTE, NETTO, new BigDecimal("999.99"))))
        .isInstanceOfSatisfying(
            BetraegePassenNicht.class,
            fehler ->
                assertThat(fehler.felder())
                    .isEqualTo(Map.of("brutto", List.of(BetraegePassenNicht.MELDUNG))));
    verify(nachgetragene, never()).save(any());
  }

  @Test
  void anlege_withBruttoEqualToNetto_thenAccepted() {
    // When
    final NachgetrageneRechnung angelegt =
        useCase.anlege(new Nachtragsdaten(FIRMA, "RE-1", HEUTE, NETTO, new BigDecimal("1000.0")));

    // Then
    assertThat(angelegt.brutto()).isEqualByComparingTo(NETTO);
  }

  @Test
  void anlege_withAStillgelegteFirma_thenAccepted() {
    // Given
    when(firmen.findById(FIRMA))
        .thenReturn(Optional.of(Rechnungsdoppel.firma(FIRMA, "Alt GmbH").stillgelegt(JETZT)));

    // When / Then
    assertThat(useCase.anlege(daten(HEUTE)).firmaId()).isEqualTo(FIRMA);
  }

  @Test
  void anlege_withAnUnknownFirma_thenFirmaNichtGefunden() {
    // Given
    when(firmen.findById(FIRMA)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> useCase.anlege(daten(HEUTE))).isInstanceOf(FirmaNichtGefunden.class);
    verify(nachgetragene, never()).save(any());
  }
}
