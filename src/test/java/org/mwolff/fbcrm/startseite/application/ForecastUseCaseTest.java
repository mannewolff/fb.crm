package org.mwolff.fbcrm.startseite.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.angebot.application.AngebotMitFirma;
import org.mwolff.fbcrm.angebot.application.AngeboteUebersichtUseCase;
import org.mwolff.fbcrm.angebot.application.Angebotsauswahl;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.Angebotsposition;
import org.mwolff.fbcrm.angebot.domain.Angebotsstatus;
import org.mwolff.fbcrm.common.Abrechnungsmodus;
import org.mwolff.fbcrm.common.Einheit;
import org.mwolff.fbcrm.rechnung.application.Rechnungsauskunft;

/**
 * Der Forecast je Jahr (#302, Kriterien 1 bis 7; Plan #303, E1, E4, E5, E6, E13).
 *
 * <p>Gegenstand sind die Rechenregeln: welche Position im Forecast steht, in welchem Jahr und in
 * welcher Spalte, und mit welchem Betrag. Die beiden Auskuenfte, aus denen das entsteht, sind
 * gemockt — sie gehoeren fremden Modulen und sind dort geprueft ({@code
 * AngeboteUebersichtUseCaseTest}, {@code RechnungsauskunftTest}). Dass die abgerechneten Mengen
 * auch die Entwuerfe zaehlen, belegt {@code RechnungsauskunftTest}; hier steht, dass der Forecast
 * genau diese Tuer fragt und nicht die der gestellten Rechnungen.
 *
 * <p><b>Keine Uhr.</b> Der Forecast ist der Stand von heute ueber alle Jahre (Kriterium 7) und
 * braucht kein Datum: Ein vergangenes Jahr mit Rest steht so darin wie ein kommendes (Kriterium 6).
 */
@ExtendWith(MockitoExtension.class)
class ForecastUseCaseTest {

  /** Das Datum des Angebots aus dem Anlass von #302 — Oktober 2026, die Leistung aber 2027. */
  private static final LocalDate ANGEBOTSDATUM = LocalDate.of(2026, 10, 15);

  private static final Instant ANGELEGT = Instant.parse("2026-10-15T08:00:00Z");

  private static final long FIRMA = 5L;
  private static final String ADLER = "Adler AG";
  private static final String BILDUNGSHAUS = "IT Bildungshaus";

  private static final Year J2024 = Year.of(2024);
  private static final Year J2025 = Year.of(2025);
  private static final Year J2026 = Year.of(2026);
  private static final Year J2027 = Year.of(2027);
  private static final Year J2028 = Year.of(2028);
  private static final Year J2029 = Year.of(2029);
  private static final Year J2030 = Year.of(2030);

  @Mock private AngeboteUebersichtUseCase uebersicht;
  @Mock private Rechnungsauskunft rechnungen;

  private ForecastUseCase useCase;

  @BeforeEach
  void baueDenAnwendungsfall() {
    useCase = new ForecastUseCase(uebersicht, rechnungen);
  }

  private static Angebotsposition position(
      final long id, final String menge, final String einzelpreis, final Year leistungsjahr) {
    return position(id, "Beratung", menge, einzelpreis, leistungsjahr);
  }

  private static Angebotsposition position(
      final long id,
      final String bezeichnung,
      final String menge,
      final String einzelpreis,
      final Year leistungsjahr) {
    return new Angebotsposition(
        Long.valueOf(id),
        bezeichnung,
        Abrechnungsmodus.AUFWAND,
        new BigDecimal(menge),
        Einheit.STUNDE,
        new BigDecimal(einzelpreis),
        leistungsjahr);
  }

  private static AngebotMitFirma angebot(
      final long id, final Angebotsstatus status, final Angebotsposition... positionen) {
    return angebot(id, status, ADLER, positionen);
  }

  private static AngebotMitFirma angebot(
      final long id,
      final Angebotsstatus status,
      final String firmaName,
      final Angebotsposition... positionen) {
    return new AngebotMitFirma(
        new Angebot(
            Long.valueOf(id),
            FIRMA,
            null,
            status.intern(),
            status,
            false,
            null,
            ANGEBOTSDATUM,
            "Website-Relaunch",
            "Schulungen",
            List.of(positionen),
            ANGELEGT,
            ANGELEGT),
        firmaName);
  }

  private void gegeben(
      final List<AngebotMitFirma> angebote, final Map<Long, BigDecimal> abgerechnet) {
    when(uebersicht.angebote(Angebotsauswahl.alle())).thenReturn(angebote);
    when(rechnungen.abgerechneteMengen()).thenReturn(abgerechnet);
  }

  private static BigDecimal geld(final String betrag) {
    return new BigDecimal(betrag);
  }

