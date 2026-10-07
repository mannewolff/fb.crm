package org.mwolff.fbcrm.rechnung.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.angebot.application.AngebotNichtGefunden;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.firma.application.FirmaNichtGefunden;
import org.mwolff.fbcrm.firma.domain.FirmaRepository;
import org.mwolff.fbcrm.rechnung.domain.NachgetrageneRechnungRepository;
import org.mwolff.fbcrm.rechnung.domain.Nummernmuster;
import org.mwolff.fbcrm.rechnung.domain.RechnungRepository;
import org.mwolff.fbcrm.rechnung.domain.Rechnungseinstellungen;
import org.mwolff.fbcrm.rechnung.domain.RechnungseinstellungenRepository;
import org.mwolff.fbcrm.rechnung.domain.Rechnungszustand;

/**
 * Was die Rechnung anderen Modulen ueber ihre gestellten Rechnungen sagt (Plan #208, E5).
 *
 * <p>Gegenstand sind zwei Auskuenfte aus <b>einem</b> Durchlauf: je Monat die Abrechnung aus Netto,
 * Brutto und Anzahl der in ihm gestellten Rechnungen (#206, Kriterium 7; Plan #274, E4) und die
 * abgerechneten Mengen je Angebotsposition ueber alle Monate (Kriterium 5). Ein Monat ohne
 * gestellte Rechnung steht nicht in der Karte. Ein Entwurf zaehlt in keine von beiden (Kriterium 6
 * der fachlichen Quelle, Antwort 6) — eine <b>bezahlte oder abgeschriebene</b> dagegen in beide:
 * Sie ist draussen, und ihr Ausgang aendert daran nichts (Issue #253).
 *
 * <p>Ueber den Monat entscheidet allein das Rechnungsdatum und nicht der Zeitpunkt des Stellens
 * (Antwort 4), und Brutto entsteht je Rechnung mit ihrem festgeschriebenen Satz — auf denselben
 * Cent wie in der Rechnungsliste (Kriterium 9).
 *
 * <p>Eine <b>nachgetragene</b> Rechnung zaehlt in die Abrechnung des Monats ihres Rechnungsdatums
 * mit Netto und Brutto wie erfasst; die Mengen je Angebotsposition beruehrt sie nicht, denn sie
 * gehoert zu keinem Angebot (Plan #259, E20; #254, Kriterien 9 und 11).
 *
 * <p>Dazu, <b>wie viel davon noch offen ist</b> (Issue #284): Eine gestellte Rechnung traegt ihr
 * Netto und eine 1 in die offenen Felder, eine bezahlte und eine abgeschriebene nichts — und das an
 * beiden Aggregaten gleich, der geschriebenen Rechnung wie dem Nachtrag. „Abgerechnet" bleibt davon
 * unberuehrt: Es zaehlt den Umsatz nach Rechnungsdatum und nicht den Zahlungseingang.
 *
 * <p>Und als dritte Antwort <b>die offenen Rechnungen selbst</b> (Issue #285): dieselbe Auswahl wie
 * beim offenen Betrag, aber als Zeilen mit Art, Kennung, Nummer, Firmenname, Rechnungsdatum und
 * Netto, aelteste zuerst. Der Firmenname kommt bei der geschriebenen Rechnung ueber ihr Angebot und
 * bei der nachgetragenen von ihr selbst; fehlt eines von beidem im Bestand, ist das ein Widerspruch
 * und keine leere Zeile. Ohne eine offene Rechnung wird nach Angeboten und Firmen gar nicht
 * gefragt.
 */
@ExtendWith(MockitoExtension.class)
class RechnungsauskunftTest {

  private static final YearMonth SEPTEMBER = YearMonth.of(2026, 9);
  private static final YearMonth OKTOBER = YearMonth.of(2026, 10);

  private static final LocalDate ERSTER_SEPTEMBER = LocalDate.of(2026, 9, 1);
  private static final LocalDate LETZTER_SEPTEMBER = LocalDate.of(2026, 9, 30);
  private static final LocalDate ERSTER_OKTOBER = LocalDate.of(2026, 10, 1);

  @Mock private RechnungRepository rechnungen;
  @Mock private NachgetrageneRechnungRepository nachtraege;
  @Mock private RechnungseinstellungenRepository einstellungen;
  @Mock private AngebotRepository angebote;
  @Mock private FirmaRepository firmen;

  private Rechnungsauskunft auskunft;

  @BeforeEach
  void baueDieAuskunft() {
    auskunft = new Rechnungsauskunft(rechnungen, nachtraege, einstellungen, angebote, firmen);
  }

