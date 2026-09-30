package org.mwolff.fbcrm.rechnung.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.rechnung.domain.Nummernkreis;
import org.mwolff.fbcrm.rechnung.domain.Nummernmuster;
import org.mwolff.fbcrm.rechnung.domain.Rechnungseinstellungen;
import org.mwolff.fbcrm.rechnung.domain.RechnungseinstellungenRepository;

/**
 * Das Lesen der Rechnungseinstellungen samt naechster Nummer (fachliche Quelle #159, Kriterium 5).
 *
 * <p>Ohne Sonderzweig fuer die frische Instanz: Die Migration hat die eine Zeile der Einstellungen
 * mit ihren Vorbelegungen angelegt, und ein Zaehlerjahr ohne Zeile steht bei 1 — das beantwortet
 * der Port.
 *
 * <p>Der Anwendungsfall entscheidet nur eines selbst: <b>welches</b> Zaehlerjahr gefragt ist. Das
 * laufende Jahr kommt aus der Geschaeftszone und nicht aus der UTC-Uhr (E12).
 */
@ExtendWith(MockitoExtension.class)
class RechnungseinstellungenLesenUseCaseTest {

  /** 31. Dezember, 23:30 UTC — in Europe/Berlin steht die Uhr schon im Folgejahr. */
  private static final Instant SILVESTERABEND = Instant.parse("2026-12-31T23:30:00Z");

  @Mock private RechnungseinstellungenRepository bestand;
  @Mock private Nummernkreis nummernkreis;

  private RechnungseinstellungenLesenUseCase useCase(final Instant jetzt) {
    return new RechnungseinstellungenLesenUseCase(
        bestand, nummernkreis, Clock.fixed(jetzt, ZoneOffset.UTC));
  }

  private static Rechnungseinstellungen einstellungen(final String muster) {
    return new Rechnungseinstellungen(new Nummernmuster(muster), new BigDecimal("19.50"), 14);
  }

  @Test
  void lese_thenAnswersWithTheStoredSettingsAndTheCounterOfTheirYear() {
    // Given
    final Rechnungseinstellungen gespeichert = einstellungen("R{JJ}-{NNNN}");
    when(bestand.lies()).thenReturn(gespeichert);
    when(nummernkreis.lies(2026)).thenReturn(4);

    // When
    final RechnungseinstellungenMitNummer gelesen =
        useCase(Instant.parse("2026-09-30T09:30:00Z")).lese();

    // Then — der Anwendungsfall reicht die Einstellungen durch und rechnet nichts um.
    assertThat(gelesen).isEqualTo(new RechnungseinstellungenMitNummer(gespeichert, 4));
  }

  @Test
  void lese_givenAPatternWithoutAYear_thenAsksForTheYearlessCounter() {
    // Given — ein Muster ohne Jahres-Platzhalter zaehlt in einem einzigen, durchlaufenden Kreis.
    when(bestand.lies()).thenReturn(einstellungen("{NNNN}"));
    when(nummernkreis.lies(Nummernmuster.OHNE_JAHR)).thenReturn(7);

    // When
    final RechnungseinstellungenMitNummer gelesen =
        useCase(Instant.parse("2026-09-30T09:30:00Z")).lese();

    // Then
    assertThat(gelesen.naechsteNummer()).isEqualTo(7);
  }

  @Test
  void lese_givenTheTurnOfTheYearInTheBusinessZone_thenAsksForTheNewYear() {
    // Given — 23:30 UTC am 31. Dezember ist in Europe/Berlin schon der 1. Januar.
    when(bestand.lies()).thenReturn(einstellungen("{NNNN}-{JJJJ}"));

    // When
    useCase(SILVESTERABEND).lese();

    // Then — ohne die Geschaeftszone stuende hier noch die 2026, und die Maske zeigte den Zaehler
    // des alten Jahres.
    verify(nummernkreis).lies(2027);
  }
}
