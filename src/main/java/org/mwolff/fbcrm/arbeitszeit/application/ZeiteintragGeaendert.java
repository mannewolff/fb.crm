package org.mwolff.fbcrm.arbeitszeit.application;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.common.Bearbeitungskonflikt;

/**
 * Der Zeiteintrag wurde seit dem Oeffnen geaendert, und das Aendern beruht auf dem alten Stand
 * (#411, Kriterien 1 und 4; Plan #415, E1, E14).
 *
 * <p>Gilt auch, wenn ein C-Level einen fremden Eintrag korrigiert (#410): Der Stand gehoert dem
 * Eintrag, nicht der Person. Status und Abbildung nach 409 legt {@link Bearbeitungskonflikt} fest.
 */
public final class ZeiteintragGeaendert extends Bearbeitungskonflikt {

  /**
   * Der Konflikt mit allem, was die Meldung nennt.
   *
   * @param urheberKonto wer den Eintrag zuletzt geaendert hat, oder {@code null}, wenn die Zeile es
   *     nicht weiss
   * @param handelndesKonto wer gerade abgewiesen wird
   * @param zeitpunkt wann der Eintrag zuletzt geaendert wurde
   */
  public ZeiteintragGeaendert(
      final @Nullable Long urheberKonto, final long handelndesKonto, final Instant zeitpunkt) {
    super(Art.VERALTET, urheberKonto, handelndesKonto, zeitpunkt);
  }
}
