package org.mwolff.fbcrm.rechnung.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
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
import org.mwolff.fbcrm.arbeitszeit.application.Arbeitszeitauskunft;
import org.mwolff.fbcrm.rechnung.domain.Rechnung;
import org.mwolff.fbcrm.rechnung.domain.RechnungRepository;
import org.mwolff.fbcrm.rechnung.domain.Rechnungsposition;
import org.mwolff.fbcrm.rechnung.domain.Rechnungszustand;

/**
 * Das Anlegen eines Rechnungsentwurfs zu einem Angebot (Plan #169, E5; #160, Kriterien 2, 4, 9, 10;
 * Plan #194, A10, A11, E1, E7).
 *
 * <p>Die Wahl des Angebots legt den Entwurf sofort an — mit allen Positionen, an denen etwas offen
 * ist, vorbelegt mit der offenen Menge und dem Einzelpreis von jetzt. Dass der Entwurf gespeichert
 * wird und nicht erst beim Speichern der Maske entsteht, ist die Voraussetzung dafuer, dass er bei
 * „bereits abgerechnet" mitzaehlt (Kriterium 6).
 *
 * <p><b>Mit einem Monat belegt die Arbeitszeit vor</b> (Issue #193, Kriterien 9 bis 12): Jede
 * buchbare Position traegt die Summe dieses Monats, auch ueber ihre offene Menge hinaus; eine
 * buchbare Position ohne Stunden fehlt; eine Festpreisposition bekommt wie immer ihre offene Menge.
 * Traegt der Monat an keiner buchbaren Position eine Stunde, entsteht genau der Entwurf, der ohne
 * Monat entstanden waere — samt laufendem Monat als Leistungszeitraum.
 *
 * <p>Die Uhr ist fest auf den 30. September 22:30 UTC gestellt: In der Geschaeftszone ist das
 * bereits der 1. Oktober, und daran zeigt sich, dass die Vorbelegung gegen den Kalender des
 * Freiberuflers rechnet und nicht gegen den Nullmeridian (E12).
 */
@ExtendWith(MockitoExtension.class)
class RechnungAnlegenUseCaseTest {

  /** 30. September 22:30 UTC — in Europe/Berlin schon der 1. Oktober, 00:30. */
  private static final Instant SPAETABENDS = Instant.parse("2026-09-30T22:30:00Z");

  /** Der Monat des Beispiels aus #193 — und nicht der laufende. */
  private static final YearMonth NOVEMBER = YearMonth.of(2026, 11);

  private final Clock uhr = Clock.fixed(SPAETABENDS, ZoneOffset.UTC);

  @Mock private AngebotRepository angebote;
  @Mock private RechnungRepository rechnungen;
  @Mock private Arbeitszeitauskunft arbeitszeit;

  @Captor private ArgumentCaptor<Rechnung> geschrieben;

  private RechnungAnlegenUseCase useCase() {
    return new RechnungAnlegenUseCase(angebote, rechnungen, arbeitszeit, uhr);
  }

  private Rechnung angelegt() {
    verify(rechnungen).save(geschrieben.capture());
    return geschrieben.getValue();
  }

  /** Dasselbe Angebot mit frei gewaehlten Positionen, bestellt und damit abrechenbar. */
  private void gegebenesAngebot(final List<Angebotsposition> positionen) {
    when(angebote.findById(Rechnungsdoppel.ANGEBOT))
        .thenReturn(Optional.of(Rechnungsdoppel.angebot(Angebotsstatus.BESTELLT, positionen)));
  }

  /** Die Stunden, die die Zeiterfassung fuer den November meldet. */
  private void gemeldeteStunden(final Map<Long, BigDecimal> stunden) {
    when(arbeitszeit.imMonat(Rechnungsdoppel.ANGEBOT, NOVEMBER)).thenReturn(stunden);
  }

  @Test
  void anlegen_withAnUnknownAngebot_thenNothingIsWritten() {
    // Given
    when(angebote.findById(Rechnungsdoppel.ANGEBOT)).thenReturn(Optional.empty());

    final RechnungAnlegenUseCase useCase = useCase();

    // When / Then
    assertThatThrownBy(() -> useCase.anlegen(Rechnungsdoppel.ANGEBOT, Optional.empty()))
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

    final RechnungAnlegenUseCase useCase = useCase();

    // When / Then
    assertThatThrownBy(() -> useCase.anlegen(Rechnungsdoppel.ANGEBOT, Optional.of(NOVEMBER)))
        .isInstanceOf(AngebotNichtAbrechenbar.class);
    verify(rechnungen, never()).save(any());
  }

