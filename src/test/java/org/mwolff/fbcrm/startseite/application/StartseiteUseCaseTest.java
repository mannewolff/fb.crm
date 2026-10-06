package org.mwolff.fbcrm.startseite.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.angebot.application.AngebotMitFirma;
import org.mwolff.fbcrm.angebot.application.AngeboteUebersichtUseCase;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.Angebotsposition;
import org.mwolff.fbcrm.angebot.domain.Angebotsstatus;
import org.mwolff.fbcrm.arbeitszeit.application.Arbeitszeitauskunft;
import org.mwolff.fbcrm.common.Abrechnungsmodus;
import org.mwolff.fbcrm.common.Einheit;
import org.mwolff.fbcrm.rechnung.application.Gestellte;
import org.mwolff.fbcrm.rechnung.application.Monatsabrechnung;
import org.mwolff.fbcrm.rechnung.application.Rechnungsauskunft;

/**
 * Die drei Kennzahlen der Startseite (#206, Kriterien 3 bis 8; Plan #208).
 *
 * <p>Gegenstand sind die Rechenregeln und nichts sonst: welche Angebote „in Arbeit" sind, was aus
 * erfasster Zeit noch abzurechnen ist, was im gewaehlten Zeitraum erfasst wurde, welche Zeitraeume
 * zur Wahl stehen und welcher ueberhaupt gilt (#273; Plan #274). Die vier Auskuenfte, aus denen das
 * entsteht, sind gemockt — sie gehoeren fremden Modulen und sind dort geprueft ({@code
 * AngeboteUebersichtUseCaseTest}, {@code ArbeitszeitauskunftTest}, {@code RechnungsauskunftTest}).
 *
 * <p>Die Verdichtung der Rechnungen zu Summe und Monatszeilen ({@code Abrechnungsblick}) wird hier
 * mitgeprueft und hat keine eigene Testklasse: Sie ist paket-privat und keine eigene Zusage (Plan
 * #274, E16).
 *
 * <p>Die Uhr steht fest auf dem 30. September 2026 um 22:30 UTC. In der Geschaeftszone ist das
 * schon der 1. Oktober — der laufende Monat ist darum Oktober und nicht September. Am Nullmeridian
 * gelesen zeigte die Startseite den Vormonat.
 *
 * <p><b>Interne Angebote werden an drei Stellen belegt</b> (#207, Kriterium 9): Sie fehlen in den
 * beiden Betraegen der Kennzahl 2, in „Angebote in Arbeit" und in „Abgerechnet". Nur die erste
 * Stelle hat im Code eine eigene Bedingung — die anderen beiden leisten die Statusmenge von {@code
 * inArbeit} und die Rechnungsauskunft, und genau das sagen ihre Testnamen (Plan #218, E17).
 *
 * <p><b>Nicht buchbar heisst 0,00</b> (Plan #208, E12): Eine Position, auf die einmal gebucht wurde
 * und die inzwischen zum Festpreis oder in Personentagen abrechnet, traegt zu keinem der beiden
 * Betraege bei. Geprueft wird das an zwei Positionen, die je nur eine Haelfte der Regel verletzen.
 */
@ExtendWith(MockitoExtension.class)
class StartseiteUseCaseTest {

  /** 30. September 2026, 22:30 UTC — in Europe/Berlin bereits der 1. Oktober. */
  private static final Instant JETZT = Instant.parse("2026-09-30T22:30:00Z");

  /** Der laufende Monat an dieser Uhr, in der Geschaeftszone gelesen. */
  private static final YearMonth OKTOBER = YearMonth.of(2026, 10);

  /** Ein Monat des laufenden Jahres vor dem laufenden — waehlbar, sobald etwas in ihm geschah. */
  private static final YearMonth AUGUST = YearMonth.of(2026, 8);

  /** Ein frueherer Monat des laufenden Jahres, fuer die Jahressumme. */
  private static final YearMonth MAERZ = YearMonth.of(2026, 3);

  /** Ein Monat nach dem laufenden, im selben Jahr (Plan #274, E9). */
  private static final YearMonth DEZEMBER = YearMonth.of(2026, 12);

  /** Ein Monat des letzten Jahres. */
  private static final YearMonth MAI_LETZTES_JAHR = YearMonth.of(2025, 5);

  /** Das laufende Jahr an dieser Uhr. */
  private static final Year DIESES_JAHR = Year.of(2026);

  /** Das letzte Jahr an dieser Uhr. */
  private static final Year LETZTES_JAHR = Year.of(2025);

  /**
   * Der erste Tag des letzten Jahres — die untere Grenze der Frage nach Monaten mit Arbeitszeit.
   */
  private static final LocalDate ANFANG_LETZTES_JAHR = LocalDate.of(2025, 1, 1);

  /** Der letzte Tag des laufenden Jahres — ihre obere Grenze. */
  private static final LocalDate ENDE_DIESES_JAHR = LocalDate.of(2026, 12, 31);

  private static final LocalDate ANGEBOTSDATUM = LocalDate.of(2026, 9, 20);
  private static final Instant ANGELEGT = Instant.parse("2026-09-20T08:00:00Z");

  private static final long FIRMA = 5L;
  private static final String ADLER = "Adler AG";
  private static final String BUCHE = "Buche GmbH";

  /** 20 Stunden zu 100,00 € — die angebotene Menge des Beispiels aus #193. */
  private static final long BERATUNG_ID = 101L;

  /** 10 Stunden zu 130,00 € — die zweite buchbare Position desselben Angebots. */
  private static final long WARTUNG_ID = 102L;

  /** Rechnet inzwischen zum Festpreis, zaehlt aber weiter in Stunden. */
  private static final long GEWANDELT_ID = 103L;

  /** Rechnet nach Aufwand, aber in Personentagen. */
  private static final long PERSONENTAGE_ID = 104L;

