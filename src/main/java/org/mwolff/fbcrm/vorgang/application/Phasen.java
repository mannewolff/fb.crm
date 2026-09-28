package org.mwolff.fbcrm.vorgang.application;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.mwolff.fbcrm.vorgang.domain.Belegstand;
import org.mwolff.fbcrm.vorgang.domain.Phase;

/**
 * Sammelt ein, welche Phasen an einer Menge von Vorgaengen nachgewiesen sind (Plan E2).
 *
 * <p>Der Port {@link Belegstand} ist mehrfach besetzbar: Jede Belegart bringt eine Umsetzung mit,
 * und jede sagt ueber {@link Belegstand#phase()}, wofuer sie steht. Diese Klasse fragt sie alle und
 * legt die Antworten je Vorgang zusammen — <b>eine</b> Abfrage je Belegart fuer die ganze Menge und
 * nicht eine je Zeile, sonst entstuende die Abfrage-Lawine gleich in doppelter Ausfuehrung.
 *
 * <p><b>Hier faellt keine Entscheidung darueber, welche Phase gilt.</b> Die Karte traegt alles, was
 * nachgewiesen ist; welche davon gewinnt, entscheidet {@code Vorgang.phase} als Maximum. Die
 * Ordnung der Kette steht an genau einer Stelle, und das ist die Reihenfolge der Werte in {@link
 * Phase}.
 *
 * <p>Kein eigenes Bean: Die Anwendungsfaelle bauen sich ihre Instanz im Konstruktor. Die Klasse
 * haelt keinen Zustand ueber einen Aufruf hinaus.
 */
final class Phasen {

  private final List<Belegstand> belege;

  Phasen(final List<Belegstand> belege) {
    this.belege = List.copyOf(belege);
  }

  /**
   * Die nachgewiesenen Phasen je Vorgang.
   *
   * <p>Die Karte traegt nur Vorgaenge, an denen mindestens ein Beleg haengt — wer fehlt, ist in der
   * Anbahnung, und das ist der Rueckfall von {@code Vorgang.phase} auf die leere Menge.
   */
  Map<Long, Set<Phase>> zu(final List<Long> vorgangIds) {
    if (vorgangIds.isEmpty()) {
      return Map.of();
    }
    final Map<Long, Set<Phase>> karte = new HashMap<>();
    for (final Belegstand beleg : belege) {
      vermerke(karte, beleg.phase(), beleg.mitBeleg(vorgangIds));
    }
    return karte;
  }

  private static void vermerke(
      final Map<Long, Set<Phase>> karte, final Phase phase, final Collection<Long> vorgangIds) {
    for (final Long vorgangId : vorgangIds) {
      karte.computeIfAbsent(vorgangId, unbekannt -> EnumSet.noneOf(Phase.class)).add(phase);
    }
  }
}
