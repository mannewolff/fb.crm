package org.mwolff.fbcrm.vorgang.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.common.Uploadgrenze;
import org.mwolff.fbcrm.vorgang.domain.Eintrag;
import org.mwolff.fbcrm.vorgang.domain.Eintragsart;
import org.mwolff.fbcrm.vorgang.domain.Herkunft;
import org.mwolff.fbcrm.vorgang.domain.Vorgang;

/**
 * Das Hinzufuegen eines Kommentars oder eines Anhangs zur Historie (Kriterien 13, 14, 18).
 *
 * <p>Fakes statt Mocks (CLAUDE-java.md §4): Zwei der Zusagen dieses Anwendungsfalls sind nur an
 * Zustand ablesbar — die Reihenfolge „erst das Objekt, dann die Zeile" (E8) an einem gemeinsamen
 * Protokoll beider Ports, und „eine abgewiesene Datei wird nicht abgelegt" daran, dass der Speicher
 * leer bleibt.
 *
 * <p>Die Uhr steht fest. Die Toleranz aus E15 ist sonst nicht pruefbar: Ein Zeitpunkt „59 Sekunden
 * voraus" ist nur gegenueber einer bekannten Gegenwart 59 Sekunden voraus.
 */
class EintragHinzufuegenUseCaseTest {

  private static final Instant JETZT = Instant.parse("2026-09-12T09:00:00Z");
  private static final Instant GESTERN = Instant.parse("2026-09-11T14:30:00Z");
  private static final Instant ANGELEGT = Instant.parse("2026-09-01T08:00:00Z");
  private static final long VORGANG_ID = 1L;

  private final List<String> protokoll = new ArrayList<>();
  private final Ports.Vorgaenge vorgaenge = new Ports.Vorgaenge();
  private final Ports.Eintraege eintraege = new Ports.Eintraege(protokoll);
  private final Ports.Speicher speicher = new Ports.Speicher(protokoll);

  private EintragHinzufuegenUseCase useCase;

  @BeforeEach
  void baueDenAnwendungsfall() {
    vorgaenge.save(new Vorgang(null, 1L, "Website-Relaunch", 7L, null, false, ANGELEGT, ANGELEGT));
    useCase =
        new EintragHinzufuegenUseCase(
            vorgaenge, eintraege, speicher, Clock.fixed(JETZT, ZoneOffset.UTC));
  }

  private static EintragDaten kommentar(final String text, final Instant geschehenAm) {
    return EintragDaten.kommentar(text, geschehenAm);
  }

  private static EintragDaten anhang(final long groesse) {
    return EintragDaten.anhang(
        null, GESTERN, "Angebot.pdf", groesse, InputStream.nullInputStream());
  }

  @Test
  void hinzufuegen_givenAComment_thenStoresItWithTextAndMoment() {
    // Given — Kriterium 13.

    // When
    useCase.hinzufuegen(VORGANG_ID, kommentar("Angerufen", GESTERN));

    // Then
    assertThat(eintraege.alle())
        .singleElement()
        .extracting(Eintrag::art, Eintrag::text, Eintrag::geschehenAm)
        .containsExactly(Eintragsart.KOMMENTAR, "Angerufen", GESTERN);
  }

  @Test
  void hinzufuegen_givenAComment_thenTheOriginIsByHand() {
    // Given — Kriterium 16: in diesem Stand entsteht jeder Eintrag von Hand.

    // When
    useCase.hinzufuegen(VORGANG_ID, kommentar("Angerufen", GESTERN));

    // Then
    assertThat(eintraege.alle())
        .singleElement()
        .extracting(Eintrag::herkunft)
        .isEqualTo(Herkunft.VON_HAND);
  }

  @Test
  void hinzufuegen_givenAComment_thenRecordsTheMomentOfEntryFromTheClock() {
    // Given — der Zeitpunkt des Geschehens ist nicht der der Erfassung.

    // When
    useCase.hinzufuegen(VORGANG_ID, kommentar("Angerufen", GESTERN));

    // Then
    assertThat(eintraege.alle()).singleElement().extracting(Eintrag::createdAt).isEqualTo(JETZT);
  }

