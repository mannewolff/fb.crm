package org.mwolff.fbcrm.arbeitszeit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.angebot.application.AngebotNichtGefunden;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.angebot.domain.Angebotsposition;
import org.mwolff.fbcrm.angebot.domain.Angebotsstatus;
import org.mwolff.fbcrm.arbeitszeit.domain.Zeiteintrag;
import org.mwolff.fbcrm.arbeitszeit.domain.ZeiteintragRepository;
import org.mwolff.fbcrm.common.Abrechnungsmodus;
import org.mwolff.fbcrm.common.Einheit;
import org.mwolff.fbcrm.firma.application.FirmaNichtGefunden;
import org.mwolff.fbcrm.firma.domain.FirmaRepository;

/**
 * Die Monatsliste der Ansicht „Arbeitszeit" (Issue #193, Kriterium 5; Plan #194, A20).
 *
 * <p>Gegenstand sind die Reihenfolge — Tage aufsteigend, innerhalb eines Tages nach {@code von}
 * aufsteigend —, die Summe je Tag und die des Monats, und die Anreicherung jeder Zeile um ihre
 * Position mit Angebot und Firmenname. Die Namen kommen in <b>einem</b> Aufruf und nicht je Zeile,
 * wie in {@code AngeboteUebersichtUseCase}.
 *
 * <p>Der laufende Monat kommt aus der injizierten Uhr und wird in der Geschaeftszone gelesen (E4):
 * Der 1. November 2026 um 00:30 Ortszeit ist am Nullmeridian noch Oktober.
 */
@ExtendWith(MockitoExtension.class)
class ArbeitszeitMonatUseCaseTest {

  private static final YearMonth NOVEMBER = YearMonth.of(2026, 11);
  private static final LocalDate ERSTER = LocalDate.of(2026, 11, 1);
  private static final LocalDate LETZTER = LocalDate.of(2026, 11, 30);

  /** Der zweite Tag des Beispiels — Zeitdoppel.TAG ist der erste. */
  private static final LocalDate SPAETER = LocalDate.of(2026, 11, 20);

  /** Ein Angebot, das mit diesem Monat nichts zu tun hat, samt seiner Firma und Position. */
  private static final long FREMDES_ANGEBOT = 12L;

  private static final long FREMDE_FIRMA = 6L;

  private static final long FREMDE_POSITION = 201L;

  /** Ein internes Angebot derselben Firma, samt seiner Position. */
  private static final long INTERNES_ANGEBOT = 13L;

  private static final long INTERNE_POSITION = 301L;

  @Mock private ZeiteintragRepository zeiten;
  @Mock private AngebotRepository angebote;
  @Mock private FirmaRepository firmen;

  private ArbeitszeitMonatUseCase useCase;

  @BeforeEach
  void baueDenAnwendungsfall() {
    useCase =
        new ArbeitszeitMonatUseCase(
            zeiten, angebote, firmen, Clock.fixed(Zeitdoppel.JETZT, ZoneOffset.UTC));
  }

  private void imNovemberLiegen(final Zeiteintrag... eintraege) {
    when(zeiten.findImZeitraum(ERSTER, LETZTER)).thenReturn(List.of(eintraege));
  }

  private void dasAngebotGibtEs() {
    when(angebote.findAlle(Optional.empty())).thenReturn(List.of(Zeitdoppel.angebot()));
  }

  private void dieFirmaGibtEs() {
    when(firmen.findAllById(Set.of(Zeitdoppel.FIRMA))).thenReturn(List.of(Zeitdoppel.firma()));
  }

  private static Zeiteintrag amSpaeterenTag(final long id, final String von, final String bis) {
    final Zeiteintrag vorlage = Zeitdoppel.zeiteintrag(id, Zeitdoppel.KONZEPTION_ID, von, bis);
    return new Zeiteintrag(
        vorlage.id(),
        vorlage.angebotPositionId(),
        SPAETER,
        vorlage.von(),
        vorlage.bis(),
        vorlage.createdAt(),
        vorlage.updatedAt());
  }

