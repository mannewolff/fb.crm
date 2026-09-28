package org.mwolff.fbcrm.vorgang.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.vorgang.domain.Belegstand;
import org.mwolff.fbcrm.vorgang.domain.Phase;

/**
 * Die nachgewiesenen Phasen je Vorgang, eingesammelt ueber alle Belegarten (Plan E2).
 *
 * <p>Der tragende Nachweis ist die Zahl der Abfragen: <b>eine je Belegart</b>, nicht eine je Zeile
 * und nicht eine je Belegart und Zeile. Der Port ist seit diesem Paket mehrfach besetzbar, und
 * genau daran koennte die bekannte Abfrage-Lawine wieder entstehen.
 *
 * <p>Welche Phase am Ende gewinnt, steht hier <b>nicht</b> auf dem Pruefstand: Das entscheidet
 * {@code Vorgang.phase} als Maximum der Menge. Diese Klasse sammelt nur ein, was nachgewiesen ist.
 */
@ExtendWith(MockitoExtension.class)
class PhasenTest {

  @Mock private Belegstand angebote;
  @Mock private Belegstand auftraege;

  @Test
  void zu_givenFiveVorgaengeAndTwoBelegstaende_thenAsksEachExactlyOnce() {
    // Given — zwei Belegarten, fuenf Zeilen: zwei Abfragen, nicht zehn.
    final List<Long> ids = List.of(1L, 2L, 3L, 4L, 5L);
    lenient().when(angebote.phase()).thenReturn(Phase.ANGEBOT);
    lenient().when(auftraege.phase()).thenReturn(Phase.AUFTRAG);
    when(angebote.mitBeleg(ids)).thenReturn(Set.of());
    when(auftraege.mitBeleg(ids)).thenReturn(Set.of());

    // When
    new Phasen(List.of(angebote, auftraege)).zu(ids);

    // Then
    verify(angebote, times(1)).mitBeleg(anyCollection());
    verify(auftraege, times(1)).mitBeleg(anyCollection());
  }

  @Test
  void zu_thenCarriesOnlyVorgaengeWithABeleg() {
    // Given — an Vorgang 2 haengt nichts.
    final List<Long> ids = List.of(1L, 2L);
    when(angebote.phase()).thenReturn(Phase.ANGEBOT);
    when(angebote.mitBeleg(ids)).thenReturn(Set.of(Long.valueOf(1L)));

    // When
    final Map<Long, Set<Phase>> karte = new Phasen(List.of(angebote)).zu(ids);

    // Then
    assertThat(karte).containsOnlyKeys(Long.valueOf(1L));
  }

  @Test
  void zu_givenBothKindsOfBeleg_thenTheVorgangCarriesBothPhases() {
    // Given — Kriterium 10: am selben Vorgang haengen Angebot und Auftrag.
    final List<Long> ids = List.of(1L);
    when(angebote.phase()).thenReturn(Phase.ANGEBOT);
    when(auftraege.phase()).thenReturn(Phase.AUFTRAG);
    when(angebote.mitBeleg(ids)).thenReturn(Set.of(Long.valueOf(1L)));
    when(auftraege.mitBeleg(ids)).thenReturn(Set.of(Long.valueOf(1L)));

    // When
    final Map<Long, Set<Phase>> karte = new Phasen(List.of(angebote, auftraege)).zu(ids);

    // Then — welche gewinnt, entscheidet der Vorgang und nicht diese Karte.
    assertThat(karte.get(Long.valueOf(1L))).containsExactlyInAnyOrder(Phase.ANGEBOT, Phase.AUFTRAG);
  }

  @Test
  void zu_givenAnEmptySelection_thenAsksNoBelegstand() {
    // When — ohne diesen Zweig liefe je Belegart eine Abfrage mit leerer IN-Liste los.
    final Map<Long, Set<Phase>> karte = new Phasen(List.of(angebote, auftraege)).zu(List.of());

    // Then
    assertThat(karte).isEmpty();
    verifyNoInteractions(angebote, auftraege);
  }

  @Test
  void zu_givenNoBelegstandAtAll_thenEveryVorgangStaysWithoutAPhase() {
    // Given — der Zustand vor der ersten Belegart; die Karte bleibt leer statt zu scheitern.
    // When
    final Map<Long, Set<Phase>> karte = new Phasen(List.of()).zu(List.of(1L, 2L));

    // Then
    assertThat(karte).isEmpty();
  }
}
