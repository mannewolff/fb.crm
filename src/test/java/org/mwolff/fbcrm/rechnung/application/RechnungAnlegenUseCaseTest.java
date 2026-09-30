package org.mwolff.fbcrm.rechnung.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.angebot.application.AngebotNichtGefunden;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.angebot.domain.Angebotsposition;
import org.mwolff.fbcrm.angebot.domain.Angebotsstatus;
import org.mwolff.fbcrm.rechnung.domain.Rechnung;
import org.mwolff.fbcrm.rechnung.domain.RechnungRepository;
import org.mwolff.fbcrm.rechnung.domain.Rechnungsposition;
import org.mwolff.fbcrm.rechnung.domain.Rechnungszustand;

/**
 * Das Anlegen eines Rechnungsentwurfs zu einem Angebot (Plan #169, E5; #160, Kriterien 2, 4, 9,
 * 10).
 *
 * <p>Die Wahl des Angebots legt den Entwurf sofort an — mit allen Positionen, an denen etwas offen
 * ist, vorbelegt mit der offenen Menge und dem Einzelpreis von jetzt. Dass der Entwurf gespeichert
 * wird und nicht erst beim Speichern der Maske entsteht, ist die Voraussetzung dafuer, dass er bei
 * „bereits abgerechnet" mitzaehlt (Kriterium 6).
 *
 * <p>Die Uhr ist fest auf den 30. September 22:30 UTC gestellt: In der Geschaeftszone ist das
 * bereits der 1. Oktober, und daran zeigt sich, dass die Vorbelegung gegen den Kalender des
 * Freiberuflers rechnet und nicht gegen den Nullmeridian (E12).
 */
@ExtendWith(MockitoExtension.class)
class RechnungAnlegenUseCaseTest {

  /** 30. September 22:30 UTC — in Europe/Berlin schon der 1. Oktober, 00:30. */
  private static final Instant SPAETABENDS = Instant.parse("2026-09-30T22:30:00Z");

  private final Clock uhr = Clock.fixed(SPAETABENDS, ZoneOffset.UTC);

  @Mock private AngebotRepository angebote;
  @Mock private RechnungRepository rechnungen;

  @Captor private ArgumentCaptor<Rechnung> geschrieben;

  private RechnungAnlegenUseCase useCase() {
    return new RechnungAnlegenUseCase(angebote, rechnungen, uhr);
  }

  private Rechnung angelegt() {
    verify(rechnungen).save(geschrieben.capture());
    return geschrieben.getValue();
  }

  @Test
  void anlegen_withAnUnknownAngebot_thenNothingIsWritten() {
    // Given
    when(angebote.findById(Rechnungsdoppel.ANGEBOT)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> useCase().anlegen(Rechnungsdoppel.ANGEBOT))
        .isInstanceOf(AngebotNichtGefunden.class);
    verify(rechnungen, never()).save(any());
  }

  @ParameterizedTest
  @EnumSource(
      value = Angebotsstatus.class,
      names = {"ANGELEGT", "ABGEGEBEN"})
  void anlegen_withAnAngebotBeforeBestellt_thenItIsRejected(final Angebotsstatus status) {
    // Given
    when(angebote.findById(Rechnungsdoppel.ANGEBOT))
        .thenReturn(Optional.of(Rechnungsdoppel.angebot(status)));

    // When / Then
    assertThatThrownBy(() -> useCase().anlegen(Rechnungsdoppel.ANGEBOT))
        .isInstanceOf(AngebotNichtAbrechenbar.class);
    verify(rechnungen, never()).save(any());
  }

  @Test
  void anlegen_withNothingLeftOpen_thenItIsRejected() {
    // Given — beide Positionen bereits vollstaendig abgerechnet.
    when(angebote.findById(Rechnungsdoppel.ANGEBOT))
        .thenReturn(Optional.of(Rechnungsdoppel.angebot()));
    when(rechnungen.findByAngebot(Rechnungsdoppel.ANGEBOT))
        .thenReturn(
            List.of(
                Rechnungsdoppel.gestellt(
                    1L,
                    "R26-0001",
                    List.of(Rechnungsdoppel.beratung("160.00"), Rechnungsdoppel.pauschale("1")))));

    // When / Then
    assertThatThrownBy(() -> useCase().anlegen(Rechnungsdoppel.ANGEBOT))
        .isInstanceOf(AngebotNichtAbrechenbar.class);
    verify(rechnungen, never()).save(any());
  }

