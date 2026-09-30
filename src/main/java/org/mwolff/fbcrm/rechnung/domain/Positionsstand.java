package org.mwolff.fbcrm.rechnung.domain;

import java.math.BigDecimal;
import org.mwolff.fbcrm.angebot.domain.Angebotsposition;
import org.mwolff.fbcrm.common.Geldrechnung;

/**
 * Was an einer Angebotsposition abgerechnet und was noch offen ist (Plan #169, E6).
 *
 * <p>Die eine Zeile des {@link Abrechnungsstand}. Sie traegt nur zwei Werte — die Angebotsposition
 * und die schon abgerechnete Menge —, alles Weitere rechnet sie daraus: Eine gespeicherte offene
 * Menge waere ein zweiter Wahrheitsort, der bei jeder Aenderung am Angebot und an jeder Rechnung
 * nachgezogen werden muesste (#160, Kriterien 6 und 7).
 *
 * <p><b>Offen und Ueberschreitung sind zwei Angaben und nicht eine mit Vorzeichen.</b> Offen ist
 * nie kleiner als 0, und was ueber die angebotene Menge hinaus abgerechnet wurde, steht daneben
 * (Kriterium 7). Eine negative offene Menge belegte sonst die Vorbelegung der naechsten Rechnung
 * mit einer negativen Position.
 *
 * @param position die Angebotsposition, um die es geht
 * @param abgerechnet die Summe der Rechnungspositionen zu dieser Angebotsposition
 */
public record Positionsstand(Angebotsposition position, BigDecimal abgerechnet) {

  /** Die angebotene Menge — die Menge am Angebot. */
  public BigDecimal angeboten() {
    return position.menge();
  }

  /** Die noch offene Menge: angeboten abzueglich abgerechnet, nie kleiner als 0 (Kriterium 7). */
  public BigDecimal offen() {
    return BigDecimal.ZERO.max(angeboten().subtract(abgerechnet));
  }

  /** Was ueber die angebotene Menge hinaus abgerechnet wurde, sonst 0 (Kriterium 7). */
  public BigDecimal ueberschreitung() {
    return ueberschreitungMit(BigDecimal.ZERO);
  }

  /**
   * Die Ueberschreitung, wenn eine weitere Menge dazukaeme (Kriterium 8).
   *
   * <p>Das ist der Blick der Entwurfsmaske: Dort ist die eigene Rechnung aus {@code abgerechnet}
   * ausgenommen, und der Hinweis an der Position soll zeigen, was mit der gerade eingetragenen
   * Menge zusammen zu viel waere.
   *
   * @param zusaetzlich die Menge, die dazukaeme
   */
  public BigDecimal ueberschreitungMit(final BigDecimal zusaetzlich) {
    return BigDecimal.ZERO.max(abgerechnet.add(zusaetzlich).subtract(angeboten()));
  }

  /** Ob an dieser Position ueberhaupt noch etwas offen ist. */
  public boolean offenesVorhanden() {
    return offen().signum() > 0;
  }

  /** Der offene Betrag: offene Menge mal Einzelpreis des Angebots, auf den Cent gerundet. */
  public BigDecimal offenerBetrag() {
    return Geldrechnung.betrag(offen(), position.einzelpreis());
  }
}