  /** Das Angebot der Rechnungen und die Firma dahinter — der Weg zum Namen (Issue #285). */
  private void gegebenesAngebotMitFirma() {
    when(angebote.findAlle(Optional.empty())).thenReturn(List.of(Rechnungsdoppel.angebot()));
    when(firmen.findAllById(Set.of(Long.valueOf(Rechnungsdoppel.FIRMA))))
        .thenReturn(List.of(Rechnungsdoppel.firma()));
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
    gegebenesAngebotMitFirma();
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
    auskunft.gestellte();

    // Then — ein Durchlauf durch dieselben Daten, nicht zwei (Plan #208, E5).
    verify(rechnungen, times(1)).findAlle();
  }

  @Test
  void gestellte_withTheFirstAndTheLastDayOfTheMonth_thenBothBelongToIt() {
    // Given — 1. und 30. September, dazu der 1. Oktober.
    gegebenerAktuellerSatz("19.00");
    gegebenesAngebotMitFirma();
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
    final Map<YearMonth, Monatsabrechnung> jeMonat = auskunft.gestellte().jeMonat();

    // Then — im September 30 Stunden zu 100,00 €, zwei Rechnungen; der Oktober steht fuer sich.
    assertThat(jeMonat).containsOnlyKeys(SEPTEMBER, OKTOBER);
    assertThat(jeMonat.get(SEPTEMBER).netto()).isEqualByComparingTo("3000.00");
    assertThat(jeMonat.get(SEPTEMBER).anzahl()).isEqualTo(2);
    assertThat(jeMonat.get(OKTOBER).netto()).isEqualByComparingTo("4000.00");
    assertThat(jeMonat.get(OKTOBER).anzahl()).isEqualTo(1);
  }

  @Test
  void gestellte_withRechnungenInTwoMonate_thenTheMengenCountOverBoth() {
    // Given — eine Rechnung im September, eine im Oktober, beide auf dieselbe Position.
    gegebenerAktuellerSatz("19.00");
    gegebenesAngebotMitFirma();
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
    final Gestellte gestellte = auskunft.gestellte();

    // Then — jeder Monat traegt seine Rechnung, die Mengen zaehlen ueber beide.
    assertThat(gestellte.jeMonat().get(SEPTEMBER).netto()).isEqualByComparingTo("8000.00");
    assertThat(gestellte.jeMonat().get(SEPTEMBER).anzahl()).isEqualTo(1);
    assertThat(gestellte.jeMonat().get(OKTOBER).netto()).isEqualByComparingTo("3000.00");
    assertThat(gestellte.jeMonat().get(OKTOBER).anzahl()).isEqualTo(1);
    assertThat(gestellte.mengenJePosition())
        .containsOnly(
            Map.entry(Long.valueOf(Rechnungsdoppel.BERATUNG_ID), new BigDecimal("110.00")));
  }

  @Test
  void gestellte_withSeveralRechnungenOnThePositions_thenTheMengenAreAddedUp() {
    // Given — zwei Rechnungen auf die Beratung, eine davon zusaetzlich auf die Pauschale.
    gegebenerAktuellerSatz("19.00");
    gegebenesAngebotMitFirma();
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
    final Map<Long, BigDecimal> mengen = auskunft.gestellte().mengenJePosition();

    // Then
    assertThat(mengen.get(Long.valueOf(Rechnungsdoppel.BERATUNG_ID)))
        .isEqualByComparingTo("100.50");
    assertThat(mengen.get(Long.valueOf(Rechnungsdoppel.PAUSCHALE_ID))).isEqualByComparingTo("0.50");
  }

  @Test
  void gestellte_withAnEntwurfOnTheSamePositions_thenItChangesNeitherAnswer() {
    // Given — eine gestellte Rechnung und ein Entwurf ueber dieselbe Position im selben Monat.
    gegebenerAktuellerSatz("19.00");
    gegebenesAngebotMitFirma();
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
    final Gestellte gestellte = auskunft.gestellte();

    // Then — der Entwurf zaehlt nirgends mit (#206, Antwort 6).
    assertThat(gestellte.jeMonat()).containsOnlyKeys(SEPTEMBER);
    assertThat(gestellte.jeMonat().get(SEPTEMBER).netto()).isEqualByComparingTo("8000.00");
    assertThat(gestellte.jeMonat().get(SEPTEMBER).anzahl()).isEqualTo(1);
    assertThat(gestellte.mengenJePosition())
        .containsOnly(
            Map.entry(Long.valueOf(Rechnungsdoppel.BERATUNG_ID), new BigDecimal("80.00")));
  }