  @Test
  void forecast_thenEachYearCarriesZugesagtAngebotenAndTheirSum() {
    // Given — bestellt 10 h zu 100,00 €, angelegt 5 h zu 60,00 €, beide 2026.
    gegeben(
        List.of(
            angebot(1L, Angebotsstatus.BESTELLT, position(11L, "10.00", "100.00", J2026)),
            angebot(2L, Angebotsstatus.ANGELEGT, position(21L, "5.00", "60.00", J2026))),
        Map.of());

    // When
    final Forecast forecast = useCase.forecast();

    // Then
    assertThat(forecast.jahre())
        .extracting(
            Forecastjahr::jahr,
            Forecastjahr::zugesagt,
            Forecastjahr::angeboten,
            Forecastjahr::summe)
        .containsExactly(tuple(J2026, geld("1000.00"), geld("300.00"), geld("1300.00")));
  }

  @Test
  void forecast_thenTheFiveKundenstatusFallIntoTheirColumnAndAbgerechnetWithRestIsZugesagt() {
    // Given — je Status ein eigenes Jahr, damit jeder Fall einzeln in seiner Spalte steht.
    // Das abgerechnete Angebot hat von 10 h erst 4 auf einer Rechnung: 6 h Rest.
    gegeben(
        List.of(
            angebot(1L, Angebotsstatus.ANGELEGT, position(11L, "1.00", "100.00", J2026)),
            angebot(2L, Angebotsstatus.ABGEGEBEN, position(21L, "2.00", "100.00", J2027)),
            angebot(3L, Angebotsstatus.BESTELLT, position(31L, "3.00", "100.00", J2028)),
            angebot(4L, Angebotsstatus.ERLEDIGT, position(41L, "4.00", "100.00", J2029)),
            angebot(5L, Angebotsstatus.ABGERECHNET, position(51L, "10.00", "100.00", J2030))),
        Map.of(51L, geld("4.00")));

    // When
    final Forecast forecast = useCase.forecast();

    // Then
    assertThat(forecast.jahre())
        .extracting(Forecastjahr::jahr, Forecastjahr::zugesagt, Forecastjahr::angeboten)
        .containsExactly(
            tuple(J2026, geld("0.00"), geld("100.00")),
            tuple(J2027, geld("0.00"), geld("200.00")),
            tuple(J2028, geld("300.00"), geld("0.00")),
            tuple(J2029, geld("400.00"), geld("0.00")),
            tuple(J2030, geld("600.00"), geld("0.00")));
  }

  @Test
  void forecast_thenInterneArbeitNeverAppears() {
    // Given — beide internen Status mit offener Position.
    gegeben(
        List.of(
            angebot(1L, Angebotsstatus.LAEUFT, position(11L, "40.00", "100.00", J2026)),
            angebot(2L, Angebotsstatus.ABGESCHLOSSEN, position(21L, "8.00", "100.00", J2026))),
        Map.of());

    // When
    final Forecast forecast = useCase.forecast();

    // Then
    assertThat(forecast.jahre()).isEmpty();
  }

  @Test
  void forecast_thenRestIsOffeneMengeTimesEinzelpreisPerPositionToTheCentAndNeverBelowZero() {
    // Given — zwei Viertelstunden zu 99,90 € (je 24,975 → 24,98 €; ungerundet summiert waere
    // es 49,95 €) und eine Position, auf der mehr abgerechnet ist als angeboten.
    gegeben(
        List.of(
            angebot(
                1L,
                Angebotsstatus.BESTELLT,
                position(11L, "0.25", "99.90", J2026),
                position(12L, "0.25", "99.90", J2026),
                position(13L, "10.00", "100.00", J2026))),
        Map.of(13L, geld("12.00")));

    // When
    final Forecast forecast = useCase.forecast();

    // Then
    assertThat(forecast.jahre())
        .singleElement()
        .satisfies(
            jahr -> {
              assertThat(jahr.zugesagt()).isEqualTo(geld("49.96"));
              assertThat(jahr.positionen())
                  .extracting(zeile -> zeile.position().requireId(), Forecastposition::netto)
                  .containsExactly(tuple(11L, geld("24.98")), tuple(12L, geld("24.98")));
            });
  }

  @Test
  void forecast_thenPartlyBilledCountsWithItsRestFullyBilledNotAtAllAndAEntwurfReducesTheRest() {
    // Given — 20 h, davon 5 auf einer Rechnung (Entwurf oder gestellt — die Tuer zaehlt beides):
    // 15 h Rest; daneben eine voll abgerechnete Position.
    gegeben(
        List.of(
            angebot(
                1L,
                Angebotsstatus.ERLEDIGT,
                position(11L, "20.00", "100.00", J2026),
                position(12L, "10.00", "100.00", J2026))),
        Map.of(11L, geld("5.00"), 12L, geld("10.00")));

    // When
    final Forecast forecast = useCase.forecast();

    // Then
    assertThat(forecast.jahre())
        .singleElement()
        .satisfies(
            jahr -> {
              assertThat(jahr.zugesagt()).isEqualTo(geld("1500.00"));
              assertThat(jahr.positionen())
                  .extracting(zeile -> zeile.position().requireId(), Forecastposition::netto)
                  .containsExactly(tuple(11L, geld("1500.00")));
            });
  }

