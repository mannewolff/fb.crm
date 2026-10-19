package org.mwolff.fbcrm.rechnung.domain;

/**
 * Die sechs einstellbaren Raender des Briefbogens in ganzen Zehntel-Millimetern (Plan #363, E8).
 *
 * <p>Ganzzahlig und nicht als Dezimal- oder Gleitkommazahl: 98,5 mm aus Kriterium 15 der fachlichen
 * Quelle #361 braucht genau eine Nachkommastelle, und der Satz rechnet in ganzen Punkten —
 * umgerechnet wird erst an seiner Grenze, mit {@link #inPunkten(int)}.
 *
 * <p>Kein Rand ist negativ (Kriterium 13, erster Satz). Ob die Raender zusammen noch Platz fuer den
 * Inhalt lassen, entscheidet nicht dieser Record, sondern die Randpruefung am Satz.
 *
 * @param links linker Rand
 * @param rechts rechter Rand
 * @param inhaltOben Beginn des Inhalts auf Seite 1, vom oberen Blattrand
 * @param unten unterer Rand auf Seite 1
 * @param folgeOben oberer Rand auf Folgeseiten
 * @param folgeUnten unterer Rand auf Folgeseiten
 */
public record Randmasse(
    int links, int rechts, int inhaltOben, int unten, int folgeOben, int folgeUnten) {

  /** 72 Punkte je Zoll. */
  private static final int PUNKTE_JE_ZOLL = 72;

  /** 254 Zehntel-Millimeter je Zoll. */
  private static final int ZEHNTEL_MM_JE_ZOLL = 254;

  /** Die Haelfte des Nenners, damit die ganzzahlige Division kaufmaennisch rundet. */
  private static final int HALBER_ZOLL = 127;

  public Randmasse {
    pruefe("links", links);
    pruefe("rechts", rechts);
    pruefe("inhaltOben", inhaltOben);
    pruefe("unten", unten);
    pruefe("folgeOben", folgeOben);
    pruefe("folgeUnten", folgeUnten);
  }

  /**
   * Ein Mass in ganzen Punkten, kaufmaennisch gerundet (E8).
   *
   * @param zehntelMm das Mass in Zehntel-Millimetern, nicht negativ
   * @return das Mass in Punkten
   */
  public static int inPunkten(final int zehntelMm) {
    return (zehntelMm * PUNKTE_JE_ZOLL + HALBER_ZOLL) / ZEHNTEL_MM_JE_ZOLL;
  }

  private static void pruefe(final String rand, final int zehntelMm) {
    if (zehntelMm < 0) {
      throw new IllegalArgumentException("Der Rand " + rand + " ist negativ: " + zehntelMm);
    }
  }
}
