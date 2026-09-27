package org.mwolff.fbcrm.vorgang.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.common.Uploadgrenze;
import org.mwolff.fbcrm.vorgang.domain.Eintragsart;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

/**
 * Die klassenweite Constraint der beiden Eintragsanfragen (Kriterien 13, 14, 18).
 *
 * <p>Geprueft wird nicht nur, <b>dass</b> eine Eingabe abgewiesen wird, sondern an <b>welchem
 * Feld</b> die Meldung haengt: Nur eine Verletzung mit Feldbezug landet im {@code
 * GlobalExceptionHandler} unter {@code fieldErrors.<feld>}, und nur von dort holt die Maske sie an
 * das richtige Eingabefeld (Plan-Review Fund 3). Eine Verletzung ohne Feldbezug stuende als Meldung
 * ueber dem ganzen Formular.
 *
 * <p>Gefahren wird die echte Bean-Validation-Kette samt Interpolation der Meldungen — ein Test
 * gegen einen nachgebauten Kontext koennte nicht zeigen, dass die Meldung wirklich so hinausgeht.
 */
class EintragConstraintTest {

  private static final Instant JETZT = Instant.parse("2026-09-12T09:00:00Z");
  private static final Instant GESTERN = Instant.parse("2026-09-11T14:30:00Z");

  private final Validator validator =
      Pruefer.mitUhr(Clock.fixed(JETZT, ZoneOffset.UTC)).getValidator();

  private static MockMultipartFile datei(final String name) {
    return new MockMultipartFile("datei", name, "application/pdf", new byte[] {1, 2, 3});
  }

  private static MultipartFile dateiMitGroesse(final long groesse) {
    return new MockMultipartFile("datei", "gross.pdf", "application/pdf", new byte[] {1}) {
      @Override
      public long getSize() {
        return groesse;
      }
    };
  }

  private Set<ConstraintViolation<EintragRequest>> pruefe(final EintragRequest anfrage) {
    return validator.validate(anfrage);
  }

  @Test
  void kommentar_givenTextAndMoment_thenPasses() {
    // When
    final Set<ConstraintViolation<EintragRequest>> verletzungen =
        pruefe(new EintragRequest(Eintragsart.KOMMENTAR, "Angerufen", GESTERN, null));

    // Then
    assertThat(verletzungen).isEmpty();
  }

  @Test
  void kommentar_givenNoText_thenReportsOnTheTextField() {
    // Given — Kriterium 13.

    // When
    final Set<ConstraintViolation<EintragRequest>> verletzungen =
        pruefe(new EintragRequest(Eintragsart.KOMMENTAR, null, GESTERN, null));

    // Then
    assertThat(verletzungen)
        .extracting(v -> v.getPropertyPath().toString(), ConstraintViolation::getMessage)
        .containsExactly(tuple(EintragConstraint.FELD_TEXT, EintragConstraint.TEXT_FEHLT));
  }

  @Test
  void kommentar_givenBlankText_thenReportsOnTheTextField() {
    // When
    final Set<ConstraintViolation<EintragRequest>> verletzungen =
        pruefe(new EintragRequest(Eintragsart.KOMMENTAR, "   ", GESTERN, null));

    // Then
    assertThat(verletzungen)
        .extracting(v -> v.getPropertyPath().toString())
        .containsExactly(EintragConstraint.FELD_TEXT);
  }

  @Test
  void anhang_givenAFile_thenPasses() {
    // When — beim Anhang ist der Text die freiwillige Beschreibung.
    final Set<ConstraintViolation<EintragRequest>> verletzungen =
        pruefe(new EintragRequest(Eintragsart.ANHANG, null, GESTERN, datei("Angebot.pdf")));

    // Then
    assertThat(verletzungen).isEmpty();
  }

  @Test
  void anhang_givenNoFile_thenReportsOnTheFileField() {
    // Given — Kriterium 13.

    // When
    final Set<ConstraintViolation<EintragRequest>> verletzungen =
        pruefe(new EintragRequest(Eintragsart.ANHANG, null, GESTERN, null));

    // Then
    assertThat(verletzungen)
        .extracting(v -> v.getPropertyPath().toString(), ConstraintViolation::getMessage)
        .containsExactly(tuple(EintragConstraint.FELD_DATEI, EintragConstraint.DATEI_FEHLT));
  }