  /** Ein Angebot einer anderen Firma mit einer eigenen Position, auf die nichts gebucht ist. */
  private static Angebot fremdesAngebot() {
    return new Angebot(
        Long.valueOf(FREMDES_ANGEBOT),
        FREMDE_FIRMA,
        null,
        false,
        Angebotsstatus.BESTELLT,
        ERSTER,
        "Wartungsvertrag",
        List.of(
            new Angebotsposition(
                Long.valueOf(FREMDE_POSITION),
                "Wartung",
                Abrechnungsmodus.AUFWAND,
                new BigDecimal("5.00"),
                Einheit.STUNDE,
                new BigDecimal("100.00"))),
        Zeitdoppel.FRUEHER,
        Zeitdoppel.FRUEHER);
  }

  /** Ein internes Angebot derselben Firma mit einer buchbaren Position (Issue #229). */
  private static Angebot internesAngebot() {
    return new Angebot(
        Long.valueOf(INTERNES_ANGEBOT),
        Zeitdoppel.FIRMA,
        null,
        true,
        Angebotsstatus.LAEUFT,
        ERSTER,
        "Eigene Werkzeuge",
        List.of(
            new Angebotsposition(
                Long.valueOf(INTERNE_POSITION),
                "Werkzeugbau",
                Abrechnungsmodus.AUFWAND,
                new BigDecimal("8.00"),
                Einheit.STUNDE,
                BigDecimal.ZERO)),
        Zeitdoppel.FRUEHER,
        Zeitdoppel.FRUEHER);
  }

  /** Ein Eintrag auf der internen Position, am Tag des Beispiels. */
  private static Zeiteintrag intern(final long id, final String von, final String bis) {
    return Zeitdoppel.zeiteintrag(id, INTERNE_POSITION, von, bis);
  }

  private void beideAngeboteGibtEs() {
    when(angebote.findAlle(Optional.empty()))
        .thenReturn(List.of(Zeitdoppel.angebot(), internesAngebot()));
  }

  private Arbeitsmonat november() {
    return useCase.monat(Optional.of(NOVEMBER));
  }

  @Test
  void monat_whenNothingIsRecorded_thenNoDaysAndTheSumIsZero() {
    // Given — ein leerer Monat fragt den Bestand der Angebote gar nicht (Muster der Uebersicht).
    imNovemberLiegen();

    // When
    final Arbeitsmonat gelesen = november();

    // Then
    assertThat(gelesen.monat()).isEqualTo(NOVEMBER);
    assertThat(gelesen.tage()).isEmpty();
    assertThat(gelesen.stunden()).isEqualByComparingTo("0.00");
    assertThat(gelesen.stundenFuerKunden()).isEqualByComparingTo("0.00");
    assertThat(gelesen.stundenIntern()).isEqualByComparingTo("0.00");
    verifyNoInteractions(angebote, firmen);
  }

  @Test
  void monat_thenGroupsTheEntriesByDayAscending() {
    // Given — der spaetere Tag kommt zuerst aus dem Bestand; die Ordnung macht der Anwendungsfall.
    imNovemberLiegen(
        amSpaeterenTag(9L, "13:00", "15:00"),
        Zeitdoppel.zeiteintrag(7L, Zeitdoppel.KONZEPTION_ID, "09:00", "10:45"));
    dasAngebotGibtEs();
    dieFirmaGibtEs();

    // When
    final Arbeitsmonat gelesen = november();

    // Then
    assertThat(gelesen.tage()).extracting(Arbeitstag::tag).containsExactly(Zeitdoppel.TAG, SPAETER);
  }

  @Test
  void monat_thenOrdersTheEntriesOfADayByTheirBeginning() {
    // Given — zwei Eintraege desselben Tages, der spaetere zuerst.
    imNovemberLiegen(
        Zeitdoppel.zeiteintrag(8L, Zeitdoppel.WARTUNG_ID, "13:00", "15:00"),
        Zeitdoppel.zeiteintrag(7L, Zeitdoppel.KONZEPTION_ID, "09:00", "10:45"));
    dasAngebotGibtEs();
    dieFirmaGibtEs();

    // When
    final List<Zeitbuchung> buchungen = november().tage().get(0).buchungen();

    // Then
    assertThat(buchungen).extracting(buchung -> buchung.eintrag().id()).containsExactly(7L, 8L);
  }