  /** Zwei Viertelstunden zu 99,90 € — der Fall, an dem die Rundungsfolge sichtbar wird. */
  private static final long VIERTEL_A_ID = 105L;

  private static final long VIERTEL_B_ID = 106L;

  /** Die Position des zweiten beitragenden Angebots: 5 Stunden zu 200,00 €. */
  private static final long ANALYSE_ID = 107L;

  /** Eine Position eines internen Angebots: nach Aufwand in Stunden zu 100,00 € (Kriterium 8). */
  private static final long EIGENE_ID = 201L;

  /** Eine zweite Position desselben internen Angebots, die zum Festpreis abrechnet. */
  private static final long EIGENE_FESTPREIS_ID = 202L;

  private static final Angebotsposition BERATUNG = stunden(BERATUNG_ID, "20.00", "100.00");
  private static final Angebotsposition WARTUNG = stunden(WARTUNG_ID, "10.00", "130.00");
  private static final Angebotsposition ANALYSE = stunden(ANALYSE_ID, "5.00", "200.00");
  private static final Angebotsposition VIERTEL_A = stunden(VIERTEL_A_ID, "10.00", "99.90");
  private static final Angebotsposition VIERTEL_B = stunden(VIERTEL_B_ID, "10.00", "99.90");
  private static final Angebotsposition EIGENE = stunden(EIGENE_ID, "40.00", "100.00");

  private static final Angebotsposition EIGENE_FESTPREIS =
      new Angebotsposition(
          Long.valueOf(EIGENE_FESTPREIS_ID),
          "Aufraeumen",
          Abrechnungsmodus.FESTPREIS,
          new BigDecimal("5.00"),
          Einheit.STUNDE,
          BigDecimal.ZERO);

  private static final Angebotsposition GEWANDELT =
      new Angebotsposition(
          Long.valueOf(GEWANDELT_ID),
          "Konzeption",
          Abrechnungsmodus.FESTPREIS,
          new BigDecimal("10.00"),
          Einheit.STUNDE,
          new BigDecimal("90.00"));

  private static final Angebotsposition PERSONENTAGE =
      new Angebotsposition(
          Long.valueOf(PERSONENTAGE_ID),
          "Schulung",
          Abrechnungsmodus.AUFWAND,
          new BigDecimal("10.00"),
          Einheit.PERSONENTAG,
          new BigDecimal("800.00"));

  /** Was im Oktober gestellt wurde — die Zahlen der Kennzahl 3 kommen fertig aus der Auskunft. */
  private static final Monatsabrechnung OKTOBER_GESTELLT =
      new Monatsabrechnung(new BigDecimal("1000.00"), new BigDecimal("1070.00"), 1);

  /** Dieselbe Angabe fuer den August — ein anderer Monat, andere Zahlen. */
  private static final Monatsabrechnung AUGUST_GESTELLT =
      new Monatsabrechnung(new BigDecimal("2000.00"), new BigDecimal("2140.00"), 2);

  /** Was ein Monat ohne gestellte Rechnung ergibt — er fehlt in der Karte der Auskunft. */
  private static final Monatsabrechnung NICHTS_GESTELLT =
      new Monatsabrechnung(new BigDecimal("0.00"), new BigDecimal("0.00"), 0);

  /** Was im Maerz gestellt wurde — Betraege mit Cent, damit die Jahressumme sie trifft. */
  private static final Monatsabrechnung MAERZ_GESTELLT =
      new Monatsabrechnung(new BigDecimal("300.10"), new BigDecimal("357.12"), 1);

  /** Rechnungen in drei Monaten des laufenden Jahres und einer des letzten, in loser Folge. */
  private static final Map<YearMonth, Monatsabrechnung> DREI_MONATE_UND_EIN_ALTER =
      Map.of(
          OKTOBER, OKTOBER_GESTELLT,
          MAERZ, MAERZ_GESTELLT,
          AUGUST, AUGUST_GESTELLT,
          MAI_LETZTES_JAHR,
              new Monatsabrechnung(new BigDecimal("999.99"), new BigDecimal("999.99"), 9));

  @Mock private AngeboteUebersichtUseCase uebersicht;
  @Mock private Arbeitszeitauskunft arbeitszeit;
  @Mock private Rechnungsauskunft rechnungen;

  private StartseiteUseCase useCase;

  @BeforeEach
  void baueDenAnwendungsfall() {
    useCase =
        new StartseiteUseCase(
            uebersicht, arbeitszeit, rechnungen, Clock.fixed(JETZT, ZoneOffset.UTC));
  }

  private static Angebotsposition stunden(
      final long id, final String menge, final String einzelpreis) {
    return new Angebotsposition(
        Long.valueOf(id),
        "Beratung",
        Abrechnungsmodus.AUFWAND,
        new BigDecimal(menge),
        Einheit.STUNDE,
        new BigDecimal(einzelpreis));
  }

  private static AngebotMitFirma angebot(
      final long id, final Angebotsstatus status, final List<Angebotsposition> positionen) {
    return angebot(id, status, positionen, ADLER);
  }

  private static AngebotMitFirma angebot(
      final long id,
      final Angebotsstatus status,
      final List<Angebotsposition> positionen,
      final String firmaName) {
    return new AngebotMitFirma(
        new Angebot(
            Long.valueOf(id),
            FIRMA,
            null,
            status.intern(),
            status,
            ANGEBOTSDATUM,
            "Neugestaltung der Website",
            positionen,
            ANGELEGT,
            ANGELEGT),
        firmaName);
  }