  @Test
  void anhang_givenAnEmptyFile_thenReportsOnTheFileField() {
    // Given — ein Browser schickt ein leeres Dateifeld als leeren Teil mit.
    final MultipartFile leer = new MockMultipartFile("datei", "", null, new byte[0]);

    // When
    final Set<ConstraintViolation<EintragRequest>> verletzungen =
        pruefe(new EintragRequest(Eintragsart.ANHANG, null, GESTERN, leer));

    // Then
    assertThat(verletzungen)
        .extracting(v -> v.getPropertyPath().toString(), ConstraintViolation::getMessage)
        .containsExactly(tuple(EintragConstraint.FELD_DATEI, EintragConstraint.DATEI_FEHLT));
  }

  @Test
  void anhang_givenExactlyTheMaximumSize_thenPasses() {
    // Given — E10: die Grenze selbst geht noch durch.

    // When
    final Set<ConstraintViolation<EintragRequest>> verletzungen =
        pruefe(
            new EintragRequest(
                Eintragsart.ANHANG, null, GESTERN, dateiMitGroesse(Uploadgrenze.MAX_BYTE)));

    // Then
    assertThat(verletzungen).isEmpty();
  }

  @Test
  void anhang_givenOneByteBeyondTheMaximum_thenReportsTheLimitOnTheFileField() {
    // Given — Kriterium 18: die Meldung nennt die Grenze.

    // When
    final Set<ConstraintViolation<EintragRequest>> verletzungen =
        pruefe(
            new EintragRequest(
                Eintragsart.ANHANG, null, GESTERN, dateiMitGroesse(Uploadgrenze.MAX_BYTE + 1L)));

    // Then
    assertThat(verletzungen)
        .extracting(v -> v.getPropertyPath().toString(), ConstraintViolation::getMessage)
        .containsExactly(tuple(EintragConstraint.FELD_DATEI, Uploadgrenze.MELDUNG));
  }

  @Test
  void anhang_givenAFileNameThatSanitizesToNothing_thenReportsOnTheFileField() {
    // Given — E13: von "../" bleibt nach der Saeuberung nichts uebrig.

    // When
    final Set<ConstraintViolation<EintragRequest>> verletzungen =
        pruefe(new EintragRequest(Eintragsart.ANHANG, null, GESTERN, datei("../")));

    // Then
    assertThat(verletzungen)
        .extracting(v -> v.getPropertyPath().toString(), ConstraintViolation::getMessage)
        .containsExactly(
            tuple(EintragConstraint.FELD_DATEI, EintragConstraint.DATEINAME_UNBRAUCHBAR));
  }

  @Test
  void neuerEintrag_givenAMomentWithinTheTolerance_thenPasses() {
    // Given — E15.

    // When
    final Set<ConstraintViolation<EintragRequest>> verletzungen =
        pruefe(new EintragRequest(Eintragsart.KOMMENTAR, "Angerufen", JETZT.plusSeconds(59), null));

    // Then
    assertThat(verletzungen).isEmpty();
  }

  @Test
  void neuerEintrag_givenAMomentBeyondTheTolerance_thenReportsOnTheMomentField() {
    // Given — Kriterium 14.

    // When
    final Set<ConstraintViolation<EintragRequest>> verletzungen =
        pruefe(new EintragRequest(Eintragsart.KOMMENTAR, "Angerufen", JETZT.plusSeconds(61), null));

    // Then
    assertThat(verletzungen)
        .extracting(v -> v.getPropertyPath().toString(), ConstraintViolation::getMessage)
        .containsExactly(tuple(EintragConstraint.FELD_ZEITPUNKT, EintragConstraint.ZUKUNFT));
  }

  @Test
  void neuerEintrag_givenNoArt_thenReportsOnTheArtField() {
    // Given — ohne Art weiss keine Regel, welche Angaben Pflicht sind.

    // When
    final Set<ConstraintViolation<EintragRequest>> verletzungen =
        pruefe(new EintragRequest(null, "Angerufen", GESTERN, null));

    // Then
    assertThat(verletzungen).extracting(v -> v.getPropertyPath().toString()).containsExactly("art");
  }