  @Test
  void monat_thenCarriesTheSumOfEachDayAndOfTheMonth() {
    // Given — 1,75 Std. am ersten Tag, 2,00 Std. am zweiten.
    imNovemberLiegen(
        Zeitdoppel.zeiteintrag(7L, Zeitdoppel.KONZEPTION_ID, "09:00", "10:45"),
        amSpaeterenTag(9L, "13:00", "15:00"));
    dasAngebotGibtEs();
    dieFirmaGibtEs();

    // When
    final Arbeitsmonat gelesen = november();

    // Then
    assertThat(gelesen.tage().get(0).stunden()).isEqualByComparingTo("1.75");
    assertThat(gelesen.tage().get(1).stunden()).isEqualByComparingTo("2.00");
    assertThat(gelesen.stunden()).isEqualByComparingTo("3.75");
  }

  @Test
  void monat_thenEachEntryCarriesItsPositionWithOfferAndCompany() {
    // Given — A20: die Zeile zeigt die Position, das Angebot und die Firma.
    imNovemberLiegen(Zeitdoppel.zeiteintrag(7L, Zeitdoppel.KONZEPTION_ID, "09:00", "10:45"));
    dasAngebotGibtEs();
    dieFirmaGibtEs();

    // When
    final Buchungsposition position = november().tage().get(0).buchungen().get(0).position();

    // Then
    assertThat(position.id()).isEqualTo(Zeitdoppel.KONZEPTION_ID);
    assertThat(position.bezeichnung()).isEqualTo("Konzeption");
    assertThat(position.angebotId()).isEqualTo(Zeitdoppel.ANGEBOT);
    assertThat(position.angebotDatum()).isEqualTo(ERSTER);
    assertThat(position.firmaName()).isEqualTo(Zeitdoppel.FIRMENNAME);
  }

  @Test
  void monat_thenAsksForTheCompanyNamesInOneCall() {
    // Given — zwei Eintraege auf zwei Positionen desselben Angebots.
    imNovemberLiegen(
        Zeitdoppel.zeiteintrag(7L, Zeitdoppel.KONZEPTION_ID, "09:00", "10:45"),
        Zeitdoppel.zeiteintrag(8L, Zeitdoppel.WARTUNG_ID, "13:00", "15:00"));
    dasAngebotGibtEs();
    dieFirmaGibtEs();

    // When
    november();

    // Then — ein Zug fuer alle beteiligten Firmen, keiner je Zeile.
    verify(firmen).findAllById(Set.of(Zeitdoppel.FIRMA));
    verify(angebote).findAlle(Optional.empty());
  }

  @Test
  void monat_withoutAMonth_thenReadsTheCurrentMonthInTheBusinessZone() {
    // Given — 23:30 UTC am letzten Oktobertag ist in Europe/Berlin schon der 1. November (E4).
    // Am Nullmeridian gelesen waere es Oktober und die Ansicht zeigte den falschen Monat.
    useCase =
        new ArbeitszeitMonatUseCase(
            zeiten,
            angebote,
            firmen,
            Clock.fixed(Instant.parse("2026-10-31T23:30:00Z"), ZoneOffset.UTC));
    imNovemberLiegen();

    // When
    final Arbeitsmonat gelesen = useCase.monat(Optional.empty());

    // Then
    assertThat(gelesen.monat()).isEqualTo(NOVEMBER);
  }

  @Test
  void monat_whenTheOfferOfABookedPositionIsGone_thenFails() {
    // Given — ein Widerspruch im Bestand: der Fremdschluessel haelt jede gebuchte Position fest.
    imNovemberLiegen(Zeitdoppel.zeiteintrag(7L, Zeitdoppel.FREMDE_POSITION, "09:00", "10:45"));
    dasAngebotGibtEs();

    // When / Then
    assertThatExceptionOfType(AngebotNichtGefunden.class).isThrownBy(this::november);
    // Und ohne beteiligtes Angebot wird nach keinem Firmennamen gefragt.
    verifyNoInteractions(firmen);
  }

