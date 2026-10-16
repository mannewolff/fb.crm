package org.mwolff.fbcrm.rechnung.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.common.Einheit;

/**
 * Die abgerechneten Mengen je Angebotsposition als eine Stelle (Plan #303, E3).
 *
 * <p>Gegenstand ist die Zaehlweise des {@link Abrechnungsstand}: Jede uebergebene Rechnung zaehlt,
 * ein Entwurf ebenso wie eine gestellte. Dieselbe Summierung speist das „Offen" der Angebotsansicht
 * und den Forecast (#302, Kriterium 3); eine zweite Abschrift liefe beim ersten Nachziehen
 * auseinander.
 */
class AbgerechneteMengenTest {

  private static final long KONZEPTION = 101L;
  private static final long SCHULUNG = 102L;
  private static final long ANGEBOT = 11L;

  private static final Instant ANGELEGT = Instant.parse("2026-09-20T08:00:00Z");
  private static final LocalDate RECHNUNGSDATUM = LocalDate.of(2026, 9, 30);

  private static Rechnung rechnung(
      final long id, final Rechnungszustand zustand, final List<Rechnungsposition> positionen) {
    final boolean gestellt = zustand == Rechnungszustand.GESTELLT;
    return new Rechnung(
        Long.valueOf(id),
        ANGEBOT,
        zustand,
        RECHNUNGSDATUM,
        "September 2026",
        positionen,
        gestellt ? "R26-000" + id : null,
        gestellt ? new BigDecimal("19.00") : null,
        gestellt ? Integer.valueOf(10) : null,
        gestellt ? ANGELEGT : null,
        null,
        null,
        null,
        ANGELEGT,
        ANGELEGT);
  }

  private static Rechnungsposition abrechnung(final long angebotPositionId, final String menge) {
    return new Rechnungsposition(
        angebotPositionId,
        "Beratung",
        new BigDecimal(menge),
        Einheit.STUNDE,
        new BigDecimal("100.00"));
  }

  @Test
  void aus_withTheSamePositionOnSeveralRechnungen_thenTheMengenAreAdded() {
    // When
    final AbgerechneteMengen mengen =
        AbgerechneteMengen.aus(
            List.of(
                rechnung(
                    1L,
                    Rechnungszustand.GESTELLT,
                    List.of(abrechnung(KONZEPTION, "80.00"), abrechnung(SCHULUNG, "0.50"))),
                rechnung(2L, Rechnungszustand.GESTELLT, List.of(abrechnung(KONZEPTION, "20.50")))));

    // Then
    assertThat(mengen.jePosition())
        .containsOnly(
            Map.entry(Long.valueOf(KONZEPTION), new BigDecimal("100.50")),
            Map.entry(Long.valueOf(SCHULUNG), new BigDecimal("0.50")));
  }

  @Test
  void aus_withAnEntwurf_thenItCountsLikeAGestellteRechnung() {
    // When
    final AbgerechneteMengen mengen =
        AbgerechneteMengen.aus(
            List.of(
                rechnung(1L, Rechnungszustand.GESTELLT, List.of(abrechnung(KONZEPTION, "80.00"))),
                rechnung(2L, Rechnungszustand.ENTWURF, List.of(abrechnung(KONZEPTION, "50.00")))));

    // Then — sonst zeigte der Entwurf dieselbe Menge noch einmal als offen (#160, Kriterium 6).
    assertThat(mengen.jePosition())
        .containsOnly(Map.entry(Long.valueOf(KONZEPTION), new BigDecimal("130.00")));
  }

  @Test
  void ohne_theNamedRechnung_thenItsMengeDoesNotCount() {
    // When
    final AbgerechneteMengen mengen =
        AbgerechneteMengen.ohne(
            List.of(
                rechnung(1L, Rechnungszustand.GESTELLT, List.of(abrechnung(KONZEPTION, "80.00"))),
                rechnung(2L, Rechnungszustand.ENTWURF, List.of(abrechnung(KONZEPTION, "50.00")))),
            2L);

    // Then
    assertThat(mengen.jePosition())
        .containsOnly(Map.entry(Long.valueOf(KONZEPTION), new BigDecimal("80.00")));
  }

  @Test
  void aus_aPositionWithoutAnyRechnungszeile_thenItIsNotInTheMap() {
    // When
    final AbgerechneteMengen mengen =
        AbgerechneteMengen.aus(
            List.of(
                rechnung(1L, Rechnungszustand.GESTELLT, List.of(abrechnung(KONZEPTION, "80.00")))));

    // Then — die Schulung steht auf keiner Rechnung und darum nicht in der Karte.
    assertThat(mengen.jePosition()).doesNotContainKey(Long.valueOf(SCHULUNG));
    assertThat(mengen.jePosition()).containsOnlyKeys(Long.valueOf(KONZEPTION));
  }

  @Test
  void jePosition_thenTheMapCannotBeChangedFromOutside() {
    // When
    final AbgerechneteMengen mengen = AbgerechneteMengen.aus(List.of());

    // Then
    assertThat(mengen.jePosition()).isUnmodifiable().isEmpty();
  }
}
