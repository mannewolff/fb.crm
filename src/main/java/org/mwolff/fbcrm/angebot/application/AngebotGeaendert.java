package org.mwolff.fbcrm.angebot.application;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.common.Bearbeitungskonflikt;

/**
 * Das Angebot wurde seit dem Oeffnen geaendert, und die Aenderung beruht auf dem alten Stand (#411,
 * Kriterium 1; Plan #415, E9).
 *
 * <p>Das Angebot zaehlt als Ganzes: Kopf und Positionen tragen eine Version. Status und Abbildung
 * nach 409 mit der Erweiterung {@code konflikt} legt {@link Bearbeitungskonflikt} fest; hier stehen
 * nur, wer zuletzt geaendert hat, wer abgewiesen wird und wann der gueltige Stand entstand.
 */
public final class AngebotGeaendert extends Bearbeitungskonflikt {

  /**
   * Der Konflikt mit allem, was die Meldung nennt.
   *
   * @param urheberKonto wer das Angebot zuletzt geaendert hat, oder {@code null}, wenn die Zeile es
   *     nicht weiss
   * @param handelndesKonto wer gerade abgewiesen wird
   * @param zeitpunkt wann das Angebot zuletzt geaendert wurde
   */
  public AngebotGeaendert(
      final @Nullable Long urheberKonto, final long handelndesKonto, final Instant zeitpunkt) {
    super(Art.VERALTET, urheberKonto, handelndesKonto, zeitpunkt);
  }
}