  @Test
  void forecast_thenPositionsStandInTheirLeistungsjahrAndOneAngebotInBothOfItsYears() {
    // Given — der Anlass aus #302: Angebot vom Oktober 2026, drei Positionen fuer 2027 zu je
    // 60,00 € (135 h, 135 h, 27 h = 17.820,00 €); dazu eine Position desselben Angebots in 2026.
    gegeben(
        List.of(
            angebot(
                1L,
                Angebotsstatus.ANGELEGT,
                BILDUNGSHAUS,
                position(11L, "Aufwaende Mai 2027", "135.00", "60.00", J2027),
                position(12L, "Aufwaende Juni 2027", "135.00", "60.00", J2027),
                position(13L, "Aufwaende 3 Tage Rest", "27.00", "60.00", J2027),
                position(14L, "Vorgespraech", "2.00", "60.00", J2026))),
        Map.of());

    // When
    final Forecast forecast = useCase.forecast();

    // Then
    assertThat(forecast.jahre())
        .extracting(
            Forecastjahr::jahr,
            Forecastjahr::angeboten,
            jahr -> jahr.positionen().stream().map(z -> z.position().requireId()).toList())
        .containsExactly(
            tuple(J2026, geld("120.00"), List.of(14L)),
            tuple(J2027, geld("17820.00"), List.of(11L, 12L, 13L)));
  }

  @Test
  void forecast_thenPositionLinesCarryFirmaAngebotBezeichnungAndBetrag() {
    // Given
    gegeben(
        List.of(
            angebot(
                7L,
                Angebotsstatus.ABGEGEBEN,
                BILDUNGSHAUS,
                position(71L, "Aufwaende Mai 2027", "135.00", "60.00", J2027))),
        Map.of());

    // When
    final Forecast forecast = useCase.forecast();

    // Then
    assertThat(forecast.jahre().getFirst().positionen())
        .extracting(
            zeile -> zeile.angebot().firmaName(),
            zeile -> zeile.angebot().angebot().requireId(),
            zeile -> zeile.position().bezeichnung(),
            Forecastposition::netto)
        .containsExactly(tuple(BILDUNGSHAUS, 7L, "Aufwaende Mai 2027", geld("8100.00")));
  }

  @Test
  void forecast_thenAYearWithoutOpenPositionIsMissingAndAPastYearWithRestStays() {
    // Given — 2024 noch offen, 2025 voll abgerechnet, 2026 offen.
    gegeben(
        List.of(
            angebot(
                1L,
                Angebotsstatus.ABGERECHNET,
                position(11L, "3.00", "100.00", J2024),
                position(12L, "4.00", "100.00", J2025)),
            angebot(2L, Angebotsstatus.BESTELLT, position(21L, "5.00", "100.00", J2026))),
        Map.of(12L, geld("4.00")));

    // When
    final Forecast forecast = useCase.forecast();

    // Then
    assertThat(forecast.jahre())
        .extracting(Forecastjahr::jahr, Forecastjahr::zugesagt)
        .containsExactly(tuple(J2024, geld("300.00")), tuple(J2026, geld("500.00")));
  }

  @Test
  void forecast_thenAnOpenPositionPricedAtZeroStandsWithZero() {
    // Given — offen ist die Menge, nicht der Betrag (E5).
    gegeben(
        List.of(angebot(1L, Angebotsstatus.BESTELLT, position(11L, "8.00", "0.00", J2026))),
        Map.of());

    // When
    final Forecast forecast = useCase.forecast();

    // Then
    assertThat(forecast.jahre())
        .singleElement()
        .satisfies(
            jahr -> {
              assertThat(jahr.zugesagt()).isEqualTo(geld("0.00"));
              assertThat(jahr.summe()).isEqualTo(geld("0.00"));
              assertThat(jahr.positionen())
                  .extracting(zeile -> zeile.position().requireId(), Forecastposition::netto)
                  .containsExactly(tuple(11L, geld("0.00")));
            });
  }

  @Test
  void forecast_thenYearsStandAscendingOldestFirst() {
    // Given — die Uebersicht liefert neueste zuerst; die Jahre der Positionen in loser Folge.
    gegeben(
        List.of(
            angebot(3L, Angebotsstatus.BESTELLT, position(31L, "1.00", "100.00", J2028)),
            angebot(2L, Angebotsstatus.ANGELEGT, position(21L, "1.00", "100.00", J2026)),
            angebot(1L, Angebotsstatus.ERLEDIGT, position(11L, "1.00", "100.00", J2027))),
        Map.of());

    // When
    final Forecast forecast = useCase.forecast();

    // Then
    assertThat(forecast.jahre())
        .extracting(Forecastjahr::jahr)
        .containsExactly(J2026, J2027, J2028);
  }

  @Test
  void forecastUseCase_thenNoPublicMethodTakesAZeitraum() {
    // Kriterium 7: Der Forecast haengt nicht an der Zeitraumwahl — an der Signatur pruefbar.
    final List<Method> oeffentliche =
        Arrays.stream(ForecastUseCase.class.getDeclaredMethods())
            .filter(methode -> Modifier.isPublic(methode.getModifiers()))
            .toList();

    assertThat(oeffentliche)
        .extracting(Method::getName, Method::getParameterCount)
        .containsExactly(tuple("forecast", 0));
  }
}
