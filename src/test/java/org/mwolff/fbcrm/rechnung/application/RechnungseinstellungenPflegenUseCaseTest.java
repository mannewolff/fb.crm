package org.mwolff.fbcrm.rechnung.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.rechnung.domain.Nummernkreis;
import org.mwolff.fbcrm.rechnung.domain.Nummernmuster;
import org.mwolff.fbcrm.rechnung.domain.RechnungRepository;
import org.mwolff.fbcrm.rechnung.domain.Rechnungseinstellungen;
import org.mwolff.fbcrm.rechnung.domain.RechnungseinstellungenRepository;

/**
 * Das Fortschreiben der Rechnungseinstellungen (fachliche Quelle #159, Kriterien 7 und 15).
 *
 * <p>Geschrieben wird immer der ganze Satz — die Maske zeigt alle vier Werte auf einmal. Die Nummer
 * geht getrennt an den Nummernkreis, und zwar unter dem Zaehlerjahr des <b>neu eingereichten</b>
 * Musters.
 *
 * <p>Zeitpunkt und laufendes Jahr kommen aus der injizierten Uhr, nie aus {@code Instant.now()}
 * (CLAUDE-java.md §6.2); das Jahr rechnet gegen die Geschaeftszone (E12).
 *
 * <p>Dazu die Pruefung der eingereichten Nummer gegen den Bestand (#160, Kriterium 19; Plan #169,
 * E16): Gefragt wird mit der Nummer, die das <b>eingereichte</b> Muster in seinem Zaehlerjahr
 * ergaebe — und traegt sie schon eine Rechnung, wird nichts geschrieben.
 */
@ExtendWith(MockitoExtension.class)
class RechnungseinstellungenPflegenUseCaseTest {

  private static final Instant JETZT = Instant.parse("2026-09-30T09:30:00Z");

  /** 31. Dezember, 23:30 UTC — in Europe/Berlin steht die Uhr schon im Folgejahr. */
  private static final Instant SILVESTERABEND = Instant.parse("2026-12-31T23:30:00Z");

  @Mock private RechnungseinstellungenRepository bestand;
  @Mock private Nummernkreis nummernkreis;
  @Mock private RechnungRepository rechnungen;

  private RechnungseinstellungenPflegenUseCase useCase(final Instant jetzt) {
    return new RechnungseinstellungenPflegenUseCase(
        bestand, nummernkreis, rechnungen, Clock.fixed(jetzt, ZoneOffset.UTC));
  }

  private static Rechnungseinstellungen einstellungen(final String muster) {
    return new Rechnungseinstellungen(new Nummernmuster(muster), new BigDecimal("19.50"), 14);
  }

  @Test
  void pflege_thenStoresTheSettingsWithTheTimeOfTheInjectedClock() {
    // Given
    final Rechnungseinstellungen eingereicht = einstellungen("R{JJ}-{NNNN}");

    // When
    useCase(JETZT).pflege(eingereicht, 4);

    // Then — unveraendert weitergereicht; zu normalisieren gibt es hier nichts, die drei Werte
    // sind Zahlen und ein bereits geprueftes Muster.
    verify(bestand).speichere(eingereicht, JETZT);
  }

  @Test
  void pflege_thenSetsTheCounterOfTheYearOfTheSubmittedPattern() {
    // When — Kriterium 15: eine selbst gesetzte Nummer gilt.
    useCase(JETZT).pflege(einstellungen("{NNNN}-{JJJJ}"), 4);

    // Then
    verify(nummernkreis).setze(2026, 4);
  }

  @Test
  void pflege_givenAPatternWithoutAYear_thenSetsTheYearlessCounter() {
    // When — wer in derselben Eingabe auf ein Muster ohne Jahr umstellt, meint den neuen Kreis.
    useCase(JETZT).pflege(einstellungen("{NNNN}"), 7);

    // Then
    verify(nummernkreis).setze(Nummernmuster.OHNE_JAHR, 7);
  }

  @Test
  void pflege_givenTheTurnOfTheYearInTheBusinessZone_thenSetsTheCounterOfTheNewYear() {
    // When — 23:30 UTC am 31. Dezember ist in Europe/Berlin schon der 1. Januar.
    useCase(SILVESTERABEND).pflege(einstellungen("{NNNN}-{JJJJ}"), 4);

    // Then — ohne die Geschaeftszone landete die gesetzte Nummer im Zaehler des alten Jahres.
    verify(nummernkreis).setze(2027, 4);
  }

  @Test
  void pflege_thenAsksTheBestandForTheNummerTheSubmittedPatternWouldForm() {
    // When — Kriterium 19: gefragt wird mit dem eingereichten Muster, nicht dem gespeicherten.
    useCase(JETZT).pflege(einstellungen("R{JJ}-{NNNN}"), 4);

    // Then
    verify(rechnungen).existiertNummer("R26-0004");
  }

  @Test
  void pflege_givenAPatternWithoutAYear_thenAsksWithoutAYear() {
    // When — ohne Jahres-Platzhalter steht in der Nummer kein Jahr, also auch in der Frage keines.
    useCase(JETZT).pflege(einstellungen("{NNNN}"), 7);

    // Then
    verify(rechnungen).existiertNummer("0007");
  }

  @Test
  void pflege_givenANummerAnExistingRechnungAlreadyCarries_thenRefusesAndWritesNothing() {
    // Given — Kriterium 19: die 1 des laufenden Jahres steht schon auf einem Beleg.
    when(rechnungen.existiertNummer("0001-2026")).thenReturn(true);

    // When / Then — die Meldung nennt die Nummer und haengt am Feld der Maske.
    final NaechsteNummerSchonVergeben fehler =
        catchThrowableOfType(
            NaechsteNummerSchonVergeben.class,
            () -> useCase(JETZT).pflege(einstellungen("{NNNN}-{JJJJ}"), 1));

    // Then — dieselbe Meldung im Rumpf und am Feld; liefen sie auseinander, lase die Maske zwei
    // verschiedene Saetze fuer dieselbe Lage.
    assertThat(fehler).hasMessage(NaechsteNummerSchonVergeben.MELDUNG + "0001-2026");
    assertThat(fehler.felder())
        .containsExactly(
            entry(
                NaechsteNummerSchonVergeben.FELD,
                List.of(NaechsteNummerSchonVergeben.MELDUNG + "0001-2026")));

    // Then — nichts gespeichert, kein Zaehler gesetzt.
    verify(bestand, never()).speichere(any(), any());
    verifyNoInteractions(nummernkreis);
  }
}
