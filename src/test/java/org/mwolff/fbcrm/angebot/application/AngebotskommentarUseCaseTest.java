package org.mwolff.fbcrm.angebot.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.angebot.domain.Angebotskommentar;
import org.mwolff.fbcrm.angebot.domain.AngebotskommentarRepository;
import org.mwolff.fbcrm.auth.domain.AccountRepository;
import org.mwolff.fbcrm.common.Bearbeitungskonflikt;

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

  /** Der Stand, den die Ansicht vorlegt (Plan #415, E2, E8). */
  private static final long STAND = 3L;

  private static final Instant GELOESCHT = Instant.parse("2026-09-30T09:00:00Z");

  @Mock private AngebotRepository angebote;
  @Mock private AngebotskommentarRepository kommentare;
  @Mock private AccountRepository konten;

  @Captor private ArgumentCaptor<Angebotskommentar> gespeichert;

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
        Long.valueOf(KOMMENTAR),
        angebotId,
        ANNA,
        "Erster Stand",
        ANGELEGT,
        ANGELEGT,
        STAND,
        ANNA,
        null,
        null);
  }

  /** Derselbe Kommentar, inzwischen von Anna auf einen neueren Stand gebracht. */
  private static Angebotskommentar weiter() {
    return new Angebotskommentar(
        Long.valueOf(KOMMENTAR),
        ANGEBOT,
        ANNA,
        "Annas Stand",
        ANGELEGT,
        ANGELEGT,
        STAND + 1,
        ANNA,
        null,
        null);
  }

  /** Derselbe Kommentar, inzwischen von Anna geloescht (E17). */
  private static Angebotskommentar geloescht() {
    return kommentar(ANGEBOT).geloescht(ANNA, GELOESCHT);
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
        useCase.aendere(ANGEBOT, KOMMENTAR, STAND, BERT, "  Zweiter Stand  ").kommentar();

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
    final KommentarMitVerfasser geaendert =
        useCase.aendere(ANGEBOT, KOMMENTAR, STAND, BERT, "Zweiter Stand");

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
    assertThatThrownBy(() -> useCase.aendere(ANGEBOT, KOMMENTAR, STAND, BERT, "   "))
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
    assertThatThrownBy(() -> useCase.aendere(ANGEBOT, KOMMENTAR, STAND, BERT, "Zweiter Stand"))
        .isInstanceOf(AngebotskommentarNichtGefunden.class);
  }

  @Test
  void aendere_whenTheKommentarBelongsToAnotherAngebot_thenRejects() {
    // Given — E5: die Kennung allein genuegt nicht, der Kommentar muss an diesem Angebot haengen.
    angebotGibtEs();
    kommentarGehoertZu(FREMDES_ANGEBOT);

    // When / Then
    assertThatThrownBy(() -> useCase.aendere(ANGEBOT, KOMMENTAR, STAND, BERT, "Zweiter Stand"))
        .isInstanceOf(AngebotskommentarNichtGefunden.class);
  }

  @Test
  void aendere_whenTheAngebotIsUnknown_thenRejects() {
    // Given
    angebotGibtEsNicht();

    // When / Then
    assertThatThrownBy(() -> useCase.aendere(ANGEBOT, KOMMENTAR, STAND, BERT, "Zweiter Stand"))
        .isInstanceOf(AngebotNichtGefunden.class);
    verifyNoInteractions(kommentare);
  }

  @Test
  void loesche_thenMarksTheKommentarWithAccountAndInstant() {
    // Given — E17: geloescht wird mit Kennzeichen, damit ein spaeterer Zugriff Urheber und
    // Zeitpunkt nennen kann.
    angebotGibtEs();
    kommentarGehoertZu(ANGEBOT);
    speichernGibtZurueck();

    // When
    useCase.loesche(ANGEBOT, KOMMENTAR, STAND, BERT);

    // Then
    verify(kommentare).save(gespeichert.capture());
    assertThat(gespeichert.getValue())
        .satisfies(
            k -> assertThat(k.geloeschtAm()).isEqualTo(JETZT),
            k -> assertThat(k.geloeschtVon()).isEqualTo(BERT),
            k -> assertThat(k.text()).isEqualTo("Erster Stand"));
  }

  @Test
  void loesche_whenTheKommentarBelongsToAnotherAngebot_thenRejectsAndDeletesNothing() {
    // Given
    angebotGibtEs();
    kommentarGehoertZu(FREMDES_ANGEBOT);

    // When / Then
    assertThatThrownBy(() -> useCase.loesche(ANGEBOT, KOMMENTAR, STAND, BERT))
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
    assertThatThrownBy(() -> useCase.loesche(ANGEBOT, KOMMENTAR, STAND, BERT))
        .isInstanceOf(AngebotskommentarNichtGefunden.class);
  }

  @Test
  void loesche_whenTheAngebotIsUnknown_thenRejects() {
    // Given
    angebotGibtEsNicht();

    // When / Then
    assertThatThrownBy(() -> useCase.loesche(ANGEBOT, KOMMENTAR, STAND, BERT))
        .isInstanceOf(AngebotNichtGefunden.class);
    verifyNoInteractions(kommentare);
  }

  @Test
  @DisplayName("Szenario: Kommentar trotz geöffnetem Angebot")
  void schreibe_whileAnotherHasTheAngebotOpen_thenStoresWithoutStandAndLeavesTheAngebotVersion() {
    // Given — #411, Kriterium 7; Plan #415, E8: Anna bearbeitet gerade das Angebot auf Stand 3.
    when(angebote.findById(ANGEBOT)).thenReturn(Optional.of(Angebotsdoppel.angebot(ANGEBOT)));
    speichernGibtZurueck();
    when(konten.displayNamesByIds(Set.of(BERT))).thenReturn(Map.of(BERT, "Ben"));

    // When — Ben schreibt einen Kommentar; ein vorgelegter Stand gehoert nicht dazu.
    final KommentarMitVerfasser geschrieben = useCase.schreibe(ANGEBOT, BERT, "Kunde hat Zeit.");

    // Then — Bens Kommentar ist gespeichert, und das Angebot wurde nur gelesen, nie geschrieben:
    // Seine Version steht unveraendert, Annas Speichern danach wird dadurch nicht abgewiesen. Den
    // Zaehler in der Tabelle belegt AngebotKommentarIT.
    assertThat(geschrieben.kommentar().text()).isEqualTo("Kunde hat Zeit.");
    verify(kommentare).save(any());
    verify(angebote).findById(ANGEBOT);
    verifyNoMoreInteractions(angebote);
  }

  @Test
  void schreibe_thenStartsTheKommentarAtVersionZero() {
    // Given — ein neuer Kommentar ist ein eigener Datensatz mit eigenem Stand (E8).
    angebotGibtEs();
    speichernGibtZurueck();
    annaHeisst("Anna");

    // When
    final Angebotskommentar geschrieben =
        useCase.schreibe(ANGEBOT, ANNA, "Bitte melden.").kommentar();

    // Then
    assertThat(geschrieben.version()).isZero();
    assertThat(geschrieben.geloeschtAm()).isNull();
  }

  @Test
  void aendere_thenWritesTheActingAccountAsUrheber() {
    // Given — E14: Bert aendert Annas Kommentar; der Verfasser bleibt Anna.
    angebotGibtEs();
    kommentarGehoertZu(ANGEBOT);
    speichernGibtZurueck();
    annaHeisst("Anna");

    // When
    final Angebotskommentar geaendert =
        useCase.aendere(ANGEBOT, KOMMENTAR, STAND, BERT, "Zweiter Stand").kommentar();

    // Then
    assertThat(geaendert.geaendertVon()).isEqualTo(BERT);
    assertThat(geaendert.verfasserId()).isEqualTo(ANNA);
    assertThat(geaendert.version()).isEqualTo(STAND);
  }

  @Test
  void aendere_onAnOutdatedStand_thenRejectsWithUrheberAndInstantAndWritesNothing() {
    // Given — #411, Kriterium 1: Anna hat den Kommentar inzwischen geaendert.
    angebotGibtEs();
    when(kommentare.findById(KOMMENTAR)).thenReturn(Optional.of(weiter()));

    // When / Then
    assertThatThrownBy(() -> useCase.aendere(ANGEBOT, KOMMENTAR, STAND, BERT, "Zweiter Stand"))
        .isInstanceOfSatisfying(
            AngebotskommentarGeaendert.class,
            konflikt -> {
              assertThat(konflikt.art()).isEqualTo(Bearbeitungskonflikt.Art.VERALTET);
              assertThat(konflikt.urheberKonto()).hasValue(ANNA);
              assertThat(konflikt.handelndesKonto()).isEqualTo(BERT);
              assertThat(konflikt.zeitpunkt()).contains(ANGELEGT);
            });
    verify(kommentare, never()).save(any());
  }

  @Test
  void aendere_onAnOutdatedStandWithAnEmptyText_thenRejectsTheStandFirst() {
    // Given — zuerst der Stand (E9): die fremde Aenderung ist die gruendlichere Aussage.
    angebotGibtEs();
    when(kommentare.findById(KOMMENTAR)).thenReturn(Optional.of(weiter()));

    // When / Then
    assertThatThrownBy(() -> useCase.aendere(ANGEBOT, KOMMENTAR, STAND, BERT, "   "))
        .isInstanceOf(AngebotskommentarGeaendert.class);
  }

  @Test
  void aendere_aDeletedKommentar_thenRejectsAsDeletedWithUrheberAndInstantAndWritesNothing() {
    // Given — #411, Kriterium 6; E16: Anna hat den Kommentar inzwischen geloescht.
    angebotGibtEs();
    when(kommentare.findById(KOMMENTAR)).thenReturn(Optional.of(geloescht()));

    // When / Then — auch mit dem Stand, den der Kommentar vor dem Loeschen trug.
    assertThatThrownBy(() -> useCase.aendere(ANGEBOT, KOMMENTAR, STAND, BERT, "Zweiter Stand"))
        .isInstanceOfSatisfying(
            AngebotskommentarGeloescht.class,
            konflikt -> {
              assertThat(konflikt.art()).isEqualTo(Bearbeitungskonflikt.Art.GELOESCHT);
              assertThat(konflikt.urheberKonto()).hasValue(ANNA);
              assertThat(konflikt.handelndesKonto()).isEqualTo(BERT);
              assertThat(konflikt.zeitpunkt()).contains(GELOESCHT);
            });
    verify(kommentare, never()).save(any());
  }

  @Test
  void loesche_onAnOutdatedStand_thenRejectsAndDeletesNothing() {
    // Given — Kriterium 6: ein inzwischen geaenderter Kommentar wird nicht geloescht.
    angebotGibtEs();
    when(kommentare.findById(KOMMENTAR)).thenReturn(Optional.of(weiter()));

    // When / Then
    assertThatThrownBy(() -> useCase.loesche(ANGEBOT, KOMMENTAR, STAND, BERT))
        .isInstanceOf(AngebotskommentarGeaendert.class);
    verify(kommentare, never()).save(any());
  }

  @Test
  void loesche_aDeletedKommentar_thenRejectsAsDeletedAndWritesNothing() {
    // Given — Kriterium 6: auch ein zweites Loeschen, etwa aus einem anderen Fenster, wird
    // abgewiesen und schreibt kein neues Kennzeichen.
    angebotGibtEs();
    when(kommentare.findById(KOMMENTAR)).thenReturn(Optional.of(geloescht()));

    // When / Then
    assertThatThrownBy(() -> useCase.loesche(ANGEBOT, KOMMENTAR, STAND, ANNA))
        .isInstanceOfSatisfying(
            AngebotskommentarGeloescht.class,
            konflikt -> assertThat(konflikt.urheberKonto()).hasValue(ANNA));
    verify(kommentare, times(1)).findById(KOMMENTAR);
    verifyNoMoreInteractions(kommentare);
  }
}
