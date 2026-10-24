package org.mwolff.fbcrm.rechnung.application;

import java.time.Instant;
import org.mwolff.fbcrm.common.Bearbeitungskonflikt;
import org.mwolff.fbcrm.rechnung.domain.NachgetrageneRechnung;

/**
 * Der eine Vergleich vor jedem Schritt an einer nachgetragenen Rechnung: Gibt es sie noch, und ist
 * der vorgelegte Stand der aktuelle (#411, Kriterien 1, 4 und 6; Plan #415, E14, E16, E17)?
 *
 * <p>Aendern, Loeschen, Zustand setzen und das Ablegen oder Entfernen des Originals fragen ihn als
 * Erstes, vor jeder fachlichen Regel und bevor etwas im Speicher landet. <b>Das Kennzeichen geht
 * dem Stand vor</b>: Wer eine geloeschte Rechnung bearbeitet, soll von der Loeschung erfahren
 * ({@link NachtragGeloescht}) und nicht von einem Stand, den es nicht mehr gibt ({@link
 * NachtragGeaendert}).
 *
 * <p>Paket-privat und ohne eigene Testklasse: Er ist keine eigene Zusage, geprueft wird er ueber
 * die Tests der Anwendungsfaelle.
 */
final class Nachtragsvergleich {

  private Nachtragsvergleich() {}

  /**
   * Weist den Schritt ab, wenn die Rechnung geloescht ist oder einen anderen Stand traegt.
   *
   * @param rechnung die Rechnung, wie sie der Bestand liefert
   * @param version der vorgelegte Stand aus {@code If-Match}
   * @param konto Kennung des handelnden Kontos
   * @throws NachtragGeloescht wenn die Rechnung inzwischen geloescht ist
   * @throws NachtragGeaendert wenn die Rechnung inzwischen einen anderen Stand hat
   */
  static void pruefe(final NachgetrageneRechnung rechnung, final long version, final long konto) {
    final Instant geloeschtAm = rechnung.geloeschtAm();
    if (geloeschtAm != null) {
      throw new NachtragGeloescht(rechnung.geloeschtVon(), konto, geloeschtAm);
    }
    Bearbeitungskonflikt.pruefe(
        version,
        rechnung.version(),
        () -> new NachtragGeaendert(rechnung.geaendertVon(), konto, rechnung.updatedAt()));
  }
}
