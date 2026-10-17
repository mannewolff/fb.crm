package org.mwolff.fbcrm.rechnung.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.mwolff.fbcrm.angebot.domain.Angebot;
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
 * <p><b>Entwuerfe zaehlen wie gestellte Rechnungen</b> — warum, steht an der einen Stelle, die die
 * Mengen summiert: {@link AbgerechneteMengen} (Plan #303, E3). Fuer die Maske eines Entwurfs bleibt
 * genau dieser Entwurf aussen vor ({@link #ohne(List, List, long)}).
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
    return berechne(angebotspositionen, AbgerechneteMengen.aus(rechnungen));
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
    return berechne(angebotspositionen, AbgerechneteMengen.ohne(rechnungen, ausgenommen));
  }

  /** Ob an mindestens einer Position des Angebots noch etwas offen ist (Kriterium 2). */
  public boolean etwasOffen() {
    return positionen.stream().anyMatch(Positionsstand::offenesVorhanden);
  }

  /** Der offene Betrag des Angebots: die Summe der offenen Betraege seiner Positionen. */
  public BigDecimal offenerBetrag() {
    return Geldrechnung.summe(positionen.stream().map(Positionsstand::offenerBetrag));
  }

  /**
   * Ob aus dem Angebot noch eine neue Rechnung entstehen kann (#330, Kriterium 7; Plan #332, E8).
   *
   * <p>Ohne Storno dasselbe wie {@link #etwasOffen()}; am stornierten Angebot zaehlen die nicht
   * abgerechneten Stunden ({@link Positionsstand#abrechenbareMenge(Angebot, BigDecimal)}).
   *
   * @param angebot das Angebot, dessen Positionen dieser Stand traegt
   * @param angefallen die erfassten Stunden je Positionskennung; eine fehlende Position zaehlt 0
   */
  public boolean etwasAbrechenbar(final Angebot angebot, final Map<Long, BigDecimal> angefallen) {
    return positionen.stream()
        .anyMatch(stand -> abrechenbar(stand, angebot, angefallen).signum() > 0);
  }

  /**
   * Der Betrag, der noch in eine neue Rechnung darf: je Position die abrechenbare Menge mal
   * Einzelpreis des Angebots, auf den Cent gerundet und danach summiert.
   *
   * @param angebot das Angebot, dessen Positionen dieser Stand traegt
   * @param angefallen die erfassten Stunden je Positionskennung; eine fehlende Position zaehlt 0
   */
  public BigDecimal abrechenbarerBetrag(
      final Angebot angebot, final Map<Long, BigDecimal> angefallen) {
    return Geldrechnung.summe(
        positionen.stream()
            .map(
                stand ->
                    Geldrechnung.betrag(
                        abrechenbar(stand, angebot, angefallen), stand.position().einzelpreis())));
  }

  private static BigDecimal abrechenbar(
      final Positionsstand stand, final Angebot angebot, final Map<Long, BigDecimal> angefallen) {
    return stand.abrechenbareMenge(
        angebot, angefallen.getOrDefault(stand.position().requireId(), BigDecimal.ZERO));
  }

  private static Abrechnungsstand berechne(
      final List<Angebotsposition> angebotspositionen, final AbgerechneteMengen abgerechnet) {
    return new Abrechnungsstand(
        angebotspositionen.stream()
            .map(
                position ->
                    new Positionsstand(
                        position,
                        abgerechnet
                            .jePosition()
                            .getOrDefault(position.requireId(), BigDecimal.ZERO)))
            .toList());
  }
}