  @Test
  void gestellte_thenTheMonatIsTheRechnungsdatumAndNotTheZeitpunktOfStellen() {
    // Given — gestellt am 20. September (Rechnungsdoppel.ANGELEGT), Rechnungsdatum 1. Oktober.
    gegebenerAktuellerSatz("19.00");
    gegebenesAngebotMitFirma();
    when(rechnungen.findAlle())
        .thenReturn(
            List.of(
                Rechnungsdoppel.gestellt(
                    1L,
                    Rechnungsdoppel.ANGEBOT,
                    "R26-0001",
                    List.of(Rechnungsdoppel.beratung("80.00")),
                    ERSTER_OKTOBER)));

    // When
    final Map<YearMonth, Monatsabrechnung> jeMonat = auskunft.gestellte().jeMonat();

    // Then — sie steht im Oktober und nicht im September.
    assertThat(jeMonat).containsOnlyKeys(OKTOBER);
    assertThat(jeMonat.get(OKTOBER).anzahl()).isEqualTo(1);
  }

  @Test
  void gestellte_thenBruttoUsesTheFestgeschriebenenSatzOfEachRechnung() {
    // Given — die Rechnung traegt 7 %, die Einstellungen nennen inzwischen 19 %.
    gegebenerAktuellerSatz("19.00");
    gegebenesAngebotMitFirma();
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
    final Monatsabrechnung imMonat = auskunft.gestellte().jeMonat().get(SEPTEMBER);

    // Then — 8.000,00 € zu 7 % ergeben 8.560,00 € und nicht 9.520,00 € (#206, Kriterium 9).
    assertThat(imMonat.netto()).isEqualByComparingTo("8000.00");
    assertThat(imMonat.brutto()).isEqualByComparingTo("8560.00");
  }

  @Test
  void gestellte_thenBruttoIsTheSumOfTheRoundedBruttoOfEachRechnung() {
    // Given — zwei Rechnungen ueber je 0,50 € netto zu 7 %: je Rechnung 0,035 € Steuer.
    gegebenerAktuellerSatz("19.00");
    gegebenesAngebotMitFirma();
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
    final Monatsabrechnung imMonat = auskunft.gestellte().jeMonat().get(SEPTEMBER);

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
    final Gestellte gestellte = auskunft.gestellte();

    // Then
    assertThat(gestellte.jeMonat()).isEmpty();
    assertThat(gestellte.mengenJePosition()).isEmpty();
  }

  @Test
  void gestellte_withOnlyAnEntwurf_thenItsMonatIsInNoKey() {
    // Given — ein einziger Entwurf mit Rechnungsdatum 30. September (#273, Kriterium 3).
    gegebenerAktuellerSatz("19.00");
    when(rechnungen.findAlle())
        .thenReturn(
            List.of(Rechnungsdoppel.entwurf(1L, List.of(Rechnungsdoppel.beratung("50.00")))));

    // When
    final Gestellte gestellte = auskunft.gestellte();

    // Then — kein Monatsschluessel, auch nicht der September seines Rechnungsdatums.
    assertThat(gestellte.jeMonat()).isEmpty();
    assertThat(gestellte.mengenJePosition()).isEmpty();
  }