  @Test
  void anlegen_thenTheEntwurfCarriesTodayInTheGeschaeftszoneAndTheRunningMonth() {
    // Given
    when(angebote.findById(Rechnungsdoppel.ANGEBOT))
        .thenReturn(Optional.of(Rechnungsdoppel.angebot()));
    when(rechnungen.findByAngebot(Rechnungsdoppel.ANGEBOT)).thenReturn(List.of());

    // When
    useCase().anlegen(Rechnungsdoppel.ANGEBOT);

    // Then
    assertThat(angelegt())
        .satisfies(
            rechnung -> assertThat(rechnung.rechnungDatum()).isEqualTo("2026-10-01"),
            rechnung -> assertThat(rechnung.leistungszeitraum()).isEqualTo("Oktober 2026"),
            rechnung -> assertThat(rechnung.zustand()).isEqualTo(Rechnungszustand.ENTWURF),
            rechnung -> assertThat(rechnung.nummer()).isNull(),
            rechnung -> assertThat(rechnung.id()).isNull(),
            rechnung -> assertThat(rechnung.createdAt()).isEqualTo(SPAETABENDS));
  }

  @Test
  void anlegen_thenOnlyPositionenWithSomethingOpenAreCarried() {
    // Given — die Pauschale ist bereits abgerechnet, von der Beratung sind 80 offen.
    when(angebote.findById(Rechnungsdoppel.ANGEBOT))
        .thenReturn(Optional.of(Rechnungsdoppel.angebot()));
    when(rechnungen.findByAngebot(Rechnungsdoppel.ANGEBOT))
        .thenReturn(
            List.of(
                Rechnungsdoppel.gestellt(
                    1L,
                    "R26-0001",
                    List.of(Rechnungsdoppel.beratung("80.00"), Rechnungsdoppel.pauschale("1")))));

    // When
    useCase().anlegen(Rechnungsdoppel.ANGEBOT);

    // Then
    final List<Rechnungsposition> positionen = angelegt().positionen();
    assertThat(positionen)
        .extracting(
            Rechnungsposition::angebotPositionId,
            Rechnungsposition::bezeichnung,
            Rechnungsposition::einheit,
            Rechnungsposition::einzelpreis)
        .containsExactly(
            tuple(
                Rechnungsdoppel.BERATUNG_ID,
                "Beratung",
                Rechnungsdoppel.BERATUNG.einheit(),
                Rechnungsdoppel.STUNDENSATZ));
    assertThat(positionen.getFirst().menge()).isEqualByComparingTo("80");
  }

  @Test
  void anlegen_thenAnswersTheEntwurfAsTheBestandWroteIt() {
    // Given — erst der Bestand vergibt die Kennung, unter der die Maske den Entwurf oeffnet.
    when(angebote.findById(Rechnungsdoppel.ANGEBOT))
        .thenReturn(Optional.of(Rechnungsdoppel.angebot()));
    when(rechnungen.findByAngebot(Rechnungsdoppel.ANGEBOT)).thenReturn(List.of());
    final Rechnung gespeichert =
        Rechnungsdoppel.entwurf(4L, List.of(Rechnungsdoppel.beratung("160.00")));
    when(rechnungen.save(any())).thenReturn(gespeichert);

    // When
    final Rechnung entwurf = useCase().anlegen(Rechnungsdoppel.ANGEBOT);

    // Then
    assertThat(entwurf).isSameAs(gespeichert);
  }

  @Test
  void anlegen_thenThePreisIsTheOneTheAngebotCarriesNow() {
    // Given — das Angebot ist inzwischen auf 120,00 € umgepreist.
    when(angebote.findById(Rechnungsdoppel.ANGEBOT))
        .thenReturn(
            Optional.of(
                Rechnungsdoppel.angebot(
                    Angebotsstatus.BESTELLT,
                    List.of(
                        new Angebotsposition(
                            Long.valueOf(Rechnungsdoppel.BERATUNG_ID),
                            "Beratung",
                            Rechnungsdoppel.BERATUNG.abrechnungsmodus(),
                            new BigDecimal("160.00"),
                            Rechnungsdoppel.BERATUNG.einheit(),
                            new BigDecimal("120.00"))))));
    when(rechnungen.findByAngebot(Rechnungsdoppel.ANGEBOT)).thenReturn(List.of());

    // When
    useCase().anlegen(Rechnungsdoppel.ANGEBOT);

    // Then
    assertThat(angelegt().positionen().getFirst().einzelpreis()).isEqualByComparingTo("120.00");
  }
}
