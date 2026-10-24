package org.mwolff.fbcrm.firma.application;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.common.Bearbeitungskonflikt;
import org.mwolff.fbcrm.firma.domain.Firma;

/**
 * Die Firma wurde seit dem Oeffnen geaendert, und der Schritt beruht auf dem alten Stand (#411,
 * Kriterium 4; Plan #415, E18).
 *
 * <p>Gilt fuer das Aendern der Angaben, das Stilllegen und das Wiederaktivieren gleichermassen —
 * beide Zustandswechsel sind Aenderungen wie jede andere. Einen Loeschweg hat die Firma nicht,
 * darum gibt es hier nur die Art „veraltet". Status und Abbildung nach 409 mit der Erweiterung
 * {@code konflikt} legt {@link Bearbeitungskonflikt} fest; hier stehen nur, wer zuletzt geaendert
 * hat, wer abgewiesen wird und wann der gueltige Stand entstand.
 */
public final class FirmaGeaendert extends Bearbeitungskonflikt {

  /**
   * Der Konflikt mit allem, was die Meldung nennt.
   *
   * @param urheberKonto wer die Firma zuletzt geaendert hat, oder {@code null}, wenn die Zeile es
   *     nicht weiss
   * @param handelndesKonto wer gerade abgewiesen wird
   * @param zeitpunkt wann die Firma zuletzt geaendert wurde
   */
  public FirmaGeaendert(
      final @Nullable Long urheberKonto, final long handelndesKonto, final Instant zeitpunkt) {
    super(Art.VERALTET, urheberKonto, handelndesKonto, zeitpunkt);
  }

  /**
   * Weist einen Schritt ab, dessen vorgelegter Stand nicht der der Firma ist.
   *
   * @param firma die Firma, wie sie gerade im Bestand steht
   * @param version der vorgelegte Stand aus {@code If-Match}
   * @param konto Kennung des handelnden Kontos
   * @throws FirmaGeaendert wenn die beiden Staende voneinander abweichen
   */
  static void pruefe(final Firma firma, final long version, final long konto) {
    Bearbeitungskonflikt.pruefe(
        version,
        firma.version(),
        () -> new FirmaGeaendert(firma.geaendertVon(), konto, firma.updatedAt()));
  }
}
