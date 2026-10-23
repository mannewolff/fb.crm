package org.mwolff.fbcrm.rechnung.application;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.common.Bearbeitungskonflikt;
import org.mwolff.fbcrm.rechnung.domain.NachgetrageneRechnung;

/**
 * Die nachgetragene Rechnung wurde seit dem Oeffnen geaendert, und der Schritt beruht auf dem alten
 * Stand (#411, Kriterium 4; Plan #415).
 *
 * <p>Gilt fuer das Aendern der Eckdaten, fuer den Zustandswechsel und fuer das Ersetzen oder
 * Entfernen des Originals gleichermassen — in jedem Zustand, denn einen Entwurf hat sie nicht.
 * Status und Abbildung nach 409 mit der Erweiterung {@code konflikt} legt {@link
 * Bearbeitungskonflikt} fest; hier stehen nur, wer zuletzt geaendert hat, wer abgewiesen wird und
 * wann der gueltige Stand entstand.
 */
public final class NachtragGeaendert extends Bearbeitungskonflikt {

  /**
   * Der Konflikt mit allem, was die Meldung nennt.
   *
   * @param urheberKonto wer die Rechnung zuletzt geaendert hat, oder {@code null}, wenn die Zeile
   *     es nicht weiss
   * @param handelndesKonto wer gerade abgewiesen wird
   * @param zeitpunkt wann die Rechnung zuletzt geaendert wurde
   */
  public NachtragGeaendert(
      final @Nullable Long urheberKonto, final long handelndesKonto, final Instant zeitpunkt) {
    super(Art.VERALTET, urheberKonto, handelndesKonto, zeitpunkt);
  }

  /**
   * Weist einen Schritt ab, dessen vorgelegter Stand nicht der der Rechnung ist.
   *
   * @param rechnung die Rechnung, wie sie gerade im Bestand steht
   * @param version der vorgelegte Stand aus {@code If-Match}
   * @param konto Kennung des handelnden Kontos
   * @throws NachtragGeaendert wenn die beiden Staende voneinander abweichen
   */
  static void pruefe(final NachgetrageneRechnung rechnung, final long version, final long konto) {
    Bearbeitungskonflikt.pruefe(
        version,
        rechnung.version(),
        () -> new NachtragGeaendert(rechnung.geaendertVon(), konto, rechnung.updatedAt()));
  }
}
