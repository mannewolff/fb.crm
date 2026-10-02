package org.mwolff.fbcrm.rechnung.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.rechnung.domain.Nummernmuster;
import org.mwolff.fbcrm.rechnung.domain.RechnungRepository;
import org.mwolff.fbcrm.rechnung.domain.Rechnungseinstellungen;
import org.mwolff.fbcrm.rechnung.domain.RechnungseinstellungenRepository;

/**
 * Was die Rechnung anderen Modulen ueber ihre gestellten Rechnungen sagt (Plan #208, E5).
 *
 * <p>Gegenstand sind zwei Auskuenfte aus <b>einem</b> Durchlauf: die Monatsabrechnung aus Netto,
 * Brutto und Anzahl der im Monat gestellten Rechnungen (#206, Kriterium 7) und die abgerechneten
 * Mengen je Angebotsposition ueber alle Monate (Kriterium 5). Ein Entwurf zaehlt in keine von
 * beiden (Kriterium 6 der fachlichen Quelle, Antwort 6).
 *
 * <p>Ueber den Monat entscheidet allein das Rechnungsdatum und nicht der Zeitpunkt des Stellens
 * (Antwort 4), und Brutto entsteht je Rechnung mit ihrem festgeschriebenen Satz — auf denselben
 * Cent wie in der Rechnungsliste (Kriterium 9).
 */
@ExtendWith(MockitoExtension.class)
class RechnungsauskunftTest {

  private static final YearMonth SEPTEMBER = YearMonth.of(2026, 9);

  private static final LocalDate ERSTER_SEPTEMBER = LocalDate.of(2026, 9, 1);
  private static final LocalDate LETZTER_SEPTEMBER = LocalDate.of(2026, 9, 30);
  private static final LocalDate ERSTER_OKTOBER = LocalDate.of(2026, 10, 1);

  @Mock private RechnungRepository rechnungen;
  @Mock private RechnungseinstellungenRepository einstellungen;

  private Rechnungsauskunft auskunft;

  @BeforeEach
  void baueDieAuskunft() {
    auskunft = new Rechnungsauskunft(rechnungen, einstellungen);
  }

  /** Der Satz der aktuellen Einstellungen — er gilt nur, wo eine Rechnung keinen eigenen traegt. */
  private void gegebenerAktuellerSatz(final String steuersatz) {
    when(einstellungen.lies())
        .thenReturn(
            new Rechnungseinstellungen(
                new Nummernmuster("R{JJ}-{NNNN}"), new BigDecimal(steuersatz), 10));
  }

  @Test
  void gestellte_thenReadsFindAlleExactlyOnce() {
    // Given — zwei gestellte Rechnungen, beide im Monat.
    gegebenerAktuellerSatz("19.00");
    when(rechnungen.findAlle())
        .thenReturn(
            List.of(
                Rechnungsdoppel.gestellt(
                    1L,
                    Rechnungsdoppel.ANGEBOT,
                    "R26-0001",
                    List.of(Rechnungsdoppel.beratung("80.00")),
                    LETZTER_SEPTEMBER),
                Rechnungsdoppel.gestellt(
                    2L,
                    Rechnungsdoppel.ANGEBOT,
                    "R26-0002",
                    List.of(Rechnungsdoppel.pauschale("1")),
                    ERSTER_SEPTEMBER)));

    // When
    auskunft.gestellte(SEPTEMBER);

    // Then — ein Durchlauf durch dieselben Daten, nicht zwei (Plan #208, E5).
    verify(rechnungen, times(1)).findAlle();
  }

  @Test
  void gestellte_withTheFirstAndTheLastDayOfTheMonth_thenBothBelongToIt() {
    // Given — 1. und 30. September, dazu der 1. Oktober.
    gegebenerAktuellerSatz("19.00");
    when(rechnungen.findAlle())
        .thenReturn(
            List.of(
                Rechnungsdoppel.gestellt(
                    1L,
                    Rechnungsdoppel.ANGEBOT,
                    "R26-0001",
                    List.of(Rechnungsdoppel.beratung("10.00")),
                    ERSTER_SEPTEMBER),
                Rechnungsdoppel.gestellt(
                    2L,
                    Rechnungsdoppel.ANGEBOT,
                    "R26-0002",
                    List.of(Rechnungsdoppel.beratung("20.00")),
                    LETZTER_SEPTEMBER),
                Rechnungsdoppel.gestellt(
                    3L,
                    Rechnungsdoppel.ANGEBOT,
                    "R26-0003",
                    List.of(Rechnungsdoppel.beratung("40.00")),
                    ERSTER_OKTOBER)));

    // When
    final Monatsabrechnung imMonat = auskunft.gestellte(SEPTEMBER).imMonat();

    // Then — 30 Stunden zu 100,00 €, zwei Rechnungen; der Oktober bleibt draussen.
    assertThat(imMonat.netto()).isEqualByComparingTo("3000.00");
    assertThat(imMonat.anzahl()).isEqualTo(2);
  }

