package org.mwolff.fbcrm.angebot.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.angebot.domain.Angebotskommentar;
import org.mwolff.fbcrm.angebot.domain.AngebotskommentarRepository;

/**
 * Die Anwendungsfaelle der Kommentare am Angebot (Issue #140, Kriterien 2 bis 10).
 *
 * <p>Gegenstand ist alles, was der Anwendungsfall entscheidet: Er prueft, dass es das Angebot gibt,
 * schneidet Leerraum am Rand ab und weist einen danach leeren Text ab (Kriterium 6, Plan E7), setzt
 * {@code createdAt} aus der Uhr und laesst es beim Aendern stehen (Kriterium 9, E8), sortiert
 * neuester zuerst (Kriterium 4) und weist einen Kommentar ab, der zu einem anderen Angebot gehoert
 * (E5).
 */
@ExtendWith(MockitoExtension.class)
class AngebotskommentarUseCaseTest {

  private static final long ANGEBOT = 11L;
  private static final long FREMDES_ANGEBOT = 12L;
  private static final long KOMMENTAR = 4L;
  private static final Instant ANGELEGT = Instant.parse("2026-09-30T08:00:00Z");
  private static final Instant JETZT = Instant.parse("2026-09-30T09:30:00Z");

  @Mock private AngebotRepository angebote;
  @Mock private AngebotskommentarRepository kommentare;

  private AngebotskommentarUseCase useCase;

  @BeforeEach
  void baueDenAnwendungsfall() {
    useCase =
        new AngebotskommentarUseCase(angebote, kommentare, Clock.fixed(JETZT, ZoneOffset.UTC));
  }

  private void angebotGibtEs() {
    when(angebote.findById(ANGEBOT)).thenReturn(Optional.of(Angebotsdoppel.angebot(ANGEBOT)));
  }

  private void angebotGibtEsNicht() {
    when(angebote.findById(ANGEBOT)).thenReturn(Optional.empty());
  }

  private static Angebotskommentar kommentar(final long angebotId) {
    return new Angebotskommentar(
        Long.valueOf(KOMMENTAR), angebotId, "Erster Stand", ANGELEGT, ANGELEGT);
  }

  private void kommentarGehoertZu(final long angebotId) {
    when(kommentare.findById(KOMMENTAR)).thenReturn(Optional.of(kommentar(angebotId)));
  }

  private void speichernGibtZurueck() {
    when(kommentare.save(any())).thenAnswer(aufruf -> aufruf.getArgument(0));
  }

  @Test
  void liste_thenReturnsTheKommentareNewestFirst() {
    // Given — Kriterium 4: der Bestand sagt keine Reihenfolge zu (E8).
    angebotGibtEs();
    when(kommentare.findByAngebot(ANGEBOT))
        .thenReturn(
            List.of(
                new Angebotskommentar(Long.valueOf(1L), ANGEBOT, "Alt", ANGELEGT, ANGELEGT),
                new Angebotskommentar(Long.valueOf(2L), ANGEBOT, "Neu", JETZT, JETZT)));

    // When
    final List<Angebotskommentar> liste = useCase.liste(ANGEBOT);

    // Then
    assertThat(liste).extracting(Angebotskommentar::text).containsExactly("Neu", "Alt");
  }

  @Test
  void liste_whenTheAngebotIsUnknown_thenRejects() {
    // Given
    angebotGibtEsNicht();

    // When / Then
    assertThatThrownBy(() -> useCase.liste(ANGEBOT)).isInstanceOf(AngebotNichtGefunden.class);
    verifyNoInteractions(kommentare);
  }

  @Test
  void schreibe_thenStoresTheStrippedTextWithTheInstantOfTheClock() {
    // Given — Kriterium 6: Leerzeilen am Anfang und am Ende werden nicht gespeichert.
    angebotGibtEs();
    speichernGibtZurueck();

    // When
    final Angebotskommentar geschrieben = useCase.schreibe(ANGEBOT, "\n\n  Bitte melden.  \n\n");

    // Then
    assertThat(geschrieben.text()).isEqualTo("Bitte melden.");
    assertThat(geschrieben.angebotId()).isEqualTo(ANGEBOT);
    assertThat(geschrieben.createdAt()).isEqualTo(JETZT);
    assertThat(geschrieben.updatedAt()).isEqualTo(JETZT);
    assertThat(geschrieben.id()).isNull();
  }

  @Test
  void schreibe_withATextOfOnlyWhitespace_thenRejectsAndWritesNothing() {
    // Given — dieselbe Pruefung wie in FirmaDaten: die Transaktionsgrenze verlaesst sich nicht
    // darauf, dass @NotBlank den Aufruf schon abgewiesen hat (E7).
    angebotGibtEs();

    // When / Then
    assertThatThrownBy(() -> useCase.schreibe(ANGEBOT, " \n\t "))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(kommentare);
  }

