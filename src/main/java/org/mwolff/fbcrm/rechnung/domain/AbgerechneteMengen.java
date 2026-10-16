package org.mwolff.fbcrm.rechnung.domain;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Die abgerechneten Mengen je Angebotsposition: die eine Stelle, an der sie summiert werden (Plan
 * #303, E3).
 *
 * <p><b>Entwuerfe zaehlen wie gestellte Rechnungen</b>, ebenso bezahlte und abgeschriebene. Sonst
 * zeigte ein zweiter Entwurf zum selben Angebot dieselben Mengen noch einmal als offen, und es
 * wuerde doppelt abgerechnet (#160, Kriterium 6, Frage 3). Fuer die Maske eines Entwurfs bleibt
 * genau dieser Entwurf aussen vor ({@link #ohne(List, long)}): Seine eigene Menge ist keine fremde
 * Abrechnung, sondern das, was gerade eingetragen wird.
 *
 * <p>Dieselbe Summierung speist den {@link Abrechnungsstand} — das „Offen" der Angebotsansicht —
 * und den Forecast der Startseite (#302, Kriterium 3). Zwei Abschriften liefen beim ersten
 * Nachziehen auseinander, darum steht sie nur hier.
 *
 * @param jePosition je Angebotsposition mit mindestens einer Rechnungszeile die Summe ihrer Mengen;
 *     eine Position ohne Rechnungszeile steht nicht in der Karte
 */
public record AbgerechneteMengen(Map<Long, BigDecimal> jePosition) {

  /** Nimmt die Karte als Kopie: Der Aufrufer darf seine Karte danach weiterverwenden. */
  public AbgerechneteMengen {
    jePosition = Map.copyOf(jePosition);
  }

  /**
   * Die Mengen, in denen jede uebergebene Rechnung zaehlt.
   *
   * @param rechnungen die Rechnungen, Entwuerfe wie gestellte
   */
  public static AbgerechneteMengen aus(final List<Rechnung> rechnungen) {
    return summiere(rechnungen, null);
  }

  /**
   * Die Mengen ohne das, was die genannte Rechnung selbst abrechnet.
   *
   * @param rechnungen die Rechnungen, Entwuerfe wie gestellte
   * @param ausgenommen Kennung der Rechnung, die nicht mitzaehlt
   */
  public static AbgerechneteMengen ohne(final List<Rechnung> rechnungen, final long ausgenommen) {
    return summiere(rechnungen, ausgenommen);
  }

  private static AbgerechneteMengen summiere(
      final List<Rechnung> rechnungen, final @Nullable Long ausgenommen) {
    final Map<Long, BigDecimal> abgerechnet = new HashMap<>();
    for (final Rechnung rechnung : rechnungen) {
      if (ausgenommen != null && ausgenommen == rechnung.requireId()) {
        continue;
      }
      for (final Rechnungsposition position : rechnung.positionen()) {
        abgerechnet.merge(position.angebotPositionId(), position.menge(), BigDecimal::add);
      }
    }
    return new AbgerechneteMengen(abgerechnet);
  }
}
