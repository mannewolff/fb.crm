package org.mwolff.fbcrm.firma.application;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.common.Bearbeitungskonflikt;
import org.mwolff.fbcrm.firma.domain.Ansprechpartner;

/**
 * Der Ansprechpartner wurde seit dem Oeffnen geaendert, und der Schritt beruht auf dem alten Stand
 * (#411, Kriterium 4; Plan #415, E18).
 *
 * <p>Wie {@link FirmaGeaendert}: Aendern, Stilllegen und Wiederaktivieren werden gleich geprueft,
 * einen Loeschweg gibt es nicht. Sein Stand gehoert ihm allein — eine Aenderung an der Firma laesst
 * ihn unberuehrt (E2).
 */
public final class AnsprechpartnerGeaendert extends Bearbeitungskonflikt {

  /**
   * Der Konflikt mit allem, was die Meldung nennt.
   *
   * @param urheberKonto wer den Ansprechpartner zuletzt geaendert hat, oder {@code null}, wenn die
   *     Zeile es nicht weiss
   * @param handelndesKonto wer gerade abgewiesen wird
   * @param zeitpunkt wann der Ansprechpartner zuletzt geaendert wurde
   */
  public AnsprechpartnerGeaendert(
      final @Nullable Long urheberKonto, final long handelndesKonto, final Instant zeitpunkt) {
    super(Art.VERALTET, urheberKonto, handelndesKonto, zeitpunkt);
  }

  /**
   * Weist einen Schritt ab, dessen vorgelegter Stand nicht der des Ansprechpartners ist.
   *
   * @param partner der Ansprechpartner, wie er gerade im Bestand steht
   * @param version der vorgelegte Stand aus {@code If-Match}
   * @param konto Kennung des handelnden Kontos
   * @throws AnsprechpartnerGeaendert wenn die beiden Staende voneinander abweichen
   */
  static void pruefe(final Ansprechpartner partner, final long version, final long konto) {
    Bearbeitungskonflikt.pruefe(
        version,
        partner.version(),
        () -> new AnsprechpartnerGeaendert(partner.geaendertVon(), konto, partner.updatedAt()));
  }
}
