package org.mwolff.fbcrm.rechnung.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.angebot.domain.Angebotsposition;
import org.mwolff.fbcrm.common.Abrechnungsmodus;
import org.mwolff.fbcrm.common.Einheit;

/**
 * Der Abrechnungsstand als reiner Rechner (Plan #169, E6; fachliche Quelle #160, Kriterien 6 bis
 * 8).
 *
 * <p>Gegenstand ist die Teilabrechnung: Was an einer Angebotsposition angeboten, was schon
 * abgerechnet und was noch offen ist. Entwuerfe zaehlen wie gestellte Rechnungen mit — sonst zeigte
 * ein zweiter Entwurf dieselbe Menge noch einmal als offen, und es wuerde doppelt abgerechnet
 * (Kriterium 6). Fuer die Maske eines Entwurfs bleibt genau dieser Entwurf aussen vor: Sonst
 * erschiene die eigene Menge als fremde Abrechnung.
 */
class AbrechnungsstandTest {

  private static final long KONZEPTION = 101L;
  private static final long SCHULUNG = 102L;
  private static final long ANGEBOT = 11L;

  private static final Instant ANGELEGT = Instant.parse("2026-09-20T08:00:00Z");
  private static final LocalDate RECHNUNGSDATUM = LocalDate.of(2026, 9, 30);

  /** 160 Stunden zu 100,00 € — das Beispiel aus dem Ziel von #160. */
  private static final Angebotsposition BERATUNG =
      new Angebotsposition(
          Long.valueOf(KONZEPTION),
          "Beratung",
          Abrechnungsmodus.AUFWAND,
          new BigDecimal("160.00"),
          Einheit.STUNDE,
          new BigDecimal("100.00"));

  /** Eine Pauschale — an ihr zeigt sich, dass Bruchteile genau rechnen (Frage 1 aus #160). */
  private static final Angebotsposition PAUSCHALE =
      new Angebotsposition(
          Long.valueOf(SCHULUNG),
          "Schulungstag",
          Abrechnungsmodus.FESTPREIS,
          BigDecimal.ONE,
          Einheit.PAUSCHAL,
          new BigDecimal("1200.00"));

  private static Rechnung rechnung(
      final long id, final Rechnungszustand zustand, final List<Rechnungsposition> positionen) {
    return new Rechnung(
        Long.valueOf(id),
        ANGEBOT,
        zustand,
        RECHNUNGSDATUM,
        "September 2026",
        positionen,
        zustand == Rechnungszustand.GESTELLT ? "R26-0001" : null,
        zustand == Rechnungszustand.GESTELLT ? new BigDecimal("19.00") : null,
        zustand == Rechnungszustand.GESTELLT ? Integer.valueOf(10) : null,
        zustand == Rechnungszustand.GESTELLT ? ANGELEGT : null,
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
        BERATUNG.einzelpreis());
  }

  private static Positionsstand ersterStand(final Abrechnungsstand stand) {
    return stand.positionen().getFirst();
  }

  @Test
  void fuer_withOneGestellteRechnung_thenAbgerechnetAndOffenSplitTheAngeboteneMenge() {
    // Given — 160 angeboten, 80 gestellt.
    final Abrechnungsstand stand =
        Abrechnungsstand.fuer(
            List.of(BERATUNG),
            List.of(
                rechnung(1L, Rechnungszustand.GESTELLT, List.of(abrechnung(KONZEPTION, "80.00")))));

    // When / Then
    assertThat(ersterStand(stand))
        .satisfies(
            positionsstand -> assertThat(positionsstand.angeboten()).isEqualByComparingTo("160"),
            positionsstand -> assertThat(positionsstand.abgerechnet()).isEqualByComparingTo("80"),
            positionsstand -> assertThat(positionsstand.offen()).isEqualByComparingTo("80"),
            positionsstand ->
                assertThat(positionsstand.ueberschreitung()).isEqualByComparingTo("0"));
  }

  @Test
  void fuer_withAnEntwurfOnTop_thenTheEntwurfCountsAsAbgerechnet() {
    // Given — 80 gestellt und 50 als Entwurf: zusammen 130 von 160.
    final Abrechnungsstand stand =
        Abrechnungsstand.fuer(
            List.of(BERATUNG),
            List.of(
                rechnung(1L, Rechnungszustand.GESTELLT, List.of(abrechnung(KONZEPTION, "80.00"))),
                rechnung(2L, Rechnungszustand.ENTWURF, List.of(abrechnung(KONZEPTION, "50.00")))));

    // When / Then
    assertThat(ersterStand(stand).offen()).isEqualByComparingTo("30");
  }

