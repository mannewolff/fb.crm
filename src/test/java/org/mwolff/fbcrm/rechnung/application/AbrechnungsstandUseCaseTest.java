package org.mwolff.fbcrm.rechnung.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.angebot.application.AngebotNichtGefunden;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.angebot.domain.Angebotsstatus;
import org.mwolff.fbcrm.arbeitszeit.application.Arbeitszeitauskunft;
import org.mwolff.fbcrm.rechnung.domain.Nummernmuster;
import org.mwolff.fbcrm.rechnung.domain.RechnungRepository;
import org.mwolff.fbcrm.rechnung.domain.Rechnungseinstellungen;
import org.mwolff.fbcrm.rechnung.domain.RechnungseinstellungenRepository;

/**
 * Der Abrechnungsstand eines Angebots (#160, Kriterium 26).
 *
 * <p>Gegenstand sind die beiden Haelften der Antwort: je Position des Angebots angeboten,
 * abgerechnet, offen und Ueberschreitung — Entwuerfe zaehlen dabei mit (Kriterium 6) — und die
 * Rechnungen dieses Angebots mit ihrem Bruttobetrag, neueste zuerst.
 *
 * <p>Dazu kommen die beiden Angaben aus der Arbeitszeit (Issue #193, Kriterien 7, 8, 11; Plan #194,
 * A6, A12): ob die Position Stunden traegt und wie viele insgesamt angefallen sind. Die erste
 * haengt <b>nicht am Status des Angebots</b> — der Endstand des Beispiels aus #193 steht an einem
 * Angebot, das beim Stellen der Novemberrechnung selbst auf „abgerechnet" gesprungen ist, und muss
 * die 22 angefallenen Stunden auf 20 angebotene weiter zeigen. Die zweite ist an einer
 * Festpreisposition 0,00, auch wenn die Auskunft dort etwas melden wuerde: Was buchbar ist, steht
 * in {@code Buchbarkeit} und nur dort.
 */
@ExtendWith(MockitoExtension.class)
class AbrechnungsstandUseCaseTest {

  @Mock private AngebotRepository angebote;
  @Mock private RechnungRepository rechnungen;
  @Mock private RechnungseinstellungenRepository einstellungen;
  @Mock private Arbeitszeitauskunft arbeitszeit;

  private AbrechnungsstandUseCase useCase;

  @BeforeEach
  void baueDenAnwendungsfall() {
    useCase = new AbrechnungsstandUseCase(angebote, rechnungen, einstellungen, arbeitszeit);
  }

  private void gegebeneEinstellungen(final String steuersatz) {
    when(einstellungen.lies())
        .thenReturn(
            new Rechnungseinstellungen(
                new Nummernmuster("R{JJ}-{NNNN}"), new BigDecimal(steuersatz), 10));
  }

  @Test
  void zu_withAnUnknownAngebot_thenNotFound() {
    // Given
    when(angebote.findById(Rechnungsdoppel.ANGEBOT)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> useCase.zu(Rechnungsdoppel.ANGEBOT))
        .isInstanceOf(AngebotNichtGefunden.class);
    verifyNoInteractions(rechnungen, einstellungen, arbeitszeit);
  }

  @Test
  void zu_thenEveryPositionOfTheAngebotCarriesItsStand() {
    // Given — 100 gestellt und 80 im Entwurf auf 160 angebotene Stunden.
    when(angebote.findById(Rechnungsdoppel.ANGEBOT))
        .thenReturn(Optional.of(Rechnungsdoppel.angebot()));
    when(rechnungen.findByAngebot(Rechnungsdoppel.ANGEBOT))
        .thenReturn(
            List.of(
                Rechnungsdoppel.gestellt(
                    1L, "R26-0001", List.of(Rechnungsdoppel.beratung("100.00"))),
                Rechnungsdoppel.entwurf(2L, List.of(Rechnungsdoppel.beratung("80.00")))));
    gegebeneEinstellungen("19.00");

    // When
    final Angebotsabrechnung abrechnung = useCase.zu(Rechnungsdoppel.ANGEBOT);

    // Then — die Beratung ist um 20 ueberschritten, die Pauschale ist ganz offen.
    assertThat(abrechnung.positionen())
        .hasSize(2)
        .satisfies(
            zeilen -> assertThat(zeilen.getFirst().stand().angeboten()).isEqualByComparingTo("160"),
            zeilen ->
                assertThat(zeilen.getFirst().stand().abgerechnet()).isEqualByComparingTo("180"),
            zeilen -> assertThat(zeilen.getFirst().stand().offen()).isEqualByComparingTo("0"),
            zeilen ->
                assertThat(zeilen.getFirst().stand().ueberschreitung()).isEqualByComparingTo("20"),
            zeilen -> assertThat(zeilen.get(1).stand().offen()).isEqualByComparingTo("1"));
  }

