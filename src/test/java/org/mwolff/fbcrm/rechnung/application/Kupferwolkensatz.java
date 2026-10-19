package org.mwolff.fbcrm.rechnung.application;

import java.util.List;
import org.mwolff.fbcrm.rechnung.domain.Rechnung;

/**
 * Der Satz der Kupferwolke zu einer gestellten Rechnung, fuer Tests ausserhalb dieses Pakets (Plan
 * #363, E18).
 *
 * <p>{@link Rechnungsbeleg} bleibt paketprivat: Kein Anwendungsfall eines anderen Pakets soll die
 * Druckdaten selbst bilden. Ein Integrationstest, der den abgelegten Beleg mit dem Druck ohne
 * Briefpapier vergleichen muss, holt sich den Satz hier — so wie {@code EchterDrucker} den Drucker.
 */
public final class Kupferwolkensatz {

  private Kupferwolkensatz() {}

  /**
   * Die Elemente, die {@link Rechnungslayout} zu dieser Rechnung setzt.
   *
   * @param gestellt eine gestellte Rechnung
   * @return die Elementfolge der Kupferwolke
   */
  public static List<Druckelement> von(final Rechnung gestellt) {
    return Rechnungslayout.setze(Rechnungsbeleg.druckdaten(gestellt));
  }
}