  /** Stellt die vier Auskuenfte fuer den laufenden Monat Oktober bereit. */
  private void gegebenImOktober(
      final List<AngebotMitFirma> angebote,
      final Map<Long, BigDecimal> angefallen,
      final Map<Long, BigDecimal> imMonat,
      final Map<Long, BigDecimal> gestellteMengen) {
    when(uebersicht.angebote(Optional.empty())).thenReturn(angebote);
    when(arbeitszeit.alleAngefallen()).thenReturn(angefallen);
    when(arbeitszeit.alleImZeitraum(OKTOBER.atDay(1), OKTOBER.atEndOfMonth())).thenReturn(imMonat);
    when(rechnungen.gestellte())
        .thenReturn(new Gestellte(Map.of(OKTOBER, OKTOBER_GESTELLT), gestellteMengen));
  }

  @Test
  void stand_thenInArbeitHoldsOnlyBestelltAndErledigtInTheOrderOfTheUebersicht() {
    // Given — alle fuenf Status, in der Reihenfolge „neueste zuerst" der Uebersicht.
    gegebenImOktober(
        List.of(
            angebot(1L, Angebotsstatus.ABGERECHNET, List.of()),
            angebot(2L, Angebotsstatus.ERLEDIGT, List.of()),
            angebot(3L, Angebotsstatus.ANGELEGT, List.of()),
            angebot(4L, Angebotsstatus.BESTELLT, List.of()),
            angebot(5L, Angebotsstatus.ABGEGEBEN, List.of())),
        Map.of(),
        Map.of(),
        Map.of());

    // When
    final Startseitenstand stand = useCase.stand(Optional.empty());

    // Then
    assertThat(stand.inArbeit())
        .extracting(zeile -> zeile.angebot().requireId())
        .containsExactly(2L, 4L);
  }

  @Test
  void stand_thenNichtAbgerechnetIsErfasstCappedMinusGestellt() {
    // Given — 20 Stunden angeboten, 22 erfasst, 5 gestellt: min(22, 20) − 5 = 15.
    gegebenImOktober(
        List.of(angebot(1L, Angebotsstatus.BESTELLT, List.of(BERATUNG))),
        Map.of(BERATUNG_ID, new BigDecimal("22.00")),
        Map.of(),
        Map.of(BERATUNG_ID, new BigDecimal("5.00")));

    // When
    final Startseitenstand stand = useCase.stand(Optional.empty());

    // Then
    assertThat(stand.nichtAbgerechnet().betrag()).isEqualByComparingTo("1500.00");
  }

  @Test
  void stand_withTheExampleFromIssue193_thenTheAngebotContributesNothing() {
    // Given — 20 angeboten, 22 erfasst, 20 auf einer gestellten Rechnung (#206, Kriterium 5).
    gegebenImOktober(
        List.of(angebot(1L, Angebotsstatus.ABGERECHNET, List.of(BERATUNG))),
        Map.of(BERATUNG_ID, new BigDecimal("22.00")),
        Map.of(),
        Map.of(BERATUNG_ID, new BigDecimal("20.00")));

    // When
    final Startseitenstand stand = useCase.stand(Optional.empty());

    // Then
    assertThat(stand.nichtAbgerechnet().betrag()).isEqualByComparingTo("0.00");
    assertThat(stand.nichtAbgerechnet().anteile()).isEmpty();
  }

  @Test
  void stand_withMoreGestelltThanErfasst_thenTheBetragStaysAtZero() {
    // Given — 10 erfasst, 15 gestellt: der Abzug ergaebe −5, die Kennzahl bleibt bei 0.
    gegebenImOktober(
        List.of(angebot(1L, Angebotsstatus.BESTELLT, List.of(BERATUNG))),
        Map.of(BERATUNG_ID, new BigDecimal("10.00")),
        Map.of(),
        Map.of(BERATUNG_ID, new BigDecimal("15.00")));

    // When
    final Startseitenstand stand = useCase.stand(Optional.empty());

    // Then
    assertThat(stand.nichtAbgerechnet().betrag()).isEqualByComparingTo("0.00");
  }

  @Test
  void stand_thenOnlyGestellteMengenLowerTheBetrag() {
    // Given — zweimal dieselbe Lage: erst ohne gestellte Menge (ein Entwurf ueber die vollen
    // 20 Stunden steht in der Auskunft nicht), dann mit der gestellten Rechnung darueber.
    when(uebersicht.angebote(Optional.empty()))
        .thenReturn(List.of(angebot(1L, Angebotsstatus.BESTELLT, List.of(BERATUNG))));
    when(arbeitszeit.alleAngefallen()).thenReturn(Map.of(BERATUNG_ID, new BigDecimal("20.00")));
    when(arbeitszeit.alleImZeitraum(OKTOBER.atDay(1), OKTOBER.atEndOfMonth())).thenReturn(Map.of());
    when(rechnungen.gestellte())
        .thenReturn(
            new Gestellte(Map.of(OKTOBER, OKTOBER_GESTELLT), Map.of()),
            new Gestellte(
                Map.of(OKTOBER, OKTOBER_GESTELLT), Map.of(BERATUNG_ID, new BigDecimal("20.00"))));

    // When
    final BigDecimal mitEntwurf = useCase.stand(Optional.empty()).nichtAbgerechnet().betrag();
    final BigDecimal nachDemStellen = useCase.stand(Optional.empty()).nichtAbgerechnet().betrag();

    // Then
    assertThat(mitEntwurf).isEqualByComparingTo("2000.00");
    assertThat(nachDemStellen).isEqualByComparingTo("0.00");
  }