  @Test
  void monat_thenAsksOnlyForTheCompaniesOfTheOffersItActuallyNeeds() {
    // Given — im Bestand steht ein zweites Angebot einer anderen Firma, auf das nichts gebucht ist.
    // Dessen Firma kennt der Bestand der Firmen hier nicht; sie darf darum auch nicht gefragt
    // werden — sonst scheiterte die Monatsliste an einem Angebot, das sie nicht anzeigt.
    imNovemberLiegen(Zeitdoppel.zeiteintrag(7L, Zeitdoppel.KONZEPTION_ID, "09:00", "10:45"));
    when(angebote.findAlle(Optional.empty()))
        .thenReturn(List.of(Zeitdoppel.angebot(), fremdesAngebot()));
    dieFirmaGibtEs();

    // When
    final Arbeitsmonat gelesen = november();

    // Then
    assertThat(gelesen.tage()).hasSize(1);
    verify(firmen).findAllById(Set.of(Zeitdoppel.FIRMA));
  }

  @Test
  void monat_whenBothKindsAreBooked_thenTheMonthSumSplitsIntoCustomerAndInternal() {
    // Given — Kriterium 10 von #207: 1,75 Std. fuer einen Kunden und 1,00 Std. intern, an
    // demselben Tag. Der Tag teilt nicht auf, der Monat schon.
    imNovemberLiegen(
        Zeitdoppel.zeiteintrag(7L, Zeitdoppel.KONZEPTION_ID, "09:00", "10:45"),
        intern(10L, "11:00", "12:00"));
    beideAngeboteGibtEs();
    dieFirmaGibtEs();

    // When
    final Arbeitsmonat gelesen = november();

    // Then
    assertThat(gelesen.stunden()).isEqualByComparingTo("2.75");
    assertThat(gelesen.stundenFuerKunden()).isEqualByComparingTo("1.75");
    assertThat(gelesen.stundenIntern()).isEqualByComparingTo("1.00");
    assertThat(gelesen.stunden())
        .isEqualByComparingTo(gelesen.stundenFuerKunden().add(gelesen.stundenIntern()));
    // Und die Tagessumme bleibt die ungeteilte des Tages mit beiden Arten.
    assertThat(gelesen.tage()).hasSize(1);
    assertThat(gelesen.tage().get(0).stunden()).isEqualByComparingTo("2.75");
  }

  @Test
  void monat_whenOnlyInternalWorkIsBooked_thenTheCustomerShareIsZero() {
    // Given
    imNovemberLiegen(intern(10L, "11:00", "12:00"));
    beideAngeboteGibtEs();
    dieFirmaGibtEs();

    // When
    final Arbeitsmonat gelesen = november();

    // Then
    assertThat(gelesen.stundenFuerKunden()).isEqualByComparingTo("0.00");
    assertThat(gelesen.stundenIntern()).isEqualByComparingTo(gelesen.stunden());
    assertThat(gelesen.stunden()).isEqualByComparingTo("1.00");
  }

  @Test
  void monat_whenOnlyCustomerWorkIsBooked_thenTheInternalShareIsZero() {
    // Given
    imNovemberLiegen(Zeitdoppel.zeiteintrag(7L, Zeitdoppel.KONZEPTION_ID, "09:00", "10:45"));
    dasAngebotGibtEs();
    dieFirmaGibtEs();

    // When
    final Arbeitsmonat gelesen = november();

    // Then
    assertThat(gelesen.stundenIntern()).isEqualByComparingTo("0.00");
    assertThat(gelesen.stundenFuerKunden()).isEqualByComparingTo(gelesen.stunden());
    assertThat(gelesen.stunden()).isEqualByComparingTo("1.75");
  }

  @Test
  void monat_whenTheCompanyOfTheOfferIsGone_thenFails() {
    // Given — Firmen werden nie geloescht; eine fehlende waere ein Widerspruch im Bestand.
    imNovemberLiegen(Zeitdoppel.zeiteintrag(7L, Zeitdoppel.KONZEPTION_ID, "09:00", "10:45"));
    dasAngebotGibtEs();
    when(firmen.findAllById(Set.of(Zeitdoppel.FIRMA))).thenReturn(List.of());

    // When / Then
    assertThatExceptionOfType(FirmaNichtGefunden.class).isThrownBy(this::november);
  }
}
