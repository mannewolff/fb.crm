package org.mwolff.fbcrm.vorgang.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.common.Anschrift;
import org.mwolff.fbcrm.firma.domain.Firma;
import org.mwolff.fbcrm.firma.domain.FirmaRepository;
import org.mwolff.fbcrm.vorgang.domain.Belegstand;
import org.mwolff.fbcrm.vorgang.domain.EintragRepository;
import org.mwolff.fbcrm.vorgang.domain.Phase;
import org.mwolff.fbcrm.vorgang.domain.Vorgang;
import org.mwolff.fbcrm.vorgang.domain.VorgangRepository;

/**
 * Die Uebersicht der Vorgaenge (Kriterien 2, 3).
 *
 * <p>Zwei Aussagen tragen die Klasse. Erstens die Nummernerkennung aus E17: Der Suchtext geht als
 * Text in den Bestand, die erkannte Nummer daneben als eigener Parameter — {@code #} schneidet die
 * Anwendungsschicht ab, und was keine Zahl ist, wird keine. Zweitens der Tag je Zeile: Er kommt vom
 * juengsten Eintrag, und ein Vorgang ohne Eintrag zaehlt mit seinem Anlagezeitpunkt (Kriterium 2).
 */
@ExtendWith(MockitoExtension.class)
class VorgaengeUebersichtUseCaseTest {

  private static final Instant ANGELEGT = Instant.parse("2026-09-01T08:00:00Z");
  private static final Instant GESCHEHEN = Instant.parse("2026-09-12T09:00:00Z");

  @Mock private VorgangRepository vorgaenge;
  @Mock private EintragRepository eintraege;
  @Mock private FirmaRepository firmen;
  @Mock private Belegstand belegstand;
  @Mock private Belegstand zweiterBelegstand;

  private VorgaengeUebersichtUseCase useCase;

  /*
   * Der Belegstand antwortet vorgabeweise „an keinem dieser Vorgaenge haengt ein festgeschriebenes
   * Angebot" — der Regelfall dieser Klasse. Wo die Phase der Gegenstand ist, setzt der Test seine
   * eigene Antwort darueber.
   *
   * Der Port ist mehrfach besetzbar; der zweite Belegstand steht nur dort im Spiel, wo die Zahl der
   * Abfragen oder das Zusammentreffen zweier Belegarten der Gegenstand ist.
   */
  @BeforeEach
  void baueDenAnwendungsfall() {
    lenient().when(belegstand.phase()).thenReturn(Phase.ANGEBOT);
    lenient().when(belegstand.mitBeleg(anyCollection())).thenReturn(Set.of());
    useCase = new VorgaengeUebersichtUseCase(vorgaenge, eintraege, firmen, List.of(belegstand));
  }

  private static Vorgang vorgang(final long id, final long nummer, final long firmaId) {
    return new Vorgang(
        id, nummer, "Website-Relaunch", firmaId, null, null, null, false, ANGELEGT, ANGELEGT);
  }

  private static Firma firma(final long id, final String name) {
    return new Firma(
        id, name, new Anschrift(null, null, null, null), null, null, true, ANGELEGT, ANGELEGT);
  }

  static Stream<Arguments> suchtexteUndNummern() {
    return Stream.of(
        Arguments.of("12", Long.valueOf(12L)),
        Arguments.of("#12", Long.valueOf(12L)),
        Arguments.of("#", null),
        Arguments.of("abc", null),
        Arguments.of("12abc", null),
        Arguments.of("", null));
  }

  @ParameterizedTest(name = "\"{0}\" ergibt {1}")
  @MethodSource("suchtexteUndNummern")
  void uebersicht_givenASearchText_thenRecognizesTheNumberBehindIt(
      final String suche, final Long erwarteteNummer) {
    // Given — E17: das fuehrende # schneidet die Anwendungsschicht ab, nicht der Bestand.
    when(vorgaenge.uebersicht(eq(suche), nullable(Long.class), eq(false))).thenReturn(List.of());
    when(eintraege.juengstesGeschehenJeVorgang(List.of())).thenReturn(Map.of());
    when(vorgaenge.zaehleAlle()).thenReturn(0L);

    // When
    useCase.uebersicht(suche, false);

    // Then
    verify(vorgaenge).uebersicht(suche, erwarteteNummer, false);
  }

