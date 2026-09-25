package org.mwolff.fbcrm.vorgang.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mwolff.fbcrm.vorgang.domain.Eintrag;
import org.mwolff.fbcrm.vorgang.domain.Herkunft;

/**
 * Das Herausgeben eines Anhangs (Kriterium 17).
 *
 * <p>Fakes statt Mocks: Der Anwendungsfall reicht Bytes durch, und ob er die richtigen durchreicht,
 * ist nur an einem Speicher ablesbar, der wirklich etwas haelt (CLAUDE-java.md §4).
 */
class AnhangLesenUseCaseTest {

  private static final long VORGANG = 7L;
  private static final Instant ANGELEGT = Instant.parse("2026-09-12T09:00:00Z");
  private static final String SCHLUESSEL = "vorgang/7/objekt-1";
  private static final byte[] INHALT =
      "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8);

  private final List<String> protokoll = new ArrayList<>();
  private final Ports.Eintraege eintraege = new Ports.Eintraege(protokoll);
  private final Ports.Speicher speicher = new Ports.Speicher(protokoll);
  private final AnhangLesenUseCase useCase = new AnhangLesenUseCase(eintraege, speicher);

  private long anhangId;

  @BeforeEach
  void legeEinenAnhangAn() {
    anhangId =
        eintraege
            .mit(
                Eintrag.anhang(
                    VORGANG,
                    null,
                    ANGELEGT,
                    Herkunft.VON_HAND,
                    "Angebot.pdf",
                    INHALT.length,
                    SCHLUESSEL,
                    ANGELEGT))
            .requireId();
    speicher.mit(SCHLUESSEL, INHALT);
  }

  private long neuerEintrag(final Eintrag eintrag) {
    return eintraege.mit(eintrag).requireId();
  }

  @Test
  void lese_givenAnAttachment_thenReturnsNameAndSizeFromTheRow() {
    // Given — Name und Groesse stehen in der Datenbank, nicht im Objektspeicher (E9).

    // When
    final AnhangInhalt anhang = useCase.lese(VORGANG, anhangId);

    // Then
    assertThat(anhang)
        .extracting(AnhangInhalt::dateiName, AnhangInhalt::groesse)
        .containsExactly("Angebot.pdf", Long.valueOf(INHALT.length));
  }

  @Test
  void lese_givenAnAttachment_thenTheStreamCarriesTheStoredBytes() throws IOException {
    // Given — Kriterium 17: was hochgeladen wurde, kommt unveraendert wieder heraus.

    // When
    final byte[] gelesen = useCase.lese(VORGANG, anhangId).inhalt().readAllBytes();

    // Then
    assertThat(gelesen).isEqualTo(INHALT);
  }

  @Test
  void lese_givenAnUnknownEintrag_thenFailsWithNotFound() {
    // When / Then
    assertThatExceptionOfType(EintragNichtGefunden.class)
        .isThrownBy(() -> useCase.lese(VORGANG, 4711L));
  }

  @ParameterizedTest(name = "Vorgang {0}")
  @ValueSource(longs = {4711L, 8L})
  void lese_givenAVorgangThatDoesNotCarryTheEintrag_thenFailsWithNotFound(final long fremder) {
    // Given — zwei Lagen, eine Antwort: Den Vorgang im Pfad gibt es nicht (4711), oder es gibt
    // ihn, aber der Anhang haengt an einem anderen (8). Unter dieser Adresse ist er beide Male
    // nicht da (vgl. EintragNichtGefunden).

    // When / Then
    assertThatExceptionOfType(EintragNichtGefunden.class)
        .isThrownBy(() -> useCase.lese(fremder, anhangId));
  }

  @Test
  void lese_givenAComment_thenFailsWithNotFound() {
    // Given — ein Kommentar traegt keine Datei; unter dem Dateiweg gibt es ihn nicht.
    final long kommentarId =
        neuerEintrag(
            Eintrag.kommentar(VORGANG, "Angerufen", ANGELEGT, Herkunft.VON_HAND, ANGELEGT));

    // When / Then
    assertThatExceptionOfType(EintragNichtGefunden.class)
        .isThrownBy(() -> useCase.lese(VORGANG, kommentarId));
  }

  @Test
  void lese_givenTheObjectIsMissingInTheStore_thenFailsLoudly() {
    // Given — die Zeile steht, das Objekt fehlt. Das ist ein kaputter Bestand und keine erfundene
    // Adresse; ein 404 liesse beides gleich aussehen (vgl. VorgangLesenUseCase).
    final long ohneObjekt =
        neuerEintrag(
            Eintrag.anhang(
                VORGANG,
                null,
                ANGELEGT,
                Herkunft.VON_HAND,
                "Verschwunden.pdf",
                3L,
                "vorgang/7/objekt-weg",
                ANGELEGT));

    // When / Then
    assertThatExceptionOfType(IllegalStateException.class)
        .isThrownBy(() -> useCase.lese(VORGANG, ohneObjekt));
  }
}
