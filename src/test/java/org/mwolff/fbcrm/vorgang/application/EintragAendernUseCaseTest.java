package org.mwolff.fbcrm.vorgang.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.vorgang.domain.Eintrag;
import org.mwolff.fbcrm.vorgang.domain.Herkunft;

/**
 * Das Aendern von Text und Zeitpunkt eines Eintrags (Kriterien 18, 19).
 *
 * <p>Der Weg ist ueber den Vorgang adressiert ({@code /api/vorgaenge/{id}/eintraege/{eintragId}}).
 * Deshalb gehoert zu jedem Aendern die Frage, ob der Eintrag wirklich zu diesem Vorgang gehoert —
 * ein Eintrag eines fremden Vorgangs ist unter dieser Adresse nicht vorhanden.
 *
 * <p>Die Datei bleibt unberuehrt: Geaendert werden Text und Zeitpunkt, nicht der Anhang selbst.
 */
class EintragAendernUseCaseTest {

  private static final Instant JETZT = Instant.parse("2026-09-12T09:00:00Z");
  private static final Instant GESTERN = Instant.parse("2026-09-11T14:30:00Z");
  private static final Instant VORGESTERN = Instant.parse("2026-09-10T11:00:00Z");
  private static final long VORGANG_ID = 1L;

  private final List<String> protokoll = new ArrayList<>();
  private final Ports.Eintraege eintraege = new Ports.Eintraege(protokoll);

  private EintragAendernUseCase useCase;

  @BeforeEach
  void baueDenAnwendungsfall() {
    useCase = new EintragAendernUseCase(eintraege, Clock.fixed(JETZT, ZoneOffset.UTC));
  }

  private long kommentar() {
    return eintraege
        .mit(Eintrag.kommentar(VORGANG_ID, "Angerufen", VORGESTERN, Herkunft.VON_HAND, VORGESTERN))
        .requireId();
  }

  private long anhang(final long vorgangId) {
    return eintraege
        .mit(
            Eintrag.anhang(
                vorgangId,
                null,
                VORGESTERN,
                Herkunft.VON_HAND,
                "Angebot.pdf",
                4096L,
                "vorgang/" + vorgangId + "/objekt-1",
                VORGESTERN))
        .requireId();
  }

  private long ereignis() {
    return eintraege.mit(Eintrag.ereignis(VORGANG_ID, "Angebot versandt.", VORGESTERN)).requireId();
  }

  private Eintrag gespeicherter(final long id) {
    return eintraege.findById(id).orElseThrow();
  }

  @Test
  void aendern_thenWritesTextAndMoment() {
    // Given — Kriterium 18.
    final long id = kommentar();

    // When
    useCase.aendern(VORGANG_ID, id, "Doch geschrieben", GESTERN);

    // Then
    assertThat(gespeicherter(id))
        .extracting(Eintrag::text, Eintrag::geschehenAm)
        .containsExactly("Doch geschrieben", GESTERN);
  }

  @Test
  void aendern_thenRecordsTheMomentOfChange() {
    // Given — Kriterium 19: daran haengt der Vermerk „geaendert".
    final long id = kommentar();

    // When
    useCase.aendern(VORGANG_ID, id, "Doch geschrieben", GESTERN);

    // Then
    assertThat(gespeicherter(id).geaendertAm()).isEqualTo(JETZT);
  }

  @Test
  void aendern_givenAnAttachment_thenLeavesTheFileUntouched() {
    // Given — geaendert werden Text und Zeitpunkt, nicht die Datei.
    final long id = anhang(VORGANG_ID);

    // When
    useCase.aendern(VORGANG_ID, id, "Das Angebot", GESTERN);

    // Then
    assertThat(gespeicherter(id))
        .extracting(Eintrag::dateiName, Eintrag::dateiGroesse, Eintrag::objektSchluessel)
        .containsExactly("Angebot.pdf", Long.valueOf(4096L), "vorgang/1/objekt-1");
  }

  @Test
  void aendern_thenKeepsTheMomentOfEntry() {
    // Given — wann der Eintrag entstand, aendert sich durch eine Korrektur nicht.
    final long id = kommentar();

    // When
    useCase.aendern(VORGANG_ID, id, "Doch geschrieben", GESTERN);

    // Then
    assertThat(gespeicherter(id).createdAt()).isEqualTo(VORGESTERN);
  }

  @Test
  void aendern_givenAnEvent_thenRefuses() {
    // Given — Kriterium 19: ein Ereignis laesst sich nicht aendern.
    final long id = ereignis();

    // When / Then
    assertThatExceptionOfType(EintragNichtAenderbar.class)
        .isThrownBy(() -> useCase.aendern(VORGANG_ID, id, "Umgeschrieben", GESTERN));
  }

  @Test
  void aendern_givenAnEvent_thenWritesNothing() {
    // Given — die Abweisung kommt vor dem Speichern; das Protokoll zeugt davon.
    final long id = ereignis();

    // When
    assertThatExceptionOfType(EintragNichtAenderbar.class)
        .isThrownBy(() -> useCase.aendern(VORGANG_ID, id, "Umgeschrieben", GESTERN));

    // Then
    assertThat(protokoll).isEmpty();
    assertThat(gespeicherter(id))
        .extracting(Eintrag::text, Eintrag::geschehenAm, Eintrag::geaendertAm)
        .containsExactly("Angebot versandt.", VORGESTERN, null);
  }

  @Test
  void aendern_givenACommentWithoutText_thenRefuses() {
    // Given — die Schranke hinter der Bean Validation.
    final long id = kommentar();

    // When / Then
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> useCase.aendern(VORGANG_ID, id, "   ", GESTERN));
  }

  @Test
  void aendern_givenAnUnknownEintrag_thenRefuses() {
    // When / Then
    assertThatExceptionOfType(EintragNichtGefunden.class)
        .isThrownBy(() -> useCase.aendern(VORGANG_ID, 4711L, "Doch geschrieben", GESTERN));
  }

  @Test
  void aendern_givenAnEintragOfAnotherVorgang_thenRefuses() {
    // Given — unter dieser Adresse ist der Eintrag nicht vorhanden.
    final long id = anhang(2L);

    // When / Then
    assertThatExceptionOfType(EintragNichtGefunden.class)
        .isThrownBy(() -> useCase.aendern(VORGANG_ID, id, "Doch geschrieben", GESTERN));
  }

  @Test
  void aendern_givenAnUnknownVorgang_thenRefuses() {
    // Given — zu einem Vorgang, den es nicht gibt, gehoert kein Eintrag.
    final long id = kommentar();

    // When / Then
    assertThatExceptionOfType(EintragNichtGefunden.class)
        .isThrownBy(() -> useCase.aendern(4711L, id, "Doch geschrieben", GESTERN));
  }

  @Test
  void aendern_givenAMomentWithinTheTolerance_thenWritesIt() {
    // Given — E15.
    final long id = kommentar();

    // When
    useCase.aendern(VORGANG_ID, id, "Doch geschrieben", JETZT.plusSeconds(59));

    // Then
    assertThat(gespeicherter(id).geschehenAm()).isEqualTo(JETZT.plusSeconds(59));
  }

  @Test
  void aendern_givenAMomentBeyondTheTolerance_thenRefuses() {
    // Given — Kriterium 14 gilt auch beim Aendern.
    final long id = kommentar();

    // When / Then
    assertThatExceptionOfType(ZeitpunktInDerZukunft.class)
        .isThrownBy(
            () -> useCase.aendern(VORGANG_ID, id, "Doch geschrieben", JETZT.plusSeconds(61)));
  }
}