  @Test
  void uebersicht_thenCarriesNumberTitleFirmaPhaseAndTheDayOfTheLatestEntry() {
    // Given
    when(vorgaenge.uebersicht("", null, false)).thenReturn(List.of(vorgang(4L, 12L, 7L)));
    when(eintraege.juengstesGeschehenJeVorgang(List.of(4L))).thenReturn(Map.of(4L, GESCHEHEN));
    when(firmen.findById(7L)).thenReturn(Optional.of(firma(7L, "Adler AG")));
    when(vorgaenge.zaehleAlle()).thenReturn(1L);

    // When
    final VorgaengeUebersicht uebersicht = useCase.uebersicht("", false);

    // Then — Kriterium 2: genau diese fuenf Angaben stehen je Zeile.
    assertThat(uebersicht.zeilen())
        .containsExactly(
            new VorgangZeile(
                4L, 12L, "Website-Relaunch", "Adler AG", Phase.ANBAHNUNG, false, GESCHEHEN));
  }

  @Test
  void uebersicht_givenAVorgangWithoutAnEntry_thenTakesTheDayItWasCreated() {
    // Given
    when(vorgaenge.uebersicht("", null, false)).thenReturn(List.of(vorgang(4L, 12L, 7L)));
    when(eintraege.juengstesGeschehenJeVorgang(List.of(4L))).thenReturn(Map.of());
    when(firmen.findById(7L)).thenReturn(Optional.of(firma(7L, "Adler AG")));
    when(vorgaenge.zaehleAlle()).thenReturn(1L);

    // When
    final VorgaengeUebersicht uebersicht = useCase.uebersicht("", false);

    // Then — Kriterium 2: ohne Eintrag steht dort der Tag des Anlegens.
    assertThat(uebersicht.zeilen())
        .singleElement()
        .extracting(VorgangZeile::letzteAktivitaet)
        .isEqualTo(ANGELEGT);
  }

  @Test
  void uebersicht_thenKeepsTheOrderOfTheRepository() {
    // Given — sortiert wird im Bestand (E16); die Uebersicht sortiert nicht nach.
    when(vorgaenge.uebersicht("", null, false))
        .thenReturn(List.of(vorgang(9L, 1L, 7L), vorgang(3L, 2L, 7L)));
    when(eintraege.juengstesGeschehenJeVorgang(List.of(9L, 3L))).thenReturn(Map.of());
    when(firmen.findById(7L)).thenReturn(Optional.of(firma(7L, "Adler AG")));
    when(vorgaenge.zaehleAlle()).thenReturn(2L);

    // When
    final VorgaengeUebersicht uebersicht = useCase.uebersicht("", false);

    // Then
    assertThat(uebersicht.zeilen()).extracting(VorgangZeile::id).containsExactly(9L, 3L);
  }

  @Test
  void uebersicht_givenTwoVorgaengeOfTheSameFirma_thenAsksForThatFirmaOnce() {
    // Given
    when(vorgaenge.uebersicht("", null, false))
        .thenReturn(List.of(vorgang(9L, 1L, 7L), vorgang(3L, 2L, 7L)));
    when(eintraege.juengstesGeschehenJeVorgang(List.of(9L, 3L))).thenReturn(Map.of());
    when(firmen.findById(7L)).thenReturn(Optional.of(firma(7L, "Adler AG")));
    when(vorgaenge.zaehleAlle()).thenReturn(2L);

    // When
    useCase.uebersicht("", false);

    // Then — eine Abfrage je Firma, nicht je Zeile.
    verify(firmen, times(1)).findById(anyLong());
  }

