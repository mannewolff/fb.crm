package org.mwolff.fbcrm.rechnung.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.rechnung.domain.Briefbogen;
import org.mwolff.fbcrm.rechnung.domain.BriefbogenRepository;
import org.mwolff.fbcrm.rechnung.domain.Briefpapierblatt;
import org.mwolff.fbcrm.rechnung.domain.Randmasse;
import org.mwolff.fbcrm.rechnung.domain.Zeilenstand;

/**
 * Das Fortschreiben von Schaltern und Raendern (fachliche Quelle #361, Kriterien 13 und 16; Plan
 * #363, E9 und E15).
 *
 * <p>Geschrieben wird der ganze Satz. Welcher Rand „der geaenderte" ist, entsteht aus dem Vergleich
 * mit der gespeicherten Zeile; passt der Satz nicht, faellt der ganze {@code PUT}, und die
 * bisherige Zeile bleibt. Die Grenzwerte stammen aus {@code RandgrenzenTest}, wo sie von Hand
 * gerechnet sind.
 *
 * <p>Davor steht der Vergleich des vorgelegten Stands (#411, Kriterium 4; Plan #415, E1, E11, E14):
 * Auf einem alten Stand wird abgewiesen, bevor die Raender geprueft werden.
 */
@ExtendWith(MockitoExtension.class)
class BriefbogenPflegenUseCaseTest {

  private static final Instant JETZT = Instant.parse("2026-10-19T09:30:00Z");

  /** Die Raender aus Kriterium 15 in Zehntel-Millimetern. */
  private static final Randmasse VORGABE = new Randmasse(250, 200, 985, 200, 200, 200);

  private static final Briefpapierblatt BLATT =
      new Briefpapierblatt("briefpapier/3f2a.pdf", "kopf.pdf", 98_765L, 2);

  /** Der Stand, den die Maske vorlegt und den die Zeile traegt. */
  private static final long STAND = 3L;

  /** Das handelnde Konto. */
  private static final long KONTO = 31L;

  /** Das Konto, das zuletzt geaendert hat — etwa mit einem Hochladen. */
  private static final long ANNA = 30L;

  private static final Instant ANNAS_AENDERUNG = Instant.parse("2026-10-19T08:42:00Z");

  @Mock private BriefbogenRepository bestand;

  @BeforeEach
  void richteDenStandEin() {
    // Nachsichtig: Die abweisenden Faelle kommen nie zum Schreiben.
    lenient().when(bestand.stand()).thenReturn(new Zeilenstand(STAND, ANNA, ANNAS_AENDERUNG));
    lenient()
        .when(bestand.speichere(any(), any(), anyLong(), anyLong()))
        .thenReturn(new Zeilenstand(STAND + 1, KONTO, JETZT));
  }

  private BriefbogenPflegenUseCase useCase() {
    return new BriefbogenPflegenUseCase(bestand, Clock.fixed(JETZT, ZoneOffset.UTC));
  }

  private Map<String, List<String>> weiseAb(
      final boolean absenderzeile, final boolean steuerBankZeile, final Randmasse raender) {
    final RandNichtMoeglich fehler =
        catchThrowableOfType(
            RandNichtMoeglich.class,
            () -> useCase().pflege(absenderzeile, steuerBankZeile, raender, STAND, KONTO));
    assertThat(fehler).isNotNull();
    // Kriterium 13: die bisherige Einstellung bleibt — der ganze PUT faellt.
    verify(bestand, never()).speichere(any(), any(), anyLong(), anyLong());
    return fehler.felder();
  }

  @Test
  void pflege_givenAFittingSet_thenStoresItAndKeepsTheBriefpapier() {
    // Given — E15: das Blatt gehoert nicht zu dem, was dieser Weg schreibt.
    when(bestand.lies()).thenReturn(new Briefbogen(BLATT, true, true, VORGABE));
    final Randmasse neu = new Randmasse(300, 150, 1_000, 250, 180, 220);

    // When
    final Briefbogenstand stand = useCase().pflege(false, true, neu, STAND, KONTO);

    // Then — mit dem vorgelegten Stand und dem handelnden Konto; die Antwort traegt die erhoehte
    // Version.
    verify(bestand).speichere(new Briefbogen(BLATT, false, true, neu), JETZT, STAND, KONTO);
    assertThat(stand)
        .isEqualTo(new Briefbogenstand(true, "kopf.pdf", 98_765L, 2, false, true, neu, STAND + 1));
  }

