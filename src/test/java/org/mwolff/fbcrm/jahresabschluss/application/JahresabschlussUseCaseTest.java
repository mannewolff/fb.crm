package org.mwolff.fbcrm.jahresabschluss.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.angebot.application.AngebotMitFirma;
import org.mwolff.fbcrm.angebot.application.AngeboteUebersichtUseCase;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.Angebotsstatus;
import org.mwolff.fbcrm.rechnung.application.GestellteRechnung;
import org.mwolff.fbcrm.rechnung.application.Rechnungsauskunft;
import org.mwolff.fbcrm.rechnung.domain.Rechnungszustand;

/**
 * Die Uebersicht aller Jahre des Jahresabschlusses (#287, Kriterien 1 und 3; Plan #288, E7, E8,
 * E10, E19, E21).
 *
 * <p>Gegenstand sind die Regeln dieses Moduls: welche Jahre erscheinen, in welcher Reihenfolge,
 * welches noch laeuft und wie die drei Hauptzahlen entstehen. Die beiden Auskuenfte, aus denen das
 * entsteht, sind gemockt — sie gehoeren fremden Modulen und sind dort geprueft ({@code
 * RechnungsauskunftTest}, {@code AngeboteUebersichtUseCaseTest}).
 *
 * <p>Die Statusmengen ({@code Angebotsblick}) und die Prozentrundung ({@code Quote}) werden hier
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

  private JahresabschlussUseCase useCase;

  @BeforeEach
  void baueDenAnwendungsfall() {
    useCase =
        new JahresabschlussUseCase(rechnungen, uebersicht, Clock.fixed(JETZT, ZoneOffset.UTC));
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

  private static AngebotMitFirma angebot(final LocalDate datum, final Angebotsstatus status) {
    return new AngebotMitFirma(
        new Angebot(
            null,
            FIRMA,
            null,
            status.intern(),
            status,
            datum,
            "Neugestaltung der Website",
            List.of(),
            ANGELEGT,
            ANGELEGT),
        ADLER);
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

    // Then
    verify(rechnungen).gestellteRechnungen();
    verify(uebersicht).angebote(Optional.empty());
  }
}
