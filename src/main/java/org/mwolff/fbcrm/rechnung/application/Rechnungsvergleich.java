package org.mwolff.fbcrm.rechnung.application;

import java.time.Instant;
import org.mwolff.fbcrm.common.Bearbeitungskonflikt;
import org.mwolff.fbcrm.rechnung.domain.Rechnung;

/**
 * Der eine Vergleich vor jedem Schritt an einer Rechnung: Gibt es sie noch, und ist der vorgelegte
 * Stand der aktuelle (#411, Kriterien 1, 4 bis 6; Plan #415, E14, E16, E17)?
 *
 * <p>Aendern, Loeschen, Zustand setzen und Stellen fragen ihn als Erstes, vor jeder fachlichen
 * Regel. <b>Das Kennzeichen geht dem Stand vor</b>: Wer einen geloeschten Entwurf bearbeitet, soll
 * von der Loeschung erfahren ({@link RechnungGeloescht}) und nicht von einem Stand, den es nicht
 * mehr gibt ({@link RechnungGeaendert}).
 *
 * <p>Paket-privat und ohne eigene Testklasse: Er ist keine eigene Zusage, geprueft wird er ueber
 * die Tests der vier Anwendungsfaelle.
 */
final class Rechnungsvergleich {

  private Rechnungsvergleich() {}

  /**
   * Weist den Schritt ab, wenn die Rechnung geloescht ist oder einen anderen Stand traegt.
   *
   * @param rechnung die Rechnung, wie sie der Bestand liefert
   * @param version der vorgelegte Stand aus {@code If-Match}
   * @param konto Kennung des handelnden Kontos
   * @throws RechnungGeloescht wenn die Rechnung inzwischen geloescht ist
   * @throws RechnungGeaendert wenn die Rechnung inzwischen einen anderen Stand hat
   */
  static void pruefe(final Rechnung rechnung, final long version, final long konto) {
    final Instant geloeschtAm = rechnung.geloeschtAm();
    if (geloeschtAm != null) {
      throw new RechnungGeloescht(rechnung.geloeschtVon(), konto, geloeschtAm);
    }
    Bearbeitungskonflikt.pruefe(
        version,
        rechnung.version(),
        () -> new RechnungGeaendert(rechnung.geaendertVon(), konto, rechnung.updatedAt()));
  }
}