  @Test
  void pflege_givenTheStandBeforeAnUpload_thenRefusesBeforeTheMarginsAndWritesNothing() {
    // When — E11: Anna hat inzwischen ein Briefpapier hochgeladen; das schreibt in andere Spalten,
    // erhoeht aber denselben Stand. Die Raender passen nicht einmal — der Stand steht davor.
    final BriefbogenGeaendert konflikt =
        catchThrowableOfType(
            BriefbogenGeaendert.class,
            () ->
                useCase()
                    .pflege(true, true, new Randmasse(250, 200, 985, 701, 200, 200), 2L, KONTO));

    // Then
    assertThat(konflikt).isNotNull();
    assertThat(konflikt.urheberKonto()).hasValue(ANNA);
    assertThat(konflikt.handelndesKonto()).isEqualTo(KONTO);
    assertThat(konflikt.zeitpunkt()).hasValue(ANNAS_AENDERUNG);
    verify(bestand, never()).lies();
    verify(bestand, never()).speichere(any(), any(), anyLong(), anyLong());
  }

  @Test
  void pflege_givenOneChangedMarginThatDoesNotFit_thenRefusesAtItsFieldWithTheLargestValue() {
    // Given — nur der untere Rand weicht ab; er traegt hoechstens 70,0 mm.
    when(bestand.lies()).thenReturn(new Briefbogen(null, true, true, VORGABE));

    // When
    final Map<String, List<String>> felder =
        weiseAb(true, true, new Randmasse(250, 200, 985, 701, 200, 200));

    // Then
    assertThat(felder)
        .containsExactly(
            Map.entry(
                "unten",
                List.of(
                    "Bei den übrigen eingestellten Rändern darf dieser Rand höchstens 70,0 mm"
                        + " betragen.")));
  }

  @Test
  void pflege_givenSeveralChangedMargins_thenRefusesAtTheFirstInTheFixedOrder() {
    // Given — rechts und unten weichen ab, und allein unten passt nicht. Die Meldung haengt
    // trotzdem am ersten abweichenden Rand in der Reihenfolge links, rechts, Beginn des Inhalts,
    // unten, Folgeseite oben, Folgeseite unten (E9); sein Grenzwert gilt bei den uebrigen
    // eingereichten Raendern, und bei unten 75 mm passt kein rechter Rand mehr.
    when(bestand.lies()).thenReturn(new Briefbogen(null, true, true, VORGABE));

    // When
    final Map<String, List<String>> felder =
        weiseAb(true, true, new Randmasse(250, 210, 985, 750, 200, 200));

    // Then
    assertThat(felder)
        .containsExactly(Map.entry("rechts", List.of(RandNichtMoeglich.MELDUNG_KEIN_WERT)));
  }

  @Test
  void pflege_givenSeveralChangedMarginsWithALimit_thenNamesTheLimitOfTheFirst() {
    // Given — links und rechts weichen ab; zusammen lassen sie der Tabelle keine volle Breite.
    // Bei rechts 30 mm (85 Punkte) traegt links hoechstens 595 - 85 - 432 = 78 Punkte, also 27,6
    // mm.
    when(bestand.lies()).thenReturn(new Briefbogen(null, true, true, VORGABE));

    // When
    final Map<String, List<String>> felder =
        weiseAb(true, true, new Randmasse(300, 300, 985, 200, 200, 200));

    // Then
    assertThat(felder)
        .containsExactly(
            Map.entry(
                "links",
                List.of(
                    "Bei den übrigen eingestellten Rändern darf dieser Rand höchstens 27,6 mm"
                        + " betragen.")));
  }

  @Test
  void pflege_givenOnlyASwitchTurnedOn_thenMeasuresWithItAndRefusesAtAMarginThatCanShrink() {
    // Given — Folgeseite oben 260 mm passt nur ohne Steuer- und Bankzeile (RandgrenzenTest). Kein
    // Rand weicht ab; die Meldung haengt am ersten Rand, der durch Verkleinern wieder passt.
    final Randmasse gespeichert = new Randmasse(250, 200, 985, 200, 2600, 200);
    when(bestand.lies()).thenReturn(new Briefbogen(null, true, false, gespeichert));

    // When
    final Map<String, List<String>> felder = weiseAb(true, true, gespeichert);

    // Then — 842 - 32 - 87 = 723 Punkte: 255,2 mm.
    assertThat(felder)
        .containsExactly(
            Map.entry(
                "folgeOben",
                List.of(
                    "Bei den übrigen eingestellten Rändern darf dieser Rand höchstens 255,2 mm"
                        + " betragen.")));
  }

  @Test
  void pflege_givenNoChangedMarginAndNoMarginThatCanShrink_thenRefusesAtTheLeftMargin() {
    // Given — beide seitlichen Raender je 150 mm: kein einzelner Rand gibt der Tabelle ihre Breite
    // zurueck.
    final Randmasse ohneAusweg = new Randmasse(1_500, 1_500, 985, 200, 200, 200);
    when(bestand.lies()).thenReturn(new Briefbogen(null, true, true, ohneAusweg));

    // When
    final Map<String, List<String>> felder = weiseAb(true, true, ohneAusweg);

    // Then
    assertThat(felder)
        .containsExactly(Map.entry("links", List.of(RandNichtMoeglich.MELDUNG_KEIN_WERT)));
  }
}
