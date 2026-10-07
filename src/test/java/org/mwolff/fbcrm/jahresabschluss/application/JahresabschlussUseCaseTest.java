package org.mwolff.fbcrm.jahresabschluss.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mwolff.fbcrm.rechnung.domain.Rechnungszustand.ABGESCHRIEBEN;
import static org.mwolff.fbcrm.rechnung.domain.Rechnungszustand.BEZAHLT;
import static org.mwolff.fbcrm.rechnung.domain.Rechnungszustand.ENTWURF;
import static org.mwolff.fbcrm.rechnung.domain.Rechnungszustand.GESTELLT;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
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
import org.mwolff.fbcrm.common.Geldrechnung;
import org.mwolff.fbcrm.rechnung.application.GestellteRechnung;
import org.mwolff.fbcrm.rechnung.application.Monatsabrechnung;
import org.mwolff.fbcrm.rechnung.application.Rechnungsauskunft;
import org.mwolff.fbcrm.rechnung.domain.Rechnungszustand;

/**
 * Die Uebersicht aller Jahre des Jahresabschlusses (#287, Kriterien 1 und 3; Plan #288, E7, E8,
 * E10, E19, E21) und der Abschluss eines Jahres (Kriterien 4 bis 12; E2, E3, E5, E9, E11 bis E14,
 * E22).
 *
 * <p>Gegenstand sind die Regeln dieses Moduls: welche Jahre erscheinen, in welcher Reihenfolge,
 * welches noch laeuft und wie die drei Hauptzahlen entstehen; dazu, wie Einnahmen, Rechnungsstand
 * und Steuerzeilen eines Jahres entstehen und wann es keinen Abschluss gibt; dazu Angebotsbilanz,
 * Umsatz je Kunde und Arbeitszeit. Die drei Auskuenfte, aus denen das entsteht, sind gemockt — sie
 * gehoeren fremden Modulen und sind dort geprueft ({@code RechnungsauskunftTest}, {@code
 * AngeboteUebersichtUseCaseTest}, {@code ArbeitszeitauskunftTest}).
 *
 * <p>Die Statusmengen ({@code Angebotsblick}), die Prozentrundung ({@code Quote}), die Gruppierung
 * je Steuersatz ({@code Steuerblick}) und die je Firma ({@code Kundenblick}) werden hier
 * mitgeprueft und haben keine eigene Testklasse: Sie sind paket-privat und keine eigene Zusage.
 *
 * <p>Die Uhr steht fest auf dem 31. Dezember 2026 um 23:30 UTC. In der Geschaeftszone ist das schon
 * der 1. Januar 2027, 00:30 Uhr — das laufende Jahr ist darum 2027 und nicht 2026. Am Nullmeridian
 * gelesen truege das falsche Jahr das Kennzeichen „laeuft noch".
 */
@ExtendWith(MockitoExtension.class)
class JahresabschlussUseCaseTest {

  /** 31. Dezember 2026, 23:30 UTC — in Europe/Berlin bereits der 1. Januar 2027, 00:30 Uhr. */
  private static final Instant JETZT = Instant.parse("2026-12-31T23:30:00Z");

  private static final Year LAUFENDES_JAHR = Year.of(2027);
  private static final Year LETZTES_JAHR = Year.of(2026);

  private static final Instant ANGELEGT = Instant.parse("2024-01-10T08:00:00Z");
  private static final long FIRMA = 5L;
  private static final String ADLER = "Adler AG";

  @Mock private Rechnungsauskunft rechnungen;
  @Mock private AngeboteUebersichtUseCase uebersicht;
  @Mock private Arbeitszeitauskunft arbeitszeit;

  private JahresabschlussUseCase useCase;

  @BeforeEach
  void baueDenAnwendungsfall() {
    useCase =
        new JahresabschlussUseCase(
            rechnungen, uebersicht, arbeitszeit, Clock.fixed(JETZT, ZoneOffset.UTC));
  }

  private static GestellteRechnung rechnung(final LocalDate datum, final String netto) {
    return new GestellteRechnung(
        datum,
        new BigDecimal(netto),
        new BigDecimal(netto).multiply(new BigDecimal("1.19")),
        new BigDecimal("19.00"),
        Rechnungszustand.GESTELLT,
        FIRMA,
        ADLER);
  }

  /* Eine Rechnung mit allen Angaben in der Hand des Tests; ein Satz null ist ein Nachtrag. */
  private static GestellteRechnung rechnung(
      final LocalDate datum,
      final String netto,
      final String brutto,
      final String satz,
      final Rechnungszustand zustand) {
    return new GestellteRechnung(
        datum,
        new BigDecimal(netto),
        new BigDecimal(brutto),
        satz == null ? null : new BigDecimal(satz),
        zustand,
        FIRMA,
        ADLER);
  }

  /* Eine gestellte Rechnung an eine bestimmte Firma; die Auskunft nennt ihren heutigen Namen. */
  private static GestellteRechnung anFirma(
      final long firmaId, final String firmaName, final String netto) {
    return new GestellteRechnung(
        LocalDate.of(2025, 6, 1),
        new BigDecimal(netto),
        new BigDecimal(netto),
        new BigDecimal("0.00"),
        Rechnungszustand.GESTELLT,
        firmaId,
        firmaName);
  }

