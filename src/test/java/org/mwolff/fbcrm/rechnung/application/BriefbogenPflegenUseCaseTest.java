package org.mwolff.fbcrm.rechnung.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.rechnung.domain.Briefbogen;
import org.mwolff.fbcrm.rechnung.domain.BriefbogenRepository;
import org.mwolff.fbcrm.rechnung.domain.Briefpapierblatt;
import org.mwolff.fbcrm.rechnung.domain.Randmasse;

/**
 * Das Fortschreiben von Schaltern und Raendern (fachliche Quelle #361, Kriterien 13 und 16; Plan
 * #363, E9 und E15).
 *
 * <p>Geschrieben wird der ganze Satz. Welcher Rand „der geaenderte" ist, entsteht aus dem Vergleich
 * mit der gespeicherten Zeile; passt der Satz nicht, faellt der ganze {@code PUT}, und die
 * bisherige Zeile bleibt. Die Grenzwerte stammen aus {@code RandgrenzenTest}, wo sie von Hand
 * gerechnet sind.
 */
@ExtendWith(MockitoExtension.class)
class BriefbogenPflegenUseCaseTest {

  private static final Instant JETZT = Instant.parse("2026-10-19T09:30:00Z");

  /** Die Raender aus Kriterium 15 in Zehntel-Millimetern. */
  private static final Randmasse VORGABE = new Randmasse(250, 200, 985, 200, 200, 200);

  private static final Briefpapierblatt BLATT =
      new Briefpapierblatt("briefpapier/3f2a.pdf", "kopf.pdf", 98_765L, 2);

  @Mock private BriefbogenRepository bestand;

  private BriefbogenPflegenUseCase useCase() {
    return new BriefbogenPflegenUseCase(bestand, Clock.fixed(JETZT, ZoneOffset.UTC));
  }

  private Map<String, List<String>> weiseAb(
      final boolean absenderzeile, final boolean steuerBankZeile, final Randmasse raender) {
    final RandNichtMoeglich fehler =
        catchThrowableOfType(
            RandNichtMoeglich.class,
            () -> useCase().pflege(absenderzeile, steuerBankZeile, raender));
    assertThat(fehler).isNotNull();
    // Kriterium 13: die bisherige Einstellung bleibt — der ganze PUT faellt.
    verify(bestand, never()).speichere(any(), any());
    return fehler.felder();
  }

  @Test
  void pflege_givenAFittingSet_thenStoresItAndKeepsTheBriefpapier() {
    // Given — E15: das Blatt gehoert nicht zu dem, was dieser Weg schreibt.
    when(bestand.lies()).thenReturn(new Briefbogen(BLATT, true, true, VORGABE));
    final Randmasse neu = new Randmasse(300, 150, 1_000, 250, 180, 220);

    // When
    final Briefbogenstand stand = useCase().pflege(false, true, neu);

    // Then
    verify(bestand).speichere(new Briefbogen(BLATT, false, true, neu), JETZT);
    assertThat(stand)
        .isEqualTo(new Briefbogenstand(true, "kopf.pdf", 98_765L, 2, false, true, neu));
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
