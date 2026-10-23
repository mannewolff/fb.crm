package org.mwolff.fbcrm.angebot.application;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.common.Bearbeitungskonflikt;

/**
 * Der Kommentar wurde seit dem Oeffnen geaendert, und das Aendern oder Loeschen beruht auf dem
 * alten Stand (#411, Kriterien 1, 4 und 6; Plan #415, E8).
 *
 * <p>Der Kommentar traegt seine eigene Version, getrennt von der des Angebots (E8). Status und
 * Abbildung nach 409 legt {@link Bearbeitungskonflikt} fest.
 */
public final class AngebotskommentarGeaendert extends Bearbeitungskonflikt {

  /**
   * Der Konflikt mit allem, was die Meldung nennt.
   *
   * @param urheberKonto wer den Kommentar zuletzt geaendert hat, oder {@code null}, wenn die Zeile
   *     es nicht weiss
   * @param handelndesKonto wer gerade abgewiesen wird
   * @param zeitpunkt wann der Kommentar zuletzt geaendert wurde
   */
  public AngebotskommentarGeaendert(
      final @Nullable Long urheberKonto, final long handelndesKonto, final Instant zeitpunkt) {
    super(Art.VERALTET, urheberKonto, handelndesKonto, zeitpunkt);
  }
}