  @Test
  void uebersicht_givenTwoVorgaenge_thenAsksEveryBelegstandOnceForTheWholeSet() {
    // Given — E2: eine Abfrage je Belegart fuer die ganze Menge, nicht eine je Zeile.
    lenient().when(zweiterBelegstand.phase()).thenReturn(Phase.AUFTRAG);
    when(zweiterBelegstand.mitBeleg(anyCollection())).thenReturn(Set.of());
    final VorgaengeUebersichtUseCase mitZweiBelegarten =
        new VorgaengeUebersichtUseCase(
            vorgaenge, eintraege, firmen, List.of(belegstand, zweiterBelegstand));
    when(vorgaenge.uebersicht("", null, false))
        .thenReturn(List.of(vorgang(9L, 1L, 7L), vorgang(3L, 2L, 7L)));
    when(eintraege.juengstesGeschehenJeVorgang(List.of(9L, 3L))).thenReturn(Map.of());
    when(firmen.findById(7L)).thenReturn(Optional.of(firma(7L, "Adler AG")));
    when(vorgaenge.zaehleAlle()).thenReturn(2L);

    // When
    mitZweiBelegarten.uebersicht("", false);

    // Then
    verify(belegstand, times(1)).mitBeleg(List.of(9L, 3L));
    verify(zweiterBelegstand, times(1)).mitBeleg(List.of(9L, 3L));
  }

  @Test
  void uebersicht_givenAnOrderOnTopOfAnOffer_thenTheRowShowsAuftrag() {
    // Given — Kriterium 10: an derselben Zeile haengen beide Belegarten.
    when(zweiterBelegstand.phase()).thenReturn(Phase.AUFTRAG);
    when(zweiterBelegstand.mitBeleg(List.of(9L))).thenReturn(Set.of(Long.valueOf(9L)));
    final VorgaengeUebersichtUseCase mitZweiBelegarten =
        new VorgaengeUebersichtUseCase(
            vorgaenge, eintraege, firmen, List.of(belegstand, zweiterBelegstand));
    when(vorgaenge.uebersicht("", null, false)).thenReturn(List.of(vorgang(9L, 1L, 7L)));
    when(eintraege.juengstesGeschehenJeVorgang(List.of(9L))).thenReturn(Map.of());
    when(firmen.findById(7L)).thenReturn(Optional.of(firma(7L, "Adler AG")));
    when(belegstand.mitBeleg(List.of(9L))).thenReturn(Set.of(Long.valueOf(9L)));
    when(vorgaenge.zaehleAlle()).thenReturn(1L);

    // When
    final VorgaengeUebersicht uebersicht = mitZweiBelegarten.uebersicht("", false);

    // Then — das Maximum gewinnt, und das ist der Auftrag.
    assertThat(uebersicht.zeilen()).extracting(VorgangZeile::phase).containsExactly(Phase.AUFTRAG);
  }

  @Test
  void uebersicht_givenAVorgangWithACommittedOffer_thenItsRowShowsThePhaseAngebot() {
    // Given — Kriterium 22: der zweite Vorgang hat noch kein festgeschriebenes Angebot.
    when(vorgaenge.uebersicht("", null, false))
        .thenReturn(List.of(vorgang(9L, 1L, 7L), vorgang(3L, 2L, 7L)));
    when(eintraege.juengstesGeschehenJeVorgang(List.of(9L, 3L))).thenReturn(Map.of());
    when(firmen.findById(7L)).thenReturn(Optional.of(firma(7L, "Adler AG")));
    when(belegstand.mitBeleg(List.of(9L, 3L))).thenReturn(Set.of(Long.valueOf(9L)));
    when(vorgaenge.zaehleAlle()).thenReturn(2L);

    // When
    final VorgaengeUebersicht uebersicht = useCase.uebersicht("", false);

    // Then
    assertThat(uebersicht.zeilen())
        .extracting(VorgangZeile::phase)
        .containsExactly(Phase.ANGEBOT, Phase.ANBAHNUNG);
  }

