package org.mwolff.fbcrm.angebot.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.angebot.domain.Angebotskommentar;
import org.mwolff.fbcrm.angebot.domain.AngebotskommentarRepository;
import org.mwolff.fbcrm.auth.domain.AccountRepository;

/**
 * Die Anwendungsfaelle der Kommentare am Angebot (Issue #140, Kriterien 2 bis 10).
 *
 * <p>Gegenstand ist alles, was der Anwendungsfall entscheidet: Er prueft, dass es das Angebot gibt,
 * schneidet Leerraum am Rand ab und weist einen danach leeren Text ab (Kriterium 6, Plan E7), setzt
 * {@code createdAt} aus der Uhr und laesst es beim Aendern stehen (Kriterium 9, E8), sortiert
 * neuester zuerst (Kriterium 4) und weist einen Kommentar ab, der zu einem anderen Angebot gehoert
 * (E5). Dazu der Verfasser (Plan #414, A7): Er kommt beim Schreiben als Kennung herein, bleibt beim
 * Aendern stehen, und jede Antwort traegt seinen aktuellen Namen — fuer eine Liste in einem Zug
 * geholt.
 */
@ExtendWith(MockitoExtension.class)
class AngebotskommentarUseCaseTest {

  private static final long ANGEBOT = 11L;
  private static final long FREMDES_ANGEBOT = 12L;
  private static final long KOMMENTAR = 4L;
  private static final Instant ANGELEGT = Instant.parse("2026-09-30T08:00:00Z");
  private static final Instant JETZT = Instant.parse("2026-09-30T09:30:00Z");
  private static final long ANNA = 21L;
  private static final long BERT = 22L;

  @Mock private AngebotRepository angebote;
  @Mock private AngebotskommentarRepository kommentare;
  @Mock private AccountRepository konten;

  private AngebotskommentarUseCase useCase;

  @BeforeEach
  void baueDenAnwendungsfall() {
    useCase =
        new AngebotskommentarUseCase(
            angebote, kommentare, konten, Clock.fixed(JETZT, ZoneOffset.UTC));
  }

  private void angebotGibtEs() {
    when(angebote.findById(ANGEBOT)).thenReturn(Optional.of(Angebotsdoppel.angebot(ANGEBOT)));
  }

  private void angebotGibtEsNicht() {
    when(angebote.findById(ANGEBOT)).thenReturn(Optional.empty());
  }

  private static Angebotskommentar kommentar(final long angebotId) {
    return new Angebotskommentar(
        Long.valueOf(KOMMENTAR), angebotId, ANNA, "Erster Stand", ANGELEGT, ANGELEGT);
  }

