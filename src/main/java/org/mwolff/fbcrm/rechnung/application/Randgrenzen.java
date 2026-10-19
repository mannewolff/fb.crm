package org.mwolff.fbcrm.rechnung.application;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import org.mwolff.fbcrm.common.Anschrift;
import org.mwolff.fbcrm.eigeneangaben.domain.EigeneAngaben;
import org.mwolff.fbcrm.rechnung.domain.Briefbogen;
import org.mwolff.fbcrm.rechnung.domain.Randmasse;

/**
 * Ob die Raender des Briefbogens dem Inhalt noch Platz lassen, und wie gross der geaenderte Rand
 * hoechstens sein darf (Kriterium 13; Plan #363, E9).
 *
 * <p><b>Eine Regel fuer Pruefung und Meldung.</b> {@link #passt} setzt die Probe auf eine Position
 * beschraenkt mit dem {@link Briefbogenlayout} und prueft am Satz drei Bedingungen: Seite 1 traegt
 * alles, die Tabelle steht zwischen linkem und rechtem Rand in voller Spaltenbreite, und eine
 * Folgeseite fasst eine Tabellenzeile. {@link #groesstZulaessig} sucht den Grenzwert mit genau
 * dieser Pruefung — eine zweite, geschlossen gerechnete Formel gibt es nicht, also koennen Pruefung
 * und Meldung nicht auseinanderlaufen.
 *
 * <p><b>Die Suche ist binaer und exakt.</b> Ein groesserer Rand verbessert das Passen nie, und die
 * Raender sind ganze Zehntel-Millimeter (E8); also gibt es genau einen groessten Wert, der passt.
 *
 * <p><b>Allein Raender und Schalter entscheiden</b> (E10). Die Steuer- und Bankzeile haelt ihren
 * Platz am Schalter frei, nicht am Inhalt. Und gemessen wird immer mit der Namenszeile unter dem
 * Gruss, auch wenn die eigenen Angaben keinen Namen tragen — auf einer gestellten Rechnung steht
 * er, und eine Pruefung, die ohne ihn misst, liesse Raender zu, an denen die erste Rechnung
 * umbricht.
 */
final class Randgrenzen {

  /**
   * Die eigenen Angaben, mit denen die Pruefung misst: keine. Was von ihnen in den Satz eingeht —
   * Absenderzeile, Steuer- und Bankzeile, der Ort vor dem Datum —, steht an fester Lage oder haelt
   * seinen Platz am Schalter frei; nur der Name nicht, und der wird beim Messen immer gesetzt.
   */
  private static final EigeneAngaben KEINE_ANGABEN =
      new EigeneAngaben(
          null, null, new Anschrift(null, null, null, null), null, null, null, null, null, null);

  /** Steht beim Messen fuer einen fehlenden Namen; seine Laenge bewegt nichts. */
  private static final String NAMENSZEILE = "Name";

  /**
   * Bis hierhin wird gesucht: die Hoehe des Blattes in Zehntel-Millimetern. Ein Rand dieser Groesse
   * laesst auf keiner Seite und in keiner Richtung Platz.
   */
  private static final int OBERGRENZE = 2970;

  private Randgrenzen() {}

  /** Die sechs Raender in der Reihenfolge, in der ein abweichender gesucht wird. */
  enum Rand {
    /** Der linke Rand. */
    LINKS("links"),
    /** Der rechte Rand. */
    RECHTS("rechts"),
    /** Der Beginn des Inhalts auf Seite 1. */
    INHALT_OBEN("inhaltOben"),
    /** Der untere Rand auf Seite 1. */
    UNTEN("unten"),
    /** Der obere Rand auf Folgeseiten. */
    FOLGE_OBEN("folgeOben"),
    /** Der untere Rand auf Folgeseiten. */
    FOLGE_UNTEN("folgeUnten");

    private final String feldname;

    Rand(final String feldname) {
      this.feldname = feldname;
    }

    /**
     * Das Feld, unter dem Maske und Schnittstelle diesen Rand fuehren — der Name seiner Komponente
     * in {@link Randmasse}.
     *
     * @return der Feldname
     */
    String feld() {
      return feldname;
    }

    /**
     * Der Wert dieses Randes.
     *
     * @param masse die sechs Raender
     * @return der Rand in Zehntel-Millimetern
     */
    int wert(final Randmasse masse) {
      return switch (this) {
        case LINKS -> masse.links();
        case RECHTS -> masse.rechts();
        case INHALT_OBEN -> masse.inhaltOben();
        case UNTEN -> masse.unten();
        case FOLGE_OBEN -> masse.folgeOben();
        case FOLGE_UNTEN -> masse.folgeUnten();
      };
    }

