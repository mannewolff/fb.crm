package org.mwolff.fbcrm.arbeitszeit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.angebot.domain.Angebotsstatus;
import org.mwolff.fbcrm.arbeitszeit.domain.Zeiteintrag;
import org.mwolff.fbcrm.arbeitszeit.domain.ZeiteintragRepository;
import org.mwolff.fbcrm.auth.Konten;
import org.mwolff.fbcrm.auth.application.GetCurrentAccountUseCase;
import org.mwolff.fbcrm.auth.application.MitarbeitendeVerwaltung;
import org.mwolff.fbcrm.auth.domain.Account;
import org.mwolff.fbcrm.auth.domain.Role;
import org.mwolff.fbcrm.common.Bearbeitungskonflikt;
import org.mwolff.fbcrm.firma.application.FirmaNichtGefunden;
import org.mwolff.fbcrm.firma.domain.FirmaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Das Erfassen, Aendern und Loeschen eines Zeiteintrags (Issue #193, Kriterien 1, 3, 4 und 6).
 *
 * <p>Gegenstand ist alles, was der Anwendungsfall entscheidet, und in der Reihenfolge, in der er es
 * entscheidet (Plan #194, A19, A7, A8): Raster und {@code bis > von} als Meldung am Feld, „Buchung
 * zulaessig" beim Anlegen und beim Wechsel der Position, und die Ueberschneidung ueber alle
 * Positionen desselben Tages — beim Aendern ohne den Eintrag selbst.
 *
 * <p>Die beiden Wege, die am abgerechneten Angebot offen bleiben, stehen bewusst daneben: Tag und
 * Uhrzeit eines alten Eintrags bleiben aenderbar, und Loeschen ist immer erlaubt (A7, Antwort 4).
 *
 * <p><b>Rechte je Eintrag und je Rolle</b> (Plan #426, E7, E12, E13, E19; fachliche Quelle #410,
 * Kriterien 1 bis 5 und 9): Ein Employee schreibt nur die eigenen Eintraege, ein C-Level jeden und
 * auch fuer andere aktive Personen, ReadOnly nichts — auch nicht die eigenen. Die Ueberschneidung
 * gilt je Person. Die Konten sind Anna und Ben (Employee), Manfred (C-Level), Clara (stillgelegt)
 * und Lea (ReadOnly). Die Szenarien und die Rechtefaelle laufen gegen einen {@link Zeitbestand} im
 * Speicher, damit „der Bestand ist danach unveraendert" und „angefallen" an echten Zeilen gemessen
 * werden und nicht an einem Mock, der beides vorgibt.
 */
@ExtendWith(MockitoExtension.class)
class ZeiteintragUseCaseTest {

  private static final long EINTRAG = 4L;
  private static final long FREMDER_EINTRAG = 5L;
  private static final long ANDERES_KONTO = 22L;

  /** Anna — dieselbe Kennung wie {@link Zeitdoppel#KONTO}, also die Anfragende der Grundfaelle. */
  private static final long ANNA = Zeitdoppel.KONTO;

  private static final long BEN = ANDERES_KONTO;
  private static final long MANFRED = 1L;
  private static final long CLARA = 3L;
  private static final long LEA = 6L;
  private static final long UNBEKANNT = 999L;

  /** Der Stand, den die Zeiteintraege des {@link Zeitdoppel} tragen und den die Ansicht vorlegt. */
  private static final long STAND = 0L;

  private static final LocalDate DRITTER = LocalDate.of(2026, 11, 3);
  private static final LocalDate VIERTER = LocalDate.of(2026, 11, 4);
  private static final LocalDate FUENFTER = LocalDate.of(2026, 11, 5);

  /** Wann Manfred einen Eintrag geloescht hat (Plan #415, E17). */
  private static final Instant GELOESCHT_UM = Instant.parse("2026-11-12T16:00:00Z");

  @Mock private ZeiteintragRepository zeiten;
  @Mock private AngebotRepository angebote;
  @Mock private FirmaRepository firmen;
  @Mock private GetCurrentAccountUseCase aktuellesKonto;
  @Mock private MitarbeitendeVerwaltung konten;

  private ZeiteintragUseCase useCase;

  /*
   * Die Konten stehen fuer jeden Test bereit, nachsichtig gestubbt: Welche davon ein Test liest,
   * haengt an seinem Weg — das anfragende Konto immer, die Liste aller Konten nur, wenn ein C-Level
   * eine andere Person nennt. Ein Test, der eine Abfrage erwartet, prueft sie selbst.
   */
  @BeforeEach
  void baueDenAnwendungsfall() {
    final List<Account> alle =
        List.of(
            konto(ANNA, "Anna", Role.EMPLOYEE, true),
            konto(BEN, "Ben", Role.EMPLOYEE, true),
            konto(CLARA, "Clara", Role.EMPLOYEE, false),
            konto(LEA, "Lea", Role.READ_ONLY, true),
            konto(MANFRED, "Manfred", Role.C_LEVEL, true));
    for (final Account konto : alle) {
      lenient().when(aktuellesKonto.byId(konto.requireId())).thenReturn(konto);
    }
    lenient().when(konten.liste()).thenReturn(alle);
    useCase = useCaseMit(zeiten);
  }

  private ZeiteintragUseCase useCaseMit(final ZeiteintragRepository bestand) {
    return new ZeiteintragUseCase(
        bestand,
        angebote,
        firmen,
        aktuellesKonto,
        konten,
        Clock.fixed(Zeitdoppel.JETZT, ZoneOffset.UTC));
  }

  private static Account konto(
      final long id, final String name, final Role rolle, final boolean aktiv) {
    return Konten.mit(
            Long.valueOf(id),
            name.toLowerCase(Locale.ROOT) + "@example.org",
            rolle,
            aktiv,
            0,
            Zeitdoppel.FRUEHER)
        .mitNamen(name, Zeitdoppel.FRUEHER);
  }

  private void angebotStehtIn(final Angebotsstatus status) {
    when(angebote.findAlle(Optional.empty())).thenReturn(List.of(Zeitdoppel.angebot(status)));
  }

  private void stornierteAngebote() {
    when(angebote.findAlle(Optional.empty())).thenReturn(List.of(Zeitdoppel.storniertesAngebot()));
  }

  private void keinEintragAmTag() {
    when(zeiten.findImZeitraumFuerKonto(Zeitdoppel.TAG, Zeitdoppel.TAG, ANNA))
        .thenReturn(List.of());
  }

  /*
   * Was am Tag der Anna gehoert — mehr fragt die Pruefung nicht (E8): Die Ueberschneidung gilt je
   * Person, und gefiltert wird im Bestand.
   */
  private void amTagLiegt(final Zeiteintrag... vorhandene) {
    amTagLiegtFuer(ANNA, vorhandene);
  }

  private void amTagLiegtFuer(final long accountId, final Zeiteintrag... vorhandene) {
    when(zeiten.findImZeitraumFuerKonto(Zeitdoppel.TAG, Zeitdoppel.TAG, accountId))
        .thenReturn(List.of(vorhandene));
  }

  private void eintragGibtEs(final Zeiteintrag vorhandener) {
    when(zeiten.findById(EINTRAG)).thenReturn(Optional.of(vorhandener));
  }

  private void firmaGibtEs() {
    when(firmen.findById(Zeitdoppel.FIRMA)).thenReturn(Optional.of(Zeitdoppel.firma()));
  }

  private void speichernGibtZurueck() {
    when(zeiten.save(any())).thenAnswer(aufruf -> aufruf.getArgument(0));
  }

  private Zeiteintrag anlegen(final long angebotPositionId, final String von, final String bis) {
    return useCase.anlegen(
        ANNA,
        Optional.empty(),
        angebotPositionId,
        Zeitdoppel.TAG,
        LocalTime.parse(von),
        LocalTime.parse(bis));
  }

  private Zeiteintrag aendern(final long angebotPositionId, final String von, final String bis) {
    return aendern(ANNA, angebotPositionId, von, bis);
  }

  private Zeiteintrag aendern(
      final long anfragend, final long angebotPositionId, final String von, final String bis) {
    return useCase.aendern(
        anfragend,
        EINTRAG,
        STAND,
        Optional.empty(),
        angebotPositionId,
        Zeitdoppel.TAG,
        LocalTime.parse(von),
        LocalTime.parse(bis));
  }

  private static Zeiteintrag konzeption(final String von, final String bis) {
    return Zeitdoppel.zeiteintrag(EINTRAG, Zeitdoppel.KONZEPTION_ID, von, bis);
  }

  @Test
  void anlegen_givenABookablePositionAndAFreeSlot_thenStoresTheEntryWithBothTimestamps() {
    // Given — Kriterium 1; der Eintrag gehoert dem anfragenden Konto (Plan #426, E1).
    angebotStehtIn(Angebotsstatus.BESTELLT);
    keinEintragAmTag();
    speichernGibtZurueck();

    // When
    final Zeiteintrag angelegt = anlegen(Zeitdoppel.KONZEPTION_ID, "09:00", "10:45");

    // Then
    assertThat(angelegt)
        .isEqualTo(
            new Zeiteintrag(
                null,
                Zeitdoppel.KONTO,
                Zeitdoppel.KONZEPTION_ID,
                Zeitdoppel.TAG,
                LocalTime.of(9, 0),
                LocalTime.of(10, 45),
                Zeitdoppel.JETZT,
                Zeitdoppel.JETZT));
  }

  @ParameterizedTest
  @ValueSource(strings = {"09:10", "09:01", "09:14"})
  void anlegen_givenABeginnOutsideTheGrid_thenReportsItAtVon(final String von) {
    // Given — A19: Kriterium 3 verlangt die Meldung am Feld, nicht einen Fehler der Datenbank.
    // When / Then
    assertThatExceptionOfType(UhrzeitNichtImRaster.class)
        .isThrownBy(() -> anlegen(Zeitdoppel.KONZEPTION_ID, von, "11:00"))
        .satisfies(
            fehler -> assertThat(fehler.felder()).containsOnlyKeys(UhrzeitNichtImRaster.FELD_VON));
    verify(zeiten, never()).save(any());
  }

  @Test
  void anlegen_givenAnEndeOutsideTheGrid_thenReportsItAtBis() {
    // Given — dieselbe Regel, das andere Feld.
    // When / Then
    assertThatExceptionOfType(UhrzeitNichtImRaster.class)
        .isThrownBy(() -> anlegen(Zeitdoppel.KONZEPTION_ID, "09:00", "11:05"))
        .satisfies(
            fehler -> assertThat(fehler.felder()).containsOnlyKeys(UhrzeitNichtImRaster.FELD_BIS));
  }

  @ParameterizedTest
  @CsvSource({"09:00,09:00", "11:00,09:00"})
  void anlegen_givenAnEndeNotAfterTheBeginn_thenReportsItAtBis(final String von, final String bis) {
    // Given — A19: die Gleichheit ist schon zu wenig, die Umkehrung erst recht.
    // When / Then
    assertThatExceptionOfType(EndeVorBeginn.class)
        .isThrownBy(() -> anlegen(Zeitdoppel.KONZEPTION_ID, von, bis))
        .satisfies(fehler -> assertThat(fehler.felder()).containsOnlyKeys(EndeVorBeginn.FELD));
    verify(zeiten, never()).save(any());
  }

  @Test
  void anlegen_givenAnUnknownPosition_thenReportsItAtTheFieldOfThePosition() {
    // Given — eine Kennung, die zu keinem Angebot gehoert. Dass es sie nicht gibt, verraet die
    // Antwort nicht: Es ist dieselbe Lage wie eine Position, auf die nicht gebucht werden darf.
    angebotStehtIn(Angebotsstatus.BESTELLT);

    // When / Then — die Meldung ist die des Kundenangebots (E22): Die Art eines Angebots, das es
    // nicht gibt, ist nicht bestimmbar.
    assertThatExceptionOfType(PositionNichtBuchbar.class)
        .isThrownBy(() -> anlegen(Zeitdoppel.FREMDE_POSITION, "09:00", "11:00"))
        .satisfies(
            fehler ->
                assertThat(fehler.felder())
                    .isEqualTo(
                        Map.of(
                            PositionNichtBuchbar.FELD,
                            List.of(PositionNichtBuchbar.MELDUNG_EXTERN))));
    verify(zeiten, never()).save(any());
  }

  @Test
  void anlegen_givenAFlatRatePosition_thenReportsItAtTheFieldOfThePosition() {
    // Given — Antworten 3 und 5: gebucht wird nur nach Aufwand in Stunden.
    angebotStehtIn(Angebotsstatus.BESTELLT);

    // When / Then
    assertThatExceptionOfType(PositionNichtBuchbar.class)
        .isThrownBy(() -> anlegen(Zeitdoppel.SCHULUNG_ID, "09:00", "11:00"));
    verify(zeiten, never()).save(any());
  }

  @Test
  void anlegen_givenAnAngebotThatIsNotOrderedYet_thenReportsItAtTheFieldOfThePosition() {
    // Given — Antwort 2: vor der Zusage des Kunden gibt es nichts zu buchen.
    angebotStehtIn(Angebotsstatus.ABGEGEBEN);

    // When / Then — die Meldung des Kundenangebots nennt „bestellt oder erledigt" (E22).
    assertThatExceptionOfType(PositionNichtBuchbar.class)
        .isThrownBy(() -> anlegen(Zeitdoppel.KONZEPTION_ID, "09:00", "11:00"))
        .satisfies(
            fehler ->
                assertThat(fehler.felder())
                    .isEqualTo(
                        Map.of(
                            PositionNichtBuchbar.FELD,
                            List.of(PositionNichtBuchbar.MELDUNG_EXTERN))));
    verify(zeiten, never()).save(any());
  }

  @Test
  void anlegen_givenACancelledAngebot_thenReportsItAtTheFieldOfThePosition() {
    // Given — #330, Kriterium 7: auf ein storniertes Angebot wird keine Arbeitszeit mehr gebucht.
    stornierteAngebote();

    // When / Then — die Meldung des Kundenangebots nennt das Storno (E16).
    assertThatExceptionOfType(PositionNichtBuchbar.class)
        .isThrownBy(() -> anlegen(Zeitdoppel.KONZEPTION_ID, "09:00", "11:00"))
        .satisfies(
            fehler ->
                assertThat(fehler.felder())
                    .isEqualTo(
                        Map.of(
                            PositionNichtBuchbar.FELD,
                            List.of(PositionNichtBuchbar.MELDUNG_EXTERN))));
    verify(zeiten, never()).save(any());
  }

  @Test
  void anlegen_givenFinishedInternalWork_thenReportsItWithTheInternalMessage() {
    // Given — Kriterium 5 von #207: gebucht wird nur, solange die interne Arbeit laeuft. Der Satz
    // des Kundenangebots waere hier falsch, und er erscheint am Feld der Positionswahl (E22).
    angebotStehtIn(Angebotsstatus.ABGESCHLOSSEN);

    // When / Then
    assertThatExceptionOfType(PositionNichtBuchbar.class)
        .isThrownBy(() -> anlegen(Zeitdoppel.KONZEPTION_ID, "09:00", "11:00"))
        .satisfies(
            fehler ->
                assertThat(fehler.felder())
                    .isEqualTo(
                        Map.of(
                            PositionNichtBuchbar.FELD,
                            List.of(PositionNichtBuchbar.MELDUNG_INTERN))));
    verify(zeiten, never()).save(any());
  }

  @Test
  void anlegen_givenAFlatRatePositionOfRunningInternalWork_thenStoresTheEntry() {
    // Given — E11: an der laufenden internen Arbeit traegt auch die Pauschale Stunden.
    angebotStehtIn(Angebotsstatus.LAEUFT);
    keinEintragAmTag();
    speichernGibtZurueck();

    // When
    final Zeiteintrag gespeichert = anlegen(Zeitdoppel.SCHULUNG_ID, "09:00", "11:00");

    // Then
    assertThat(gespeichert.angebotPositionId()).isEqualTo(Zeitdoppel.SCHULUNG_ID);
  }

  @Test
  void anlegen_givenAnOverlapOnAnotherPosition_thenNamesTheOtherEntryWithTimePositionAndFirma() {
    // Given — A8: niemand arbeitet zur selben Zeit fuer zwei Kunden (Kriterium 4).
    angebotStehtIn(Angebotsstatus.BESTELLT);
    amTagLiegt(Zeitdoppel.zeiteintrag(FREMDER_EINTRAG, Zeitdoppel.KONZEPTION_ID, "09:00", "11:00"));
    firmaGibtEs();

    // When / Then
    assertThatExceptionOfType(ZeitenUeberschneidenSich.class)
        .isThrownBy(() -> anlegen(Zeitdoppel.WARTUNG_ID, "10:00", "12:00"))
        .withMessage("Überschneidet sich mit 9:00 bis 11:00, Konzeption (IT Bildungshaus).");
    verify(zeiten, never()).save(any());
  }

  @Test
  void anlegen_givenAnOverlap_thenReportsItAtBothTimeFields() {
    // Given — A8: die Meldung haengt an von und an bis, weil erst beide zusammen die Lage ergeben.
    angebotStehtIn(Angebotsstatus.BESTELLT);
    amTagLiegt(Zeitdoppel.zeiteintrag(FREMDER_EINTRAG, Zeitdoppel.KONZEPTION_ID, "09:00", "11:00"));
    firmaGibtEs();

    // When / Then
    assertThatExceptionOfType(ZeitenUeberschneidenSich.class)
        .isThrownBy(() -> anlegen(Zeitdoppel.KONZEPTION_ID, "10:00", "12:00"))
        .satisfies(
            fehler ->
                assertThat(fehler.felder())
                    .containsOnlyKeys(
                        ZeitenUeberschneidenSich.FELD_VON, ZeitenUeberschneidenSich.FELD_BIS));
  }

  @Test
  void anlegen_givenTouchingBoundaries_thenAccepted() {
    // Given — A8: 9:00 bis 10:00 und 10:00 bis 11:00 sind zwei Eintraege und kein Widerspruch.
    angebotStehtIn(Angebotsstatus.BESTELLT);
    amTagLiegt(Zeitdoppel.zeiteintrag(FREMDER_EINTRAG, Zeitdoppel.KONZEPTION_ID, "09:00", "10:00"));
    speichernGibtZurueck();

    // When
    final Zeiteintrag angelegt = anlegen(Zeitdoppel.KONZEPTION_ID, "10:00", "11:00");

    // Then
    assertThat(angelegt.von()).isEqualTo(LocalTime.of(10, 0));
  }

  @Test
  void anlegen_whenTheFirmaOfTheCollidingEntryIsMissing_thenReportsTheContradiction() {
    // Given — Firmen werden nie geloescht; fehlt die Firma, widerspricht sich der Bestand. Die
    // Meldung der Ueberschneidung nennt den Firmennamen und darf ihn nicht erfinden.
    angebotStehtIn(Angebotsstatus.BESTELLT);
    amTagLiegt(Zeitdoppel.zeiteintrag(FREMDER_EINTRAG, Zeitdoppel.KONZEPTION_ID, "09:00", "11:00"));
    when(firmen.findById(Zeitdoppel.FIRMA)).thenReturn(Optional.empty());

    // When / Then
    assertThatExceptionOfType(FirmaNichtGefunden.class)
        .isThrownBy(() -> anlegen(Zeitdoppel.KONZEPTION_ID, "10:00", "12:00"));
  }

  @Test
  void aendern_givenAnotherTime_thenKeepsCreatedAtAndSetsUpdatedAtFromTheClock() {
    // Given — Kriterium 6; der Eintrag bleibt auf seiner Position und bei seiner Person.
    eintragGibtEs(konzeption("09:00", "11:00"));
    amTagLiegt(konzeption("09:00", "11:00"));
    speichernGibtZurueck();

    // When
    final Zeiteintrag geaendert = aendern(Zeitdoppel.KONZEPTION_ID, "09:00", "12:00");

    // Then
    assertThat(geaendert)
        .isEqualTo(
            new Zeiteintrag(
                Long.valueOf(EINTRAG),
                Zeitdoppel.KONTO,
                Zeitdoppel.KONZEPTION_ID,
                Zeitdoppel.TAG,
                LocalTime.of(9, 0),
                LocalTime.of(12, 0),
                Zeitdoppel.FRUEHER,
                Zeitdoppel.JETZT,
                STAND,
                Long.valueOf(ANNA),
                null,
                null));
  }

  @Test
  void aendern_onAnOutdatedStand_thenRejectsWithUrheberAndTimeAndStoresNothing() {
    // Given — #411, Kriterium 1: Ben hat den Eintrag inzwischen geaendert, sein Stand ist 1.
    eintragGibtEs(
        new Zeiteintrag(
            Long.valueOf(EINTRAG),
            ANNA,
            Zeitdoppel.KONZEPTION_ID,
            Zeitdoppel.TAG,
            LocalTime.of(9, 0),
            LocalTime.of(11, 0),
            Zeitdoppel.FRUEHER,
            Zeitdoppel.JETZT,
            STAND + 1,
            Long.valueOf(BEN),
            null,
            null));

    // When
    final ZeiteintragGeaendert konflikt =
        catchThrowableOfType(
            ZeiteintragGeaendert.class, () -> aendern(Zeitdoppel.KONZEPTION_ID, "09:00", "12:00"));

    // Then — der Konflikt nennt Urheber, Handelnden und Zeitpunkt; geschrieben wurde nichts.
    assertThat(konflikt.art()).isEqualTo(Bearbeitungskonflikt.Art.VERALTET);
    assertThat(konflikt.urheberKonto()).hasValue(BEN);
    assertThat(konflikt.handelndesKonto()).isEqualTo(ANNA);
    assertThat(konflikt.zeitpunkt()).hasValue(Zeitdoppel.JETZT);
    verify(zeiten, never()).save(any());
  }

  @Test
  void aendern_onAnOutdatedStand_thenRejectsBeforeAnyBusinessRule() {
    // Given — Plan #415, E1: der Vergleich kommt vor Raster, Buchbarkeit und Ueberschneidung. Die
    // Eingabe verstiesse gegen das Raster, gemeldet wird trotzdem der Konflikt.
    eintragGibtEs(
        new Zeiteintrag(
            Long.valueOf(EINTRAG),
            ANNA,
            Zeitdoppel.KONZEPTION_ID,
            Zeitdoppel.TAG,
            LocalTime.of(9, 0),
            LocalTime.of(11, 0),
            Zeitdoppel.FRUEHER,
            Zeitdoppel.JETZT,
            STAND + 2,
            null,
            null,
            null));

    // When / Then
    assertThatExceptionOfType(ZeiteintragGeaendert.class)
        .isThrownBy(() -> aendern(Zeitdoppel.SCHULUNG_ID, "09:10", "12:00"));
    verify(zeiten, never()).findImZeitraumFuerKonto(any(), any(), anyLong());
  }

  @Test
  void aendern_byACLevelOnAForeignEntryWithAnOutdatedStand_thenRejectsAsWell() {
    // Given — #410: auch die Korrektur eines fremden Eintrags durch ein C-Level ist geschuetzt.
    eintragGibtEs(
        new Zeiteintrag(
            Long.valueOf(EINTRAG),
            BEN,
            Zeitdoppel.KONZEPTION_ID,
            Zeitdoppel.TAG,
            LocalTime.of(9, 0),
            LocalTime.of(11, 0),
            Zeitdoppel.FRUEHER,
            Zeitdoppel.JETZT,
            STAND + 1,
            Long.valueOf(BEN),
            null,
            null));

    // When
    final ZeiteintragGeaendert konflikt =
        catchThrowableOfType(
            ZeiteintragGeaendert.class,
            () -> aendern(MANFRED, Zeitdoppel.KONZEPTION_ID, "09:00", "12:00"));

    // Then
    assertThat(konflikt.handelndesKonto()).isEqualTo(MANFRED);
    verify(zeiten, never()).save(any());
  }

  @Test
  void aendern_aForeignEntryOfAnEmployee_thenReportsNotFoundBeforeTheStand() {
    // Given — E7 vor E1: einem Employee verriete ein Konflikt Urheber und Zeitpunkt eines
    // Eintrags, den es fuer ihn nicht gibt.
    eintragGibtEs(
        new Zeiteintrag(
            Long.valueOf(EINTRAG),
            BEN,
            Zeitdoppel.KONZEPTION_ID,
            Zeitdoppel.TAG,
            LocalTime.of(9, 0),
            LocalTime.of(11, 0),
            Zeitdoppel.FRUEHER,
            Zeitdoppel.JETZT,
            STAND + 1,
            Long.valueOf(BEN),
            null,
            null));

    // When / Then
    assertThatExceptionOfType(ZeiteintragNichtGefunden.class)
        .isThrownBy(() -> aendern(ANNA, Zeitdoppel.KONZEPTION_ID, "09:00", "12:00"));
  }

  @Test
  void aendern_byACLevelOnAForeignEntry_thenRecordsTheCLevelAsUrheber() {
    // Given — E14: geaendert_von nennt den Handelnden, nicht den Eigentuemer.
    final Zeiteintrag vorhandener =
        Zeitdoppel.zeiteintrag(EINTRAG, BEN, Zeitdoppel.KONZEPTION_ID, "09:00", "11:00");
    eintragGibtEs(vorhandener);
    amTagLiegtFuer(BEN, vorhandener);
    speichernGibtZurueck();

    // When
    final Zeiteintrag geaendert = aendern(MANFRED, Zeitdoppel.KONZEPTION_ID, "09:00", "12:00");

    // Then
    assertThat(geaendert.geaendertVon()).isEqualTo(MANFRED);
    assertThat(geaendert.version()).isEqualTo(STAND);
  }

  @Test
  void aendern_byACLevelWithoutAPerson_thenTheEntryOfAnotherAccountKeepsItsOwner() {
    // Given — E12: ein PUT ohne Feld haengt einen fremden Eintrag nie um; er bleibt bei seiner
    // Person, und geprueft wird gegen deren Zeiten.
    final Zeiteintrag vorhandener =
        Zeitdoppel.zeiteintrag(EINTRAG, ANDERES_KONTO, Zeitdoppel.KONZEPTION_ID, "09:00", "11:00");
    eintragGibtEs(vorhandener);
    amTagLiegtFuer(ANDERES_KONTO, vorhandener);
    speichernGibtZurueck();

    // When
    final Zeiteintrag geaendert = aendern(MANFRED, Zeitdoppel.KONZEPTION_ID, "09:00", "12:00");

    // Then
    assertThat(geaendert.accountId()).isEqualTo(ANDERES_KONTO);
  }

  @Test
  void aendern_whenTheEntryIsUnknown_thenReportsItAsNotFound() {
    // Given — die Kennung steht im Pfad: 404 und nicht 422.
    when(zeiten.findById(EINTRAG)).thenReturn(Optional.empty());

    // When / Then
    assertThatExceptionOfType(ZeiteintragNichtGefunden.class)
        .isThrownBy(() -> aendern(Zeitdoppel.KONZEPTION_ID, "09:00", "11:00"));
  }

  @Test
  void aendern_givenAnAngebotThatIsAlreadyBilled_thenStillChangesTheTime() {
    // Given — A7, Antwort 4: ein vergessener Tippfehler bleibt korrigierbar. Die Position
    // wechselt nicht, also wird „Buchung zulaessig" gar nicht gefragt.
    eintragGibtEs(konzeption("09:00", "11:00"));
    amTagLiegt(konzeption("09:00", "11:00"));
    speichernGibtZurueck();

    // When
    final Zeiteintrag geaendert = aendern(Zeitdoppel.KONZEPTION_ID, "09:00", "10:00");

    // Then — kein Blick ins Angebot: der Wechsel der Position ist der einzige Anlass dafuer.
    assertThat(geaendert.bis()).isEqualTo(LocalTime.of(10, 0));
    verify(angebote, never()).findAlle(any());
  }

  @Test
  void aendern_whenTheEntryKeepsItsSlot_thenItDoesNotCollideWithItself() {
    // Given — A8: der Eintrag selbst zaehlt beim Aendern nicht mit.
    eintragGibtEs(konzeption("09:00", "11:00"));
    amTagLiegt(konzeption("09:00", "11:00"));
    speichernGibtZurueck();

    // When
    final Zeiteintrag geaendert = aendern(Zeitdoppel.KONZEPTION_ID, "09:00", "11:00");

    // Then
    assertThat(geaendert.id()).isEqualTo(Long.valueOf(EINTRAG));
  }

  @Test
  void aendern_whenAnotherEntryHoldsTheSlot_thenReportsTheOverlap() {
    // Given — der fremde Eintrag zaehlt weiter mit, auch auf einer anderen Position.
    eintragGibtEs(konzeption("09:00", "11:00"));
    amTagLiegt(
        konzeption("09:00", "11:00"),
        Zeitdoppel.zeiteintrag(FREMDER_EINTRAG, Zeitdoppel.WARTUNG_ID, "13:00", "15:00"));
    when(angebote.findAlle(Optional.empty())).thenReturn(List.of(Zeitdoppel.angebot()));
    firmaGibtEs();

    // When / Then
    assertThatExceptionOfType(ZeitenUeberschneidenSich.class)
        .isThrownBy(() -> aendern(Zeitdoppel.KONZEPTION_ID, "14:00", "16:00"))
        .withMessage("Überschneidet sich mit 13:00 bis 15:00, Wartung (IT Bildungshaus).");
    verify(zeiten, never()).save(any());
  }

  @Test
  void aendern_whenThePositionChanges_thenChecksThatBookingIsAllowed() {
    // Given — A7: der Wechsel der Position ist der zweite Anlass fuer die Pruefung.
    eintragGibtEs(konzeption("09:00", "11:00"));
    angebotStehtIn(Angebotsstatus.BESTELLT);

    // When / Then
    assertThatExceptionOfType(PositionNichtBuchbar.class)
        .isThrownBy(() -> aendern(Zeitdoppel.SCHULUNG_ID, "09:00", "11:00"));
    verify(zeiten, never()).save(any());
  }

  @Test
  void aendern_whenThePositionChangesToABookableOne_thenStoresTheEntry() {
    // Given — derselbe Weg, diesmal erlaubt.
    eintragGibtEs(konzeption("09:00", "11:00"));
    angebotStehtIn(Angebotsstatus.BESTELLT);
    amTagLiegt(konzeption("09:00", "11:00"));
    speichernGibtZurueck();

    // When
    final Zeiteintrag geaendert = aendern(Zeitdoppel.WARTUNG_ID, "09:00", "11:00");

    // Then
    assertThat(geaendert.angebotPositionId()).isEqualTo(Zeitdoppel.WARTUNG_ID);
  }

  @Test
  void aendern_whenThePositionChangesToOneOfACancelledAngebot_thenReportsIt() {
    // Given — E14: der Wechsel der Position ist eine neue Buchung auf der Zielposition.
    eintragGibtEs(konzeption("09:00", "11:00"));
    stornierteAngebote();

    // When / Then
    assertThatExceptionOfType(PositionNichtBuchbar.class)
        .isThrownBy(() -> aendern(Zeitdoppel.WARTUNG_ID, "09:00", "11:00"));
    verify(zeiten, never()).save(any());
  }

  @Test
  void aendern_givenAnEntryOfACancelledAngebot_thenStillChangesDayAndTime() {
    // Given — E14, Kriterium 7: erfasste Stunden am stornierten Angebot bleiben aenderbar. Die
    // Position wechselt nicht, also wird „Buchung zulaessig" nicht gefragt.
    final LocalDate andererTag = Zeitdoppel.TAG.plusDays(1);
    eintragGibtEs(konzeption("09:00", "11:00"));
    when(zeiten.findImZeitraumFuerKonto(andererTag, andererTag, ANNA)).thenReturn(List.of());
    speichernGibtZurueck();

    // When
    final Zeiteintrag geaendert =
        useCase.aendern(
            ANNA,
            EINTRAG,
            STAND,
            Optional.empty(),
            Zeitdoppel.KONZEPTION_ID,
            andererTag,
            LocalTime.of(10, 0),
            LocalTime.of(12, 0));

    // Then
    assertThat(geaendert.tag()).isEqualTo(andererTag);
    assertThat(geaendert.von()).isEqualTo(LocalTime.of(10, 0));
    verify(angebote, never()).findAlle(any());
  }

  @Test
  void loeschen_givenAnEntryOfACancelledAngebot_thenTheEntryIsGone() {
    // Given — E14, Kriterium 7: Loeschen bleibt am stornierten Angebot erlaubt, ohne Blick ins
    // Angebot.
    eintragGibtEs(konzeption("09:00", "11:00"));
    speichernGibtZurueck();

    // When
    useCase.loeschen(ANNA, EINTRAG, STAND);

    // Then
    verify(zeiten).save(konzeption("09:00", "11:00").geloescht(ANNA, Zeitdoppel.JETZT));
    verify(angebote, never()).findAlle(any());
  }

  @Test
  void aendern_givenABeginnOutsideTheGrid_thenReportsItAtVon() {
    // Given — A19 gilt auf beiden Schreibwegen.
    eintragGibtEs(konzeption("09:00", "11:00"));

    // When / Then
    assertThatExceptionOfType(UhrzeitNichtImRaster.class)
        .isThrownBy(() -> aendern(Zeitdoppel.KONZEPTION_ID, "09:10", "11:00"));
    verify(zeiten, never()).save(any());
  }

  @Test
  void loeschen_thenTheEntryIsMarkedWithAccountAndTime() {
    // Given — A5, Antwort 4: ein falscher Eintrag ist ein Tippfehler und kein Geschaeftsvorfall;
    // geloescht wird auch an einem abgerechneten Angebot, ohne Blick in dessen Status. Seit Plan
    // #415 (E17) mit Kennzeichen: Die Zeile bleibt mit Zeitpunkt und Konto der Loeschung.
    eintragGibtEs(konzeption("09:00", "11:00"));
    speichernGibtZurueck();

    // When
    useCase.loeschen(ANNA, EINTRAG, STAND);

    // Then
    verify(zeiten).save(konzeption("09:00", "11:00").geloescht(ANNA, Zeitdoppel.JETZT));
    verify(angebote, never()).findAlle(any());
  }

  @Test
  void loeschen_whenTheEntryIsUnknown_thenReportsItAsNotFound() {
    // Given
    when(zeiten.findById(EINTRAG)).thenReturn(Optional.empty());

    // When / Then
    assertThatExceptionOfType(ZeiteintragNichtGefunden.class)
        .isThrownBy(() -> useCase.loeschen(ANNA, EINTRAG, STAND));
    verify(zeiten, never()).save(any());
  }

  @Test
  void loeschen_onAnOutdatedStand_thenRejectsWithUrheberAndTimeAndMarksNothing() {
    // Given — #411, Kriterium 6: Ben hat den Eintrag inzwischen geaendert, sein Stand ist 1.
    eintragGibtEs(
        new Zeiteintrag(
            Long.valueOf(EINTRAG),
            ANNA,
            Zeitdoppel.KONZEPTION_ID,
            Zeitdoppel.TAG,
            LocalTime.of(9, 0),
            LocalTime.of(11, 0),
            Zeitdoppel.FRUEHER,
            Zeitdoppel.JETZT,
            STAND + 1,
            Long.valueOf(BEN),
            null,
            null));

    // When
    final ZeiteintragGeaendert konflikt =
        catchThrowableOfType(
            ZeiteintragGeaendert.class, () -> useCase.loeschen(ANNA, EINTRAG, STAND));

    // Then — der Konflikt nennt Urheber, Handelnden und Zeitpunkt; gekennzeichnet wurde nichts.
    assertThat(konflikt.art()).isEqualTo(Bearbeitungskonflikt.Art.VERALTET);
    assertThat(konflikt.urheberKonto()).hasValue(BEN);
    assertThat(konflikt.handelndesKonto()).isEqualTo(ANNA);
    assertThat(konflikt.zeitpunkt()).hasValue(Zeitdoppel.JETZT);
    verify(zeiten, never()).save(any());
  }

  /** Annas Eintrag, den Manfred um {@link #GELOESCHT_UM} geloescht hat (Plan #415, E17). */
  private static Zeiteintrag vonManfredGeloescht() {
    return konzeption("09:00", "11:00").geloescht(MANFRED, GELOESCHT_UM);
  }

  private static void istGeloeschtVonManfred(final ZeiteintragGeloescht konflikt) {
    assertThat(konflikt.art()).isEqualTo(Bearbeitungskonflikt.Art.GELOESCHT);
    assertThat(konflikt.urheberKonto()).hasValue(MANFRED);
    assertThat(konflikt.handelndesKonto()).isEqualTo(ANNA);
    assertThat(konflikt.zeitpunkt()).hasValue(GELOESCHT_UM);
  }

  @Test
  void loeschen_onADeletedEntry_thenRejectsWithTheDeletionAndMarksNothing() {
    // Given — #411, Kriterium 6; Plan #415, E16: Manfred hat Annas Eintrag schon geloescht.
    eintragGibtEs(vonManfredGeloescht());

    // When
    final ZeiteintragGeloescht konflikt =
        catchThrowableOfType(
            ZeiteintragGeloescht.class, () -> useCase.loeschen(ANNA, EINTRAG, STAND));

    // Then
    istGeloeschtVonManfred(konflikt);
    verify(zeiten, never()).save(any());
  }

  @Test
  void aendern_onADeletedEntry_thenRejectsWithTheDeletionBeforeAnyBusinessRule() {
    // Given — E16: auch Speichern auf einen geloeschten Eintrag wird abgewiesen, und es entsteht
    // nichts neu. Die Eingabe verstiesse gegen das Raster, gemeldet wird trotzdem die Loeschung.
    eintragGibtEs(vonManfredGeloescht());

    // When
    final ZeiteintragGeloescht konflikt =
        catchThrowableOfType(
            ZeiteintragGeloescht.class, () -> aendern(Zeitdoppel.KONZEPTION_ID, "09:10", "12:00"));

    // Then
    istGeloeschtVonManfred(konflikt);
    verify(zeiten, never()).save(any());
    verify(zeiten, never()).findImZeitraumFuerKonto(any(), any(), anyLong());
  }

  @Test
  void aendern_onADeletedEntryWithAnOutdatedStand_thenTheDeletionComesFirst() {
    // Given — das Kennzeichen geht dem Stand vor: Wer einen geloeschten Eintrag bearbeitet, soll
    // von der Loeschung erfahren und nicht von einem Stand, den es nicht mehr gibt.
    eintragGibtEs(
        new Zeiteintrag(
            Long.valueOf(EINTRAG),
            ANNA,
            Zeitdoppel.KONZEPTION_ID,
            Zeitdoppel.TAG,
            LocalTime.of(9, 0),
            LocalTime.of(11, 0),
            Zeitdoppel.FRUEHER,
            Zeitdoppel.JETZT,
            STAND + 1,
            Long.valueOf(BEN),
            GELOESCHT_UM,
            Long.valueOf(MANFRED)));

    // When / Then
    assertThatExceptionOfType(ZeiteintragGeloescht.class)
        .isThrownBy(() -> aendern(Zeitdoppel.KONZEPTION_ID, "09:00", "12:00"))
        .satisfies(ZeiteintragUseCaseTest::istGeloeschtVonManfred);
  }

  @Test
  void loeschen_aDeletedForeignEntryOfAnEmployee_thenReportsNotFoundBeforeTheDeletion() {
    // Given — E7 vor E16: einem Employee verriete der Konflikt Urheber und Zeitpunkt der Loeschung
    // eines Eintrags, den es fuer ihn nicht gibt.
    eintragGibtEs(
        Zeitdoppel.zeiteintrag(EINTRAG, BEN, Zeitdoppel.KONZEPTION_ID, "09:00", "11:00")
            .geloescht(MANFRED, GELOESCHT_UM));

    // When / Then
    assertThatExceptionOfType(ZeiteintragNichtGefunden.class)
        .isThrownBy(() -> useCase.loeschen(ANNA, EINTRAG, STAND));
  }

  // --- Rechte je Eintrag und je Rolle (Plan #426) --------------------------------------------

  private static Zeiteintrag gebucht(
      final Zeitbestand bestand,
      final long accountId,
      final LocalDate tag,
      final String von,
      final String bis) {
    return bestand.save(
        new Zeiteintrag(
            null,
            accountId,
            Zeitdoppel.KONZEPTION_ID,
            tag,
            LocalTime.parse(von),
            LocalTime.parse(bis),
            Zeitdoppel.FRUEHER,
            Zeitdoppel.FRUEHER));
  }

  private static Zeiteintrag anlegenIn(
      final ZeiteintragUseCase anwendungsfall,
      final long anfragend,
      final Optional<Long> person,
      final LocalDate tag,
      final String von,
      final String bis) {
    return anwendungsfall.anlegen(
        anfragend,
        person,
        Zeitdoppel.KONZEPTION_ID,
        tag,
        LocalTime.parse(von),
        LocalTime.parse(bis));
  }

  private static Zeiteintrag aendernIn(
      final ZeiteintragUseCase anwendungsfall,
      final long anfragend,
      final Zeiteintrag eintrag,
      final Optional<Long> person,
      final String von,
      final String bis) {
    return anwendungsfall.aendern(
        anfragend,
        eintrag.requireId(),
        eintrag.version(),
        person,
        eintrag.angebotPositionId(),
        eintrag.tag(),
        LocalTime.parse(von),
        LocalTime.parse(bis));
  }

  private BigDecimal angefallenAnDerKonzeption(final Zeitbestand bestand) {
    when(angebote.findById(Zeitdoppel.ANGEBOT)).thenReturn(Optional.of(Zeitdoppel.angebot()));
    return new Arbeitszeitauskunft(bestand, angebote)
        .angefallen(Zeitdoppel.ANGEBOT)
        .get(Zeitdoppel.KONZEPTION_ID);
  }

  private static void istNichtWaehlbar(final PersonNichtWaehlbar fehler) {
    assertThat(fehler.felder())
        .isEqualTo(Map.of(PersonNichtWaehlbar.FELD, List.of(PersonNichtWaehlbar.MELDUNG)));
    assertThat(PersonNichtWaehlbar.class.getAnnotation(ResponseStatus.class).code())
        .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
  }

  @Test
  @DisplayName("Szenario: Zwei Personen arbeiten zur selben Zeit")
  void szenarioZweiPersonenArbeitenZurSelbenZeit_thenBensEntryIsStoredAndBothCount() {
    // Given — Anna hat am 3. November von 9:00 bis 12:00 Uhr auf die Konzeption gebucht.
    final Zeitbestand bestand = new Zeitbestand();
    final ZeiteintragUseCase anwendungsfall = useCaseMit(bestand);
    gebucht(bestand, ANNA, DRITTER, "09:00", "12:00");
    angebotStehtIn(Angebotsstatus.BESTELLT);

    // When — Ben bucht fuer denselben Tag von 10:00 bis 11:00 Uhr auf dieselbe Position.
    final Zeiteintrag bens =
        anlegenIn(anwendungsfall, BEN, Optional.empty(), DRITTER, "10:00", "11:00");

    // Then — Bens Eintrag ist gespeichert, und „angefallen" zaehlt beide zusammen.
    assertThat(bestand.findById(bens.requireId())).contains(bens);
    assertThat(bens.accountId()).isEqualTo(BEN);
    assertThat(angefallenAnDerKonzeption(bestand)).isEqualByComparingTo("4.00");
  }

  @Test
  @DisplayName("Szenario: Dieselbe Person bucht doppelt")
  void szenarioDieselbePersonBuchtDoppelt_thenNotStoredAndHerEntryIsNamed() {
    // Given — Anna hat am 3. November von 9:00 bis 12:00 Uhr gebucht.
    final Zeitbestand bestand = new Zeitbestand();
    final ZeiteintragUseCase anwendungsfall = useCaseMit(bestand);
    final Zeiteintrag annas = gebucht(bestand, ANNA, DRITTER, "09:00", "12:00");
    angebotStehtIn(Angebotsstatus.BESTELLT);
    firmaGibtEs();

    // When / Then — ihr zweiter Eintrag von 11:00 bis 13:00 Uhr wird abgewiesen und nennt den
    // ersten als Kollision.
    assertThatExceptionOfType(ZeitenUeberschneidenSich.class)
        .isThrownBy(
            () -> anlegenIn(anwendungsfall, ANNA, Optional.empty(), DRITTER, "11:00", "13:00"))
        .withMessage("Überschneidet sich mit 9:00 bis 12:00, Konzeption (IT Bildungshaus).");
    assertThat(bestand.alle()).containsExactly(annas);
  }

  @Test
  @DisplayName("Szenario: C-Level korrigiert einen fremden Eintrag")
  void szenarioCLevelKorrigiertEinenFremdenEintrag_thenBensEntryHasEightHours() {
    // Given — Ben hat am 4. November versehentlich 8:00 bis 18:00 Uhr gebucht.
    final Zeitbestand bestand = new Zeitbestand();
    final ZeiteintragUseCase anwendungsfall = useCaseMit(bestand);
    final Zeiteintrag bens = gebucht(bestand, BEN, VIERTER, "08:00", "18:00");
    final BigDecimal vorher = angefallenAnDerKonzeption(bestand);

    // When — Manfred hat auf Ben umgeschaltet und aendert den Eintrag auf 8:00 bis 16:00 Uhr;
    // die Maske nennt Ben als Person, wie er es schon ist.
    final Zeiteintrag korrigiert =
        aendernIn(anwendungsfall, MANFRED, bens, Optional.of(BEN), "08:00", "16:00");

    // Then — Bens Eintrag hat 8 Stunden und gehoert weiter ihm; „angefallen" sinkt um 2 Stunden.
    assertThat(korrigiert.stunden()).isEqualByComparingTo("8.00");
    assertThat(bestand.findImZeitraumFuerKonto(VIERTER, VIERTER, BEN)).containsExactly(korrigiert);
    assertThat(vorher.subtract(angefallenAnDerKonzeption(bestand))).isEqualByComparingTo("2.00");
    verify(konten, never()).liste();
  }

  @Test
  void aendern_whenBenAndManfredChangeTheSameEntry_thenTheSecondIsRejectedAndBensStandStays() {
    // Given — #411, Kriterium 1: Ben und Manfred haben Bens Eintrag vom 4. November offen.
    final Zeitbestand bestand = new Zeitbestand();
    final ZeiteintragUseCase anwendungsfall = useCaseMit(bestand);
    final Zeiteintrag geoeffnet = gebucht(bestand, BEN, VIERTER, "08:00", "18:00");

    // When — Ben speichert zuerst, Manfred danach auf dem Stand, den er geoeffnet hatte.
    final Zeiteintrag bens =
        aendernIn(anwendungsfall, BEN, geoeffnet, Optional.empty(), "08:00", "16:00");
    final ZeiteintragGeaendert konflikt =
        catchThrowableOfType(
            ZeiteintragGeaendert.class,
            () ->
                aendernIn(anwendungsfall, MANFRED, geoeffnet, Optional.empty(), "08:00", "17:00"));

    // Then — Bens Stand bleibt, und der Konflikt nennt Ben als Urheber.
    assertThat(bens.version()).isEqualTo(geoeffnet.version() + 1);
    assertThat(bestand.findImZeitraumFuerKonto(VIERTER, VIERTER, BEN)).containsExactly(bens);
    assertThat(konflikt.urheberKonto()).hasValue(BEN);
    assertThat(konflikt.handelndesKonto()).isEqualTo(MANFRED);
  }

  @Test
  @DisplayName("Szenario: C-Level traegt Zeit fuer Ben nach")
  void szenarioCLevelTraegtZeitFuerBenNach_thenTheEntryBelongsToBen() {
    // Given — Ben hat seine Zeit vom 5. November auf Papier notiert. Die Vorbelegung der Person
    // in der Maske gehoert zum Paket „Erfassungsdialog: Personenauswahl fuer ein C-Level".
    final Zeitbestand bestand = new Zeitbestand();
    final ZeiteintragUseCase anwendungsfall = useCaseMit(bestand);
    angebotStehtIn(Angebotsstatus.BESTELLT);

    // When — Manfred erfasst als C-Level fuer Ben.
    final Zeiteintrag nachgetragen =
        anlegenIn(anwendungsfall, MANFRED, Optional.of(BEN), FUENFTER, "09:00", "12:00");

    // Then — der Eintrag gehoert Ben und steht in Bens Liste, nicht in Manfreds.
    assertThat(nachgetragen.accountId()).isEqualTo(BEN);
    assertThat(bestand.findImZeitraumFuerKonto(FUENFTER, FUENFTER, BEN))
        .containsExactly(nachgetragen);
    assertThat(bestand.findImZeitraumFuerKonto(FUENFTER, FUENFTER, MANFRED)).isEmpty();
  }

  @Test
  void aendern_byAnEmployeeOnAForeignEntry_thenNotFoundAndTheStockIsUnchanged() {
    // Given — E7: Bens Eintrag ist fuer Anna einer, den es nicht gibt; eine 403 bestaetigte ihr
    // die Kennung.
    final Zeitbestand bestand = new Zeitbestand();
    final ZeiteintragUseCase anwendungsfall = useCaseMit(bestand);
    final Zeiteintrag bens = gebucht(bestand, BEN, DRITTER, "09:00", "12:00");

    // When / Then
    assertThatExceptionOfType(ZeiteintragNichtGefunden.class)
        .isThrownBy(
            () -> aendernIn(anwendungsfall, ANNA, bens, Optional.empty(), "09:00", "10:00"));
    assertThat(bestand.alle()).containsExactly(bens);
  }

  @Test
  void loeschen_byAnEmployeeOnAForeignEntry_thenNotFoundAndTheStockIsUnchanged() {
    // Given — E7, Kriterium 2: auch auf anderem Weg loescht ein Employee keinen fremden Eintrag.
    final Zeitbestand bestand = new Zeitbestand();
    final ZeiteintragUseCase anwendungsfall = useCaseMit(bestand);
    final Zeiteintrag bens = gebucht(bestand, BEN, DRITTER, "09:00", "12:00");

    // When / Then
    assertThatExceptionOfType(ZeiteintragNichtGefunden.class)
        .isThrownBy(() -> anwendungsfall.loeschen(ANNA, bens.requireId(), bens.version()));
    assertThat(bestand.alle()).containsExactly(bens);
  }

  @Test
  void loeschen_byACLevelOnAForeignEntry_thenTheEntryIsGone() {
    // Given — Kriterium 3: ein C-Level loescht jeden Eintrag.
    final Zeitbestand bestand = new Zeitbestand();
    final ZeiteintragUseCase anwendungsfall = useCaseMit(bestand);
    final Zeiteintrag bens = gebucht(bestand, BEN, DRITTER, "09:00", "12:00");

    // When
    anwendungsfall.loeschen(MANFRED, bens.requireId(), bens.version());

    // Then — gekennzeichnet mit Manfred als Urheber; aus Bens Liste ist der Eintrag fort.
    assertThat(bestand.alle())
        .singleElement()
        .extracting(Zeiteintrag::geloeschtVon, Zeiteintrag::geloeschtAm)
        .containsExactly(Long.valueOf(MANFRED), Zeitdoppel.JETZT);
    assertThat(bestand.findImZeitraumFuerKonto(DRITTER, DRITTER, BEN)).isEmpty();
  }

  @Test
  void loeschen_whenManfredDeletesBensEntryWhileBenStillHasItOpen_thenBenIsToldWhoDeletedIt() {
    // Given — #411, Kriterium 6: Ben und Manfred haben Bens Eintrag vom 4. November offen.
    final Zeitbestand bestand = new Zeitbestand();
    final ZeiteintragUseCase anwendungsfall = useCaseMit(bestand);
    final Zeiteintrag geoeffnet = gebucht(bestand, BEN, VIERTER, "08:00", "18:00");

    // When — Manfred loescht, Ben speichert danach auf dem Stand, den er geoeffnet hatte.
    anwendungsfall.loeschen(MANFRED, geoeffnet.requireId(), geoeffnet.version());
    final ZeiteintragGeloescht konflikt =
        catchThrowableOfType(
            ZeiteintragGeloescht.class,
            () -> aendernIn(anwendungsfall, BEN, geoeffnet, Optional.empty(), "08:00", "16:00"));

    // Then — der Konflikt nennt Manfred und den Zeitpunkt; es entsteht nichts neu.
    assertThat(konflikt.urheberKonto()).hasValue(MANFRED);
    assertThat(konflikt.handelndesKonto()).isEqualTo(BEN);
    assertThat(konflikt.zeitpunkt()).hasValue(Zeitdoppel.JETZT);
    assertThat(bestand.alle())
        .singleElement()
        .extracting(Zeiteintrag::von)
        .isEqualTo(LocalTime.of(8, 0));
  }

  @Test
  void anlegen_inTheSlotOfADeletedEntryOfTheSamePerson_thenNoOverlap() {
    // Given — Plan #415, E17: die Ueberschneidungspruefung je Person zaehlt den geloeschten Eintrag
    // nicht mit.
    final Zeitbestand bestand = new Zeitbestand();
    final ZeiteintragUseCase anwendungsfall = useCaseMit(bestand);
    angebotStehtIn(Angebotsstatus.BESTELLT);
    final Zeiteintrag alter = gebucht(bestand, ANNA, Zeitdoppel.TAG, "09:00", "12:00");
    anwendungsfall.loeschen(ANNA, alter.requireId(), alter.version());

    // When
    final Zeiteintrag neu =
        anlegenIn(anwendungsfall, ANNA, Optional.empty(), Zeitdoppel.TAG, "09:00", "12:00");

    // Then
    assertThat(bestand.findImZeitraumFuerKonto(Zeitdoppel.TAG, Zeitdoppel.TAG, ANNA))
        .containsExactly(neu);
  }

  @Test
  void anlegen_byReadOnly_thenThePersonIsNotSelectable() {
    // Given — E19, Kriterium 4: ReadOnly erfasst nichts, auch nicht fuer sich selbst.
    final Zeitbestand bestand = new Zeitbestand();
    final ZeiteintragUseCase anwendungsfall = useCaseMit(bestand);

    // When / Then — vor allem anderen: kein Blick in die Angebote.
    assertThatExceptionOfType(PersonNichtWaehlbar.class)
        .isThrownBy(
            () -> anlegenIn(anwendungsfall, LEA, Optional.empty(), DRITTER, "09:00", "10:00"))
        .satisfies(ZeiteintragUseCaseTest::istNichtWaehlbar);
    assertThat(bestand.alle()).isEmpty();
    verify(angebote, never()).findAlle(any());
  }

  @Test
  void aendern_byReadOnlyOnTheOwnEntry_thenNotFoundAndTheStockIsUnchanged() {
    // Given — E19: Lea gehoert ein Eintrag aus der Zeit vor ihrem Rollenwechsel (Kriterium 4).
    final Zeitbestand bestand = new Zeitbestand();
    final ZeiteintragUseCase anwendungsfall = useCaseMit(bestand);
    final Zeiteintrag leas = gebucht(bestand, LEA, DRITTER, "09:00", "12:00");

    // When / Then
    assertThatExceptionOfType(ZeiteintragNichtGefunden.class)
        .isThrownBy(() -> aendernIn(anwendungsfall, LEA, leas, Optional.empty(), "09:00", "10:00"));
    assertThat(bestand.alle()).containsExactly(leas);
  }

  @Test
  void loeschen_byReadOnlyOnTheOwnEntry_thenNotFoundAndTheStockIsUnchanged() {
    // Given — E19: dieselbe Abweisung beim Loeschen.
    final Zeitbestand bestand = new Zeitbestand();
    final ZeiteintragUseCase anwendungsfall = useCaseMit(bestand);
    final Zeiteintrag leas = gebucht(bestand, LEA, DRITTER, "09:00", "12:00");

    // When / Then
    assertThatExceptionOfType(ZeiteintragNichtGefunden.class)
        .isThrownBy(() -> anwendungsfall.loeschen(LEA, leas.requireId(), leas.version()));
    assertThat(bestand.alle()).containsExactly(leas);
  }

  @Test
  void anlegen_byACLevelForADeactivatedPerson_thenThePersonIsNotSelectable() {
    // Given — Kriterium 9: neue Eintraege legt niemand fuer eine stillgelegte Person an.
    final Zeitbestand bestand = new Zeitbestand();
    final ZeiteintragUseCase anwendungsfall = useCaseMit(bestand);

    // When / Then
    assertThatExceptionOfType(PersonNichtWaehlbar.class)
        .isThrownBy(
            () -> anlegenIn(anwendungsfall, MANFRED, Optional.of(CLARA), DRITTER, "09:00", "10:00"))
        .satisfies(ZeiteintragUseCaseTest::istNichtWaehlbar);
    assertThat(bestand.alle()).isEmpty();
  }

  @Test
  void anlegen_byACLevelForAnUnknownAccount_thenThePersonIsNotSelectable() {
    // Given — E12: eine Kennung, zu der es kein Konto gibt.
    final Zeitbestand bestand = new Zeitbestand();
    final ZeiteintragUseCase anwendungsfall = useCaseMit(bestand);

    // When / Then
    assertThatExceptionOfType(PersonNichtWaehlbar.class)
        .isThrownBy(
            () ->
                anlegenIn(
                    anwendungsfall, MANFRED, Optional.of(UNBEKANNT), DRITTER, "09:00", "10:00"))
        .satisfies(ZeiteintragUseCaseTest::istNichtWaehlbar);
    assertThat(bestand.alle()).isEmpty();
  }

  @Test
  void anlegen_byAnEmployeeForAnotherAccount_thenThePersonIsNotSelectable() {
    // Given — E12, Kriterium 2: ein Employee erfasst nur fuer sich; die Liste der Konten liest er
    // dafuer nicht.
    final Zeitbestand bestand = new Zeitbestand();
    final ZeiteintragUseCase anwendungsfall = useCaseMit(bestand);

    // When / Then
    assertThatExceptionOfType(PersonNichtWaehlbar.class)
        .isThrownBy(
            () -> anlegenIn(anwendungsfall, ANNA, Optional.of(BEN), DRITTER, "09:00", "10:00"))
        .satisfies(ZeiteintragUseCaseTest::istNichtWaehlbar);
    assertThat(bestand.alle()).isEmpty();
    verify(konten, never()).liste();
  }

  @Test
  void anlegen_byAnEmployeeNamingTheOwnAccount_thenStoresTheEntry() {
    // Given — die eigene Kennung zu nennen ist dasselbe wie keine zu nennen.
    final Zeitbestand bestand = new Zeitbestand();
    final ZeiteintragUseCase anwendungsfall = useCaseMit(bestand);
    angebotStehtIn(Angebotsstatus.BESTELLT);

    // When
    final Zeiteintrag angelegt =
        anlegenIn(anwendungsfall, ANNA, Optional.of(ANNA), DRITTER, "09:00", "10:00");

    // Then
    assertThat(angelegt.accountId()).isEqualTo(ANNA);
  }

  @Test
  void aendern_byAnEmployeeNamingAnotherAccountOnTheOwnEntry_thenThePersonIsNotSelectable() {
    // Given — E12: auch den eigenen Eintrag haengt ein Employee nicht um.
    final Zeitbestand bestand = new Zeitbestand();
    final ZeiteintragUseCase anwendungsfall = useCaseMit(bestand);
    final Zeiteintrag annas = gebucht(bestand, ANNA, DRITTER, "09:00", "12:00");

    // When / Then
    assertThatExceptionOfType(PersonNichtWaehlbar.class)
        .isThrownBy(
            () -> aendernIn(anwendungsfall, ANNA, annas, Optional.of(BEN), "09:00", "12:00"))
        .satisfies(ZeiteintragUseCaseTest::istNichtWaehlbar);
    assertThat(bestand.alle()).containsExactly(annas);
  }

  @Test
  void aendern_byACLevelToADeactivatedPerson_thenThePersonIsNotSelectable() {
    // Given — Kriterium 3: umgehaengt wird nur auf eine aktive Person.
    final Zeitbestand bestand = new Zeitbestand();
    final ZeiteintragUseCase anwendungsfall = useCaseMit(bestand);
    final Zeiteintrag bens = gebucht(bestand, BEN, DRITTER, "09:00", "12:00");

    // When / Then
    assertThatExceptionOfType(PersonNichtWaehlbar.class)
        .isThrownBy(
            () -> aendernIn(anwendungsfall, MANFRED, bens, Optional.of(CLARA), "09:00", "12:00"))
        .satisfies(ZeiteintragUseCaseTest::istNichtWaehlbar);
    assertThat(bestand.alle()).containsExactly(bens);
  }

  @Test
  void aendern_byACLevelOnAnEntryOfADeactivatedPerson_thenStillCorrectsIt() {
    // Given — Kriterium 9: der Eintrag einer stillgelegten Person bleibt korrigierbar; bleibt er
    // bei ihr, ist ihr Zustand ohne Belang (E12) — mit und ohne Nennung.
    final Zeitbestand bestand = new Zeitbestand();
    final ZeiteintragUseCase anwendungsfall = useCaseMit(bestand);
    final Zeiteintrag claras = gebucht(bestand, CLARA, DRITTER, "09:00", "12:00");

    // When — das zweite Aendern beruht auf dem Stand, den das erste hinterlassen hat.
    final Zeiteintrag einmal =
        aendernIn(anwendungsfall, MANFRED, claras, Optional.empty(), "09:00", "11:00");
    final Zeiteintrag korrigiert =
        aendernIn(anwendungsfall, MANFRED, einmal, Optional.of(CLARA), "09:00", "10:00");

    // Then
    assertThat(korrigiert.accountId()).isEqualTo(CLARA);
    assertThat(bestand.alle()).containsExactly(korrigiert);
    verify(konten, never()).liste();
  }

  @Test
  void aendern_byACLevelToAnotherActivePerson_thenTheEntryChangesItsOwner() {
    // Given — E13: Umhaengen ist dasselbe PUT mit genannter Person.
    final Zeitbestand bestand = new Zeitbestand();
    final ZeiteintragUseCase anwendungsfall = useCaseMit(bestand);
    final Zeiteintrag bens = gebucht(bestand, BEN, DRITTER, "09:00", "12:00");

    // When
    final Zeiteintrag umgehaengt =
        aendernIn(anwendungsfall, MANFRED, bens, Optional.of(ANNA), "09:00", "12:00");

    // Then — dieselbe Zeile mit derselben Kennung, jetzt bei Anna.
    assertThat(umgehaengt.id()).isEqualTo(bens.id());
    assertThat(bestand.findImZeitraumFuerKonto(DRITTER, DRITTER, ANNA)).containsExactly(umgehaengt);
    assertThat(bestand.findImZeitraumFuerKonto(DRITTER, DRITTER, BEN)).isEmpty();
  }

  @Test
  void aendern_byACLevelToAnotherPersonWithCollidingTimes_thenNamesHerEntry() {
    // Given — E13, Kriterium 3: Anna hat 10:00 bis 11:00 auf der Wartung gebucht; Bens Eintrag
    // von 9:00 bis 12:00 soll ihr gehoeren.
    final Zeitbestand bestand = new Zeitbestand();
    final ZeiteintragUseCase anwendungsfall = useCaseMit(bestand);
    final Zeiteintrag bens = gebucht(bestand, BEN, DRITTER, "09:00", "12:00");
    final Zeiteintrag annas =
        bestand.save(
            new Zeiteintrag(
                null,
                ANNA,
                Zeitdoppel.WARTUNG_ID,
                DRITTER,
                LocalTime.of(10, 0),
                LocalTime.of(11, 0),
                Zeitdoppel.FRUEHER,
                Zeitdoppel.FRUEHER));
    angebotStehtIn(Angebotsstatus.BESTELLT);
    firmaGibtEs();

    // When / Then — die Meldung nennt Annas Eintrag, und beide Zeilen bleiben, wie sie waren.
    assertThatExceptionOfType(ZeitenUeberschneidenSich.class)
        .isThrownBy(
            () -> aendernIn(anwendungsfall, MANFRED, bens, Optional.of(ANNA), "09:00", "12:00"))
        .withMessage("Überschneidet sich mit 10:00 bis 11:00, Wartung (IT Bildungshaus).");
    assertThat(bestand.alle()).containsExactly(bens, annas);
  }

  @Test
  void anlegen_thenAsksOnlyForTheTimesOfThePersonTheEntryBelongsTo() {
    // Given — E8: gefragt wird nur nach den Zeiten der Person, der der Eintrag gehoeren soll.
    angebotStehtIn(Angebotsstatus.BESTELLT);
    keinEintragAmTag();
    speichernGibtZurueck();

    // When
    anlegen(Zeitdoppel.KONZEPTION_ID, "09:00", "10:00");

    // Then
    verify(zeiten, never()).findImZeitraum(any(), any());
  }
}