  private static AngebotMitFirma angebot(
      final LocalDate datum, final Angebotsstatus status, final Angebotsposition... positionen) {
    return new AngebotMitFirma(
        new Angebot(
            null,
            FIRMA,
            null,
            status.intern(),
            status,
            datum,
            "Neugestaltung der Website",
            List.of(positionen),
            ANGELEGT,
            ANGELEGT),
        ADLER);
  }

  private static Angebotsposition position(
      final long id, final String menge, final String einzelpreis) {
    return new Angebotsposition(
        id,
        "Umsetzung",
        Abrechnungsmodus.AUFWAND,
        new BigDecimal(menge),
        Einheit.STUNDE,
        new BigDecimal(einzelpreis));
  }

  /* Die Stunden je Position, die die Zeiterfassung fuer das Jahr 2025 nennt (E9). */
  private void stunden2025(final Map<Long, BigDecimal> jePosition) {
    when(arbeitszeit.alleImZeitraum(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31)))
        .thenReturn(jePosition);
  }

  private void gegeben(
      final List<GestellteRechnung> gestellte, final List<AngebotMitFirma> angebote) {
    when(rechnungen.gestellteRechnungen()).thenReturn(gestellte);
    when(uebersicht.angebote(Optional.empty())).thenReturn(angebote);
  }

  @Test
  void jahre_thenHoldsEveryYearWithARechnungOrAnAbgegebenesAngebotYoungestFirst() {
    // Given — eine gestellte Rechnung aus 2024, ein abgegebenes Angebot aus 2026 (Kriterium 1).
    gegeben(
        List.of(rechnung(LocalDate.of(2024, 3, 15), "1000.00")),
        List.of(angebot(LocalDate.of(2026, 5, 2), Angebotsstatus.ABGEGEBEN)));

    // When
    final List<Jahreszeile> jahre = useCase.jahre();

    // Then
    assertThat(jahre).extracting(Jahreszeile::jahr).containsExactly(Year.of(2026), Year.of(2024));
  }

  @Test
  void jahre_whenNothingIsThere_thenTheListIsEmpty() {
    // Given — kein Bestand; auch das laufende Jahr erscheint nicht (Kriterium 1, Antwort 4).
    gegeben(List.of(), List.of());

    // When / Then
    assertThat(useCase.jahre()).isEmpty();
  }

  @Test
  void jahre_whenTheLaufendesJahrHasNoDataOfItsOwn_thenItDoesNotAppear() {
    // Given — Daten nur im letzten Jahr.
    gegeben(
        List.of(rechnung(LocalDate.of(2026, 11, 30), "500.00")),
        List.of(angebot(LocalDate.of(2026, 12, 31), Angebotsstatus.BESTELLT)));

    // When
    final List<Jahreszeile> jahre = useCase.jahre();

    // Then
    assertThat(jahre)
        .extracting(Jahreszeile::jahr, Jahreszeile::laeuftNoch)
        .containsExactly(tuple(LETZTES_JAHR, false));
  }

  @Test
  void jahre_thenOnlyTheLaufendesJahrInTheGeschaeftszoneLaeuftNoch() {
    // Given — Daten in 2027 und 2026; die Uhr steht in UTC noch auf 2026.
    gegeben(
        List.of(rechnung(LocalDate.of(2026, 6, 1), "500.00")),
        List.of(angebot(LocalDate.of(2027, 1, 1), Angebotsstatus.ABGEGEBEN)));

    // When
    final List<Jahreszeile> jahre = useCase.jahre();

    // Then — 2027 laeuft noch, weil die Geschaeftszone schon im neuen Jahr steht.
    assertThat(jahre)
        .extracting(Jahreszeile::jahr, Jahreszeile::laeuftNoch)
        .containsExactly(tuple(LAUFENDES_JAHR, true), tuple(LETZTES_JAHR, false));
  }

  @Test
  void jahre_thenEachYearHoldsEinnahmenNettoAnzahlAndAnnahmequote() {
    // Given — 2025: zwei Rechnungen, drei abgegebene Angebote, davon zwei angenommen.
    //         2024: eine Rechnung, kein Angebot.
    gegeben(
        List.of(
            rechnung(LocalDate.of(2025, 1, 1), "1000.10"),
            rechnung(LocalDate.of(2024, 12, 31), "300.00"),
            rechnung(LocalDate.of(2025, 12, 31), "234.56")),
        List.of(
            angebot(LocalDate.of(2025, 2, 1), Angebotsstatus.ABGEGEBEN),
            angebot(LocalDate.of(2025, 3, 1), Angebotsstatus.ERLEDIGT),
            angebot(LocalDate.of(2025, 4, 1), Angebotsstatus.ABGERECHNET)));

    // When
    final List<Jahreszeile> jahre = useCase.jahre();

    // Then — 2 von 3 ergibt 66,7 (eine Nachkommastelle); ohne abgegebenes Angebot steht null.
    assertThat(jahre)
        .extracting(
            Jahreszeile::jahr,
            Jahreszeile::einnahmenNetto,
            Jahreszeile::anzahlRechnungen,
            Jahreszeile::annahmequote)
        .containsExactly(
            tuple(Year.of(2025), new BigDecimal("1234.66"), 2, new BigDecimal("66.7")),
            tuple(Year.of(2024), new BigDecimal("300.00"), 1, null));
  }

  @Test
  void jahre_whenAYearHasOnlyAngebote_thenEinnahmenAreZeroAndNoRechnungIsCounted() {
    // Given — drei abgegebene Angebote, keines angenommen, keine Rechnung.
    gegeben(
        List.of(),
        List.of(
            angebot(LocalDate.of(2025, 2, 1), Angebotsstatus.ABGEGEBEN),
            angebot(LocalDate.of(2025, 3, 1), Angebotsstatus.ABGEGEBEN),
            angebot(LocalDate.of(2025, 4, 1), Angebotsstatus.ABGEGEBEN)));

    // When
    final List<Jahreszeile> jahre = useCase.jahre();

    // Then — positiver Nenner, Zaehler null: 0,0 und nicht null (Kriterium 11).
    assertThat(jahre)
        .extracting(
            Jahreszeile::einnahmenNetto, Jahreszeile::anzahlRechnungen, Jahreszeile::annahmequote)
        .containsExactly(tuple(new BigDecimal("0.00"), 0, new BigDecimal("0.0")));
  }

  @Test
  void jahre_thenTheAnnahmequoteRoundsHalfUp() {
    // Given — 1 von 16 sind 6,25 %: kaufmaennisch 6,3, nicht 6,2 wie bei HALF_EVEN.
    final List<AngebotMitFirma> angebote = new ArrayList<>();
    angebote.add(angebot(LocalDate.of(2025, 1, 1), Angebotsstatus.BESTELLT));
    for (int i = 0; i < 15; i++) {
      angebote.add(angebot(LocalDate.of(2025, 1, 2), Angebotsstatus.ABGEGEBEN));
    }
    gegeben(List.of(), angebote);

    // When / Then
    assertThat(useCase.jahre())
        .extracting(Jahreszeile::annahmequote)
        .containsExactly(new BigDecimal("6.3"));
  }

  @Test
  void jahre_whenAnAngebotIsOnlyAngelegtOrIntern_thenItCountsNowhereAndOpensNoYear() {
    // Given — 2025: ein abgegebenes und ein angenommenes Angebot neben einem angelegten und zwei
    //         internen; 2023 und 2022 tragen nur ein angelegtes bzw. interne Angebote.
    gegeben(
        List.of(),
        List.of(
            angebot(LocalDate.of(2025, 2, 1), Angebotsstatus.ABGEGEBEN),
            angebot(LocalDate.of(2025, 3, 1), Angebotsstatus.BESTELLT),
            angebot(LocalDate.of(2025, 4, 1), Angebotsstatus.ANGELEGT),
            angebot(LocalDate.of(2025, 5, 1), Angebotsstatus.LAEUFT),
            angebot(LocalDate.of(2025, 6, 1), Angebotsstatus.ABGESCHLOSSEN),
            angebot(LocalDate.of(2023, 6, 1), Angebotsstatus.ANGELEGT),
            angebot(LocalDate.of(2022, 6, 1), Angebotsstatus.LAEUFT),
            angebot(LocalDate.of(2022, 7, 1), Angebotsstatus.ABGESCHLOSSEN)));

    // When
    final List<Jahreszeile> jahre = useCase.jahre();

    // Then — 1 von 2 und nicht 1 von 5 oder 3 von 5 (Kriterien 7 und 12; E8).
    assertThat(jahre)
        .extracting(Jahreszeile::jahr, Jahreszeile::annahmequote)
        .containsExactly(tuple(Year.of(2025), new BigDecimal("50.0")));
  }

  @Test
  void jahre_thenAsksEachAuskunftExactlyOnce() {
    // Given — zwei Jahre, damit eine Frage je Jahr auffiele.
    gegeben(
        List.of(
            rechnung(LocalDate.of(2024, 3, 15), "1000.00"),
            rechnung(LocalDate.of(2025, 3, 15), "1000.00")),
        List.of(angebot(LocalDate.of(2026, 5, 2), Angebotsstatus.ABGEGEBEN)));

    // When
    useCase.jahre();

    // Then — die Uebersicht braucht keine Stunden (E9).
    verify(rechnungen).gestellteRechnungen();
    verify(uebersicht).angebote(Optional.empty());
    verifyNoInteractions(arbeitszeit);
  }

  @Test
  void abschluss_thenEinnahmenSumEveryGestellteZustandAndNachtragAndUstIsBruttoMinusNetto() {
    // Given — 2025: gestellt, bezahlt, abgeschrieben und ein Nachtrag; dazu Rechnungen aus 2024
    //         und 2026, die nicht mitzaehlen.
    gegeben(
        List.of(
            rechnung(LocalDate.of(2025, 1, 1), "1000.00", "1190.00", "19.00", GESTELLT),
            rechnung(LocalDate.of(2025, 4, 1), "500.00", "535.00", "7.00", BEZAHLT),
            rechnung(LocalDate.of(2025, 7, 1), "200.00", "238.00", "19.00", ABGESCHRIEBEN),
            rechnung(LocalDate.of(2025, 12, 31), "300.00", "357.01", null, BEZAHLT),
            rechnung(LocalDate.of(2024, 12, 31), "99.00", "117.81", "19.00", GESTELLT),
            rechnung(LocalDate.of(2026, 1, 1), "88.00", "104.72", "19.00", GESTELLT)),
        List.of());

    // When
    final Jahresabschluss abschluss = useCase.abschluss(Year.of(2025));

    // Then
    assertThat(abschluss.jahr()).isEqualTo(Year.of(2025));
    assertThat(abschluss.laeuftNoch()).isFalse();
    assertThat(abschluss.einnahmen())
        .isEqualTo(
            new Einnahmen(
                new BigDecimal("2000.00"), new BigDecimal("2320.01"), new BigDecimal("320.01")));
    assertThat(abschluss.rechnungsstand().anzahl()).isEqualTo(4);
  }

  @Test
  void abschluss_thenNettoAndBruttoMatchTheMonatsabrechnungOfTheTwelveMonthsToTheCent() {
    // Given — zweimal 0,50 € zu 7 %: je Rechnung 0,535 € gerundet 0,54 €, zusammen 1,08 €; aus der
    //         Jahressumme mit dem Satz gerechnet waeren es 1,07 €. Dazu ein Nachtrag mit krummem
    //         Brutto, das kein Satz erklaert.
    final List<GestellteRechnung> gestellte =
        List.of(
            rechnung(LocalDate.of(2025, 1, 31), "0.50", "0.54", "7.00", GESTELLT),
            rechnung(LocalDate.of(2025, 6, 15), "0.50", "0.54", "7.00", BEZAHLT),
            rechnung(LocalDate.of(2025, 12, 1), "3.33", "3.96", "19.00", ABGESCHRIEBEN),
            rechnung(LocalDate.of(2025, 12, 31), "10.00", "11.91", null, GESTELLT));
    gegeben(gestellte, List.of());
    // Die Abrechnung der zwoelf Monate so, wie Rechnungsauskunft#gestellte() sie bildet: je
    // Rechnung Netto, Brutto und eine 1, je Monat summiert, die Monate summiert.
    final Monatsabrechnung ueberDieMonate =
        Monatsabrechnung.summe(
            Stream.iterate(Year.of(2025).atMonth(1), monat -> monat.plusMonths(1))
                .limit(12)
                .map(
                    monat ->
                        Monatsabrechnung.summe(
                            gestellte.stream()
                                .filter(r -> YearMonth.from(r.rechnungDatum()).equals(monat))
                                .map(
                                    r ->
                                        new Monatsabrechnung(
                                            r.netto(), r.brutto(), 1, BigDecimal.ZERO, 0)))));

    // When
    final Einnahmen einnahmen = useCase.abschluss(Year.of(2025)).einnahmen();

    // Then
    assertThat(einnahmen.netto())
        .isEqualTo(ueberDieMonate.netto())
        .isEqualTo(new BigDecimal("14.33"));
    assertThat(einnahmen.brutto())
        .isEqualTo(ueberDieMonate.brutto())
        .isEqualTo(new BigDecimal("16.95"));
  }

  @Test
  void abschluss_whenAnEntwurfIsDatedInTheYear_thenItCountsNeitherInEinnahmenNorInAnzahl() {
    // Given — neben einer gestellten Rechnung ein Entwurf mit Rechnungsdatum im Jahr.
    gegeben(
        List.of(
            rechnung(LocalDate.of(2025, 3, 1), "100.00", "119.00", "19.00", GESTELLT),
            rechnung(LocalDate.of(2025, 3, 2), "50.00", "59.50", "19.00", ENTWURF)),
        List.of());

    // When
    final Jahresabschluss abschluss = useCase.abschluss(Year.of(2025));

    // Then
    assertThat(abschluss.einnahmen().netto()).isEqualTo(new BigDecimal("100.00"));
    assertThat(abschluss.einnahmen().brutto()).isEqualTo(new BigDecimal("119.00"));
    assertThat(abschluss.rechnungsstand().anzahl()).isEqualTo(1);
  }

  @Test
  void abschluss_thenTheRechnungsstandNamesOffenAndAbgeschriebenEachWithAnzahlAndNetto() {
    // Given — zwei offene (eine davon nachgetragen), eine bezahlte, zwei abgeschriebene.
    gegeben(
        List.of(
            rechnung(LocalDate.of(2025, 1, 1), "100.00", "119.00", "19.00", GESTELLT),
            rechnung(LocalDate.of(2025, 2, 1), "50.00", "59.50", null, GESTELLT),
            rechnung(LocalDate.of(2025, 3, 1), "300.00", "357.00", "19.00", BEZAHLT),
            rechnung(LocalDate.of(2025, 4, 1), "70.00", "83.30", "19.00", ABGESCHRIEBEN),
            rechnung(LocalDate.of(2025, 5, 1), "30.00", "35.70", null, ABGESCHRIEBEN)),
        List.of());

    // When / Then
    assertThat(useCase.abschluss(Year.of(2025)).rechnungsstand())
        .isEqualTo(new Rechnungsstand(5, 2, new BigDecimal("150.00"), 2, new BigDecimal("100.00")));
  }

  @Test
  void abschluss_whenNothingIsOffenOrAbgeschrieben_thenZeroAndZeroCent() {
    // Given — alles bezahlt.
    gegeben(
        List.of(
            rechnung(LocalDate.of(2025, 1, 1), "100.00", "119.00", "19.00", BEZAHLT),
            rechnung(LocalDate.of(2025, 2, 1), "50.00", "59.50", null, BEZAHLT)),
        List.of());

    // When / Then
    assertThat(useCase.abschluss(Year.of(2025)).rechnungsstand())
        .isEqualTo(new Rechnungsstand(2, 0, new BigDecimal("0.00"), 0, new BigDecimal("0.00")));
  }

  @Test
  void abschluss_withSaetze19And7AndANachtrag_thenSteuerzeilenAscendingAndTheNachtragLast() {
    // Given — der Nachtrag hat genau 19 % zwischen Netto und Brutto; abgeleitet fiele er in die
    //         Zeile zu 19 %, und genau das darf nicht geschehen (Kriterium 6, E13).
    gegeben(
        List.of(
            rechnung(LocalDate.of(2025, 1, 1), "1000.00", "1190.00", "19.00", GESTELLT),
            rechnung(LocalDate.of(2025, 2, 1), "300.00", "357.00", null, BEZAHLT),
            rechnung(LocalDate.of(2025, 3, 1), "500.00", "535.00", "7.00", BEZAHLT),
            rechnung(LocalDate.of(2025, 4, 1), "200.00", "238.00", "19.00", ABGESCHRIEBEN)),
        List.of());

    // When / Then
    assertThat(useCase.abschluss(Year.of(2025)).steuerzeilen())
        .extracting(Steuerzeile::satz, Steuerzeile::netto, Steuerzeile::umsatzsteuer)
        .containsExactly(
            tuple(new BigDecimal("7.00"), new BigDecimal("500.00"), new BigDecimal("35.00")),
            tuple(new BigDecimal("19.00"), new BigDecimal("1200.00"), new BigDecimal("228.00")),
            tuple(null, new BigDecimal("300.00"), new BigDecimal("57.00")));
  }

  @Test
  void abschluss_whenRechnungenCarry19And19_00_thenTheyFallIntoOneSteuerzeile() {
    // Given — derselbe Satz in zwei Schreibweisen, dazu 7 %, damit die Aufteilung dasteht.
    gegeben(
        List.of(
            rechnung(LocalDate.of(2025, 1, 1), "100.00", "119.00", "19", GESTELLT),
            rechnung(LocalDate.of(2025, 2, 1), "200.00", "238.00", "19.00", GESTELLT),
            rechnung(LocalDate.of(2025, 3, 1), "10.00", "10.70", "7", GESTELLT)),
        List.of());

    // When / Then — der Satz steht auf zwei Nachkommastellen gebracht.
    assertThat(useCase.abschluss(Year.of(2025)).steuerzeilen())
        .extracting(Steuerzeile::satz, Steuerzeile::netto, Steuerzeile::umsatzsteuer)
        .containsExactly(
            tuple(new BigDecimal("7.00"), new BigDecimal("10.00"), new BigDecimal("0.70")),
            tuple(new BigDecimal("19.00"), new BigDecimal("300.00"), new BigDecimal("57.00")));
  }

  @Test
  void abschluss_whenEveryRechnungCarries19_thenTheAufteilungIsLeftOut() {
    // Given — 19 und 19,00 sind ein Satz: Die Aufteilung ergaebe eine einzige Zeile (E14).
    gegeben(
        List.of(
            rechnung(LocalDate.of(2025, 1, 1), "100.00", "119.00", "19", GESTELLT),
            rechnung(LocalDate.of(2025, 2, 1), "200.00", "238.00", "19.00", BEZAHLT)),
        List.of());

    // When / Then
    assertThat(useCase.abschluss(Year.of(2025)).steuerzeilen()).isEmpty();
  }

  @Test
  void abschluss_whenThereAreOnlyNachtraege_thenTheAufteilungIsLeftOut() {
    // Given — nur nachgetragene Rechnungen: eine einzige Zeile „Steuersatz nicht erfasst".
    gegeben(
        List.of(
            rechnung(LocalDate.of(2025, 1, 1), "100.00", "119.00", null, GESTELLT),
            rechnung(LocalDate.of(2025, 2, 1), "200.00", "214.00", null, BEZAHLT)),
        List.of());

    // When / Then
    assertThat(useCase.abschluss(Year.of(2025)).steuerzeilen()).isEmpty();
  }

  @Test
  void abschluss_thenTheSteuerzeilenAddUpToTheEinnahmenToTheCent() {
    // Given — krumme Betraege in drei Saetzen und ein Nachtrag.
    gegeben(
        List.of(
            rechnung(LocalDate.of(2025, 1, 1), "0.50", "0.54", "7.00", GESTELLT),
            rechnung(LocalDate.of(2025, 2, 1), "0.50", "0.54", "7", BEZAHLT),
            rechnung(LocalDate.of(2025, 3, 1), "3.33", "3.96", "19.00", GESTELLT),
            rechnung(LocalDate.of(2025, 4, 1), "1234.57", "1469.14", "19", ABGESCHRIEBEN),
            rechnung(LocalDate.of(2025, 5, 1), "99.99", "115.99", "16.00", GESTELLT),
            rechnung(LocalDate.of(2025, 6, 1), "10.00", "11.91", null, GESTELLT)),
        List.of());

    // When
    final Jahresabschluss abschluss = useCase.abschluss(Year.of(2025));

    // Then
    assertThat(abschluss.steuerzeilen()).hasSize(4);
    assertThat(Geldrechnung.summe(abschluss.steuerzeilen().stream().map(Steuerzeile::netto)))
        .isEqualTo(abschluss.einnahmen().netto())
        .isEqualTo(new BigDecimal("1348.89"));
    assertThat(Geldrechnung.summe(abschluss.steuerzeilen().stream().map(Steuerzeile::umsatzsteuer)))
        .isEqualTo(abschluss.einnahmen().umsatzsteuer())
        .isEqualTo(new BigDecimal("253.19"));
  }

  @Test
  void abschluss_whenTheYearHasNoGestellteRechnungAndNoAbgegebenesAngebot_thenJahrOhneDaten() {
    // Given — 1999 traegt nur einen Entwurf, ein angelegtes und ein internes Angebot; gestellt und
    //         abgegeben ist allein in 2025 (E3).
    gegeben(
        List.of(
            rechnung(LocalDate.of(2025, 1, 1), "100.00", "119.00", "19.00", GESTELLT),
            rechnung(LocalDate.of(1999, 1, 1), "100.00", "119.00", "19.00", ENTWURF)),
        List.of(
            angebot(LocalDate.of(2025, 2, 1), Angebotsstatus.ABGEGEBEN),
            angebot(LocalDate.of(1999, 2, 1), Angebotsstatus.ANGELEGT),
            angebot(LocalDate.of(1999, 3, 1), Angebotsstatus.LAEUFT)));

    // When / Then
    assertThatThrownBy(() -> useCase.abschluss(Year.of(1999))).isInstanceOf(JahrOhneDaten.class);
  }

  @Test
  void abschluss_whenTheYearHasOnlyAnAbgegebenesAngebot_thenEinnahmenAreZero() {
    // Given — 2025 hat ein abgegebenes Angebot und keine gestellte Rechnung.
    gegeben(
        List.of(rechnung(LocalDate.of(2024, 1, 1), "100.00", "119.00", "19.00", GESTELLT)),
        List.of(angebot(LocalDate.of(2025, 2, 1), Angebotsstatus.ABGEGEBEN)));

    // When
    final Jahresabschluss abschluss = useCase.abschluss(Year.of(2025));

    // Then
    assertThat(abschluss.einnahmen())
        .isEqualTo(
            new Einnahmen(new BigDecimal("0.00"), new BigDecimal("0.00"), new BigDecimal("0.00")));
    assertThat(abschluss.rechnungsstand())
        .isEqualTo(new Rechnungsstand(0, 0, new BigDecimal("0.00"), 0, new BigDecimal("0.00")));
    assertThat(abschluss.steuerzeilen()).isEmpty();
  }

  @Test
  void abschluss_thenOnlyTheLaufendesJahrInTheGeschaeftszoneLaeuftNoch() {
    // Given — eine Rechnung vom 1. Januar 2027; die Uhr steht in UTC noch auf 2026.
    gegeben(
        List.of(rechnung(LocalDate.of(2027, 1, 1), "100.00", "119.00", "19.00", GESTELLT)),
        List.of());

    // When / Then
    assertThat(useCase.abschluss(LAUFENDES_JAHR).laeuftNoch()).isTrue();
  }

  @Test
  void abschluss_thenAsksEachAuskunftExactlyOnce() {
    // Given
    gegeben(
        List.of(rechnung(LocalDate.of(2025, 3, 15), "1000.00")),
        List.of(angebot(LocalDate.of(2025, 5, 2), Angebotsstatus.ABGEGEBEN)));

    // When
    useCase.abschluss(Year.of(2025));

    // Then — die Stunden in einem Zug vom 1. Januar bis zum 31. Dezember (E9).
    verify(rechnungen).gestellteRechnungen();
    verify(uebersicht).angebote(Optional.empty());
    verify(arbeitszeit).alleImZeitraum(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31));
  }

  @Test
  void abschluss_thenTheAngebotsbilanzNamesAbgegebenAngenommenOffenAndTheQuote() {
    // Given — drei abgegebene Angebote in 2025, zwei davon heute bestellt oder erledigt; dazu ein
    //         angelegtes in 2025 und ein abgegebenes in 2024, die nicht mitzaehlen (Kriterium 7).
    gegeben(
        List.of(),
        List.of(
            angebot(LocalDate.of(2025, 2, 1), Angebotsstatus.ABGEGEBEN, position(1, "10", "100")),
            angebot(LocalDate.of(2025, 3, 1), Angebotsstatus.BESTELLT, position(2, "20", "100")),
            angebot(LocalDate.of(2025, 4, 1), Angebotsstatus.ERLEDIGT, position(3, "5", "100")),
            angebot(LocalDate.of(2025, 5, 1), Angebotsstatus.ANGELEGT, position(4, "1", "900")),
            angebot(LocalDate.of(2024, 12, 31), Angebotsstatus.BESTELLT, position(5, "1", "800"))));

    // When / Then
    assertThat(useCase.abschluss(Year.of(2025)).angebotsbilanz())
        .isEqualTo(
            new Angebotsbilanz(
                3,
                2,
                1,
                new BigDecimal("66.7"),
                new BigDecimal("3500.00"),
                new BigDecimal("2500.00")));
  }

  @Test
  void abschluss_thenTheVolumenAreTheSumsOfTheAngebotssummenOfAbgegebenAndAngenommen() {
    // Given — 2,5 h zu 1.000,01 € sind gerundet 2.500,03 €; abgerechnet gilt als angenommen.
    gegeben(
        List.of(),
        List.of(
            angebot(
                LocalDate.of(2025, 2, 1),
                Angebotsstatus.ABGEGEBEN,
                position(1, "2.5", "1000.01"),
                position(2, "1", "100.00")),
            angebot(
                LocalDate.of(2025, 3, 1), Angebotsstatus.ABGERECHNET, position(3, "3", "33.333"))));

    // When
    final Angebotsbilanz bilanz = useCase.abschluss(Year.of(2025)).angebotsbilanz();

    // Then — die Summe der Angebotssummen, je Position gerundet (Kriterium 8).
    assertThat(bilanz.volumenAbgegeben()).isEqualTo(new BigDecimal("2700.03"));
    assertThat(bilanz.volumenAngenommen()).isEqualTo(new BigDecimal("100.00"));
  }

  @Test
  void abschluss_whenAngeboteAreIntern_thenTheyCountInNoZahlNoVolumenAndNotInTheQuote() {
    // Given — neben einem abgegebenen Angebot ein laufendes und ein abgeschlossenes internes.
    gegeben(
        List.of(),
        List.of(
            angebot(LocalDate.of(2025, 2, 1), Angebotsstatus.ABGEGEBEN, position(1, "1", "100")),
            angebot(LocalDate.of(2025, 3, 1), Angebotsstatus.LAEUFT, position(2, "50", "100")),
            angebot(
                LocalDate.of(2025, 4, 1), Angebotsstatus.ABGESCHLOSSEN, position(3, "70", "100"))));

    // When / Then — 0 von 1 ergibt 0,0 und nicht null; positiver Nenner (Kriterien 11 und 12).
    assertThat(useCase.abschluss(Year.of(2025)).angebotsbilanz())
        .isEqualTo(
            new Angebotsbilanz(
                1, 0, 1, new BigDecimal("0.0"), new BigDecimal("100.00"), new BigDecimal("0.00")));
  }

  @Test
  void abschluss_whenNoAngebotIsAbgegeben_thenTheAnnahmequoteIsNull() {
    // Given — 2025 hat nur eine Rechnung.
    gegeben(List.of(anFirma(FIRMA, ADLER, "100.00")), List.of());

    // When / Then — Nenner null: keine Quote (Kriterium 11).
    assertThat(useCase.abschluss(Year.of(2025)).angebotsbilanz())
        .isEqualTo(
            new Angebotsbilanz(0, 0, 0, null, new BigDecimal("0.00"), new BigDecimal("0.00")));
  }

  @Test
  void abschluss_whenTwoRechnungenGoToTheSameFirma_thenOneKundenzeileWithItsHeutigenNamen() {
    // Given — Firma 5 hiess bei der ersten Rechnung noch „Adler AG"; die Auskunft nennt an beiden
    //         Rechnungen den heutigen Namen. Dazu eine zweite Firma.
    gegeben(
        List.of(
            anFirma(FIRMA, "Adler Digital AG", "600.00"),
            anFirma(7L, "Zeder KG", "250.00"),
            anFirma(FIRMA, "Adler Digital AG", "400.00")),
        List.of());

    // When / Then
    assertThat(useCase.abschluss(Year.of(2025)).kunden())
        .containsExactly(
            new Kundenzeile("Adler Digital AG", new BigDecimal("1000.00"), new BigDecimal("80.0")),
            new Kundenzeile("Zeder KG", new BigDecimal("250.00"), new BigDecimal("20.0")));
  }

  @Test
  void abschluss_whenTwoFirmenShareAName_thenTheyStayTwoKundenzeilen() {
    // Given — zwei Firmen mit gleichem Namen und verschiedener Kennung (E22).
    gegeben(List.of(anFirma(5L, ADLER, "300.00"), anFirma(6L, ADLER, "100.00")), List.of());

    // When / Then
    assertThat(useCase.abschluss(Year.of(2025)).kunden())
        .containsExactly(
            new Kundenzeile(ADLER, new BigDecimal("300.00"), new BigDecimal("75.0")),
            new Kundenzeile(ADLER, new BigDecimal("100.00"), new BigDecimal("25.0")));
  }

  @Test
  void abschluss_thenKundenzeilenDescendByNettoAndAscendByNameOnATie() {
    // Given — Birke und Adler haben denselben Betrag; die Rechnungen stehen in keiner Ordnung.
    gegeben(
        List.of(
            anFirma(1L, "Esche OHG", "100.00"),
            anFirma(2L, "Birke GmbH", "200.00"),
            anFirma(3L, "Zeder KG", "500.00"),
            anFirma(4L, "Adler AG", "200.00")),
        List.of());

    // When / Then (E21)
    assertThat(useCase.abschluss(Year.of(2025)).kunden())
        .extracting(Kundenzeile::firmaName, Kundenzeile::netto, Kundenzeile::anteil)
        .containsExactly(
            tuple("Zeder KG", new BigDecimal("500.00"), new BigDecimal("50.0")),
            tuple("Adler AG", new BigDecimal("200.00"), new BigDecimal("20.0")),
            tuple("Birke GmbH", new BigDecimal("200.00"), new BigDecimal("20.0")),
            tuple("Esche OHG", new BigDecimal("100.00"), new BigDecimal("10.0")));
  }

  @Test
  void abschluss_thenTheAnteilRoundsHalfUpAndIsNotEvenedOutTo100() {
    // Given — 100 von 1.600 sind 6,25 %, 1.500 von 1.600 sind 93,75 %.
    gegeben(
        List.of(anFirma(1L, "Adler AG", "100.00"), anFirma(2L, "Birke GmbH", "1500.00")),
        List.of());

    // When / Then — kaufmaennisch 6,3 und 93,8; zusammen 100,1, und kein Ausgleich (E10).
    assertThat(useCase.abschluss(Year.of(2025)).kunden())
        .extracting(Kundenzeile::anteil)
        .containsExactly(new BigDecimal("93.8"), new BigDecimal("6.3"));
  }

  @Test
  void abschluss_whenTheJahresumsatzIsZero_thenTheAnteilIsNull() {
    // Given — eine gestellte Rechnung ueber 0,00 €.
    gegeben(List.of(anFirma(FIRMA, ADLER, "0.00")), List.of());

    // When / Then — Nenner null: kein Anteil (Kriterium 11).
    assertThat(useCase.abschluss(Year.of(2025)).kunden())
        .containsExactly(new Kundenzeile(ADLER, new BigDecimal("0.00"), null));
  }

  @Test
  void abschluss_whenTheYearHasNoRechnung_thenThereIsNoKundenzeile() {
    // Given
    gegeben(List.of(), List.of(angebot(LocalDate.of(2025, 2, 1), Angebotsstatus.ABGEGEBEN)));

    // When / Then
    assertThat(useCase.abschluss(Year.of(2025)).kunden()).isEmpty();
  }

  @Test
  void abschluss_thenTheArbeitszeitSeparatesKundenarbeitFromInternAndNamesTheErloesJeStunde() {
    // Given — 10.000,00 € netto; Kundenarbeit an einem bestellten Angebot aus 2024 und an einem
    //         abgegebenen aus 2025, interne Zeit an einem laufenden und einem abgeschlossenen
    //         internen Angebot. Das Jahr des Angebots spielt keine Rolle, nur der Arbeitstag.
    gegeben(
        List.of(anFirma(FIRMA, ADLER, "10000.00")),
        List.of(
            angebot(
                LocalDate.of(2024, 11, 1),
                Angebotsstatus.BESTELLT,
                position(11, "40", "100"),
                position(12, "10", "100")),
            angebot(LocalDate.of(2025, 2, 1), Angebotsstatus.ABGEGEBEN, position(13, "8", "100")),
            angebot(LocalDate.of(2025, 1, 1), Angebotsstatus.LAEUFT, position(21, "1", "0")),
            angebot(
                LocalDate.of(2023, 1, 1), Angebotsstatus.ABGESCHLOSSEN, position(31, "1", "0"))));
    stunden2025(
        Map.of(
            11L, new BigDecimal("30.00"),
            12L, new BigDecimal("2.50"),
            13L, new BigDecimal("5.00"),
            21L, new BigDecimal("3.25"),
            31L, new BigDecimal("1.75")));

    // When
    final Jahresarbeitszeit zeit = useCase.abschluss(Year.of(2025)).arbeitszeit();

    // Then — 10.000,00 € ÷ 37,5 h = 266,666… €, kaufmaennisch 266,67 € (Kriterium 10; E12).
    assertThat(zeit)
        .isEqualTo(
            new Jahresarbeitszeit(
                new BigDecimal("37.50"), new BigDecimal("5.00"), new BigDecimal("266.67")));
  }

  @Test
  void abschluss_whenThereAreNoKundenstunden_thenTheErloesJeStundeIsNull() {
    // Given — Zeit nur an einem internen Angebot.
    gegeben(
        List.of(anFirma(FIRMA, ADLER, "10000.00")),
        List.of(
            angebot(LocalDate.of(2025, 2, 1), Angebotsstatus.ABGEGEBEN, position(11, "8", "100")),
            angebot(LocalDate.of(2025, 1, 1), Angebotsstatus.LAEUFT, position(21, "1", "0"))));
    stunden2025(Map.of(21L, new BigDecimal("4.00")));

    // When
    final Jahresarbeitszeit zeit = useCase.abschluss(Year.of(2025)).arbeitszeit();

    // Then — Nenner null: kein Erloes je Stunde (Kriterium 11).
    assertThat(zeit.kundenStunden()).isEqualByComparingTo("0");
    assertThat(zeit.interneStunden()).isEqualTo(new BigDecimal("4.00"));
    assertThat(zeit.erloesJeStunde()).isNull();
  }

  @Test
  void abschluss_whenThereAreKundenstundenButNoEinnahmen_thenTheErloesJeStundeIsZero() {
    // Given — 2025 hat ein abgegebenes Angebot mit 8 Stunden und keine Rechnung.
    gegeben(
        List.of(),
        List.of(
            angebot(LocalDate.of(2025, 2, 1), Angebotsstatus.ABGEGEBEN, position(11, "8", "100"))));
    stunden2025(Map.of(11L, new BigDecimal("8.00")));

    // When / Then — positiver Nenner, Zaehler null: 0,00 und nicht null (Kriterium 11).
    assertThat(useCase.abschluss(Year.of(2025)).arbeitszeit())
        .isEqualTo(
            new Jahresarbeitszeit(new BigDecimal("8.00"), BigDecimal.ZERO, new BigDecimal("0.00")));
  }
}