  @Test
  void gestellte_thenTheMapsAreCopiesAndTheCallerCannotChangeThem() {
    // Given — die Abbildungen und die Liste aus dem kompakten Konstruktor.
    final Map<YearMonth, Monatsabrechnung> monate = new HashMap<>();
    monate.put(
        SEPTEMBER,
        new Monatsabrechnung(
            new BigDecimal("0.00"), new BigDecimal("0.00"), 0, new BigDecimal("0.00"), 0));
    final Map<Long, BigDecimal> eigene = new HashMap<>();
    eigene.put(Long.valueOf(Rechnungsdoppel.BERATUNG_ID), new BigDecimal("80.00"));
    final List<OffeneRechnung> offene = new ArrayList<>();
    offene.add(offeneZeile(1L, "R26-0001", ERSTER_OKTOBER));
    final Gestellte gestellte = new Gestellte(monate, eigene, offene);

    // When — der Aufrufer veraendert seine Abbildungen und seine Liste weiter.
    monate.put(OKTOBER, new Monatsabrechnung(BigDecimal.ONE, BigDecimal.ONE, 1, BigDecimal.ONE, 1));
    eigene.put(Long.valueOf(Rechnungsdoppel.PAUSCHALE_ID), BigDecimal.ONE);
    offene.add(offeneZeile(2L, "R26-0002", ERSTER_OKTOBER));

    // Then
    assertThat(gestellte.jeMonat()).containsOnlyKeys(SEPTEMBER);
    assertThat(gestellte.mengenJePosition()).hasSize(1);
    assertThat(gestellte.offene()).hasSize(1);
    assertThatThrownBy(() -> gestellte.jeMonat().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> gestellte.mengenJePosition().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> gestellte.offene().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  /** Eine Zeile, wie der kompakte Konstruktor sie bekommt — ohne eine Rechnung dahinter. */
  private static OffeneRechnung offeneZeile(
      final long id, final String nummer, final LocalDate datum) {
    return new OffeneRechnung(
        false, id, nummer, Rechnungsdoppel.FIRMENNAME, datum, new BigDecimal("360.00"));
  }

  @Test
  void gestellte_withAGestellteRechnung_thenItsNettoAndOneStandAsOffen() {
    // Given — eine gestellte, nicht bezahlte Rechnung ueber 360,00 € netto (Issue #284).
    gegebenerAktuellerSatz("19.00");
    gegebenesAngebotMitFirma();
    when(rechnungen.findAlle())
        .thenReturn(
            List.of(
                Rechnungsdoppel.gestellt(
                    1L,
                    Rechnungsdoppel.ANGEBOT,
                    "R26-0001",
                    List.of(Rechnungsdoppel.beratung("100.00", new BigDecimal("3.60"))),
                    ERSTER_OKTOBER)));

    // When
    final Monatsabrechnung imMonat = auskunft.gestellte().jeMonat().get(OKTOBER);

    // Then — dasselbe Netto steht in beiden Feldern: Was gestellt und nicht bezahlt ist, ist offen.
    assertThat(imMonat.netto()).isEqualByComparingTo("360.00");
    assertThat(imMonat.offenNetto()).isEqualByComparingTo("360.00");
    assertThat(imMonat.offenAnzahl()).isEqualTo(1);
  }

  @Test
  void gestellte_withABezahlteRechnung_thenItCountsButIsNoLongerOffen() {
    // Given — dieselbe Rechnung, inzwischen bezahlt (Issue #284).
    gegebenerAktuellerSatz("19.00");
    when(rechnungen.findAlle())
        .thenReturn(
            List.of(
                Rechnungsdoppel.gestellt(
                        1L,
                        Rechnungsdoppel.ANGEBOT,
                        "R26-0001",
                        List.of(Rechnungsdoppel.beratung("100.00", new BigDecimal("3.60"))),
                        ERSTER_OKTOBER)
                    .mitZustand(Rechnungszustand.BEZAHLT, Rechnungsdoppel.ANGELEGT)));

    // When
    final Monatsabrechnung imMonat = auskunft.gestellte().jeMonat().get(OKTOBER);

    // Then — „Abgerechnet" bleibt unveraendert, das Offene ist weg.
    assertThat(imMonat.netto()).isEqualByComparingTo("360.00");
    assertThat(imMonat.anzahl()).isEqualTo(1);
    assertThat(imMonat.offenNetto()).isEqualByComparingTo("0.00");
    assertThat(imMonat.offenAnzahl()).isZero();
  }

  @Test
  void gestellte_withAnAbgeschriebeneRechnung_thenItCountsButIsNoLongerOffen() {
    // Given — dieselbe Rechnung, abgeschrieben: Sie kommt nicht mehr herein (Issue #284).
    gegebenerAktuellerSatz("19.00");
    when(rechnungen.findAlle())
        .thenReturn(
            List.of(
                Rechnungsdoppel.gestellt(
                        1L,
                        Rechnungsdoppel.ANGEBOT,
                        "R26-0001",
                        List.of(Rechnungsdoppel.beratung("100.00", new BigDecimal("3.60"))),
                        ERSTER_OKTOBER)
                    .mitZustand(Rechnungszustand.ABGESCHRIEBEN, Rechnungsdoppel.ANGELEGT)));

    // When
    final Monatsabrechnung imMonat = auskunft.gestellte().jeMonat().get(OKTOBER);

    // Then — sie zaehlt im Umsatz des Monats und steht nicht als offener Posten da.
    assertThat(imMonat.netto()).isEqualByComparingTo("360.00");
    assertThat(imMonat.anzahl()).isEqualTo(1);
    assertThat(imMonat.offenNetto()).isEqualByComparingTo("0.00");
    assertThat(imMonat.offenAnzahl()).isZero();
  }

  @Test
  void gestellte_withAnEntwurf_thenItIsNeitherAbgerechnetNorOffen() {
    // Given — ein Entwurf allein: Er ist nicht draussen und darum auch nichts Offenes (Issue #284).
    gegebenerAktuellerSatz("19.00");
    when(rechnungen.findAlle())
        .thenReturn(
            List.of(Rechnungsdoppel.entwurf(1L, List.of(Rechnungsdoppel.beratung("50.00")))));

    // When
    final Gestellte gestellte = auskunft.gestellte();

    // Then — kein Monatsschluessel, also auch kein offener Posten.
    assertThat(gestellte.jeMonat()).isEmpty();
  }

  @Test
  void gestellte_withANachgetrageneRechnung_thenItIsOffenLikeAWrittenOne() {
    // Given — zwei Nachtraege im Oktober, einer gestellt und einer bezahlt (Issue #284).
    gegebenerAktuellerSatz("19.00");
    gegebenesAngebotMitFirma();
    when(rechnungen.findAlle()).thenReturn(List.of());
    when(nachtraege.findAlle())
        .thenReturn(
            List.of(
                Rechnungsdoppel.nachgetragen(1L, "AR-1", ERSTER_OKTOBER, "360.00", "428.40", null),
                Rechnungsdoppel.nachgetragen(2L, "AR-2", ERSTER_OKTOBER, "500.00", "595.00", null)
                    .mitZustand(Rechnungszustand.BEZAHLT, Rechnungsdoppel.ANGELEGT)));

    // When
    final Monatsabrechnung imMonat = auskunft.gestellte().jeMonat().get(OKTOBER);

    // Then — beide zaehlen in „Abgerechnet", offen ist allein der gestellte Nachtrag.
    assertThat(imMonat.netto()).isEqualByComparingTo("860.00");
    assertThat(imMonat.anzahl()).isEqualTo(2);
    assertThat(imMonat.offenNetto()).isEqualByComparingTo("360.00");
    assertThat(imMonat.offenAnzahl()).isEqualTo(1);
  }

  @Test
  void gestellte_withAnAbgeschriebeneNachgetrageneRechnung_thenItIsNotOffen() {
    // Given — ein abgeschriebener Nachtrag; der Ausgang zaehlt an beiden Aggregaten gleich.
    gegebenerAktuellerSatz("19.00");
    when(rechnungen.findAlle()).thenReturn(List.of());
    when(nachtraege.findAlle())
        .thenReturn(
            List.of(
                Rechnungsdoppel.nachgetragen(1L, "AR-1", ERSTER_OKTOBER, "360.00", "428.40", null)
                    .mitZustand(Rechnungszustand.ABGESCHRIEBEN, Rechnungsdoppel.ANGELEGT)));

    // When
    final Monatsabrechnung imMonat = auskunft.gestellte().jeMonat().get(OKTOBER);

    // Then
    assertThat(imMonat.netto()).isEqualByComparingTo("360.00");
    assertThat(imMonat.offenNetto()).isEqualByComparingTo("0.00");
    assertThat(imMonat.offenAnzahl()).isZero();
  }

  @Test
  void gestellte_withABezahlteAndAnAbgeschriebeneRechnung_thenBothStillCount() {
    // Given — eine bezahlte und eine abgeschriebene Rechnung, beide im Monat (Issue #253).
    gegebenerAktuellerSatz("19.00");
    when(rechnungen.findAlle())
        .thenReturn(
            List.of(
                Rechnungsdoppel.gestellt(
                        1L,
                        Rechnungsdoppel.ANGEBOT,
                        "R26-0001",
                        List.of(Rechnungsdoppel.beratung("80.00")),
                        LETZTER_SEPTEMBER)
                    .mitZustand(Rechnungszustand.BEZAHLT, Rechnungsdoppel.ANGELEGT),
                Rechnungsdoppel.gestellt(
                        2L,
                        Rechnungsdoppel.ANGEBOT,
                        "R26-0002",
                        List.of(Rechnungsdoppel.pauschale("1")),
                        ERSTER_SEPTEMBER)
                    .mitZustand(Rechnungszustand.ABGESCHRIEBEN, Rechnungsdoppel.ANGELEGT)));

    // When
    final Gestellte gestellte = auskunft.gestellte();

    // Then — beide zaehlen in der Abrechnung des Monats und behalten ihre Positionsmengen: 8.000,00
    // aus 80 Stunden zu 100,00 und 1.200,00 aus der Pauschale.
    assertThat(gestellte.jeMonat().get(SEPTEMBER).netto()).isEqualByComparingTo("9200.00");
    assertThat(gestellte.jeMonat().get(SEPTEMBER).anzahl()).isEqualTo(2);
    assertThat(gestellte.mengenJePosition())
        .containsOnly(
            Map.entry(Long.valueOf(Rechnungsdoppel.BERATUNG_ID), new BigDecimal("80.00")),
            Map.entry(Long.valueOf(Rechnungsdoppel.PAUSCHALE_ID), new BigDecimal("1")));
  }

  @Test
  void gestellte_withNachgetragene_thenEachAddsItsBetraegeAndOneToItsMonatButNoMengen() {
    // Given — eine geschriebene Rechnung und zwei nachgetragene, eine davon im Oktober.
    gegebenerAktuellerSatz("19.00");
    gegebenesAngebotMitFirma();
    when(rechnungen.findAlle())
        .thenReturn(
            List.of(
                Rechnungsdoppel.gestellt(
                    1L,
                    Rechnungsdoppel.ANGEBOT,
                    "R26-0001",
                    List.of(Rechnungsdoppel.beratung("80.00")),
                    LETZTER_SEPTEMBER)));
    when(nachtraege.findAlle())
        .thenReturn(
            List.of(
                Rechnungsdoppel.nachgetragen(
                        1L, "AR-1", ERSTER_SEPTEMBER, "1000.00", "1190.01", null)
                    .mitZustand(Rechnungszustand.BEZAHLT, Rechnungsdoppel.ANGELEGT),
                Rechnungsdoppel.nachgetragen(
                    2L, "AR-2", ERSTER_OKTOBER, "500.00", "595.00", null)));

    // When
    final Gestellte gestellte = auskunft.gestellte();

    // Then — im September 8.000,00 + 1.000,00 netto, 8.560,00 + 1.190,01 brutto wie erfasst, zwei
    // Rechnungen; der Nachtrag vom Oktober steht im Oktober. Offen ist im September allein die
    // geschriebene Rechnung — der Nachtrag ist bezahlt. Die Mengen bleiben die der geschriebenen.
    assertThat(gestellte.jeMonat())
        .containsOnly(
            Map.entry(
                SEPTEMBER,
                new Monatsabrechnung(
                    new BigDecimal("9000.00"),
                    new BigDecimal("9750.01"),
                    2,
                    new BigDecimal("8000.00"),
                    1)),
            Map.entry(
                OKTOBER,
                new Monatsabrechnung(
                    new BigDecimal("500.00"),
                    new BigDecimal("595.00"),
                    1,
                    new BigDecimal("500.00"),
                    1)));
    assertThat(gestellte.mengenJePosition())
        .containsOnly(
            Map.entry(Long.valueOf(Rechnungsdoppel.BERATUNG_ID), new BigDecimal("80.00")));
    verify(nachtraege, times(1)).findAlle();
  }

  @Test
  void gestellte_withOnlyANachgetragene_thenItsMonatIsTheOnlyKey() {
    // Given — nur eine nachgetragene Rechnung, und die im Oktober.
    gegebenerAktuellerSatz("19.00");
    gegebenesAngebotMitFirma();
    when(rechnungen.findAlle()).thenReturn(List.of());
    when(nachtraege.findAlle())
        .thenReturn(
            List.of(
                Rechnungsdoppel.nachgetragen(
                    2L, "AR-2", ERSTER_OKTOBER, "500.00", "595.00", null)));

    // When
    final Gestellte gestellte = auskunft.gestellte();

    // Then
    assertThat(gestellte.jeMonat())
        .containsOnly(
            Map.entry(
                OKTOBER,
                new Monatsabrechnung(
                    new BigDecimal("500.00"),
                    new BigDecimal("595.00"),
                    1,
                    new BigDecimal("500.00"),
                    1)));
    assertThat(gestellte.mengenJePosition()).isEmpty();
  }

  @Test
  void gestellte_withAnOffeneRechnung_thenItStandsAsALineWithItsEckdaten() {
    // Given — eine gestellte, nicht bezahlte Rechnung ueber 360,00 € netto (Issue #285).
    gegebenerAktuellerSatz("19.00");
    gegebenesAngebotMitFirma();
    when(rechnungen.findAlle())
        .thenReturn(
            List.of(
                Rechnungsdoppel.gestellt(
                    7L,
                    Rechnungsdoppel.ANGEBOT,
                    "R26-0007",
                    List.of(Rechnungsdoppel.beratung("100.00", new BigDecimal("3.60"))),
                    ERSTER_OKTOBER)));

    // When
    final List<OffeneRechnung> offene = auskunft.gestellte().offene();

    // Then — Art, Kennung, Nummer, Firmenname, Rechnungsdatum und Netto; der Name kommt ueber das
    // Angebot, denn an der geschriebenen Rechnung haengt keine Firma.
    assertThat(offene)
        .containsExactly(
            new OffeneRechnung(
                false,
                7L,
                "R26-0007",
                Rechnungsdoppel.FIRMENNAME,
                ERSTER_OKTOBER,
                new BigDecimal("360.00")));
  }

  @Test
  void gestellte_withABezahlteOrAbgeschriebeneOrEntwurf_thenNoneOfThemIsOffen() {
    // Given — bezahlt, abgeschrieben und Entwurf: keine davon wartet auf Geld (Issue #285).
    gegebenerAktuellerSatz("19.00");
    when(rechnungen.findAlle())
        .thenReturn(
            List.of(
                Rechnungsdoppel.gestellt(
                        1L,
                        Rechnungsdoppel.ANGEBOT,
                        "R26-0001",
                        List.of(Rechnungsdoppel.beratung("80.00")),
                        ERSTER_OKTOBER)
                    .mitZustand(Rechnungszustand.BEZAHLT, Rechnungsdoppel.ANGELEGT),
                Rechnungsdoppel.gestellt(
                        2L,
                        Rechnungsdoppel.ANGEBOT,
                        "R26-0002",
                        List.of(Rechnungsdoppel.beratung("80.00")),
                        ERSTER_OKTOBER)
                    .mitZustand(Rechnungszustand.ABGESCHRIEBEN, Rechnungsdoppel.ANGELEGT),
                Rechnungsdoppel.entwurf(3L, List.of(Rechnungsdoppel.beratung("50.00")))));

    // When
    final Gestellte gestellte = auskunft.gestellte();

    // Then — die Liste bleibt leer, und nach Angeboten und Firmen wird gar nicht gefragt.
    assertThat(gestellte.offene()).isEmpty();
    verifyNoInteractions(angebote, firmen);
  }

  @Test
  void gestellte_withAnOffeneNachgetrageneRechnung_thenItStandsThereAsNachgetragen() {
    // Given — ein gestellter und ein bezahlter Nachtrag (Issue #285).
    gegebenerAktuellerSatz("19.00");
    gegebenesAngebotMitFirma();
    when(rechnungen.findAlle()).thenReturn(List.of());
    when(nachtraege.findAlle())
        .thenReturn(
            List.of(
                Rechnungsdoppel.nachgetragen(3L, "AR-1", ERSTER_OKTOBER, "360.00", "428.40", null),
                Rechnungsdoppel.nachgetragen(4L, "AR-2", ERSTER_OKTOBER, "500.00", "595.00", null)
                    .mitZustand(Rechnungszustand.BEZAHLT, Rechnungsdoppel.ANGELEGT)));

    // When
    final List<OffeneRechnung> offene = auskunft.gestellte().offene();

    // Then — nur der gestellte, als nachgetragen gekennzeichnet und mit dem Namen seiner Firma;
    // die haengt bei ihm an der Rechnung selbst und nicht an einem Angebot.
    assertThat(offene)
        .containsExactly(
            new OffeneRechnung(
                true,
                3L,
                "AR-1",
                Rechnungsdoppel.FIRMENNAME,
                ERSTER_OKTOBER,
                new BigDecimal("360.00")));
  }

  @Test
  void gestellte_withOffeneOfBothArten_thenTheOldestStandsFirstAndTheNummerBreaksTheTie() {
    // Given — vier offene Rechnungen: zwei am 1. September, eine am 30. September, eine im Oktober.
    gegebenerAktuellerSatz("19.00");
    gegebenesAngebotMitFirma();
    when(rechnungen.findAlle())
        .thenReturn(
            List.of(
                Rechnungsdoppel.gestellt(
                    1L,
                    Rechnungsdoppel.ANGEBOT,
                    "R26-0009",
                    List.of(Rechnungsdoppel.beratung("1.00")),
                    ERSTER_SEPTEMBER),
                Rechnungsdoppel.gestellt(
                    2L,
                    Rechnungsdoppel.ANGEBOT,
                    "R26-0002",
                    List.of(Rechnungsdoppel.beratung("1.00")),
                    ERSTER_OKTOBER)));
    when(nachtraege.findAlle())
        .thenReturn(
            List.of(
                Rechnungsdoppel.nachgetragen(
                    1L, "AR-5", LETZTER_SEPTEMBER, "100.00", "119.00", null),
                Rechnungsdoppel.nachgetragen(
                    2L, "AR-1", ERSTER_SEPTEMBER, "100.00", "119.00", null)));

    // When
    final List<OffeneRechnung> offene = auskunft.gestellte().offene();

    // Then — aeltestes Datum zuerst; am 1. September entscheidet die Nummer, nicht die Art und
    // nicht die Kennung (AR-1 vor R26-0009). Danach der 30. September, dann der Oktober.
    assertThat(offene)
        .extracting(OffeneRechnung::nummer)
        .containsExactly("AR-1", "R26-0009", "AR-5", "R26-0002");
  }

  @Test
  void gestellte_withoutAnyOffene_thenNeitherAngeboteNorFirmenAreAsked() {
    // Given — gar keine Rechnung: zwei Abfragen fuer eine leere Liste zahlte jeder Aufruf der
    // Startseite mit (Issue #285).
    gegebenerAktuellerSatz("19.00");
    when(rechnungen.findAlle()).thenReturn(List.of());

    // When
    final Gestellte gestellte = auskunft.gestellte();

    // Then
    assertThat(gestellte.offene()).isEmpty();
    verifyNoInteractions(angebote, firmen);
  }

  @Test
  void gestellte_whenTheAngebotOfAnOffeneRechnungIsMissing_thenAngebotNichtGefunden() {
    // Given — die offene Rechnung verweist auf ein Angebot, das es nicht gibt. Angebote werden
    // nicht geloescht; das waere ein Widerspruch im Bestand und keine Zeile ohne Firma.
    gegebenerAktuellerSatz("19.00");
    when(angebote.findAlle(Optional.empty())).thenReturn(List.of());
    when(rechnungen.findAlle())
        .thenReturn(
            List.of(
                Rechnungsdoppel.gestellt(
                    1L,
                    Rechnungsdoppel.ANGEBOT,
                    "R26-0001",
                    List.of(Rechnungsdoppel.beratung("80.00")),
                    ERSTER_OKTOBER)));

    // When / Then
    assertThatThrownBy(() -> auskunft.gestellte()).isInstanceOf(AngebotNichtGefunden.class);
  }

  @Test
  void gestellte_whenTheFirmaOfAnOffeneRechnungIsMissing_thenFirmaNichtGefunden() {
    // Given — das Angebot steht da, seine Firma nicht. Firmen werden ebenso nicht geloescht.
    gegebenerAktuellerSatz("19.00");
    when(angebote.findAlle(Optional.empty())).thenReturn(List.of(Rechnungsdoppel.angebot()));
    when(firmen.findAllById(Set.of(Long.valueOf(Rechnungsdoppel.FIRMA)))).thenReturn(List.of());
    when(rechnungen.findAlle())
        .thenReturn(
            List.of(
                Rechnungsdoppel.gestellt(
                    1L,
                    Rechnungsdoppel.ANGEBOT,
                    "R26-0001",
                    List.of(Rechnungsdoppel.beratung("80.00")),
                    ERSTER_OKTOBER)));

    // When / Then
    assertThatThrownBy(() -> auskunft.gestellte()).isInstanceOf(FirmaNichtGefunden.class);
  }

  @Test
  void gestellte_whenTheFirmaOfAnOffeneNachgetrageneRechnungIsMissing_thenFirmaNichtGefunden() {
    // Given — ein offener Nachtrag ohne seine Firma; bei ihm haengt sie an der Rechnung selbst.
    gegebenerAktuellerSatz("19.00");
    when(angebote.findAlle(Optional.empty())).thenReturn(List.of());
    when(firmen.findAllById(Set.of(Long.valueOf(Rechnungsdoppel.FIRMA)))).thenReturn(List.of());
    when(rechnungen.findAlle()).thenReturn(List.of());
    when(nachtraege.findAlle())
        .thenReturn(
            List.of(
                Rechnungsdoppel.nachgetragen(
                    1L, "AR-1", ERSTER_OKTOBER, "360.00", "428.40", null)));

    // When / Then
    assertThatThrownBy(() -> auskunft.gestellte()).isInstanceOf(FirmaNichtGefunden.class);
  }

  @Test
  void gestellte_withTwoOffeneRechnungenOfTheSameFirma_thenItIsAskedForOnlyOnce() {
    // Given — zwei offene Rechnungen derselben Firma: Je Zeile nachzufragen waere die
    // Abfrage-Lawine, die diese Tuer gerade vermeidet (Issue #285).
    gegebenerAktuellerSatz("19.00");
    gegebenesAngebotMitFirma();
    when(rechnungen.findAlle())
        .thenReturn(
            List.of(
                Rechnungsdoppel.gestellt(
                    1L,
                    Rechnungsdoppel.ANGEBOT,
                    "R26-0001",
                    List.of(Rechnungsdoppel.beratung("1.00")),
                    ERSTER_OKTOBER),
                Rechnungsdoppel.gestellt(
                    2L,
                    Rechnungsdoppel.ANGEBOT,
                    "R26-0002",
                    List.of(Rechnungsdoppel.beratung("1.00")),
                    ERSTER_OKTOBER)));

    // When
    final List<OffeneRechnung> offene = auskunft.gestellte().offene();

    // Then — beide Zeilen tragen den Namen, gefragt wurde einmal.
    assertThat(offene)
        .extracting(OffeneRechnung::firmaName)
        .containsOnly(Rechnungsdoppel.FIRMENNAME);
    verify(angebote, times(1)).findAlle(Optional.empty());
    verify(firmen, times(1)).findAllById(Set.of(Long.valueOf(Rechnungsdoppel.FIRMA)));
  }
}