  @Test
  void schreibe_whenTheAngebotIsUnknown_thenRejects() {
    // Given
    angebotGibtEsNicht();

    // When / Then
    assertThatThrownBy(() -> useCase.schreibe(ANGEBOT, "Bitte melden."))
        .isInstanceOf(AngebotNichtGefunden.class);
    verifyNoInteractions(kommentare);
  }

  @Test
  void aendere_thenKeepsCreatedAtAndSetsTheNewText() {
    // Given — Kriterium 9: der Zeitpunkt bleibt der der Anlage (E8).
    angebotGibtEs();
    kommentarGehoertZu(ANGEBOT);
    speichernGibtZurueck();

    // When
    final Angebotskommentar geaendert = useCase.aendere(ANGEBOT, KOMMENTAR, "  Zweiter Stand  ");

    // Then
    assertThat(geaendert.text()).isEqualTo("Zweiter Stand");
    assertThat(geaendert.createdAt()).isEqualTo(ANGELEGT);
    assertThat(geaendert.updatedAt()).isEqualTo(JETZT);
    assertThat(geaendert.id()).isEqualTo(Long.valueOf(KOMMENTAR));
  }

  @Test
  void aendere_withATextOfOnlyWhitespace_thenRejectsAndWritesNothing() {
    // Given
    angebotGibtEs();
    kommentarGehoertZu(ANGEBOT);

    // When / Then
    assertThatThrownBy(() -> useCase.aendere(ANGEBOT, KOMMENTAR, "   "))
        .isInstanceOf(IllegalArgumentException.class);
    verify(kommentare).findById(KOMMENTAR);
    verifyNoMoreInteractions(kommentare);
  }

  @Test
  void aendere_whenTheKommentarIsUnknown_thenRejects() {
    // Given
    angebotGibtEs();
    when(kommentare.findById(KOMMENTAR)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> useCase.aendere(ANGEBOT, KOMMENTAR, "Zweiter Stand"))
        .isInstanceOf(AngebotskommentarNichtGefunden.class);
  }

  @Test
  void aendere_whenTheKommentarBelongsToAnotherAngebot_thenRejects() {
    // Given — E5: die Kennung allein genuegt nicht, der Kommentar muss an diesem Angebot haengen.
    angebotGibtEs();
    kommentarGehoertZu(FREMDES_ANGEBOT);

    // When / Then
    assertThatThrownBy(() -> useCase.aendere(ANGEBOT, KOMMENTAR, "Zweiter Stand"))
        .isInstanceOf(AngebotskommentarNichtGefunden.class);
  }

  @Test
  void aendere_whenTheAngebotIsUnknown_thenRejects() {
    // Given
    angebotGibtEsNicht();

    // When / Then
    assertThatThrownBy(() -> useCase.aendere(ANGEBOT, KOMMENTAR, "Zweiter Stand"))
        .isInstanceOf(AngebotNichtGefunden.class);
    verifyNoInteractions(kommentare);
  }

  @Test
  void loesche_thenDeletesTheRow() {
    // Given — Kriterium 10, E6: geloescht wird wirklich.
    angebotGibtEs();
    kommentarGehoertZu(ANGEBOT);

    // When
    useCase.loesche(ANGEBOT, KOMMENTAR);

    // Then
    verify(kommentare).deleteById(KOMMENTAR);
  }

  @Test
  void loesche_whenTheKommentarBelongsToAnotherAngebot_thenRejectsAndDeletesNothing() {
    // Given
    angebotGibtEs();
    kommentarGehoertZu(FREMDES_ANGEBOT);

    // When / Then
    assertThatThrownBy(() -> useCase.loesche(ANGEBOT, KOMMENTAR))
        .isInstanceOf(AngebotskommentarNichtGefunden.class);
    verify(kommentare).findById(KOMMENTAR);
    verifyNoMoreInteractions(kommentare);
  }

  @Test
  void loesche_whenTheKommentarIsUnknown_thenRejects() {
    // Given
    angebotGibtEs();
    when(kommentare.findById(KOMMENTAR)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> useCase.loesche(ANGEBOT, KOMMENTAR))
        .isInstanceOf(AngebotskommentarNichtGefunden.class);
  }

  @Test
  void loesche_whenTheAngebotIsUnknown_thenRejects() {
    // Given
    angebotGibtEsNicht();

    // When / Then
    assertThatThrownBy(() -> useCase.loesche(ANGEBOT, KOMMENTAR))
        .isInstanceOf(AngebotNichtGefunden.class);
    verifyNoInteractions(kommentare);
  }
}