  @Test
  void gestellte_withARechnungOutsideTheMonth_thenItsMengenStillCount() {
    // Given — eine Rechnung im September, eine im Oktober, beide auf dieselbe Position.
    gegebenerAktuellerSatz("19.00");
    when(rechnungen.findAlle())
        .thenReturn(
            List.of(
                Rechnungsdoppel.gestellt(
                    1L,
                    Rechnungsdoppel.ANGEBOT,
                    "R26-0001",
                    List.of(Rechnungsdoppel.beratung("80.00")),
                    LETZTER_SEPTEMBER),
                Rechnungsdoppel.gestellt(
                    2L,
                    Rechnungsdoppel.ANGEBOT,
                    "R26-0002",
                    List.of(Rechnungsdoppel.beratung("30.00")),
                    ERSTER_OKTOBER)));

    // When
    final Gestellte gestellte = auskunft.gestellte(SEPTEMBER);

    // Then — die Monatsabrechnung kennt nur den September, die Mengen alle Monate.
    assertThat(gestellte.imMonat().netto()).isEqualByComparingTo("8000.00");
    assertThat(gestellte.imMonat().anzahl()).isEqualTo(1);
    assertThat(gestellte.mengenJePosition())
        .containsOnly(
            Map.entry(Long.valueOf(Rechnungsdoppel.BERATUNG_ID), new BigDecimal("110.00")));
  }

  @Test
  void gestellte_withSeveralRechnungenOnThePositions_thenTheMengenAreAddedUp() {
    // Given — zwei Rechnungen auf die Beratung, eine davon zusaetzlich auf die Pauschale.
    gegebenerAktuellerSatz("19.00");
    when(rechnungen.findAlle())
        .thenReturn(
            List.of(
                Rechnungsdoppel.gestellt(
                    1L,
                    Rechnungsdoppel.ANGEBOT,
                    "R26-0001",
                    List.of(Rechnungsdoppel.beratung("80.00")),
                    LETZTER_SEPTEMBER),
                Rechnungsdoppel.gestellt(
                    2L,
                    Rechnungsdoppel.ANGEBOT,
                    "R26-0002",
                    List.of(Rechnungsdoppel.beratung("20.50"), Rechnungsdoppel.pauschale("0.50")),
                    LETZTER_SEPTEMBER)));

    // When
    final Map<Long, BigDecimal> mengen = auskunft.gestellte(SEPTEMBER).mengenJePosition();

    // Then
    assertThat(mengen.get(Long.valueOf(Rechnungsdoppel.BERATUNG_ID)))
        .isEqualByComparingTo("100.50");
    assertThat(mengen.get(Long.valueOf(Rechnungsdoppel.PAUSCHALE_ID))).isEqualByComparingTo("0.50");
  }

  @Test
  void gestellte_withAnEntwurfOnTheSamePositions_thenItChangesNeitherAnswer() {
    // Given — eine gestellte Rechnung und ein Entwurf ueber dieselbe Position im selben Monat.
    gegebenerAktuellerSatz("19.00");
    when(rechnungen.findAlle())
        .thenReturn(
            List.of(
                Rechnungsdoppel.gestellt(
                    1L,
                    Rechnungsdoppel.ANGEBOT,
                    "R26-0001",
                    List.of(Rechnungsdoppel.beratung("80.00")),
                    LETZTER_SEPTEMBER),
                Rechnungsdoppel.entwurf(2L, List.of(Rechnungsdoppel.beratung("50.00")))));

    // When
    final Gestellte gestellte = auskunft.gestellte(SEPTEMBER);

    // Then — der Entwurf zaehlt nirgends mit (#206, Antwort 6).
    assertThat(gestellte.imMonat().netto()).isEqualByComparingTo("8000.00");
    assertThat(gestellte.imMonat().anzahl()).isEqualTo(1);
    assertThat(gestellte.mengenJePosition())
        .containsOnly(
            Map.entry(Long.valueOf(Rechnungsdoppel.BERATUNG_ID), new BigDecimal("80.00")));
  }