  @Test
  void stand_withHoursOnPositionsThatNoLongerCarryThem_thenBothBetraegeAreZero() {
    // Given — eine Position zum Festpreis und eine in Personentagen, auf beide wurde gebucht.
    gegebenImOktober(
        List.of(angebot(1L, Angebotsstatus.BESTELLT, List.of(GEWANDELT, PERSONENTAGE))),
        Map.of(GEWANDELT_ID, new BigDecimal("8.00"), PERSONENTAGE_ID, new BigDecimal("8.00")),
        Map.of(GEWANDELT_ID, new BigDecimal("8.00"), PERSONENTAGE_ID, new BigDecimal("8.00")),
        Map.of());

    // When
    final Startseitenstand stand = useCase.stand(Optional.empty());

    // Then
    assertThat(stand.nichtAbgerechnet().betrag()).isEqualByComparingTo("0.00");
    assertThat(stand.nichtAbgerechnet().erfasstImZeitraum()).isEqualByComparingTo("0.00");
    assertThat(stand.nichtAbgerechnet().anteile()).isEmpty();
  }

  @Test
  void stand_thenTheMonatszeileRoundsPerPositionAndAddsAfterwards() {
    // Given — zwei Viertelstunden zu 99,90 €: je Position 24,975 → 24,98, zusammen 49,96.
    // Erst zu summieren ergaebe 49,95 — ein Cent weniger (Plan #208, E23).
    gegebenImOktober(
        List.of(angebot(1L, Angebotsstatus.BESTELLT, List.of(VIERTEL_A, VIERTEL_B))),
        Map.of(),
        Map.of(VIERTEL_A_ID, new BigDecimal("0.25"), VIERTEL_B_ID, new BigDecimal("0.25")),
        Map.of());

    // When
    final Startseitenstand stand = useCase.stand(Optional.empty());

    // Then
    assertThat(stand.nichtAbgerechnet().erfasstImZeitraum()).isEqualByComparingTo("49.96");
  }

  @Test
  void stand_thenTheMonatszeileIgnoresCapAndAbzug() {
    // Given — 22 Stunden im Monat auf 20 angebotene, 20 davon schon gestellt: die Monatszeile
    // zeigt trotzdem alle 22 (#206, Kriterium 6).
    gegebenImOktober(
        List.of(angebot(1L, Angebotsstatus.BESTELLT, List.of(BERATUNG))),
        Map.of(BERATUNG_ID, new BigDecimal("22.00")),
        Map.of(BERATUNG_ID, new BigDecimal("22.00")),
        Map.of(BERATUNG_ID, new BigDecimal("20.00")));

    // When
    final Startseitenstand stand = useCase.stand(Optional.empty());

    // Then
    assertThat(stand.nichtAbgerechnet().erfasstImZeitraum()).isEqualByComparingTo("2200.00");
  }

  @Test
  void stand_thenTheAnteileCarryFirmaAngebotAndBetrag() {
    // Given — zwei beitragende Angebote, das erste mit zwei Positionen, und ein drittes ohne
    // erfasste Zeit. Der Status spielt keine Rolle (#206, Kriterium 5).
    gegebenImOktober(
        List.of(
            angebot(1L, Angebotsstatus.ERLEDIGT, List.of(BERATUNG, WARTUNG)),
            angebot(2L, Angebotsstatus.ANGELEGT, List.of(ANALYSE), BUCHE),
            angebot(3L, Angebotsstatus.BESTELLT, List.of(GEWANDELT))),
        Map.of(
            BERATUNG_ID,
            new BigDecimal("10.00"),
            WARTUNG_ID,
            new BigDecimal("2.00"),
            ANALYSE_ID,
            new BigDecimal("3.00")),
        Map.of(),
        Map.of());

    // When
    final Startseitenstand stand = useCase.stand(Optional.empty());

    // Then — 1.000,00 € + 260,00 € am ersten, 600,00 € am zweiten, das dritte traegt nichts.
    assertThat(stand.nichtAbgerechnet().anteile())
        .extracting(
            anteil -> anteil.angebot().angebot().requireId(),
            anteil -> anteil.angebot().firmaName(),
            Angebotsanteil::betrag)
        .containsExactly(
            tuple(1L, ADLER, new BigDecimal("1260.00")),
            tuple(2L, BUCHE, new BigDecimal("600.00")));
    assertThat(stand.nichtAbgerechnet().betrag()).isEqualByComparingTo("1860.00");
  }

  /** Stellt die vier Auskuenfte fuer Oktober und August auf demselben Bestand bereit. */
  private void gegebenZweiMonate() {
    when(uebersicht.angebote(Optional.empty()))
        .thenReturn(List.of(angebot(1L, Angebotsstatus.BESTELLT, List.of(BERATUNG))));
    when(arbeitszeit.alleAngefallen()).thenReturn(Map.of(BERATUNG_ID, new BigDecimal("12.00")));
    when(arbeitszeit.alleImZeitraum(OKTOBER.atDay(1), OKTOBER.atEndOfMonth()))
        .thenReturn(Map.of(BERATUNG_ID, new BigDecimal("2.00")));
    when(arbeitszeit.alleImZeitraum(AUGUST.atDay(1), AUGUST.atEndOfMonth()))
        .thenReturn(Map.of(BERATUNG_ID, new BigDecimal("5.00")));
    final Map<Long, BigDecimal> gestellteMengen = Map.of(BERATUNG_ID, new BigDecimal("5.00"));
    when(rechnungen.gestellte())
        .thenReturn(
            new Gestellte(
                Map.of(OKTOBER, OKTOBER_GESTELLT, AUGUST, AUGUST_GESTELLT), gestellteMengen));
  }

  @Test
  void stand_withAnotherMonat_thenAbgerechnetAndTheMonatszeileFollowIt() {
    // Given
    gegebenZweiMonate();

    // When
    final Startseitenstand oktober = useCase.stand(Optional.empty());
    final Startseitenstand august = useCase.stand(Optional.of(new Zeitraum.Monat(AUGUST)));

    // Then
    assertThat(oktober.abgerechnet().summe().netto()).isEqualByComparingTo("1000.00");
    assertThat(august.abgerechnet().summe().netto()).isEqualByComparingTo("2000.00");
    assertThat(oktober.nichtAbgerechnet().erfasstImZeitraum()).isEqualByComparingTo("200.00");
    assertThat(august.nichtAbgerechnet().erfasstImZeitraum()).isEqualByComparingTo("500.00");
  }

