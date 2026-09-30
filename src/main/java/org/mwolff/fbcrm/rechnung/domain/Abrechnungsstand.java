package org.mwolff.fbcrm.rechnung.domain;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.angebot.domain.Angebotsposition;
import org.mwolff.fbcrm.common.Geldrechnung;

/**
 * Der Abrechnungsstand eines Angebots: je Position angeboten, abgerechnet, offen (Plan #169, E6).
 *
 * <p>Ein reiner Rechner ohne Bestand — er bekommt die Positionen eines Angebots und die Rechnungen
 * dieses Angebots und rechnet daraus. Dieselbe Rechnung speist die Entwurfsmaske, die
 * Angebotsansicht, die Liste der abrechenbaren Angebote und spaeter den Sprung auf „abgerechnet"
 * (#160, Kriterien 4, 6, 7, 26).
 *
 * <p><b>Entwuerfe zaehlen wie gestellte Rechnungen.</b> Sonst zeigte ein zweiter Entwurf zum selben
 * Angebot dieselben Mengen noch einmal als offen, und es wuerde doppelt abgerechnet (Kriterium 6,
 * Frage 3 aus #160). Fuer die Maske eines Entwurfs bleibt genau dieser Entwurf aussen vor ({@link
 * #ohne(List, List, long)}): Seine eigene Menge ist keine fremde Abrechnung, sondern das, was
 * gerade eingetragen wird.
 *
 * @param positionen je Angebotsposition ein Stand, in der Reihenfolge der uebergebenen Positionen
 */
public record Abrechnungsstand(List<Positionsstand> positionen) {

  /** Nimmt die Staende als Kopie: Der Aufrufer darf seine Liste danach weiterverwenden. */
  public Abrechnungsstand {
    positionen = List.copyOf(positionen);
  }

  /**
   * Der Stand, in dem jede Rechnung des Angebots zaehlt.
   *
   * @param angebotspositionen die Positionen des Angebots in ihrer Reihenfolge
   * @param rechnungen die Rechnungen dieses Angebots, Entwuerfe wie gestellte
   */
  public static Abrechnungsstand fuer(
      final List<Angebotsposition> angebotspositionen, final List<Rechnung> rechnungen) {
    return berechne(angebotspositionen, rechnungen, null);
  }

  /**
   * Der Stand aus der Sicht einer Rechnung: ohne das, was sie selbst abrechnet.
   *
   * @param angebotspositionen die Positionen des Angebots in ihrer Reihenfolge
   * @param rechnungen die Rechnungen dieses Angebots, Entwuerfe wie gestellte
   * @param ausgenommen Kennung der Rechnung, die nicht mitzaehlt
   */
  public static Abrechnungsstand ohne(
      final List<Angebotsposition> angebotspositionen,
      final List<Rechnung> rechnungen,
      final long ausgenommen) {
    return berechne(angebotspositionen, rechnungen, ausgenommen);
  }

  /** Ob an mindestens einer Position des Angebots noch etwas offen ist (Kriterium 2). */
  public boolean etwasOffen() {
    return positionen.stream().anyMatch(Positionsstand::offenesVorhanden);
  }

  /** Der offene Betrag des Angebots: die Summe der offenen Betraege seiner Positionen. */
  public BigDecimal offenerBetrag() {
    return Geldrechnung.summe(positionen.stream().map(Positionsstand::offenerBetrag));
  }

  private static Abrechnungsstand berechne(
      final List<Angebotsposition> angebotspositionen,
      final List<Rechnung> rechnungen,
      final @Nullable Long ausgenommen) {
    final Map<Long, BigDecimal> abgerechnet = new HashMap<>();
    for (final Rechnung rechnung : rechnungen) {
      if (ausgenommen != null && ausgenommen == rechnung.requireId()) {
        continue;
      }
      for (final Rechnungsposition position : rechnung.positionen()) {
        abgerechnet.merge(position.angebotPositionId(), position.menge(), BigDecimal::add);
      }
    }
    return new Abrechnungsstand(
        angebotspositionen.stream()
            .map(
                position ->
                    new Positionsstand(
                        position, abgerechnet.getOrDefault(position.requireId(), BigDecimal.ZERO)))
            .toList());
  }
}
