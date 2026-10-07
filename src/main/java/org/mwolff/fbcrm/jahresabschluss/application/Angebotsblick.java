package org.mwolff.fbcrm.jahresabschluss.application;

import java.math.BigDecimal;
import java.time.Year;
import java.util.List;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.Angebotsstatus;
import org.mwolff.fbcrm.common.Geldrechnung;

/**
 * Wie der Jahresabschluss auf ein Angebot blickt: welche Angebote abgegeben, welche angenommen und
 * welche heute noch offen sind, zu welchem Jahr ein Angebot gehoert und was die abgegebenen eines
 * Jahres ergeben (#287, Kriterien 7, 8 und 12; Plan #288, E7, E8).
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

  /** Ob ein Angebot heute noch offen ist: allein der Status „abgegeben" (Kriterium 7). */
  static boolean offen(final Angebotsstatus status) {
    return switch (status) {
      case ABGEGEBEN -> true;
      case ANGELEGT, BESTELLT, ERLEDIGT, ABGERECHNET, LAEUFT, ABGESCHLOSSEN -> false;
    };
  }

  /**
   * Die Bilanz der abgegebenen Angebote eines Jahres: Zahlen, Annahmequote und Volumen (Kriterien 7
   * und 8). Das Volumen ist die Summe der Angebotssummen, Cent fuer Cent nach {@link Geldrechnung}.
   *
   * @param abgegeben die abgegebenen Angebote eines Jahres
   * @return ihre Bilanz; die Quote {@code null}, wenn keines abgegeben ist
   */
  static Angebotsbilanz bilanz(final List<Angebot> abgegeben) {
    final List<Angebot> angenommen =
        abgegeben.stream().filter(angebot -> angenommen(angebot.status())).toList();
    final List<Angebot> offen =
        abgegeben.stream().filter(angebot -> offen(angebot.status())).toList();
    return new Angebotsbilanz(
        abgegeben.size(),
        angenommen.size(),
        offen.size(),
        Quote.prozent(BigDecimal.valueOf(angenommen.size()), BigDecimal.valueOf(abgegeben.size())),
        volumen(abgegeben),
        volumen(angenommen));
  }

  private static BigDecimal volumen(final List<Angebot> angebote) {
    return Geldrechnung.summe(angebote.stream().map(Angebot::summe));
  }

  /** Das Jahr eines Angebots: das Jahr seines Angebotsdatums (Kriterium 7). */
  static Year jahr(final Angebot angebot) {
    return Year.from(angebot.angebotDatum());
  }
}