  private void annaHeisst(final String name) {
    when(konten.displayNamesByIds(Set.of(ANNA))).thenReturn(Map.of(ANNA, name));
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
                new Angebotskommentar(Long.valueOf(1L), ANGEBOT, ANNA, "Alt", ANGELEGT, ANGELEGT),
                new Angebotskommentar(Long.valueOf(2L), ANGEBOT, ANNA, "Neu", JETZT, JETZT)));
    annaHeisst("Anna");

    // When
    final List<KommentarMitVerfasser> liste = useCase.liste(ANGEBOT);

    // Then
    assertThat(liste)
        .extracting(eintrag -> eintrag.kommentar().text())
        .containsExactly("Neu", "Alt");
  }

  @Test
  void liste_thenCarriesTheCurrentNameOfEachVerfasserFetchedInOneCall() {
    // Given — #409, Kriterium 11: der Name kommt aus dem Konto, nicht aus dem Kommentar (A7).
    angebotGibtEs();
    when(kommentare.findByAngebot(ANGEBOT))
        .thenReturn(
            List.of(
                new Angebotskommentar(
                    Long.valueOf(1L), ANGEBOT, ANNA, "Von Anna", ANGELEGT, ANGELEGT),
                new Angebotskommentar(Long.valueOf(2L), ANGEBOT, BERT, "Von Bert", JETZT, JETZT),
                new Angebotskommentar(
                    Long.valueOf(3L), ANGEBOT, ANNA, "Nochmal", ANGELEGT, ANGELEGT)));
    when(konten.displayNamesByIds(Set.of(ANNA, BERT)))
        .thenReturn(Map.of(ANNA, "Anna Adler", BERT, "Bert Berg"));

    // When
    final List<KommentarMitVerfasser> liste = useCase.liste(ANGEBOT);

    // Then — ein Aufruf fuer die ganze Liste, jede Kennung einmal.
    assertThat(liste)
        .extracting(eintrag -> eintrag.kommentar().text(), KommentarMitVerfasser::verfasser)
        .containsExactly(
            tuple("Von Bert", "Bert Berg"),
            tuple("Nochmal", "Anna Adler"),
            tuple("Von Anna", "Anna Adler"));
    verify(konten).displayNamesByIds(Set.of(ANNA, BERT));
    verifyNoMoreInteractions(konten);
  }

  @Test
  void liste_whenAVerfasserHasNoAccount_thenRefusesInsteadOfShowingAPlaceholder() {
    // Given — den Fall verhindert der Fremdschluessel; tritt er doch ein, ist das ein Bruch (A7).
    angebotGibtEs();
    when(kommentare.findByAngebot(ANGEBOT)).thenReturn(List.of(kommentar(ANGEBOT)));
    when(konten.displayNamesByIds(Set.of(ANNA))).thenReturn(Map.of());

    // When / Then
    assertThatThrownBy(() -> useCase.liste(ANGEBOT))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining(String.valueOf(ANNA));
  }

  @Test
  void liste_whenTheAngebotIsUnknown_thenRejects() {
    // Given
    angebotGibtEsNicht();

    // When / Then
    assertThatThrownBy(() -> useCase.liste(ANGEBOT)).isInstanceOf(AngebotNichtGefunden.class);
    verifyNoInteractions(kommentare, konten);
  }

  @Test
  void schreibe_thenStoresTheStrippedTextWithTheInstantOfTheClock() {
    // Given — Kriterium 6: Leerzeilen am Anfang und am Ende werden nicht gespeichert.
    angebotGibtEs();
    speichernGibtZurueck();
    annaHeisst("Anna");

    // When
    final Angebotskommentar geschrieben =
        useCase.schreibe(ANGEBOT, ANNA, "\n\n  Bitte melden.  \n\n").kommentar();

    // Then
    assertThat(geschrieben.text()).isEqualTo("Bitte melden.");
    assertThat(geschrieben.angebotId()).isEqualTo(ANGEBOT);
    assertThat(geschrieben.createdAt()).isEqualTo(JETZT);
    assertThat(geschrieben.updatedAt()).isEqualTo(JETZT);
    assertThat(geschrieben.id()).isNull();
  }

  @Test
  void schreibe_atAStornoedAngebot_thenStoresTheKommentar() {
    // Given — #330, Kriterium 10, zweiter Halbsatz: Kommentare entstehen am Storno weiter.
    when(angebote.findById(ANGEBOT))
        .thenReturn(
            Optional.of(
                Angebotsdoppel.angebot(ANGEBOT)
                    .storniert("vom Kunden abgelehnt", Angebotsdoppel.ANGELEGT)));
    speichernGibtZurueck();
    annaHeisst("Anna");

    // When
    final Angebotskommentar geschrieben =
        useCase.schreibe(ANGEBOT, ANNA, "Absage per Mail.").kommentar();

    // Then
    assertThat(geschrieben.text()).isEqualTo("Absage per Mail.");
    assertThat(geschrieben.angebotId()).isEqualTo(ANGEBOT);
    verify(kommentare).save(geschrieben);
  }

  @Test
  void schreibe_withATextOfOnlyWhitespace_thenRejectsAndWritesNothing() {
    // Given — dieselbe Pruefung wie in FirmaDaten: die Transaktionsgrenze verlaesst sich nicht
    // darauf, dass @NotBlank den Aufruf schon abgewiesen hat (E7).
    angebotGibtEs();

    // When / Then
    assertThatThrownBy(() -> useCase.schreibe(ANGEBOT, ANNA, " \n\t "))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(kommentare);
  }

  @Test
  void schreibe_whenTheAngebotIsUnknown_thenRejects() {
    // Given
    angebotGibtEsNicht();

    // When / Then
    assertThatThrownBy(() -> useCase.schreibe(ANGEBOT, ANNA, "Bitte melden."))
        .isInstanceOf(AngebotNichtGefunden.class);
    verifyNoInteractions(kommentare, konten);
  }

  @Test
  void schreibe_thenStoresTheGivenAccountAsVerfasserAndAnswersWithItsName() {
    // Given — der Verfasser ist das angemeldete Konto; die Kennung reicht der Controller durch.
    angebotGibtEs();
    speichernGibtZurueck();
    annaHeisst("Anna Adler");

    // When
    final KommentarMitVerfasser geschrieben = useCase.schreibe(ANGEBOT, ANNA, "Bitte melden.");

    // Then
    assertThat(geschrieben.kommentar().verfasserId()).isEqualTo(ANNA);
    assertThat(geschrieben.verfasser()).isEqualTo("Anna Adler");
  }

  @Test
  void schreibe_withATextOfOnlyWhitespace_thenAsksForNoName() {
    // Given
    angebotGibtEs();

    // When / Then
    assertThatThrownBy(() -> useCase.schreibe(ANGEBOT, ANNA, "   "))
        .isInstanceOf(IllegalArgumentException.class);
    verify(konten, never()).displayNamesByIds(any());
  }

  @Test
  void aendere_thenKeepsCreatedAtAndSetsTheNewText() {
    // Given — Kriterium 9: der Zeitpunkt bleibt der der Anlage (E8).
    angebotGibtEs();
    kommentarGehoertZu(ANGEBOT);
    speichernGibtZurueck();
    annaHeisst("Anna");

    // When
    final Angebotskommentar geaendert =
        useCase.aendere(ANGEBOT, KOMMENTAR, "  Zweiter Stand  ").kommentar();

    // Then
    assertThat(geaendert.text()).isEqualTo("Zweiter Stand");
    assertThat(geaendert.createdAt()).isEqualTo(ANGELEGT);
    assertThat(geaendert.updatedAt()).isEqualTo(JETZT);
    assertThat(geaendert.id()).isEqualTo(Long.valueOf(KOMMENTAR));
  }

  @Test
  void aendere_thenKeepsTheVerfasserAndAnswersWithItsCurrentName() {
    // Given — Rechte je Verfasser gibt es nicht; der Kommentar bleibt dem, der ihn schrieb (A7).
    angebotGibtEs();
    kommentarGehoertZu(ANGEBOT);
    speichernGibtZurueck();
    annaHeisst("Anna Adler-Berg");

    // When
    final KommentarMitVerfasser geaendert = useCase.aendere(ANGEBOT, KOMMENTAR, "Zweiter Stand");

    // Then
    assertThat(geaendert.kommentar().verfasserId()).isEqualTo(ANNA);
    assertThat(geaendert.verfasser()).isEqualTo("Anna Adler-Berg");
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
