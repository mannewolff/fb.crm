package org.mwolff.fbcrm.auftrag.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.auftrag.domain.AuftragRepository;
import org.mwolff.fbcrm.vorgang.domain.Phase;

/**
 * Der Belegstand des Auftrags — die zweite Umsetzung des mehrfach besetzbaren Ports (Issue #117).
 *
 * <p>Mit ihr wird die Phase {@code AUFTRAG} erstmals nachgewiesen (Kriterium 10). <b>Was hier als
 * Beleg zaehlt, ist einfacher als beim Angebot</b>: sein Dasein. Ein Auftrag hat keinen Entwurf und
 * traegt seine Nummer ab dem Anlegen — es gibt also keinen Zustand, in dem er nicht zaehlt.
 *
 * <p>Drei Zusagen sind der Gegenstand: Diese Umsetzung steht fuer die Phase {@code AUFTRAG}, eine
 * leere Auswahl kostet keine Abfrage, und eine gefuellte kostet <b>eine</b> — nicht eine je
 * Kennung. Dass die Phase am Ende auch in den Ansichten erscheint, steht in {@code PhaseAuftragIT}
 * gegen die echte Datenbank.
 */
@ExtendWith(MockitoExtension.class)
class AuftragBelegstandTest {

  @Mock private AuftragRepository auftraege;

  @InjectMocks private AuftragBelegstand belegstand;

  @Test
  void phase_thenStandsForTheAuftrag() {
    // When
    final Phase phase = belegstand.phase();

    // Then
    assertThat(phase).isEqualTo(Phase.AUFTRAG);
    verifyNoInteractions(auftraege);
  }

  @Test
  void mitBeleg_givenAnEmptySelection_thenAsksNothing() {
    // When — ohne diesen Zweig liefe eine Abfrage mit leerer IN-Liste los.
    final Set<Long> gefunden = belegstand.mitBeleg(List.of());

    // Then
    assertThat(gefunden).isEmpty();
    verifyNoInteractions(auftraege);
  }

  @Test
  void mitBeleg_thenAnswersWithTheKeysOfTheBestand() {
    // Given
    when(auftraege.vorgaengeMitAuftrag(List.of(4L, 5L))).thenReturn(Set.of(Long.valueOf(4L)));

    // When
    final Set<Long> gefunden = belegstand.mitBeleg(List.of(4L, 5L));

    // Then
    assertThat(gefunden).containsExactly(Long.valueOf(4L));
  }

  @Test
  void mitBeleg_givenTwoVorgaenge_thenAsksOnceForTheWholeSet() {
    // Given
    when(auftraege.vorgaengeMitAuftrag(List.of(4L, 5L))).thenReturn(Set.of());

    // When
    belegstand.mitBeleg(List.of(4L, 5L));

    // Then — eine Abfrage fuer die ganze Menge, nicht eine je Zeile.
    verify(auftraege, times(1)).vorgaengeMitAuftrag(anyCollection());
  }
}