  @Test
  void ohne_theNamedRechnung_thenItsMengeDoesNotCountAsAbgerechnet() {
    // Given — derselbe Bestand, aber aus der Sicht des Entwurfs 2.
    final Abrechnungsstand stand =
        Abrechnungsstand.ohne(
            List.of(BERATUNG),
            List.of(
                rechnung(1L, Rechnungszustand.GESTELLT, List.of(abrechnung(KONZEPTION, "80.00"))),
                rechnung(2L, Rechnungszustand.ENTWURF, List.of(abrechnung(KONZEPTION, "50.00")))),
            2L);

    // When / Then
    assertThat(ersterStand(stand).offen()).isEqualByComparingTo("80");
  }

  @Test
  void fuer_withMoreAbgerechnetThanAngeboten_thenOffenIsZeroAndUeberschreitungShowsTheRest() {
    // Given — 170 von 160 abgerechnet (Frage 4 aus #160: erlaubt, mit Hinweis).
    final Abrechnungsstand stand =
        Abrechnungsstand.fuer(
            List.of(BERATUNG),
            List.of(
                rechnung(
                    1L, Rechnungszustand.GESTELLT, List.of(abrechnung(KONZEPTION, "170.00")))));

    // When / Then
    assertThat(ersterStand(stand))
        .satisfies(
            positionsstand -> assertThat(positionsstand.offen()).isEqualByComparingTo("0"),
            positionsstand ->
                assertThat(positionsstand.ueberschreitung()).isEqualByComparingTo("10"));
  }

  @Test
  void fuer_aPositionWithoutAnyRechnung_thenOffenEqualsAngeboten() {
    // Given — die Pauschale steht in keiner Rechnung.
    final Abrechnungsstand stand =
        Abrechnungsstand.fuer(
            List.of(BERATUNG, PAUSCHALE),
            List.of(
                rechnung(1L, Rechnungszustand.GESTELLT, List.of(abrechnung(KONZEPTION, "80.00")))));

    // When / Then
    assertThat(stand.positionen().get(1))
        .satisfies(
            positionsstand -> assertThat(positionsstand.abgerechnet()).isEqualByComparingTo("0"),
            positionsstand -> assertThat(positionsstand.offen()).isEqualByComparingTo("1"));
  }

  @Test
  void fuer_withAFractionOfAPauschale_thenItCountsExactly() {
    // Given — 0,5 von 1 ist die halbe Pauschale (Frage 1 aus #160).
    final Abrechnungsstand stand =
        Abrechnungsstand.fuer(
            List.of(PAUSCHALE),
            List.of(
                rechnung(
                    1L,
                    Rechnungszustand.ENTWURF,
                    List.of(
                        new Rechnungsposition(
                            SCHULUNG,
                            "Schulungstag",
                            new BigDecimal("0.50"),
                            Einheit.PAUSCHAL,
                            PAUSCHALE.einzelpreis())))));

    // When / Then
    assertThat(ersterStand(stand))
        .satisfies(
            positionsstand -> assertThat(positionsstand.offen()).isEqualByComparingTo("0.5"),
            positionsstand ->
                assertThat(positionsstand.offenerBetrag()).isEqualByComparingTo("600.00"));
  }

  @Test
  void ueberschreitungMit_theMengeOfTheOwnEntwurf_thenItCountsToo() {
    // Given — 100 aus anderen Rechnungen, der eigene Entwurf traegt 80: zusammen 20 zu viel.
    final Abrechnungsstand stand =
        Abrechnungsstand.ohne(
            List.of(BERATUNG),
            List.of(
                rechnung(1L, Rechnungszustand.GESTELLT, List.of(abrechnung(KONZEPTION, "100.00"))),
                rechnung(2L, Rechnungszustand.ENTWURF, List.of(abrechnung(KONZEPTION, "80.00")))),
            2L);

    // When / Then
    assertThat(ersterStand(stand).ueberschreitungMit(new BigDecimal("80.00")))
        .isEqualByComparingTo("20");
  }

  @Test
  void etwasOffen_whenEveryPositionIsFullyAbgerechnet_thenFalse() {
    // Given — beide Positionen vollstaendig abgerechnet.
    final Abrechnungsstand stand =
        Abrechnungsstand.fuer(
            List.of(BERATUNG, PAUSCHALE),
            List.of(
                rechnung(
                    1L,
                    Rechnungszustand.GESTELLT,
                    List.of(
                        abrechnung(KONZEPTION, "160.00"),
                        new Rechnungsposition(
                            SCHULUNG,
                            "Schulungstag",
                            BigDecimal.ONE,
                            Einheit.PAUSCHAL,
                            PAUSCHALE.einzelpreis())))));

    // When / Then
    assertThat(stand.etwasOffen()).isFalse();
  }