  @Test
  void stand_withAnotherMonat_thenInArbeitAndTheHauptbetragStay() {
    // Given
    gegebenZweiMonate();

    // When
    final Startseitenstand oktober = useCase.stand(Optional.empty());
    final Startseitenstand august = useCase.stand(Optional.of(new Zeitraum.Monat(AUGUST)));

    // Then — min(12, 20) − 5 = 7 Stunden zu 100,00 €, in beiden Staenden (#206, Kriterium 3).
    assertThat(august.inArbeit()).isEqualTo(oktober.inArbeit());
    assertThat(august.nichtAbgerechnet().betrag())
        .isEqualByComparingTo(oktober.nichtAbgerechnet().betrag())
        .isEqualByComparingTo("700.00");
  }

  @Test
  void stand_withASelectableMonat_thenThatMonatAnswers() {
    // Given — gefragt wird allein der August; er traegt eine Rechnung und steht darum zur Wahl.
    when(uebersicht.angebote(Optional.empty())).thenReturn(List.of());
    when(arbeitszeit.alleAngefallen()).thenReturn(Map.of());
    when(arbeitszeit.alleImZeitraum(AUGUST.atDay(1), AUGUST.atEndOfMonth())).thenReturn(Map.of());
    when(rechnungen.gestellte())
        .thenReturn(new Gestellte(Map.of(AUGUST, AUGUST_GESTELLT), Map.of()));

    // When
    final Startseitenstand stand = useCase.stand(Optional.of(new Zeitraum.Monat(AUGUST)));

    // Then
    assertThat(stand.zeitraum()).isEqualTo(new Zeitraum.Monat(AUGUST));
  }

  @Test
  void stand_withoutAZeitraum_thenTheLaufenderMonatOfTheClockAnswers() {
    // Given
    gegebenImOktober(List.of(), Map.of(), Map.of(), Map.of());

    // When
    final Startseitenstand stand = useCase.stand(Optional.empty());

    // Then — 22:30 UTC am 30. September ist in der Geschaeftszone schon Oktober.
    assertThat(stand.zeitraum()).isEqualTo(new Zeitraum.Monat(OKTOBER));
  }

  /** Stellt einen leeren Bestand bereit, dessen einzige Vorgaenge die genannten Monate sind. */
  private void gegebenVorgaenge(
      final Map<YearMonth, Monatsabrechnung> jeMonat, final Set<YearMonth> mitArbeitszeit) {
    when(uebersicht.angebote(Optional.empty())).thenReturn(List.of());
    when(arbeitszeit.alleAngefallen()).thenReturn(Map.of());
    when(arbeitszeit.monateMitEintragImZeitraum(ANFANG_LETZTES_JAHR, ENDE_DIESES_JAHR))
        .thenReturn(mitArbeitszeit);
    when(rechnungen.gestellte()).thenReturn(new Gestellte(jeMonat, Map.of()));
  }

  @Test
  void stand_withoutAnyVorgang_thenOnlyTheLaufenderMonatAndTheLaufendesJahrAreSelectable() {
    // Given — keine Rechnung und keine Arbeitszeit (#273, Kriterien 1 und 2).
    gegebenVorgaenge(Map.of(), Set.of());
    when(arbeitszeit.alleImZeitraum(OKTOBER.atDay(1), OKTOBER.atEndOfMonth())).thenReturn(Map.of());

    // When
    final Startseitenstand stand = useCase.stand(Optional.empty());

    // Then
    assertThat(stand.waehlbar().monate()).containsExactly(OKTOBER);
    assertThat(stand.waehlbar().jahre()).containsExactly(DIESES_JAHR);
  }

  @Test
  void stand_withARechnungInTheLetztesJahr_thenThatJahrIsSelectableAndAnswers() {
    // Given — eine gestellte Rechnung im Mai des letzten Jahres (#273, Kriterium 1).
    gegebenVorgaenge(Map.of(MAI_LETZTES_JAHR, AUGUST_GESTELLT), Set.of());
    final Zeitraum.Jahr letztes = new Zeitraum.Jahr(LETZTES_JAHR);
    when(arbeitszeit.alleImZeitraum(letztes.von(), letztes.bis())).thenReturn(Map.of());

    // When
    final Startseitenstand stand = useCase.stand(Optional.of(letztes));

    // Then
    assertThat(stand.waehlbar().jahre()).containsExactly(DIESES_JAHR, LETZTES_JAHR);
    assertThat(stand.zeitraum()).isEqualTo(letztes);
    assertThat(stand.abgerechnet().summe()).isEqualTo(AUGUST_GESTELLT);
  }

  @Test
  void stand_withoutARechnungInTheLetztesJahr_thenThatJahrFallsBackToTheLaufenderMonat() {
    // Given — eine Rechnung nur im laufenden Jahr (#273, Kriterium 1).
    gegebenVorgaenge(Map.of(AUGUST, AUGUST_GESTELLT), Set.of());
    when(arbeitszeit.alleImZeitraum(OKTOBER.atDay(1), OKTOBER.atEndOfMonth())).thenReturn(Map.of());

    // When
    final Startseitenstand stand = useCase.stand(Optional.of(new Zeitraum.Jahr(LETZTES_JAHR)));

    // Then
    assertThat(stand.waehlbar().jahre()).containsExactly(DIESES_JAHR);
    assertThat(stand.zeitraum()).isEqualTo(new Zeitraum.Monat(OKTOBER));
  }

