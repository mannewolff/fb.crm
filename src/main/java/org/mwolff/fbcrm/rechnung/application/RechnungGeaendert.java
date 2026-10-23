package org.mwolff.fbcrm.rechnung.application;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.common.Bearbeitungskonflikt;

/**
 * Die Rechnung wurde seit dem Oeffnen geaendert, und der Schritt beruht auf dem alten Stand (#411,
 * Kriterien 1, 4 und 5; Plan #415, E10).
 *
 * <p>Gilt fuer das Aendern des Entwurfs, fuer das Stellen und fuer den Ausgang einer gestellten
 * Rechnung gleichermassen: Beim Stellen zaehlt allein der Stand des Entwurfs. Status und Abbildung
 * nach 409 mit der Erweiterung {@code konflikt} legt {@link Bearbeitungskonflikt} fest; hier stehen
 * nur, wer zuletzt geaendert hat, wer abgewiesen wird und wann der gueltige Stand entstand.
 */
public final class RechnungGeaendert extends Bearbeitungskonflikt {

  /**
   * Der Konflikt mit allem, was die Meldung nennt.
   *
   * @param urheberKonto wer die Rechnung zuletzt geaendert hat, oder {@code null}, wenn die Zeile
   *     es nicht weiss
   * @param handelndesKonto wer gerade abgewiesen wird
   * @param zeitpunkt wann die Rechnung zuletzt geaendert wurde
   */
  public RechnungGeaendert(
      final @Nullable Long urheberKonto, final long handelndesKonto, final Instant zeitpunkt) {
    super(Art.VERALTET, urheberKonto, handelndesKonto, zeitpunkt);
  }
}
