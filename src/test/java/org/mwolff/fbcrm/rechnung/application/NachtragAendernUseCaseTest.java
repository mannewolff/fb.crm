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
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
 * Das Aendern einer nachgetragenen Rechnung (#254, Kriterien 2, 3, 4, 10; Plan #259, E7, E8).
 *
 * <p>Die Rechnung stammt aus 2026, die Uhr steht im Februar 2027: Bleibt das Datum, wie es ist,
 * laesst sich die Rechnung trotzdem aendern (E8); ein geaendertes Datum muss in die Grenze passen.
 */
@ExtendWith(MockitoExtension.class)
class NachtragAendernUseCaseTest {

  private static final long ID = 21L;
  private static final long FIRMA = 4L;
  private static final long ANDERE_FIRMA = 5L;
  private static final LocalDate GESPEICHERT = LocalDate.of(2026, 3, 1);
  private static final Instant ANGELEGT = Instant.parse("2026-03-02T08:00:00Z");
  private static final Instant JETZT = Instant.parse("2027-02-10T09:00:00Z");
  private static final BigDecimal NETTO = new BigDecimal("1000.00");
  private static final BigDecimal BRUTTO = new BigDecimal("1190.00");

  @Mock private NachgetrageneRechnungRepository nachgetragene;
  @Mock private FirmaRepository firmen;
  @Mock private Rechnungsnummern rechnungsnummern;

  @Captor private ArgumentCaptor<NachgetrageneRechnung> gespeicherte;

  private NachtragAendernUseCase useCase;

  @BeforeEach
  void baueDenAnwendungsfall() {
    useCase =
        new NachtragAendernUseCase(
            nachgetragene, firmen, rechnungsnummern, Clock.fixed(JETZT, ZoneOffset.UTC));
    lenient().when(nachgetragene.findById(ID)).thenReturn(Optional.of(gespeichert()));
    lenient()
        .when(firmen.findById(ANDERE_FIRMA))
        .thenReturn(Optional.of(Rechnungsdoppel.firma(ANDERE_FIRMA, "Neu GmbH")));
    lenient().when(nachgetragene.save(any())).thenAnswer(aufruf -> aufruf.getArgument(0));
  }

  private static NachgetrageneRechnung gespeichert() {
    return gespeichert(Rechnungszustand.GESTELLT);
  }

  private static NachgetrageneRechnung gespeichert(final Rechnungszustand zustand) {
    return new NachgetrageneRechnung(
        ID,
        FIRMA,
        "RE-1",
        GESPEICHERT,
        NETTO,
        BRUTTO,
        zustand,
        "nachgetragen/21/original.pdf",
        ANGELEGT,
        ANGELEGT);
  }

  private static Nachtragsdaten daten(final String nummer, final LocalDate datum) {
    return new Nachtragsdaten(
        ANDERE_FIRMA, nummer, datum, new BigDecimal("2000.00"), new BigDecimal("2380.00"));
  }

  @Test
  void aendere_thenWrittenWithTheNewEckdatenAndTheMomentFromTheClock() {
    // When
    final NachgetrageneRechnung geaendert = useCase.aendere(ID, daten(" RE-2 ", GESPEICHERT));

    // Then
    verify(rechnungsnummern).vergebenVonAnderer("RE-2", ID);
    verify(nachgetragene).save(gespeicherte.capture());
    assertThat(gespeicherte.getValue())
        .satisfies(
            r -> assertThat(r.id()).isEqualTo(ID),
            r -> assertThat(r.firmaId()).isEqualTo(ANDERE_FIRMA),
            r -> assertThat(r.nummer()).isEqualTo("RE-2"),
            r -> assertThat(r.netto()).isEqualTo(new BigDecimal("2000.00")),
            r -> assertThat(r.brutto()).isEqualTo(new BigDecimal("2380.00")),
            r -> assertThat(r.zustand()).isEqualTo(Rechnungszustand.GESTELLT),
            r -> assertThat(r.pdfSchluessel()).isEqualTo("nachgetragen/21/original.pdf"),
            r -> assertThat(r.createdAt()).isEqualTo(ANGELEGT),
            r -> assertThat(r.updatedAt()).isEqualTo(JETZT));
    assertThat(geaendert).isEqualTo(gespeicherte.getValue());
  }

  @Test
  void aendere_withTheUnchangedDatumAfterTheTurnOfTheYear_thenAccepted() {
    // When / Then
    assertThat(useCase.aendere(ID, daten("RE-1", GESPEICHERT)).rechnungDatum())
        .isEqualTo(GESPEICHERT);
  }

  @Test
  void aendere_withAChangedDatumInsideTheBoundary_thenAccepted() {
    // When / Then
    assertThat(useCase.aendere(ID, daten("RE-1", LocalDate.of(2027, 1, 1))).rechnungDatum())
        .isEqualTo(LocalDate.of(2027, 1, 1));
  }

  @ParameterizedTest
  @ValueSource(strings = {"2026-03-02", "2026-12-31", "2027-02-11"})
  void aendere_withAChangedDatumOutsideTheBoundary_thenRechnungsdatumAusserhalb(
      final String datum) {
    // When / Then
    assertThatThrownBy(() -> useCase.aendere(ID, daten("RE-1", LocalDate.parse(datum))))
        .isInstanceOf(RechnungsdatumAusserhalb.class);
    verify(nachgetragene, never()).save(any());
  }

  @Test
  void aendere_withItsOwnNummer_thenAccepted() {
    // Given — die eigene Zeile zaehlt bei vergebenVonAnderer nicht mit
    when(rechnungsnummern.vergebenVonAnderer("RE-1", ID)).thenReturn(false);

    // When / Then
    assertThat(useCase.aendere(ID, daten("RE-1", GESPEICHERT)).nummer()).isEqualTo("RE-1");
  }

  @Test
  void aendere_withANummerOfAnotherRechnung_thenNummerSchonVergeben() {
    // Given
    when(rechnungsnummern.vergebenVonAnderer("RE-7", ID)).thenReturn(true);

    // When / Then
    assertThatThrownBy(() -> useCase.aendere(ID, daten("RE-7", GESPEICHERT)))
        .isInstanceOf(NummerSchonVergeben.class);
    verify(nachgetragene, never()).save(any());
  }

  @Test
  void aendere_withBruttoBelowNetto_thenBetraegePassenNicht() {
    // When / Then
    assertThatThrownBy(
            () ->
                useCase.aendere(
                    ID,
                    new Nachtragsdaten(
                        ANDERE_FIRMA, "RE-1", GESPEICHERT, NETTO, new BigDecimal("999.99"))))
        .isInstanceOf(BetraegePassenNicht.class);
    verify(nachgetragene, never()).save(any());
  }

  @ParameterizedTest
  @EnumSource(
      value = Rechnungszustand.class,
      names = {"GESTELLT", "BEZAHLT", "ABGESCHRIEBEN"})
  void aendere_inEveryZustand_thenAcceptedAndTheZustandStays(final Rechnungszustand zustand) {
    // Given
    when(nachgetragene.findById(ID)).thenReturn(Optional.of(gespeichert(zustand)));

    // When / Then
    assertThat(useCase.aendere(ID, daten("RE-1", GESPEICHERT)).zustand()).isEqualTo(zustand);
  }

  @Test
  void aendere_withAnUnknownRechnung_thenNachtragNichtGefunden() {
    // Given
    when(nachgetragene.findById(ID)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> useCase.aendere(ID, daten("RE-1", GESPEICHERT)))
        .isInstanceOf(NachtragNichtGefunden.class);
    verify(nachgetragene, never()).save(any());
  }

  @Test
  void aendere_withAnUnknownFirma_thenFirmaNichtGefunden() {
    // Given
    when(firmen.findById(ANDERE_FIRMA)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> useCase.aendere(ID, daten("RE-1", GESPEICHERT)))
        .isInstanceOf(FirmaNichtGefunden.class);
    verify(nachgetragene, never()).save(any());
  }
}
