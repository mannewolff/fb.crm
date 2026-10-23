package org.mwolff.fbcrm.arbeitszeit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.angebot.application.AngebotNichtGefunden;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.angebot.domain.Angebotsposition;
import org.mwolff.fbcrm.angebot.domain.Angebotsstatus;
import org.mwolff.fbcrm.arbeitszeit.domain.Zeiteintrag;
import org.mwolff.fbcrm.arbeitszeit.domain.ZeiteintragRepository;
import org.mwolff.fbcrm.auth.Konten;
import org.mwolff.fbcrm.auth.application.GetCurrentAccountUseCase;
import org.mwolff.fbcrm.auth.application.MitarbeitendeVerwaltung;
import org.mwolff.fbcrm.auth.application.UnknownAccount;
import org.mwolff.fbcrm.auth.domain.Account;
import org.mwolff.fbcrm.auth.domain.Role;
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
 *
 * <p><b>Welche Zeilen ein Konto sieht</b>, entscheidet seine Rolle (Plan #426, E8, E10, E14;
 * fachliche Quelle #410, Kriterien 2 bis 4 und 9). Die Konten des Beispiels: Manfred (C-Level),
 * Anna und Ben (Employee), Lea (ReadOnly) und Clara, deren Konto stillgelegt ist. Der Bestand der
 * Zeiten ist ein Doppel, das wie die echte Abfrage nach Konto filtert — so zeigt jeder Test,
 * welcher der beiden Lesewege gefragt wurde, an den Zeilen, die zurueckkommen. Die Faelle ohne
 * Rollenbezug fragt Manfred nach seinen eigenen Zeiten; ihre Eintraege gehoeren ihm.
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

  private static final long MANFRED = Zeitdoppel.KONTO;
  private static final long ANNA = 22L;
  private static final long BEN = 23L;
  private static final long LEA = 24L;
  private static final long CLARA = 25L;

  @Mock private ZeiteintragRepository zeiten;
  @Mock private AngebotRepository angebote;
  @Mock private FirmaRepository firmen;
  @Mock private GetCurrentAccountUseCase aktuellesKonto;
  @Mock private MitarbeitendeVerwaltung konten;

  private ArbeitszeitMonatUseCase useCase;

  @BeforeEach
  void baueDenAnwendungsfall() {
    useCase =
        new ArbeitszeitMonatUseCase(
            zeiten,
            angebote,
            firmen,
            new Personenauskunft(aktuellesKonto, konten),
            Clock.fixed(Zeitdoppel.JETZT, ZoneOffset.UTC));
  }

  /*
   * Ein Doppel des Bestands, das wie die echte Abfrage filtert: ueber alle Konten oder nur ueber
   * eines. Lenient, weil jeder Fall nur einen der beiden Wege fragt — welchen, zeigen die Zeilen,
   * die zurueckkommen, und wo es darauf ankommt, steht es als verify im Test.
   */
  private void imNovemberLiegen(final Zeiteintrag... eintraege) {
    final List<Zeiteintrag> alle = List.of(eintraege);
    lenient().when(zeiten.findImZeitraum(ERSTER, LETZTER)).thenReturn(alle);
    lenient()
        .when(zeiten.findImZeitraumFuerKonto(eq(ERSTER), eq(LETZTER), anyLong()))
        .thenAnswer(
            aufruf -> {
              final long konto = aufruf.getArgument(2);
              return alle.stream().filter(eintrag -> eintrag.accountId() == konto).toList();
            });
  }

  private static Account konto(
      final long id, final String name, final Role rolle, final boolean aktiv) {
    return Konten.mit(
            id, name.toLowerCase(Locale.ROOT) + "@example.org", rolle, aktiv, 0, Zeitdoppel.FRUEHER)
        .mitNamen(name, Zeitdoppel.FRUEHER);
  }

  private static Account manfred() {
    return konto(MANFRED, "Manfred", Role.C_LEVEL, true);
  }

  private static Account anna() {
    return konto(ANNA, "Anna", Role.EMPLOYEE, true);
  }

  private static Account ben() {
    return konto(BEN, "Ben", Role.EMPLOYEE, true);
  }

  private static Account lea() {
    return konto(LEA, "Lea", Role.READ_ONLY, true);
  }

  private static Account clara() {
    return konto(CLARA, "Clara", Role.EMPLOYEE, false);
  }

  /** Alle Konten in der Reihenfolge des Bestands, nach Anzeigenamen. */
  private static List<Account> alleKonten() {
    return List.of(anna(), ben(), clara(), lea(), manfred());
  }

  private void esFragt(final Account anfragend) {
    when(aktuellesKonto.byId(anfragend.requireId())).thenReturn(anfragend);
  }

  private void dieKontenGibtEs() {
    when(konten.liste()).thenReturn(alleKonten());
  }

  /** Ein Eintrag des genannten Kontos am Tag des Beispiels, auf der Konzeption. */
  private static Zeiteintrag von(
      final long id, final long konto, final String beginn, final String ende) {
    return Zeitdoppel.zeiteintrag(id, konto, Zeitdoppel.KONZEPTION_ID, beginn, ende);
  }

  /** Derselbe Eintrag am spaeteren Tag. */
  private static Zeiteintrag spaeter(final Zeiteintrag vorlage) {
    return new Zeiteintrag(
        vorlage.id(),
        vorlage.accountId(),
        vorlage.angebotPositionId(),
        SPAETER,
        vorlage.von(),
        vorlage.bis(),
        vorlage.createdAt(),
        vorlage.updatedAt());
  }

  private Arbeitsmonat november(final Account anfragend, final Optional<String> person) {
    return useCase.monat(anfragend.requireId(), Optional.of(NOVEMBER), person);
  }

  private static List<Long> zeilen(final Arbeitsmonat gelesen) {
    return gelesen.tage().stream()
        .flatMap(tag -> tag.buchungen().stream())
        .map(buchung -> buchung.eintrag().requireId())
        .toList();
  }

  private void dasAngebotGibtEs() {
    when(angebote.findAlle(Optional.empty())).thenReturn(List.of(Zeitdoppel.angebot()));
  }

  private void dieFirmaGibtEs() {
    when(firmen.findAllById(Set.of(Zeitdoppel.FIRMA))).thenReturn(List.of(Zeitdoppel.firma()));
  }

  private static Zeiteintrag amSpaeterenTag(final long id, final String von, final String bis) {
    return spaeter(Zeitdoppel.zeiteintrag(id, Zeitdoppel.KONZEPTION_ID, von, bis));
  }

  /** Ein Angebot einer anderen Firma mit einer eigenen Position, auf die nichts gebucht ist. */
  private static Angebot fremdesAngebot() {
    return new Angebot(
        Long.valueOf(FREMDES_ANGEBOT),
        FREMDE_FIRMA,
        null,
        false,
        Angebotsstatus.BESTELLT,
        false,
        null,
        ERSTER,
        "Website-Relaunch",
        "Wartungsvertrag",
        List.of(
            new Angebotsposition(
                Long.valueOf(FREMDE_POSITION),
                "Wartung",
                Abrechnungsmodus.AUFWAND,
                new BigDecimal("5.00"),
                Einheit.STUNDE,
                new BigDecimal("100.00"),
                Year.of(2026))),
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
        false,
        null,
        ERSTER,
        "Website-Relaunch",
        "Eigene Werkzeuge",
        List.of(
            new Angebotsposition(
                Long.valueOf(INTERNE_POSITION),
                "Werkzeugbau",
                Abrechnungsmodus.AUFWAND,
                new BigDecimal("8.00"),
                Einheit.STUNDE,
                BigDecimal.ZERO,
                Year.of(2026))),
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

  /** Manfred fragt ohne Auswahl, also nach seinen eigenen Zeiten. */
  private Arbeitsmonat november() {
    esFragt(manfred());
    dieKontenGibtEs();
    return november(manfred(), Optional.empty());
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
    assertThat(position.angebotTitel()).isEqualTo("Website-Relaunch");
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
            new Personenauskunft(aktuellesKonto, konten),
            Clock.fixed(Instant.parse("2026-10-31T23:30:00Z"), ZoneOffset.UTC));
    imNovemberLiegen();
    esFragt(manfred());
    dieKontenGibtEs();

    // When
    final Arbeitsmonat gelesen = useCase.monat(MANFRED, Optional.empty(), Optional.empty());

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

  @Test
  @DisplayName("Szenario: Employee sieht nur die eigenen Zeiten")
  void monat_scenario_anEmployeeSeesOnlyTheOwnEntries() {
    // Given — Anna und Ben haben im November Zeiten erfasst: Anna 1,75 Std. am 12. und 2,00 Std.
    // am 20., Ben 1,00 Std. am 12. und 3,00 Std. am 20.
    imNovemberLiegen(
        von(7L, ANNA, "09:00", "10:45"),
        von(8L, BEN, "11:00", "12:00"),
        spaeter(von(9L, ANNA, "13:00", "15:00")),
        spaeter(von(10L, BEN, "08:00", "11:00")));
    dasAngebotGibtEs();
    dieFirmaGibtEs();
    esFragt(anna());

    // When — Anna oeffnet als Employee die Ansicht „Arbeitszeit" fuer November.
    final Arbeitsmonat gelesen = november(anna(), Optional.empty());

    // Then — sie sieht nur ihre eigenen Eintraege und deren Summen.
    assertThat(zeilen(gelesen)).containsExactly(7L, 9L);
    assertThat(gelesen.tage())
        .extracting(Arbeitstag::stunden)
        .containsExactly(new BigDecimal("1.75"), new BigDecimal("2.00"));
    assertThat(gelesen.stunden()).isEqualByComparingTo("3.75");
    assertThat(gelesen.auswahl()).isEqualTo(new Personenauswahl.Einzeln(ANNA));
    // Jede Zeile traegt ihre Person, auch in der Sicht einer einzelnen (E14).
    assertThat(gelesen.tage())
        .flatExtracting(Arbeitstag::buchungen)
        .extracting(Zeitbuchung::person)
        .containsOnly(new WaehlbarePerson(ANNA, "Anna", true));
    // Fremde Eintraege kommen gar nicht erst in den Speicher (E8), fremde Konten auch nicht.
    verify(zeiten, never()).findImZeitraum(any(), any());
    verifyNoInteractions(konten);
  }

  @Test
  void monat_forACLevelWithoutAPerson_thenTheOwnEntries() {
    // Given — Kriterium 3: ein C-Level sieht zunaechst seine eigenen Eintraege.
    imNovemberLiegen(von(7L, MANFRED, "09:00", "10:45"), von(8L, ANNA, "11:00", "12:00"));
    dasAngebotGibtEs();
    dieFirmaGibtEs();
    esFragt(manfred());
    dieKontenGibtEs();

    // When
    final Arbeitsmonat gelesen = november(manfred(), Optional.empty());

    // Then
    assertThat(zeilen(gelesen)).containsExactly(7L);
    assertThat(gelesen.stunden()).isEqualByComparingTo("1.75");
    assertThat(gelesen.auswahl()).isEqualTo(new Personenauswahl.Einzeln(MANFRED));
  }

  @Test
  void monat_forReadOnlyWithoutAPerson_thenAll_evenIfEntriesBelongToTheAccount() {
    // Given — Kriterium 4: ReadOnly steht beim Oeffnen auf „Alle", auch mit eigenen Zeiten.
    imNovemberLiegen(von(7L, LEA, "09:00", "10:45"), von(8L, ANNA, "11:00", "12:00"));
    dasAngebotGibtEs();
    dieFirmaGibtEs();
    esFragt(lea());
    dieKontenGibtEs();

    // When
    final Arbeitsmonat gelesen = november(lea(), Optional.empty());

    // Then — beide Zeilen, und die Summen gelten fuer alle.
    assertThat(zeilen(gelesen)).containsExactly(7L, 8L);
    assertThat(gelesen.tage().get(0).stunden()).isEqualByComparingTo("2.75");
    assertThat(gelesen.stunden()).isEqualByComparingTo("2.75");
    assertThat(gelesen.auswahl()).isEqualTo(new Personenauswahl.Alle());
  }

  @ParameterizedTest
  @ValueSource(strings = {"alle", "21", "23", "999", "niemand"})
  void monat_forAnEmployeeWithAnyPerson_thenStillOnlyTheOwnEntries(final String person) {
    // Given — Kriterium 2: die Regel entscheidet der Server, nicht der Parameter (E10). Ob die
    // Anfrage „alle", ein fremdes, ein unbekanntes oder gar kein Konto nennt, aendert nichts.
    imNovemberLiegen(
        von(7L, ANNA, "09:00", "10:45"),
        von(8L, BEN, "11:00", "12:00"),
        von(9L, MANFRED, "13:00", "14:00"));
    dasAngebotGibtEs();
    dieFirmaGibtEs();
    esFragt(anna());
    final Arbeitsmonat ohneAuswahl = november(anna(), Optional.empty());

    // When
    final Arbeitsmonat gelesen = november(anna(), Optional.of(person));

    // Then
    assertThat(gelesen).isEqualTo(ohneAuswahl);
    assertThat(zeilen(gelesen)).containsExactly(7L);
    assertThat(gelesen.auswahl()).isEqualTo(new Personenauswahl.Einzeln(ANNA));
    verify(zeiten, never()).findImZeitraum(any(), any());
    verifyNoInteractions(konten);
  }

  @Test
  void monat_forACLevelChoosingAnotherPerson_thenOnlyTheirEntriesAndTheirSums() {
    // Given — Kriterium 3: ein C-Level schaltet auf Anna um.
    imNovemberLiegen(
        von(7L, MANFRED, "09:00", "10:45"),
        von(8L, ANNA, "11:00", "12:00"),
        spaeter(von(9L, ANNA, "13:00", "15:00")));
    dasAngebotGibtEs();
    dieFirmaGibtEs();
    esFragt(manfred());
    dieKontenGibtEs();

    // When
    final Arbeitsmonat gelesen = november(manfred(), Optional.of(String.valueOf(ANNA)));

    // Then — die Summen je Tag und Monat gelten nur fuer Annas Eintraege.
    assertThat(zeilen(gelesen)).containsExactly(8L, 9L);
    assertThat(gelesen.tage())
        .extracting(Arbeitstag::stunden)
        .containsExactly(new BigDecimal("1.00"), new BigDecimal("2.00"));
    assertThat(gelesen.stunden()).isEqualByComparingTo("3.00");
    assertThat(gelesen.auswahl()).isEqualTo(new Personenauswahl.Einzeln(ANNA));
  }

  @Test
  void monat_forReadOnlyChoosingAPerson_thenOnlyTheirEntries() {
    // Given — Kriterium 4: ReadOnly schaltet wie ein C-Level um.
    imNovemberLiegen(von(7L, MANFRED, "09:00", "10:45"), von(8L, ANNA, "11:00", "12:00"));
    dasAngebotGibtEs();
    dieFirmaGibtEs();
    esFragt(lea());
    dieKontenGibtEs();

    // When
    final Arbeitsmonat gelesen = november(lea(), Optional.of(String.valueOf(MANFRED)));

    // Then
    assertThat(zeilen(gelesen)).containsExactly(7L);
    assertThat(gelesen.auswahl()).isEqualTo(new Personenauswahl.Einzeln(MANFRED));
  }

  @Test
  void monat_forACLevelChoosingAll_thenEveryEntryWithTheNameOfItsPerson() {
    // Given — Kriterium 3: bei „Alle" steht an jedem Eintrag der Name der Person.
    imNovemberLiegen(von(7L, MANFRED, "09:00", "10:45"), von(8L, ANNA, "11:00", "12:00"));
    dasAngebotGibtEs();
    dieFirmaGibtEs();
    esFragt(manfred());
    dieKontenGibtEs();

    // When
    final Arbeitsmonat gelesen = november(manfred(), Optional.of("alle"));

    // Then — und die Summen gelten fuer alle.
    assertThat(gelesen.auswahl()).isEqualTo(new Personenauswahl.Alle());
    assertThat(gelesen.tage().get(0).buchungen())
        .extracting(Zeitbuchung::person)
        .containsExactly(
            new WaehlbarePerson(MANFRED, "Manfred", true), new WaehlbarePerson(ANNA, "Anna", true));
    assertThat(gelesen.stunden()).isEqualByComparingTo("2.75");
  }

  @Test
  void monat_inTheViewAll_theEntriesOfADeactivatedPersonStayWithTheirName() {
    // Given — Kriterium 9: Claras Konto ist stillgelegt, ihre Eintraege bleiben.
    imNovemberLiegen(von(7L, CLARA, "09:00", "10:45"));
    dasAngebotGibtEs();
    dieFirmaGibtEs();
    esFragt(manfred());
    dieKontenGibtEs();

    // When
    final Arbeitsmonat gelesen = november(manfred(), Optional.of("alle"));

    // Then
    assertThat(gelesen.tage().get(0).buchungen().get(0).person())
        .isEqualTo(new WaehlbarePerson(CLARA, "Clara", false));
  }

  @Test
  void monat_afterARename_theEntryCarriesTheCurrentName() {
    // Given — Kriterium 9: der Name kommt bei jedem Aufruf frisch aus dem Konto.
    imNovemberLiegen(von(7L, CLARA, "09:00", "10:45"));
    when(angebote.findAlle(Optional.empty())).thenReturn(List.of(Zeitdoppel.angebot()));
    when(firmen.findAllById(Set.of(Zeitdoppel.FIRMA))).thenReturn(List.of(Zeitdoppel.firma()));
    when(aktuellesKonto.byId(LEA)).thenReturn(lea());
    when(konten.liste())
        .thenReturn(alleKonten())
        .thenReturn(List.of(clara().mitNamen("Clara Berg", Zeitdoppel.JETZT), lea()));
    assertThat(november(lea(), Optional.empty()).tage().get(0).buchungen().get(0).person().name())
        .isEqualTo("Clara");

    // When
    final Arbeitsmonat gelesen = november(lea(), Optional.empty());

    // Then
    assertThat(gelesen.tage().get(0).buchungen().get(0).person())
        .isEqualTo(new WaehlbarePerson(CLARA, "Clara Berg", false));
  }

  @Test
  void monat_whenNothingIsRecorded_thenTheMonthStillNamesTheSelection() {
    // Given — auch ein leerer Monat sagt, wessen Zeiten er zeigt (E10).
    imNovemberLiegen();
    esFragt(lea());
    dieKontenGibtEs();

    // When
    final Arbeitsmonat gelesen = november(lea(), Optional.of(String.valueOf(CLARA)));

    // Then
    assertThat(gelesen.auswahl()).isEqualTo(new Personenauswahl.Einzeln(CLARA));
    assertThat(gelesen.tage()).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"999", "niemand", "Alle", ""})
  void monat_forACLevelWithAPersonThatIsNotSelectable_thenFails(final String person) {
    // Given — ein unbekanntes Konto oder ein Wort, das keine Auswahl ist.
    esFragt(manfred());
    dieKontenGibtEs();

    // When / Then
    assertThatExceptionOfType(PersonenauswahlUngueltig.class)
        .isThrownBy(() -> november(manfred(), Optional.of(person)));
    verifyNoInteractions(zeiten, angebote, firmen);
  }

  @Test
  void monat_givenAnAccountThatIsGone_thenTheSessionIsWorthless() {
    // Given — die Sitzung verweist auf ein Konto, das es nicht mehr gibt.
    when(aktuellesKonto.byId(MANFRED)).thenThrow(new UnknownAccount());

    // When / Then
    assertThatExceptionOfType(UnknownAccount.class)
        .isThrownBy(() -> useCase.monat(MANFRED, Optional.of(NOVEMBER), Optional.empty()));
    verifyNoInteractions(zeiten, konten);
  }
}
