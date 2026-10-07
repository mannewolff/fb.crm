package org.mwolff.fbcrm.jahresabschluss.application;

import java.time.Year;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.Angebotsstatus;

/**
 * Wie der Jahresabschluss auf ein Angebot blickt: welche Angebote abgegeben und welche angenommen
 * sind, und zu welchem Jahr ein Angebot gehoert (#287, Kriterien 7 und 12; Plan #288, E7, E8).
 *
 * <p><b>Ausdrueckliche Mengen und nicht die Ordnungszahl von {@link Angebotsstatus}</b> (E7): Der
 * Compiler verlangt bei einem neuen Status eine Entscheidung, statt ihn stillschweigend einzureihen
 * — dieselbe Ueberlegung wie bei „in Arbeit" in {@code StartseiteUseCase}. Die Begriffe stammen aus
 * #287 und nicht aus dem Modul {@code angebot}; darum stehen sie hier.
 *
 * <p><b>Interne Angebote fallen ueber die Mengen heraus</b> (E8, Kriterium 12): {@code LAEUFT} und
 * {@code ABGESCHLOSSEN} stehen in keiner Menge. Ein zusaetzliches {@code !intern} waere eine
 * Bedingung, die kein Test kippen kann — und damit ein Loch in der Zweig- und Mutationsabdeckung
 * (CLAUDE-java.md §5).
 *
 * <p>Paket-privat und ohne eigene Testklasse — die Zerlegung ist keine eigene Zusage, geprueft wird
 * sie ueber {@code JahresabschlussUseCaseTest}.
 */
final class Angebotsblick {

  private Angebotsblick() {}

  /**
   * Ob ein Angebot abgegeben ist: alles ab dem Status „abgegeben"; die nur „angelegten" zaehlen
   * nicht (Kriterium 7).
   */
  static boolean abgegeben(final Angebotsstatus status) {
    return switch (status) {
      case ABGEGEBEN, BESTELLT, ERLEDIGT, ABGERECHNET -> true;
      case ANGELEGT, LAEUFT, ABGESCHLOSSEN -> false;
    };
  }

  /**
   * Ob ein Angebot angenommen ist: heute „bestellt", „erledigt" oder „abgerechnet" (Kriterium 7).
   */
  static boolean angenommen(final Angebotsstatus status) {
    return switch (status) {
      case BESTELLT, ERLEDIGT, ABGERECHNET -> true;
      case ANGELEGT, ABGEGEBEN, LAEUFT, ABGESCHLOSSEN -> false;
    };
  }

  /** Das Jahr eines Angebots: das Jahr seines Angebotsdatums (Kriterium 7). */
  static Year jahr(final Angebot angebot) {
    return Year.from(angebot.angebotDatum());
  }
}