  @Test
  void stand_withOnlyArbeitszeitInAMonatOfTheLetztesJahr_thenTheMonatIsSelectableButNotTheJahr() {
    // Given — Arbeitszeit im Mai des letzten Jahres, aber keine Rechnung darin (Plan #274, E8).
    gegebenVorgaenge(Map.of(), Set.of(MAI_LETZTES_JAHR));
    when(arbeitszeit.alleImZeitraum(MAI_LETZTES_JAHR.atDay(1), MAI_LETZTES_JAHR.atEndOfMonth()))
        .thenReturn(Map.of());

    // When
    final Startseitenstand stand = useCase.stand(Optional.of(new Zeitraum.Monat(MAI_LETZTES_JAHR)));

    // Then
    assertThat(stand.zeitraum()).isEqualTo(new Zeitraum.Monat(MAI_LETZTES_JAHR));
    assertThat(stand.waehlbar().monate()).containsExactly(OKTOBER, MAI_LETZTES_JAHR);
    assertThat(stand.waehlbar().jahre()).containsExactly(DIESES_JAHR);
  }

  @Test
  void stand_withARechnungAfterTheLaufenderMonat_thenThatMonatIsSelectable() {
    // Given — eine Rechnung, datiert auf den Dezember (Plan #274, E9).
    gegebenVorgaenge(Map.of(DEZEMBER, OKTOBER_GESTELLT), Set.of());
    when(arbeitszeit.alleImZeitraum(DEZEMBER.atDay(1), DEZEMBER.atEndOfMonth()))
        .thenReturn(Map.of());

    // When
    final Startseitenstand stand = useCase.stand(Optional.of(new Zeitraum.Monat(DEZEMBER)));

    // Then
    assertThat(stand.waehlbar().monate()).containsExactly(DEZEMBER, OKTOBER);
    assertThat(stand.zeitraum()).isEqualTo(new Zeitraum.Monat(DEZEMBER));
    assertThat(stand.abgerechnet().summe()).isEqualTo(OKTOBER_GESTELLT);
  }

  @Test
  void stand_withAnOldJahr_thenTheLaufenderMonatAnswers() {
    // Given — 2024 steht nie zur Wahl, auch mit Rechnung nicht (#273, Kriterium 1).
    gegebenVorgaenge(Map.of(YearMonth.of(2024, 6), AUGUST_GESTELLT), Set.of());
    when(arbeitszeit.alleImZeitraum(OKTOBER.atDay(1), OKTOBER.atEndOfMonth())).thenReturn(Map.of());

    // When
    final Startseitenstand stand = useCase.stand(Optional.of(new Zeitraum.Jahr(Year.of(2024))));

    // Then
    assertThat(stand.zeitraum()).isEqualTo(new Zeitraum.Monat(OKTOBER));
    assertThat(stand.abgerechnet().summe()).isEqualTo(NICHTS_GESTELLT);
  }

  @Test
  void stand_withAMonatWithoutVorgang_thenTheLaufenderMonatAnswers() {
    // Given — im August geschah nichts; er steht nicht zur Wahl (#273, Kriterium 2).
    gegebenVorgaenge(Map.of(), Set.of());
    when(arbeitszeit.alleImZeitraum(OKTOBER.atDay(1), OKTOBER.atEndOfMonth())).thenReturn(Map.of());

    // When
    final Startseitenstand stand = useCase.stand(Optional.of(new Zeitraum.Monat(AUGUST)));

    // Then
    assertThat(stand.zeitraum()).isEqualTo(new Zeitraum.Monat(OKTOBER));
  }

  @Test
  void stand_withTheJahr_thenAbgerechnetSumsEveryMonatOfTheJahr() {
    // Given
    gegebenVorgaenge(DREI_MONATE_UND_EIN_ALTER, Set.of());
    final Zeitraum.Jahr jahr = new Zeitraum.Jahr(DIESES_JAHR);
    when(arbeitszeit.alleImZeitraum(jahr.von(), jahr.bis())).thenReturn(Map.of());

    // When
    final Startseitenstand stand = useCase.stand(Optional.of(jahr));

    // Then — 300,10 + 2.000,00 + 1.000,00 netto; der Mai des letzten Jahres zaehlt nicht mit.
    assertThat(stand.abgerechnet().summe())
        .isEqualTo(new Monatsabrechnung(new BigDecimal("3300.10"), new BigDecimal("3567.12"), 4));
  }

  @Test
  void stand_withTheJahr_thenTheMonatszeilenStandAscending() {
    // Given
    gegebenVorgaenge(DREI_MONATE_UND_EIN_ALTER, Set.of());
    final Zeitraum.Jahr jahr = new Zeitraum.Jahr(DIESES_JAHR);
    when(arbeitszeit.alleImZeitraum(jahr.von(), jahr.bis())).thenReturn(Map.of());

    // When
    final Startseitenstand stand = useCase.stand(Optional.of(jahr));

    // Then — nur Monate mit Rechnung, aeltester zuerst (#273, Kriterium 7; Plan #274, E11).
    assertThat(stand.abgerechnet().monate())
        .containsExactly(
            new Abrechnungsmonat(MAERZ, MAERZ_GESTELLT),
            new Abrechnungsmonat(AUGUST, AUGUST_GESTELLT),
            new Abrechnungsmonat(OKTOBER, OKTOBER_GESTELLT));
  }

  @Test
  void stand_withAMonat_thenTheMonatszeilenAreEmpty() {
    // Given — dieselben Rechnungen, aber ein Monat gewaehlt (Plan #274, E11).
    gegebenVorgaenge(DREI_MONATE_UND_EIN_ALTER, Set.of());
    when(arbeitszeit.alleImZeitraum(AUGUST.atDay(1), AUGUST.atEndOfMonth())).thenReturn(Map.of());

    // When
    final Startseitenstand stand = useCase.stand(Optional.of(new Zeitraum.Monat(AUGUST)));

    // Then
    assertThat(stand.abgerechnet().summe()).isEqualTo(AUGUST_GESTELLT);
    assertThat(stand.abgerechnet().monate()).isEmpty();
  }