  @Test
  void hinzufuegen_thenAnswersWithTheStoredEintrag() {
    // Given — der Aufrufer braucht die vergebene Kennung.

    // When
    final Eintrag angelegt = useCase.hinzufuegen(VORGANG_ID, kommentar("Angerufen", GESTERN));

    // Then
    assertThat(angelegt.requireId()).isEqualTo(1L);
  }

  @Test
  void hinzufuegen_givenACommentWithoutText_thenRefuses() {
    // Given — die Schranke hinter der Bean Validation.

    // When / Then
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> useCase.hinzufuegen(VORGANG_ID, kommentar("   ", GESTERN)));
  }

  @Test
  void hinzufuegen_givenACommentWithNullText_thenRefuses() {
    // Given — Jackson und der Formularbinder setzen fuer ein fehlendes Feld null ein.

    // When / Then
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> useCase.hinzufuegen(VORGANG_ID, EintragDaten.kommentar(null, GESTERN)));
  }

  @Test
  void hinzufuegen_givenAnAttachment_thenWritesTheObjectBeforeTheRow() {
    // Given — E8: ein Rollback laesst hoechstens eine Waise liegen, nie eine Zeile ohne Datei.

    // When
    useCase.hinzufuegen(VORGANG_ID, anhang(4096L));

    // Then
    assertThat(protokoll).containsExactly(Ports.Speicher.OBJEKT, Ports.Eintraege.ZEILE);
  }

  @Test
  void hinzufuegen_givenAnAttachment_thenTheRowCarriesNameSizeAndKey() {
    // Given — Kriterium 15 zeigt Name und Groesse, E9 haelt den Schluessel intern.

    // When
    useCase.hinzufuegen(VORGANG_ID, anhang(4096L));

    // Then
    assertThat(eintraege.alle())
        .singleElement()
        .extracting(
            Eintrag::art, Eintrag::dateiName, Eintrag::dateiGroesse, Eintrag::objektSchluessel)
        .containsExactly(
            Eintragsart.ANHANG, "Angebot.pdf", Long.valueOf(4096L), "vorgang/1/objekt-1");
  }

  @Test
  void hinzufuegen_givenAnAttachmentWithADescription_thenKeepsIt() {
    // Given — beim Anhang ist der Text die Beschreibung und bleibt freiwillig.

    // When
    useCase.hinzufuegen(
        VORGANG_ID,
        EintragDaten.anhang(
            "Das Angebot", GESTERN, "Angebot.pdf", 4096L, InputStream.nullInputStream()));

    // Then
    assertThat(eintraege.alle()).singleElement().extracting(Eintrag::text).isEqualTo("Das Angebot");
  }

  @Test
  void hinzufuegen_givenAnAttachment_thenAnnouncesTheSizeToTheStore() {
    // Given — der Objektspeicher bekommt die Zahl der Byte, die zu lesen sind.

    // When
    useCase.hinzufuegen(VORGANG_ID, anhang(4096L));

    // Then
    assertThat(speicher.abgelegt()).containsEntry("vorgang/1/objekt-1", Long.valueOf(4096L));
  }

  @Test
  void hinzufuegen_givenAnAttachment_thenClosesTheStream() {
    // Given — der Port sagt zu, dass der Aufrufer den Datenstrom schliesst.
    final Zaehlstrom strom = new Zaehlstrom();

    // When
    useCase.hinzufuegen(VORGANG_ID, EintragDaten.anhang(null, GESTERN, "Angebot.pdf", 0L, strom));

    // Then
    assertThat(strom.geschlossen()).isTrue();
  }

  @Test
  void hinzufuegen_givenAStreamThatFailsToClose_thenDoesNotSwallowTheFailure() {
    // Given — ein verschluckter Fehler hinterliesse ein offenes Handle ohne Spur.
    final InputStream strom =
        new ByteArrayInputStream(new byte[0]) {
          @Override
          public void close() throws IOException {
            throw new IOException("Der Strom laesst sich nicht schliessen.");
          }
        };

    // When / Then
    assertThatExceptionOfType(UncheckedIOException.class)
        .isThrownBy(
            () ->
                useCase.hinzufuegen(
                    VORGANG_ID, EintragDaten.anhang(null, GESTERN, "Angebot.pdf", 0L, strom)));
  }

  @Test
  void hinzufuegen_givenTheArtEreignis_thenRefusesAndWritesNothing() {
    // Given — ein Ereignis schreibt allein die Anwendung; von aussen ist es kein Kommentar.

    // When / Then
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(
            () ->
                useCase.hinzufuegen(
                    VORGANG_ID,
                    new EintragDaten(
                        Eintragsart.EREIGNIS, "Angebot versandt.", GESTERN, null, 0L, null)));
    assertThat(eintraege.alle()).isEmpty();
  }

  @Test
  void hinzufuegen_givenAnAttachmentWithoutAFile_thenRefuses() {
    // Given — die Schranke hinter der Bean Validation.

    // When / Then
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(
            () ->
                useCase.hinzufuegen(
                    VORGANG_ID,
                    new EintragDaten(Eintragsart.ANHANG, null, GESTERN, "Angebot.pdf", 0L, null)));
  }

  @Test
  void hinzufuegen_givenAnAttachmentWithoutAFileName_thenRefuses() {
    // Given — ohne Namen zeigte Kriterium 15 eine namenlose Zeile.

    // When / Then
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(
            () ->
                useCase.hinzufuegen(
                    VORGANG_ID,
                    new EintragDaten(
                        Eintragsart.ANHANG,
                        null,
                        GESTERN,
                        null,
                        0L,
                        InputStream.nullInputStream())));
  }

  @Test
  void hinzufuegen_givenExactlyTheMaximumSize_thenStoresIt() {
    // Given — Kriterium 18, E10: die Grenze selbst geht noch durch.

    // When
    useCase.hinzufuegen(VORGANG_ID, anhang(Uploadgrenze.MAX_BYTE));

    // Then
    assertThat(eintraege.alle()).hasSize(1);
  }

  @Test
  void hinzufuegen_givenOneByteBeyondTheMaximum_thenRefuses() {
    // Given — Kriterium 18.

    // When / Then
    assertThatExceptionOfType(AnhangZuGross.class)
        .isThrownBy(() -> useCase.hinzufuegen(VORGANG_ID, anhang(Uploadgrenze.MAX_BYTE + 1L)));
  }

  @Test
  void hinzufuegen_givenAFileBeyondTheMaximum_thenStoresNoObject() {
    // Given — eine abgewiesene Datei darf keine Waise im Speicher hinterlassen.

    // When
    assertThatExceptionOfType(AnhangZuGross.class)
        .isThrownBy(() -> useCase.hinzufuegen(VORGANG_ID, anhang(Uploadgrenze.MAX_BYTE + 1L)));

    // Then
    assertThat(speicher.abgelegt()).isEmpty();
  }

  @Test
  void hinzufuegen_givenAMomentWithinTheTolerance_thenStoresIt() {
    // Given — E15: die Uhr des Browsers darf eine knappe Minute vorgehen.

    // When
    useCase.hinzufuegen(VORGANG_ID, kommentar("Angerufen", JETZT.plusSeconds(59)));

    // Then
    assertThat(eintraege.alle()).hasSize(1);
  }

  @Test
  void hinzufuegen_givenAMomentBeyondTheTolerance_thenRefuses() {
    // Given — Kriterium 14.

    // When / Then
    assertThatExceptionOfType(ZeitpunktInDerZukunft.class)
        .isThrownBy(
            () -> useCase.hinzufuegen(VORGANG_ID, kommentar("Angerufen", JETZT.plusSeconds(61))));
  }

  @Test
  void hinzufuegen_givenAnUnknownVorgang_thenRefuses() {
    // When / Then
    assertThatExceptionOfType(VorgangNichtGefunden.class)
        .isThrownBy(() -> useCase.hinzufuegen(4711L, kommentar("Angerufen", GESTERN)));
  }

  @Test
  void hinzufuegen_givenAnUnknownVorgang_thenStoresNoObject() {
    // Given — auch der unbekannte Vorgang darf keine Waise hinterlassen.

    // When
    assertThatExceptionOfType(VorgangNichtGefunden.class)
        .isThrownBy(() -> useCase.hinzufuegen(4711L, anhang(4096L)));

    // Then
    assertThat(speicher.abgelegt()).isEmpty();
  }

  /** Ein Datenstrom, der sich merkt, ob er geschlossen wurde. */
  private static final class Zaehlstrom extends InputStream {

    private boolean geschlossen;

    @Override
    public int read() {
      return -1;
    }

    @Override
    public void close() {
      geschlossen = true;
    }

    boolean geschlossen() {
      return geschlossen;
    }
  }
}