  @Test
  void etwasOffen_whenOnePositionStillHasSomethingLeft_thenTrue() {
    // Given — die Pauschale ist unberuehrt.
    final Abrechnungsstand stand =
        Abrechnungsstand.fuer(
            List.of(BERATUNG, PAUSCHALE),
            List.of(
                rechnung(
                    1L, Rechnungszustand.GESTELLT, List.of(abrechnung(KONZEPTION, "160.00")))));

    // When / Then
    assertThat(stand.etwasOffen()).isTrue();
  }

  @Test
  void offenerBetrag_thenSumsTheOffeneMengeTimesTheEinzelpreisOfTheAngebot() {
    // Given — 80 Stunden zu 100,00 € offen und die ganze Pauschale zu 1.200,00 €.
    final Abrechnungsstand stand =
        Abrechnungsstand.fuer(
            List.of(BERATUNG, PAUSCHALE),
            List.of(
                rechnung(1L, Rechnungszustand.GESTELLT, List.of(abrechnung(KONZEPTION, "80.00")))));

    // When / Then
    assertThat(stand.offenerBetrag()).isEqualByComparingTo("9200.00");
  }

  /** Dieselbe Beratung mit frei gewaehlter angebotener Menge — fuer das Beispiel aus #193. */
  private static Angebotsposition beratungUeber(final String menge) {
    return new Angebotsposition(
        Long.valueOf(KONZEPTION),
        "Beratung",
        Abrechnungsmodus.AUFWAND,
        new BigDecimal(menge),
        Einheit.STUNDE,
        BERATUNG.einzelpreis());
  }

  @Test
  void nichtAbgerechnet_theBeispielFrom193_thenNothingIsLeft() {
    // Given — 20 Stunden angeboten, 22 erfasst, 20 abgerechnet (#206, Kriterium 5).
    final Positionsstand stand =
        new Positionsstand(beratungUeber("20.00"), new BigDecimal("20.00"));

    // When / Then
    assertThat(stand.nichtAbgerechneteStunden(new BigDecimal("22.00"))).isEqualByComparingTo("0");
    assertThat(stand.nichtAbgerechneterBetrag(new BigDecimal("22.00")))
        .isEqualByComparingTo("0.00");
  }

  @Test
  void nichtAbgerechnet_withMoreStundenThanAngeboten_thenTheAngeboteneMengeIsTheCeiling() {
    // Given — 20 angeboten, 22 erfasst, nichts abgerechnet: der Deckel ist die angebotene Menge.
    final Positionsstand stand = new Positionsstand(beratungUeber("20.00"), BigDecimal.ZERO);

    // When / Then — 20 und nicht 22 (#206, Antwort 3).
    assertThat(stand.nichtAbgerechneteStunden(new BigDecimal("22.00"))).isEqualByComparingTo("20");
    assertThat(stand.nichtAbgerechneterBetrag(new BigDecimal("22.00")))
        .isEqualByComparingTo("2000.00");
  }

  @Test
  void nichtAbgerechnet_withFewerStundenThanAbgerechnet_thenNeverBelowZero() {
    // Given — 20 angeboten, 5 erfasst, 10 schon abgerechnet.
    final Positionsstand stand =
        new Positionsstand(beratungUeber("20.00"), new BigDecimal("10.00"));

    // When / Then — keine negative Menge und kein negativer Betrag (#206, Kriterium 5).
    assertThat(stand.nichtAbgerechneteStunden(new BigDecimal("5.00"))).isEqualByComparingTo("0");
    assertThat(stand.nichtAbgerechneterBetrag(new BigDecimal("5.00"))).isEqualByComparingTo("0.00");
  }

  @Test
  void nichtAbgerechneterBetrag_thenRoundsToTheCent() {
    // Given — 2,5 Stunden zu 1.000,01 € ergeben 2.500,025 € (Kriterium 5 aus #160).
    final Angebotsposition teuer =
        new Angebotsposition(
            Long.valueOf(KONZEPTION),
            "Beratung",
            Abrechnungsmodus.AUFWAND,
            new BigDecimal("10.00"),
            Einheit.STUNDE,
            new BigDecimal("1000.01"));
    final Positionsstand stand = new Positionsstand(teuer, BigDecimal.ZERO);

    // When / Then — kaufmaennisch aufgerundet, wie jeder Betrag dieses Projekts.
    assertThat(stand.nichtAbgerechneterBetrag(new BigDecimal("2.50")))
        .isEqualByComparingTo("2500.03");
  }
}