  @Test
  void anlegen_withNothingLeftOpen_thenItIsRejectedEvenWithAMonat() {
    // Given — beide Positionen bereits vollstaendig abgerechnet; die Abweisung bleibt unveraendert
    // auch dann, wenn im gewaehlten Monat Stunden stehen (E2).
    when(angebote.findById(Rechnungsdoppel.ANGEBOT))
        .thenReturn(Optional.of(Rechnungsdoppel.angebot()));
    when(rechnungen.findByAngebot(Rechnungsdoppel.ANGEBOT))
        .thenReturn(
            List.of(
                Rechnungsdoppel.gestellt(
                    1L,
                    "R26-0001",
                    List.of(Rechnungsdoppel.beratung("160.00"), Rechnungsdoppel.pauschale("1")))));

    final RechnungAnlegenUseCase useCase = useCase();

    // When / Then
    assertThatThrownBy(() -> useCase.anlegen(Rechnungsdoppel.ANGEBOT, Optional.of(NOVEMBER)))
        .isInstanceOf(AngebotNichtAbrechenbar.class);
    verify(rechnungen, never()).save(any());
    verifyNoInteractions(arbeitszeit);
  }

  @Test
  void anlegen_thenTheEntwurfCarriesTodayInTheGeschaeftszoneAndTheRunningMonth() {
    // Given
    when(angebote.findById(Rechnungsdoppel.ANGEBOT))
        .thenReturn(Optional.of(Rechnungsdoppel.angebot()));
    when(rechnungen.findByAngebot(Rechnungsdoppel.ANGEBOT)).thenReturn(List.of());

    // When
    useCase().anlegen(Rechnungsdoppel.ANGEBOT, Optional.empty());

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
  void anlegen_withoutAMonat_thenTheArbeitszeitIsNotAsked() {
    // Given — ohne Monat entsteht der Entwurf wie bisher, und die Zeiterfassung bleibt aussen vor.
    when(angebote.findById(Rechnungsdoppel.ANGEBOT))
        .thenReturn(Optional.of(Rechnungsdoppel.angebot()));
    when(rechnungen.findByAngebot(Rechnungsdoppel.ANGEBOT)).thenReturn(List.of());

    // When
    useCase().anlegen(Rechnungsdoppel.ANGEBOT, Optional.empty());

    // Then
    verifyNoInteractions(arbeitszeit);
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
    useCase().anlegen(Rechnungsdoppel.ANGEBOT, Optional.empty());

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
    final Rechnung entwurf = useCase().anlegen(Rechnungsdoppel.ANGEBOT, Optional.empty());

    // Then
    assertThat(entwurf).isSameAs(gespeichert);
  }

  @Test
  void anlegen_thenThePreisIsTheOneTheAngebotCarriesNow() {
    // Given — das Angebot ist inzwischen auf 120,00 € umgepreist.
    gegebenesAngebot(
        List.of(
            new Angebotsposition(
                Long.valueOf(Rechnungsdoppel.BERATUNG_ID),
                "Beratung",
                Rechnungsdoppel.BERATUNG.abrechnungsmodus(),
                new BigDecimal("160.00"),
                Rechnungsdoppel.BERATUNG.einheit(),
                new BigDecimal("120.00"))));
    when(rechnungen.findByAngebot(Rechnungsdoppel.ANGEBOT)).thenReturn(List.of());

    // When
    useCase().anlegen(Rechnungsdoppel.ANGEBOT, Optional.empty());

    // Then
    assertThat(angelegt().positionen().getFirst().einzelpreis()).isEqualByComparingTo("120.00");
  }

  @Test
  void anlegen_withAMonat_thenTheBuchbarePositionCarriesTheHoursOfThatMonth() {
    // Given — das Beispiel aus #193: 20 Stunden angeboten, 10 im Oktober abgerechnet, 12 im
    // November erfasst. Die Menge ist die Monatssumme und nicht die offene Menge von 10 (A10).
    gegebenesAngebot(List.of(Rechnungsdoppel.beratungUeber("20.00")));
    when(rechnungen.findByAngebot(Rechnungsdoppel.ANGEBOT))
        .thenReturn(
            List.of(
                Rechnungsdoppel.gestellt(
                    1L, "R26-0001", List.of(Rechnungsdoppel.beratung("10.00")))));
    gemeldeteStunden(Map.of(Long.valueOf(Rechnungsdoppel.BERATUNG_ID), new BigDecimal("12.00")));

    // When
    useCase().anlegen(Rechnungsdoppel.ANGEBOT, Optional.of(NOVEMBER));

    // Then — der Leistungszeitraum ist der gewaehlte Monat und nicht der laufende.
    final Rechnung entwurf = angelegt();
    assertThat(entwurf.leistungszeitraum()).isEqualTo("November 2026");
    assertThat(entwurf.positionen())
        .singleElement()
        .satisfies(
            position ->
                assertThat(position.angebotPositionId()).isEqualTo(Rechnungsdoppel.BERATUNG_ID),
            position -> assertThat(position.menge()).isEqualByComparingTo("12.00"));
  }

  @Test
  void anlegen_withAMonat_thenAFestpreispositionKeepsItsOffeneMenge() {
    // Given — auf die Pauschale laesst sich keine Stunde buchen; sie wird wie heute mit ihrer
    // offenen Menge vorbelegt (E1).
    gegebenesAngebot(List.of(Rechnungsdoppel.beratungUeber("20.00"), Rechnungsdoppel.PAUSCHALE));
    when(rechnungen.findByAngebot(Rechnungsdoppel.ANGEBOT)).thenReturn(List.of());
    gemeldeteStunden(
        Map.of(
            Long.valueOf(Rechnungsdoppel.BERATUNG_ID),
            new BigDecimal("12.00"),
            Long.valueOf(Rechnungsdoppel.PAUSCHALE_ID),
            new BigDecimal("0.00")));

    // When
    useCase().anlegen(Rechnungsdoppel.ANGEBOT, Optional.of(NOVEMBER));

    // Then
    assertThat(angelegt().positionen())
        .extracting(Rechnungsposition::angebotPositionId, Rechnungsposition::menge)
        .containsExactly(
            tuple(Rechnungsdoppel.BERATUNG_ID, new BigDecimal("12.00")),
            tuple(Rechnungsdoppel.PAUSCHALE_ID, BigDecimal.ONE));
  }

  @Test
  void anlegen_withAMonat_thenABuchbarePositionWithoutHoursIsAbsent() {
    // Given — im November wurde nur auf die Beratung gebucht; die Wartung steht nicht im Entwurf,
    // obwohl an ihr 10 Stunden offen sind (A10).
    gegebenesAngebot(List.of(Rechnungsdoppel.beratungUeber("20.00"), Rechnungsdoppel.WARTUNG));
    when(rechnungen.findByAngebot(Rechnungsdoppel.ANGEBOT)).thenReturn(List.of());
    gemeldeteStunden(
        Map.of(
            Long.valueOf(Rechnungsdoppel.BERATUNG_ID),
            new BigDecimal("12.00"),
            Long.valueOf(Rechnungsdoppel.WARTUNG_ID),
            new BigDecimal("0.00")));

    // When
    useCase().anlegen(Rechnungsdoppel.ANGEBOT, Optional.of(NOVEMBER));

    // Then
    assertThat(angelegt().positionen())
        .extracting(Rechnungsposition::angebotPositionId)
        .containsExactly(Rechnungsdoppel.BERATUNG_ID);
  }

  @Test
  void anlegen_withAMonat_thenTheMengeIsNotReducedByWhatIsAlreadyBilled() {
    // Given — 40 angeboten, 10 aus dem Oktober und die 12 des Novembers stehen schon in Rechnungen.
    // Die zweite Rechnung desselben Monats bekommt wieder die vollen 12 (A11); die Pauschale ist
    // abgerechnet und fehlt damit wie heute.
    gegebenesAngebot(List.of(Rechnungsdoppel.beratungUeber("40.00"), Rechnungsdoppel.PAUSCHALE));
    when(rechnungen.findByAngebot(Rechnungsdoppel.ANGEBOT))
        .thenReturn(
            List.of(
                Rechnungsdoppel.gestellt(
                    1L,
                    "R26-0001",
                    List.of(Rechnungsdoppel.beratung("10.00"), Rechnungsdoppel.pauschale("1"))),
                Rechnungsdoppel.entwurf(2L, List.of(Rechnungsdoppel.beratung("12.00")))));
    gemeldeteStunden(
        Map.of(
            Long.valueOf(Rechnungsdoppel.BERATUNG_ID),
            new BigDecimal("12.00"),
            Long.valueOf(Rechnungsdoppel.PAUSCHALE_ID),
            new BigDecimal("0.00")));

    // When
    useCase().anlegen(Rechnungsdoppel.ANGEBOT, Optional.of(NOVEMBER));

    // Then
    assertThat(angelegt().positionen())
        .extracting(Rechnungsposition::angebotPositionId, Rechnungsposition::menge)
        .containsExactly(tuple(Rechnungsdoppel.BERATUNG_ID, new BigDecimal("12.00")));
  }

  @Test
  void anlegen_withAMonatWithoutAnyHours_thenTheEntwurfIsTheOneWithoutAMonat() {
    // Given — im November wurde an keiner Position gearbeitet (E7).
    gegebenesAngebot(List.of(Rechnungsdoppel.beratungUeber("20.00"), Rechnungsdoppel.PAUSCHALE));
    when(rechnungen.findByAngebot(Rechnungsdoppel.ANGEBOT)).thenReturn(List.of());
    gemeldeteStunden(
        Map.of(
            Long.valueOf(Rechnungsdoppel.BERATUNG_ID),
            new BigDecimal("0.00"),
            Long.valueOf(Rechnungsdoppel.PAUSCHALE_ID),
            new BigDecimal("0.00")));

    // When
    useCase().anlegen(Rechnungsdoppel.ANGEBOT, Optional.of(NOVEMBER));

    // Then — beide Positionen mit ihrer offenen Menge, und der laufende Monat im Zeitraum.
    final Rechnung entwurf = angelegt();
    assertThat(entwurf.leistungszeitraum()).isEqualTo("Oktober 2026");
    assertThat(entwurf.positionen())
        .extracting(Rechnungsposition::angebotPositionId, Rechnungsposition::menge)
        .containsExactly(
            tuple(Rechnungsdoppel.BERATUNG_ID, new BigDecimal("20.00")),
            tuple(Rechnungsdoppel.PAUSCHALE_ID, BigDecimal.ONE));
  }
}