  @Test
  void neuerEintrag_givenNoMoment_thenReportsOnTheMomentFieldOnlyOnce() {
    // Given — ein fehlender Zeitpunkt ist Sache von @NotNull; die Constraint schweigt dazu.

    // When
    final Set<ConstraintViolation<EintragRequest>> verletzungen =
        pruefe(new EintragRequest(Eintragsart.KOMMENTAR, "Angerufen", null, null));

    // Then
    assertThat(verletzungen)
        .extracting(v -> v.getPropertyPath().toString())
        .containsExactly(EintragConstraint.FELD_ZEITPUNKT);
  }

  @Test
  void neuerEintrag_givenTheArtEreignis_thenReportsOnTheArtField() {
    // Given — Ereignisse schreibt allein die Anwendung (Kriterium 19).

    // When
    final Set<ConstraintViolation<EintragRequest>> verletzungen =
        pruefe(new EintragRequest(Eintragsart.EREIGNIS, "Angebot versandt.", GESTERN, null));

    // Then
    assertThat(verletzungen)
        .extracting(v -> v.getPropertyPath().toString(), ConstraintViolation::getMessage)
        .containsExactly(
            tuple(EintragConstraint.FELD_ART, EintragConstraint.EREIGNIS_NICHT_EINREICHBAR));
  }

  @Test
  void neuerEintrag_givenTheArtEreignisWithoutText_thenReportsOnlyOnTheArtField() {
    // Given — die Art ist schon der Grund; eine zweite Meldung am Text hilft niemandem.

    // When
    final Set<ConstraintViolation<EintragRequest>> verletzungen =
        pruefe(new EintragRequest(Eintragsart.EREIGNIS, null, GESTERN, null));

    // Then
    assertThat(verletzungen)
        .extracting(v -> v.getPropertyPath().toString())
        .containsExactly(EintragConstraint.FELD_ART);
  }

  @Test
  void aenderung_givenTextAndMoment_thenPasses() {
    // When
    final Set<ConstraintViolation<EintragAenderungRequest>> verletzungen =
        validator.validate(new EintragAenderungRequest("Doch geschrieben", GESTERN));

    // Then
    assertThat(verletzungen).isEmpty();
  }

  @Test
  void aenderung_givenNoText_thenReportsOnTheTextField() {
    // Given — Kriterium 18: die Maske zeigt die Meldung am Feld.

    // When
    final Set<ConstraintViolation<EintragAenderungRequest>> verletzungen =
        validator.validate(new EintragAenderungRequest("   ", GESTERN));

    // Then
    assertThat(verletzungen)
        .extracting(v -> v.getPropertyPath().toString())
        .containsExactly(EintragConstraint.FELD_TEXT);
  }

  @Test
  void aenderung_givenAMomentWithinTheTolerance_thenPasses() {
    // Given — E15.

    // When
    final Set<ConstraintViolation<EintragAenderungRequest>> verletzungen =
        validator.validate(new EintragAenderungRequest("Doch geschrieben", JETZT.plusSeconds(59)));

    // Then
    assertThat(verletzungen).isEmpty();
  }

  @Test
  void aenderung_givenAMomentBeyondTheTolerance_thenReportsOnTheMomentField() {
    // Given — Kriterium 14 gilt auch beim Aendern.

    // When
    final Set<ConstraintViolation<EintragAenderungRequest>> verletzungen =
        validator.validate(new EintragAenderungRequest("Doch geschrieben", JETZT.plusSeconds(61)));

    // Then
    assertThat(verletzungen)
        .extracting(v -> v.getPropertyPath().toString(), ConstraintViolation::getMessage)
        .containsExactly(tuple(EintragConstraint.FELD_ZEITPUNKT, EintragConstraint.ZUKUNFT));
  }

  @Test
  void aenderung_givenNoMoment_thenReportsOnTheMomentFieldOnlyOnce() {
    // Given — auch hier meldet @NotNull, und die Constraint schweigt.

    // When
    final Set<ConstraintViolation<EintragAenderungRequest>> verletzungen =
        validator.validate(new EintragAenderungRequest("Doch geschrieben", null));

    // Then
    assertThat(verletzungen)
        .extracting(v -> v.getPropertyPath().toString())
        .containsExactly(EintragConstraint.FELD_ZEITPUNKT);
  }
}