  @Test
  void stand_withTheJahrAndNoRechnungAtAll_thenTheSummeIsZeroWithTwoDecimals() {
    // Given — der leere Strom (Plan #274, E16).
    gegebenVorgaenge(Map.of(), Set.of());
    final Zeitraum.Jahr jahr = new Zeitraum.Jahr(DIESES_JAHR);
    when(arbeitszeit.alleImZeitraum(jahr.von(), jahr.bis())).thenReturn(Map.of());

    // When
    final Startseitenstand stand = useCase.stand(Optional.of(jahr));

    // Then — equals auf BigDecimal prueft die Skala mit: 0.00 und nicht 0.
    assertThat(stand.abgerechnet().summe()).isEqualTo(NICHTS_GESTELLT);
    assertThat(stand.abgerechnet().summe().netto()).isEqualTo(new BigDecimal("0.00"));
    assertThat(stand.abgerechnet().monate()).isEmpty();
  }

  @Test
  void stand_withTheJahr_thenTheErfassteZeitAndTheInternenStundenOfTheJahrCount() {
    // Given — die Stunden des ganzen Jahres, an einer Kunden- und einer internen Position.
    when(uebersicht.angebote(Optional.empty()))
        .thenReturn(
            List.of(
                angebot(1L, Angebotsstatus.LAEUFT, List.of(EIGENE)),
                angebot(2L, Angebotsstatus.BESTELLT, List.of(BERATUNG))));
    when(arbeitszeit.alleAngefallen()).thenReturn(Map.of());
    when(rechnungen.gestellte()).thenReturn(new Gestellte(Map.of(), Map.of()));
    final Zeitraum.Jahr jahr = new Zeitraum.Jahr(DIESES_JAHR);
    when(arbeitszeit.alleImZeitraum(jahr.von(), jahr.bis()))
        .thenReturn(
            Map.of(EIGENE_ID, new BigDecimal("30.00"), BERATUNG_ID, new BigDecimal("7.00")));

    // When
    final Startseitenstand stand = useCase.stand(Optional.of(jahr));

    // Then — #273, Kriterium 5.
    assertThat(stand.nichtAbgerechnet().erfasstImZeitraum()).isEqualByComparingTo("700.00");
    assertThat(stand.interneStundenImZeitraum()).isEqualByComparingTo("30.00");
  }

  @Test
  void stand_withTheJahr_thenInArbeitAndTheHauptbetragStayAsWithTheMonat() {
    // Given — derselbe Bestand, einmal mit Monats-, einmal mit Jahreswahl (#273, Kriterium 6).
    when(uebersicht.angebote(Optional.empty()))
        .thenReturn(
            List.of(
                angebot(1L, Angebotsstatus.BESTELLT, List.of(BERATUNG)),
                angebot(2L, Angebotsstatus.ANGELEGT, List.of(ANALYSE), BUCHE)));
    when(arbeitszeit.alleAngefallen())
        .thenReturn(
            Map.of(BERATUNG_ID, new BigDecimal("12.00"), ANALYSE_ID, new BigDecimal("1.00")));
    when(rechnungen.gestellte())
        .thenReturn(
            new Gestellte(
                Map.of(OKTOBER, OKTOBER_GESTELLT), Map.of(BERATUNG_ID, new BigDecimal("5.00"))));
    final Zeitraum.Jahr jahr = new Zeitraum.Jahr(DIESES_JAHR);
    when(arbeitszeit.alleImZeitraum(OKTOBER.atDay(1), OKTOBER.atEndOfMonth()))
        .thenReturn(Map.of(BERATUNG_ID, new BigDecimal("2.00")));
    when(arbeitszeit.alleImZeitraum(jahr.von(), jahr.bis()))
        .thenReturn(Map.of(BERATUNG_ID, new BigDecimal("12.00")));

    // When
    final Startseitenstand monat = useCase.stand(Optional.empty());
    final Startseitenstand imJahr = useCase.stand(Optional.of(jahr));

    // Then — (min(12, 20) − 5) × 100,00 + 1 × 200,00 = 900,00 in beiden Staenden.
    assertThat(imJahr.zeitraum()).isEqualTo(jahr);
    assertThat(imJahr.inArbeit()).isEqualTo(monat.inArbeit()).hasSize(1);
    assertThat(imJahr.nichtAbgerechnet().betrag())
        .isEqualByComparingTo(monat.nichtAbgerechnet().betrag())
        .isEqualByComparingTo("900.00");
    assertThat(imJahr.nichtAbgerechnet().anteile()).isEqualTo(monat.nichtAbgerechnet().anteile());
  }

  @Test
  void stand_withAnEmptyBestand_thenEveryKennzahlIsZeroAndEveryListEmpty() {
    // Given
    when(uebersicht.angebote(Optional.empty())).thenReturn(List.of());
    when(arbeitszeit.alleAngefallen()).thenReturn(Map.of());
    when(arbeitszeit.alleImZeitraum(OKTOBER.atDay(1), OKTOBER.atEndOfMonth())).thenReturn(Map.of());
    when(rechnungen.gestellte()).thenReturn(new Gestellte(Map.of(), Map.of()));

    // When
    final Startseitenstand stand = useCase.stand(Optional.empty());

    // Then
    assertThat(stand.inArbeit()).isEmpty();
    assertThat(stand.nichtAbgerechnet().betrag()).isEqualByComparingTo("0.00");
    assertThat(stand.nichtAbgerechnet().erfasstImZeitraum()).isEqualByComparingTo("0.00");
    assertThat(stand.nichtAbgerechnet().anteile()).isEmpty();
    assertThat(stand.abgerechnet().summe()).isEqualTo(NICHTS_GESTELLT);
    assertThat(stand.interneStundenImZeitraum()).isEqualByComparingTo("0");
  }

