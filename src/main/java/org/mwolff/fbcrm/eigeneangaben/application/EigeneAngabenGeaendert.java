package org.mwolff.fbcrm.eigeneangaben.application;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.common.Bearbeitungskonflikt;
import org.mwolff.fbcrm.eigeneangaben.domain.EigeneAngabenStand;

/**
 * Die eigenen Angaben wurden seit dem Oeffnen geaendert, und das Speichern beruht auf dem alten
 * Stand (#411, Kriterium 4; Plan #415, E1, E14).
 *
 * <p>Status und Abbildung nach 409 mit der Erweiterung {@code konflikt} legt {@link
 * Bearbeitungskonflikt} fest; hier stehen nur, wer zuletzt geaendert hat, wer abgewiesen wird und
 * wann der gueltige Stand entstand.
 *
 * <p>Beim Stellen einer Rechnung weisen die eigenen Angaben nichts ab (E10): Sie gelten, wie sie im
 * Augenblick des Stellens sind.
 */
public final class EigeneAngabenGeaendert extends Bearbeitungskonflikt {

  /**
   * Der Konflikt mit allem, was die Meldung nennt.
   *
   * @param urheberKonto wer zuletzt geaendert hat, oder {@code null}, wenn die Zeile es nicht weiss
   * @param handelndesKonto wer gerade abgewiesen wird
   * @param zeitpunkt wann zuletzt geaendert wurde
   */
  public EigeneAngabenGeaendert(
      final @Nullable Long urheberKonto, final long handelndesKonto, final Instant zeitpunkt) {
    super(Art.VERALTET, urheberKonto, handelndesKonto, zeitpunkt);
  }

  /**
   * Weist das Speichern ab, wenn die Zeile einen anderen Stand traegt als den vorgelegten.
   *
   * @param stand der Stand der Zeile, wie ihn der Bestand liefert
   * @param version der vorgelegte Stand aus {@code If-Match}
   * @param konto Kennung des handelnden Kontos
   * @throws EigeneAngabenGeaendert wenn die Zeile inzwischen einen anderen Stand hat
   */
  static void pruefe(final EigeneAngabenStand stand, final long version, final long konto) {
    Bearbeitungskonflikt.pruefe(
        version,
        stand.version(),
        () -> new EigeneAngabenGeaendert(stand.geaendertVon(), konto, stand.geaendertAm()));
  }
}