  @Test
  void uebersicht_givenTwoVorgaengeOfTheSameFirma_thenBothRowsCarryItsName() {
    // Given — die zweite Zeile nimmt den gemerkten Namen und nicht irgendeinen.
    when(vorgaenge.uebersicht("", null, false))
        .thenReturn(List.of(vorgang(9L, 1L, 7L), vorgang(3L, 2L, 7L)));
    when(eintraege.juengstesGeschehenJeVorgang(List.of(9L, 3L))).thenReturn(Map.of());
    when(firmen.findById(7L)).thenReturn(Optional.of(firma(7L, "Adler AG")));
    when(vorgaenge.zaehleAlle()).thenReturn(2L);

    // When
    final VorgaengeUebersicht uebersicht = useCase.uebersicht("", false);

    // Then
    assertThat(uebersicht.zeilen())
        .extracting(VorgangZeile::firmaName)
        .containsExactly("Adler AG", "Adler AG");
  }

  @Test
  void uebersicht_givenTheSwitch_thenPassesItOn() {
    // Given — Kriterium 20: abgeschlossene Vorgaenge erscheinen nur auf Wunsch.
    when(vorgaenge.uebersicht("", null, true)).thenReturn(List.of());
    when(eintraege.juengstesGeschehenJeVorgang(List.of())).thenReturn(Map.of());
    when(vorgaenge.zaehleAlle()).thenReturn(0L);

    // When
    useCase.uebersicht("", true);

    // Then
    verify(vorgaenge).uebersicht("", null, true);
  }

  @Test
  void uebersicht_givenAClosedVorgang_thenMarksTheRowAsClosed() {
    // Given
    when(vorgaenge.uebersicht("", null, true))
        .thenReturn(
            List.of(
                new Vorgang(
                    4L, 12L, "Website-Relaunch", 7L, null, null, null, true, ANGELEGT, ANGELEGT)));
    when(eintraege.juengstesGeschehenJeVorgang(List.of(4L))).thenReturn(Map.of());
    when(firmen.findById(7L)).thenReturn(Optional.of(firma(7L, "Adler AG")));
    when(vorgaenge.zaehleAlle()).thenReturn(1L);

    // When
    final VorgaengeUebersicht uebersicht = useCase.uebersicht("", true);

    // Then
    assertThat(uebersicht.zeilen())
        .singleElement()
        .extracting(VorgangZeile::abgeschlossen)
        .isEqualTo(true);
  }

  @Test
  void uebersicht_givenAFilter_thenGesamtCountsEveryVorgang() {
    // Given — eine Zeile im Ergebnis, sieben Vorgaenge im Bestand.
    when(vorgaenge.uebersicht("adler", null, false)).thenReturn(List.of(vorgang(4L, 12L, 7L)));
    when(eintraege.juengstesGeschehenJeVorgang(List.of(4L))).thenReturn(Map.of());
    when(firmen.findById(7L)).thenReturn(Optional.of(firma(7L, "Adler AG")));
    when(vorgaenge.zaehleAlle()).thenReturn(7L);

    // When
    final VorgaengeUebersicht uebersicht = useCase.uebersicht("adler", false);

    // Then — dieselbe Dreiteilung des Leerfalls wie bei den Firmen.
    assertThat(uebersicht.gesamt()).isEqualTo(7L);
  }

  @Test
  void uebersicht_givenAnEmptyResult_thenHasNoRows() {
    // Given
    when(vorgaenge.uebersicht("nichts", null, false)).thenReturn(List.of());
    when(eintraege.juengstesGeschehenJeVorgang(List.of())).thenReturn(Map.of());
    when(vorgaenge.zaehleAlle()).thenReturn(4L);

    // When
    final VorgaengeUebersicht uebersicht = useCase.uebersicht("nichts", false);

    // Then
    assertThat(uebersicht.zeilen()).isEmpty();
  }

  @Test
  void uebersicht_givenAVorgangWhoseFirmaIsGone_thenFailsLoudly() {
    // Given — der Fremdschluessel schliesst das aus; traete es ein, waere der Bestand kaputt.
    when(vorgaenge.uebersicht("", null, false)).thenReturn(List.of(vorgang(4L, 12L, 7L)));
    when(eintraege.juengstesGeschehenJeVorgang(List.of(4L))).thenReturn(Map.of());
    when(firmen.findById(7L)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> useCase.uebersicht("", false))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("7");
  }
}