  @Test
  void stand_withAnInternesAngebot_thenItIsMissingFromBothBetraegeOfKennzahl2() {
    // Given — eine interne Position, die nach Aufwand in Stunden zu 100,00 € abrechnet: der Fall
    // aus Kriterium 8 von #207, in dem Menge und Preis am umgestellten Angebot stehenbleiben. Sie
    // ist ueber alle Monate und im gewaehlten Monat bebucht, daneben ein Kundenangebot.
    gegebenImOktober(
        List.of(
            angebot(1L, Angebotsstatus.LAEUFT, List.of(EIGENE)),
            angebot(2L, Angebotsstatus.BESTELLT, List.of(BERATUNG))),
        Map.of(EIGENE_ID, new BigDecimal("8.00"), BERATUNG_ID, new BigDecimal("3.00")),
        Map.of(EIGENE_ID, new BigDecimal("8.00"), BERATUNG_ID, new BigDecimal("3.00")),
        Map.of());

    // When
    final Startseitenstand stand = useCase.stand(Optional.empty());

    // Then — nur die drei Stunden des Kundenangebots zu 100,00 € stehen in beiden Betraegen, und
    // das interne Angebot steht in keiner Zeile darunter.
    assertThat(stand.nichtAbgerechnet().betrag()).isEqualByComparingTo("300.00");
    assertThat(stand.nichtAbgerechnet().erfasstImZeitraum()).isEqualByComparingTo("300.00");
    assertThat(stand.nichtAbgerechnet().anteile())
        .extracting(anteil -> anteil.angebot().angebot().requireId())
        .containsExactly(2L);
  }

  @Test
  void stand_withAnInternesAngebot_thenInArbeitExcludesItThroughTheStatusmengeAlone() {
    // Given — die zwei internen Status neben den zwei, die „in Arbeit" heissen. Der Code hat dafuer
    // keine eigene Bedingung: LAEUFT und ABGESCHLOSSEN liegen in der Statusmenge von inArbeit seit
    // Issue #226 im false-Zweig (Review-Fund 4 der Pruefung zu Plan #218, E17).
    gegebenImOktober(
        List.of(
            angebot(1L, Angebotsstatus.LAEUFT, List.of()),
            angebot(2L, Angebotsstatus.BESTELLT, List.of()),
            angebot(3L, Angebotsstatus.ABGESCHLOSSEN, List.of()),
            angebot(4L, Angebotsstatus.ERLEDIGT, List.of())),
        Map.of(),
        Map.of(),
        Map.of());

    // When
    final Startseitenstand stand = useCase.stand(Optional.empty());

    // Then
    assertThat(stand.inArbeit())
        .extracting(zeile -> zeile.angebot().requireId())
        .containsExactly(2L, 4L);
  }

  @Test
  void stand_withAnInternesAngebot_thenAbgerechnetStaysWhatTheAuskunftReports() {
    // Given — ein bebuchtes internes Angebot. Es kann keine Rechnung haben (#207, Kriterium 7) und
    // darum in der Rechnungsauskunft nicht vorkommen; Kennzahl 3 entsteht ganz aus ihrer Antwort.
    // Auch hier braucht der Code keine eigene Bedingung (Review-Fund 4).
    gegebenImOktober(
        List.of(angebot(1L, Angebotsstatus.ABGESCHLOSSEN, List.of(EIGENE))),
        Map.of(EIGENE_ID, new BigDecimal("8.00")),
        Map.of(EIGENE_ID, new BigDecimal("8.00")),
        Map.of());

    // When
    final Startseitenstand stand = useCase.stand(Optional.empty());

    // Then
    assertThat(stand.abgerechnet().summe()).isEqualTo(OKTOBER_GESTELLT);
  }

  @Test
  void stand_thenInterneStundenImMonatSumsOnlyTheHoursOnInterneAngebote() {
    // Given — zwei Positionen eines internen Angebots, die zweite zum Festpreis: an einem internen
    // Angebot zaehlt jede Position, weil jede buchbar ist (Entscheidung am Issue #237). Daneben
    // Kundenzeit im selben Monat, die nicht mitzaehlt.
    gegebenImOktober(
        List.of(
            angebot(1L, Angebotsstatus.LAEUFT, List.of(EIGENE, EIGENE_FESTPREIS)),
            angebot(2L, Angebotsstatus.BESTELLT, List.of(BERATUNG))),
        Map.of(),
        Map.of(
            EIGENE_ID,
            new BigDecimal("8.00"),
            EIGENE_FESTPREIS_ID,
            new BigDecimal("4.50"),
            BERATUNG_ID,
            new BigDecimal("3.00")),
        Map.of());

    // When
    final Startseitenstand stand = useCase.stand(Optional.empty());

    // Then — 8,00 + 4,50 Stunden; die drei Kundenstunden fehlen darin.
    assertThat(stand.interneStundenImZeitraum()).isEqualByComparingTo("12.50");
  }

  @Test
  void stand_withoutAnInterneBuchungInTheMonat_thenInterneStundenImMonatIsZero() {
    // Given — ein internes Angebot mit Stunden ueber alle Monate, aber keiner im gewaehlten; die
    // Kundenzeit des Monats bleibt aussen vor.
    gegebenImOktober(
        List.of(
            angebot(1L, Angebotsstatus.LAEUFT, List.of(EIGENE)),
            angebot(2L, Angebotsstatus.BESTELLT, List.of(BERATUNG))),
        Map.of(EIGENE_ID, new BigDecimal("8.00")),
        Map.of(BERATUNG_ID, new BigDecimal("3.00")),
        Map.of());

    // When
    final Startseitenstand stand = useCase.stand(Optional.empty());

    // Then
    assertThat(stand.interneStundenImZeitraum()).isEqualByComparingTo("0");
  }
}