    /**
     * Dieselben Raender, nur dieser mit einem anderen Wert.
     *
     * @param masse die sechs Raender
     * @param wert der neue Wert dieses Randes in Zehntel-Millimetern
     * @return die geaenderten Raender
     */
    Randmasse mit(final Randmasse masse, final int wert) {
      return switch (this) {
        case LINKS ->
            new Randmasse(
                wert,
                masse.rechts(),
                masse.inhaltOben(),
                masse.unten(),
                masse.folgeOben(),
                masse.folgeUnten());
        case RECHTS ->
            new Randmasse(
                masse.links(),
                wert,
                masse.inhaltOben(),
                masse.unten(),
                masse.folgeOben(),
                masse.folgeUnten());
        case INHALT_OBEN ->
            new Randmasse(
                masse.links(),
                masse.rechts(),
                wert,
                masse.unten(),
                masse.folgeOben(),
                masse.folgeUnten());
        case UNTEN ->
            new Randmasse(
                masse.links(),
                masse.rechts(),
                masse.inhaltOben(),
                wert,
                masse.folgeOben(),
                masse.folgeUnten());
        case FOLGE_OBEN ->
            new Randmasse(
                masse.links(),
                masse.rechts(),
                masse.inhaltOben(),
                masse.unten(),
                wert,
                masse.folgeUnten());
        case FOLGE_UNTEN ->
            new Randmasse(
                masse.links(),
                masse.rechts(),
                masse.inhaltOben(),
                masse.unten(),
                masse.folgeOben(),
                wert);
      };
    }
  }

  /**
   * Ob die Raender mit diesen Schaltern dem Inhalt noch Platz lassen.
   *
   * @param masse die sechs Raender
   * @param absenderzeile ob die Absenderzeile eingedruckt wird
   * @param steuerBankZeile ob die Steuer- und Bankzeile eingedruckt wird
   * @return ob alle drei Bedingungen aus Kriterium 13 erfuellt sind
   */
  static boolean passt(
      final Randmasse masse, final boolean absenderzeile, final boolean steuerBankZeile) {
    return passt(Probedruckdaten.von(KEINE_ANGABEN), masse, absenderzeile, steuerBankZeile);
  }

  /**
   * Dieselbe Pruefung mit einer bestimmten Probe; gemessen wird an ihrer ersten Position.
   *
   * @param probe die Beispieldaten des Probedrucks
   * @param masse die sechs Raender
   * @param absenderzeile ob die Absenderzeile eingedruckt wird
   * @param steuerBankZeile ob die Steuer- und Bankzeile eingedruckt wird
   * @return ob alle drei Bedingungen aus Kriterium 13 erfuellt sind
   */
  static boolean passt(
      final Probedruckdaten probe,
      final Randmasse masse,
      final boolean absenderzeile,
      final boolean steuerBankZeile) {
    final Probedruckdaten messung =
        new Probedruckdaten(
                Objects.requireNonNullElse(probe.name(), NAMENSZEILE), probe.druckdaten())
            .mitEinerPosition();
    final Briefbogen bogen = new Briefbogen(null, absenderzeile, steuerBankZeile, masse);
    final List<Druckelement> satz = Briefbogenlayout.setze(messung, bogen);
    return aufSeiteEins(satz)
        && tabelleInVollerBreite(satz, masse)
        && Briefbogenlayout.folgeseiteFasst(messung, bogen);
  }

  /**
   * Der groesste Wert des genannten Randes, der bei den uebrigen Raendern noch passt.
   *
   * @param rand der geaenderte Rand
   * @param masse die sechs Raender; der Wert des genannten wird ersetzt
   * @param absenderzeile ob die Absenderzeile eingedruckt wird
   * @param steuerBankZeile ob die Steuer- und Bankzeile eingedruckt wird
   * @return der Grenzwert in Zehntel-Millimetern, oder leer, wenn auch 0 nicht passt
   */
  static OptionalInt groesstZulaessig(
      final Rand rand,
      final Randmasse masse,
      final boolean absenderzeile,
      final boolean steuerBankZeile) {
    if (!passt(rand.mit(masse, 0), absenderzeile, steuerBankZeile)) {
      return OptionalInt.empty();
    }
    // passt bei unten, passt nicht bei oben; OBERGRENZE + 1 wird nie gesetzt.
    int unten = 0;
    int oben = OBERGRENZE + 1;
    while (oben - unten > 1) {
      final int mitte = (unten + oben) >>> 1;
      if (passt(rand.mit(masse, mitte), absenderzeile, steuerBankZeile)) {
        unten = mitte;
      } else {
        oben = mitte;
      }
    }
    return OptionalInt.of(unten);
  }

  /**
   * Der erste Rand, der abweicht, in der Reihenfolge links, rechts, Beginn des Inhalts, unten,
   * Folgeseite oben, Folgeseite unten.
   *
   * @param alt die gespeicherten Raender
   * @param neu die ankommenden Raender
   * @return der erste abweichende Rand, oder leer, wenn keiner abweicht
   */
  static Optional<Rand> abweichenderRand(final Randmasse alt, final Randmasse neu) {
    return Arrays.stream(Rand.values())
        .filter(rand -> rand.wert(alt) != rand.wert(neu))
        .findFirst();
  }

  /** Ob der ganze Satz auf Seite 1 steht — Anschrift, Titel, Tabelle mit Summen und Schluss. */
  private static boolean aufSeiteEins(final List<Druckelement> satz) {
    return satz.stream().allMatch(element -> element.seite() == 1);
  }

  /** Ob keine Linie der Tabelle ueber den rechten Rand hinausreicht. */
  private static boolean tabelleInVollerBreite(
      final List<Druckelement> satz, final Randmasse masse) {
    final int rechteKante = Briefbogenlayout.BLATT_BREITE - Randmasse.inPunkten(masse.rechts());
    return satz.stream()
        .noneMatch(
            element -> element instanceof Druckelement.Linie linie && linie.bisX() > rechteKante);
  }
}