  @Test
  void zu_thenTheRechnungenComeNewestFirstWithTheirBruttoBetrag() {
    // Given — ein Entwurf ohne eigenen Satz und eine gestellte Rechnung mit ihren 7 Prozent.
    when(angebote.findById(Rechnungsdoppel.ANGEBOT))
        .thenReturn(Optional.of(Rechnungsdoppel.angebot()));
    when(rechnungen.findByAngebot(Rechnungsdoppel.ANGEBOT))
        .thenReturn(
            List.of(
                Rechnungsdoppel.gestellt(
                    1L,
                    Rechnungsdoppel.ANGEBOT,
                    "R26-0001",
                    List.of(Rechnungsdoppel.beratung("100.00")),
                    LocalDate.of(2026, 9, 20)),
                Rechnungsdoppel.entwurf(2L, List.of(Rechnungsdoppel.beratung("80.00")))));
    gegebeneEinstellungen("19.00");

    // When
    final Angebotsabrechnung abrechnung = useCase.zu(Rechnungsdoppel.ANGEBOT);

    // Then — der Entwurf vom 30.09. steht vorn und rechnet mit 19, die gestellte mit ihren 7.
    assertThat(abrechnung.rechnungen())
        .hasSize(2)
        .satisfies(
            zeilen -> assertThat(zeilen.getFirst().rechnung().requireId()).isEqualTo(2L),
            zeilen -> assertThat(zeilen.getFirst().brutto()).isEqualByComparingTo("9520.00"),
            zeilen -> assertThat(zeilen.get(1).rechnung().requireId()).isEqualTo(1L),
            zeilen -> assertThat(zeilen.get(1).brutto()).isEqualByComparingTo("10700.00"));
  }

  @Test
  void zu_withoutARechnung_thenTheListIsEmptyAndEverythingIsOffen() {
    // Given
    when(angebote.findById(Rechnungsdoppel.ANGEBOT))
        .thenReturn(Optional.of(Rechnungsdoppel.angebot()));
    when(rechnungen.findByAngebot(Rechnungsdoppel.ANGEBOT)).thenReturn(List.of());
    gegebeneEinstellungen("19.00");

    // When
    final Angebotsabrechnung abrechnung = useCase.zu(Rechnungsdoppel.ANGEBOT);

    // Then
    assertThat(abrechnung.rechnungen()).isEmpty();
    assertThat(abrechnung.positionen().getFirst().stand().offen()).isEqualByComparingTo("160");
  }

  @Test
  void zu_atAnAbgerechnetesAngebot_thenThePositionStaysBuchbarAndKeepsItsAngefalleneStunden() {
    // Given — der Endstand des Beispiels aus #193: 20 Stunden angeboten, im Oktober 10 und im
    // November 10 abgerechnet, 22 insgesamt angefallen. Das Stellen der Novemberrechnung hat das
    // Angebot selbst auf „abgerechnet" gesetzt, weil nichts mehr offen war.
    when(angebote.findById(Rechnungsdoppel.ANGEBOT))
        .thenReturn(
            Optional.of(
                Rechnungsdoppel.angebot(
                    Angebotsstatus.ABGERECHNET,
                    List.of(Rechnungsdoppel.beratungUeber("20.00"), Rechnungsdoppel.PAUSCHALE))));
    when(rechnungen.findByAngebot(Rechnungsdoppel.ANGEBOT))
        .thenReturn(
            List.of(
                Rechnungsdoppel.gestellt(
                    1L, "R26-0001", List.of(Rechnungsdoppel.beratung("10.00"))),
                Rechnungsdoppel.gestellt(
                    2L, "R26-0002", List.of(Rechnungsdoppel.beratung("10.00")))));
    when(arbeitszeit.angefallen(Rechnungsdoppel.ANGEBOT))
        .thenReturn(
            Map.of(
                Long.valueOf(Rechnungsdoppel.BERATUNG_ID),
                new BigDecimal("22.00"),
                Long.valueOf(Rechnungsdoppel.PAUSCHALE_ID),
                new BigDecimal("0.00")));
    gegebeneEinstellungen("19.00");

    // When
    final Angebotsabrechnung abrechnung = useCase.zu(Rechnungsdoppel.ANGEBOT);

    // Then — angeboten 20, angefallen 22, abgerechnet 20, und buchbar trotz „abgerechnet".
    assertThat(abrechnung.positionen().getFirst())
        .satisfies(
            zeile -> assertThat(zeile.buchbar()).isTrue(),
            zeile -> assertThat(zeile.angefallen()).isEqualByComparingTo("22.00"),
            zeile -> assertThat(zeile.stand().angeboten()).isEqualByComparingTo("20.00"),
            zeile -> assertThat(zeile.stand().abgerechnet()).isEqualByComparingTo("20.00"));
  }

  @Test
  void zu_atAFestpreisposition_thenItIsNotBuchbarAndCarriesNoStunden() {
    // Given — die Auskunft meldet auch zur Pauschale eine Zahl; buchbar ist sie dennoch nicht,
    // und dann stehen an ihr keine angefallenen Stunden.
    when(angebote.findById(Rechnungsdoppel.ANGEBOT))
        .thenReturn(Optional.of(Rechnungsdoppel.angebot()));
    when(rechnungen.findByAngebot(Rechnungsdoppel.ANGEBOT)).thenReturn(List.of());
    when(arbeitszeit.angefallen(Rechnungsdoppel.ANGEBOT))
        .thenReturn(Map.of(Long.valueOf(Rechnungsdoppel.PAUSCHALE_ID), new BigDecimal("5.00")));
    gegebeneEinstellungen("19.00");

    // When
    final Angebotsabrechnung abrechnung = useCase.zu(Rechnungsdoppel.ANGEBOT);

    // Then
    assertThat(abrechnung.positionen().get(1))
        .satisfies(
            zeile -> assertThat(zeile.buchbar()).isFalse(),
            zeile -> assertThat(zeile.angefallen()).isEqualByComparingTo("0"));
  }
}
