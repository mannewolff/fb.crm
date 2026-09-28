package org.mwolff.fbcrm.angebot.application;

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
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.vorgang.domain.Phase;

/**
 * Der Belegstand des Angebots — die Umsetzung des Ports, an dem der Vorgang seine Phase ableitet
 * (Plan E2).
 *
 * <p>Die Abhaengigkeit laeuft nur in eine Richtung: {@code vorgang.domain} schreibt den Port {@code
 * Belegstand} aus, {@code angebot.application} setzt ihn um. Wuerde der Vorgang das Angebot direkt
 * fragen, schluege {@code ArchitectureTest.modules_thenFreeOfCycles} an.
 *
 * <p>Drei Zusagen sind hier der Gegenstand: Diese Umsetzung steht fuer die Phase {@code ANGEBOT},
 * eine leere Auswahl kostet keine Abfrage, und eine gefuellte kostet <b>eine</b> — nicht eine je
 * Kennung. Welche Angebote als festgeschrieben gelten, entscheidet die Abfrage im Bestand und steht
 * in {@code PhaseAbleitungIT} gegen die echte Datenbank.
 */
@ExtendWith(MockitoExtension.class)
class AngebotBelegstandTest {

  @Mock private AngebotRepository angebote;

  @InjectMocks private AngebotBelegstand belegstand;

  @Test
  void phase_thenStandsForTheAngebot() {
    // When — welche Phase dieser Beleg begruendet, sagt seit dem mehrfach besetzbaren Port er
    // selbst und nicht mehr der Name seiner Methode.
    final Phase phase = belegstand.phase();

    // Then
    assertThat(phase).isEqualTo(Phase.ANGEBOT);
    verifyNoInteractions(angebote);
  }

  @Test
  void mitBeleg_givenAnEmptySelection_thenAsksNothing() {
    // When — ohne diesen Zweig liefe eine Abfrage mit leerer IN-Liste los.
    final Set<Long> gefunden = belegstand.mitBeleg(List.of());

    // Then
    assertThat(gefunden).isEmpty();
    verifyNoInteractions(angebote);
  }

  @Test
  void mitBeleg_thenAnswersWithTheKeysOfTheBestand() {
    // Given
    when(angebote.vorgaengeMitFestgeschriebenemAngebot(List.of(4L, 5L)))
        .thenReturn(Set.of(Long.valueOf(4L)));

    // When
    final Set<Long> gefunden = belegstand.mitBeleg(List.of(4L, 5L));

    // Then
    assertThat(gefunden).containsExactly(Long.valueOf(4L));
  }

  @Test
  void mitBeleg_givenTwoVorgaenge_thenAsksOnceForTheWholeSet() {
    // Given
    when(angebote.vorgaengeMitFestgeschriebenemAngebot(List.of(4L, 5L))).thenReturn(Set.of());

    // When
    belegstand.mitBeleg(List.of(4L, 5L));

    // Then — eine Abfrage fuer die ganze Menge, nicht eine je Zeile.
    verify(angebote, times(1)).vorgaengeMitFestgeschriebenemAngebot(anyCollection());
  }

  @Test
  void mitBeleg_givenNoCommittedOffer_thenEmpty() {
    // Given — ein Vorgang, an dem nur Entwuerfe haengen, steht in keiner Angebotsphase (F6).
    when(angebote.vorgaengeMitFestgeschriebenemAngebot(List.of(4L))).thenReturn(Set.of());

    // When
    final Set<Long> gefunden = belegstand.mitBeleg(List.of(4L));

    // Then
    assertThat(gefunden).isEmpty();
  }
}
