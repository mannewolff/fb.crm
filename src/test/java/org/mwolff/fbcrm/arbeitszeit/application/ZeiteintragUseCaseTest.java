package org.mwolff.fbcrm.arbeitszeit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
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
import org.mwolff.fbcrm.firma.application.FirmaNichtGefunden;
import org.mwolff.fbcrm.firma.domain.FirmaRepository;

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
 */
@ExtendWith(MockitoExtension.class)
class ZeiteintragUseCaseTest {

  private static final long EINTRAG = 4L;
  private static final long FREMDER_EINTRAG = 5L;

  @Mock private ZeiteintragRepository zeiten;
  @Mock private AngebotRepository angebote;
  @Mock private FirmaRepository firmen;

  private ZeiteintragUseCase useCase;

  @BeforeEach
  void baueDenAnwendungsfall() {
    useCase =
        new ZeiteintragUseCase(
            zeiten, angebote, firmen, Clock.fixed(Zeitdoppel.JETZT, ZoneOffset.UTC));
  }

  private void angebotStehtIn(final Angebotsstatus status) {
    when(angebote.findAlle(Optional.empty())).thenReturn(List.of(Zeitdoppel.angebot(status)));
  }

  private void keinEintragAmTag() {
    when(zeiten.findImZeitraum(Zeitdoppel.TAG, Zeitdoppel.TAG)).thenReturn(List.of());
  }

  private void amTagLiegt(final Zeiteintrag... vorhandene) {
    when(zeiten.findImZeitraum(Zeitdoppel.TAG, Zeitdoppel.TAG)).thenReturn(List.of(vorhandene));
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
        angebotPositionId, Zeitdoppel.TAG, LocalTime.parse(von), LocalTime.parse(bis));
  }

  private Zeiteintrag aendern(final long angebotPositionId, final String von, final String bis) {
    return useCase.aendern(
        EINTRAG, angebotPositionId, Zeitdoppel.TAG, LocalTime.parse(von), LocalTime.parse(bis));
  }

  private static Zeiteintrag konzeption(final String von, final String bis) {
    return Zeitdoppel.zeiteintrag(EINTRAG, Zeitdoppel.KONZEPTION_ID, von, bis);
  }

  @Test
  void anlegen_givenABookablePositionAndAFreeSlot_thenStoresTheEntryWithBothTimestamps() {
    // Given — Kriterium 1.
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

    // When / Then
    assertThatExceptionOfType(PositionNichtBuchbar.class)
        .isThrownBy(() -> anlegen(Zeitdoppel.FREMDE_POSITION, "09:00", "11:00"))
        .satisfies(
            fehler -> assertThat(fehler.felder()).containsOnlyKeys(PositionNichtBuchbar.FELD));
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

    // When / Then
    assertThatExceptionOfType(PositionNichtBuchbar.class)
        .isThrownBy(() -> anlegen(Zeitdoppel.KONZEPTION_ID, "09:00", "11:00"));
    verify(zeiten, never()).save(any());
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
    // Given — Kriterium 6; der Eintrag bleibt auf seiner Position.
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
                Zeitdoppel.KONZEPTION_ID,
                Zeitdoppel.TAG,
                LocalTime.of(9, 0),
                LocalTime.of(12, 0),
                Zeitdoppel.FRUEHER,
                Zeitdoppel.JETZT));
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
  void aendern_givenABeginnOutsideTheGrid_thenReportsItAtVon() {
    // Given — A19 gilt auf beiden Schreibwegen.
    eintragGibtEs(konzeption("09:00", "11:00"));

    // When / Then
    assertThatExceptionOfType(UhrzeitNichtImRaster.class)
        .isThrownBy(() -> aendern(Zeitdoppel.KONZEPTION_ID, "09:10", "11:00"));
    verify(zeiten, never()).save(any());
  }

  @Test
  void loeschen_thenTheEntryIsGone() {
    // Given — A5, Antwort 4: ein falscher Eintrag ist ein Tippfehler und kein Geschaeftsvorfall;
    // geloescht wird auch an einem abgerechneten Angebot, ohne Blick in dessen Status.
    eintragGibtEs(konzeption("09:00", "11:00"));

    // When
    useCase.loeschen(EINTRAG);

    // Then
    verify(zeiten).delete(EINTRAG);
    verify(angebote, never()).findAlle(any());
  }

  @Test
  void loeschen_whenTheEntryIsUnknown_thenReportsItAsNotFound() {
    // Given
    when(zeiten.findById(EINTRAG)).thenReturn(Optional.empty());

    // When / Then
    assertThatExceptionOfType(ZeiteintragNichtGefunden.class)
        .isThrownBy(() -> useCase.loeschen(EINTRAG));
    verify(zeiten, never()).delete(EINTRAG);
  }
}