  @Test
  void gestellte_thenTheMonatIsTheRechnungsdatumAndNotTheZeitpunktOfStellen() {
    // Given — gestellt am 20. September (Rechnungsdoppel.ANGELEGT), Rechnungsdatum 1. Oktober.
    gegebenerAktuellerSatz("19.00");
    when(rechnungen.findAlle())
        .thenReturn(
            List.of(
                Rechnungsdoppel.gestellt(
                    1L,
                    Rechnungsdoppel.ANGEBOT,
                    "R26-0001",
                    List.of(Rechnungsdoppel.beratung("80.00")),
                    ERSTER_OKTOBER)));

    // When / Then — im September zaehlt sie nicht, im Oktober schon.
    assertThat(auskunft.gestellte(SEPTEMBER).imMonat().anzahl()).isZero();
    assertThat(auskunft.gestellte(YearMonth.of(2026, 10)).imMonat().anzahl()).isEqualTo(1);
  }

  @Test
  void gestellte_thenBruttoUsesTheFestgeschriebenenSatzOfEachRechnung() {
    // Given — die Rechnung traegt 7 %, die Einstellungen nennen inzwischen 19 %.
    gegebenerAktuellerSatz("19.00");
    when(rechnungen.findAlle())
        .thenReturn(
            List.of(
                Rechnungsdoppel.gestellt(
                    1L,
                    Rechnungsdoppel.ANGEBOT,
                    "R26-0001",
                    List.of(Rechnungsdoppel.beratung("80.00")),
                    LETZTER_SEPTEMBER)));

    // When
    final Monatsabrechnung imMonat = auskunft.gestellte(SEPTEMBER).imMonat();

    // Then — 8.000,00 € zu 7 % ergeben 8.560,00 € und nicht 9.520,00 € (#206, Kriterium 9).
    assertThat(imMonat.netto()).isEqualByComparingTo("8000.00");
    assertThat(imMonat.brutto()).isEqualByComparingTo("8560.00");
  }

  @Test
  void gestellte_thenBruttoIsTheSumOfTheRoundedBruttoOfEachRechnung() {
    // Given — zwei Rechnungen ueber je 0,50 € netto zu 7 %: je Rechnung 0,035 € Steuer.
    gegebenerAktuellerSatz("19.00");
    when(rechnungen.findAlle())
        .thenReturn(
            List.of(
                Rechnungsdoppel.gestellt(
                    1L,
                    Rechnungsdoppel.ANGEBOT,
                    "R26-0001",
                    List.of(Rechnungsdoppel.beratung("1.00", new BigDecimal("0.50"))),
                    LETZTER_SEPTEMBER),
                Rechnungsdoppel.gestellt(
                    2L,
                    Rechnungsdoppel.ANGEBOT,
                    "R26-0002",
                    List.of(Rechnungsdoppel.beratung("1.00", new BigDecimal("0.50"))),
                    LETZTER_SEPTEMBER)));

    // When
    final Monatsabrechnung imMonat = auskunft.gestellte(SEPTEMBER).imMonat();

    // Then — je Rechnung 0,54 €, zusammen 1,08 €; aus 1,00 € netto gerechnet waeren es 1,07 €.
    assertThat(imMonat.netto()).isEqualByComparingTo("1.00");
    assertThat(imMonat.brutto()).isEqualByComparingTo("1.08");
  }

  @Test
  void gestellte_withoutAnyRechnung_thenZeroAndAnEmptyMap() {
    // Given — es gibt keine Rechnung.
    gegebenerAktuellerSatz("19.00");
    when(rechnungen.findAlle()).thenReturn(List.of());

    // When
    final Gestellte gestellte = auskunft.gestellte(SEPTEMBER);

    // Then
    assertThat(gestellte.imMonat())
        .isEqualTo(new Monatsabrechnung(new BigDecimal("0.00"), new BigDecimal("0.00"), 0));
    assertThat(gestellte.mengenJePosition()).isEmpty();
  }

  @Test
  void gestellte_thenTheMapIsACopyAndTheCallerCannotChangeIt() {
    // Given — die Abbildung aus dem kompakten Konstruktor.
    final Map<Long, BigDecimal> eigene = new HashMap<>();
    eigene.put(Long.valueOf(Rechnungsdoppel.BERATUNG_ID), new BigDecimal("80.00"));
    final Gestellte gestellte =
        new Gestellte(
            new Monatsabrechnung(new BigDecimal("0.00"), new BigDecimal("0.00"), 0), eigene);

    // When — der Aufrufer veraendert seine Liste weiter.
    eigene.put(Long.valueOf(Rechnungsdoppel.PAUSCHALE_ID), BigDecimal.ONE);

    // Then
    assertThat(gestellte.mengenJePosition()).hasSize(1);
    assertThatThrownBy(() -> gestellte.mengenJePosition().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
