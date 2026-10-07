package org.mwolff.fbcrm.startseite.application;

import java.math.BigDecimal;
import java.util.List;
import org.mwolff.fbcrm.angebot.application.AngebotMitFirma;

/**
 * Was die Startseite zeigt: die drei Kennzahlen und der Zeitraum, fuer den sie gelten (#206; #273).
 *
 * <p>Ein Stand aus einer Transaktion und nicht drei Abrufe (Plan #208, E7): Kriterium 3 ist eine
 * Aussage ueber Werte, und drei Antworten koennten sich widersprechen.
 *
 * <p><b>Der Zeitraum steht in der Antwort, auch wenn er gefragt war.</b> Wer keinen nennt oder
 * einen, der nicht zur Wahl steht, bekommt das laufende Jahr — und erfaehrt hier, welches das ist
 * (Plan #208, E8, E18; Issue #283). Dazu stehen die waehlbaren Jahre und Monate selbst darin: Die
 * Wahl der Ansicht rendert genau diese Listen, und der gezeigte Zeitraum ist immer einer der
 * waehlbaren (#273, Kriterien 1 und 2).
 *
 * <p>Die Zahl der Angebote in Arbeit traegt der Stand nicht als eigenes Feld — die Liste ist die
 * Wahrheit, und die Ansicht zaehlt sie. Bei {@link Abgerechnet} ist die Anzahl dagegen ein Feld:
 * Dort stehen die Rechnungen selbst nicht in der Antwort (Plan #208, E20).
 *
 * <p><b>Die internen Stunden stehen neben den Betraegen und nicht darin</b> (#207, Kriterium 9):
 * Interne Arbeit geht an keinen Kunden, traegt keinen Preis und wird in Stunden gezaehlt. Ein
 * Betrag daraus braeuchte einen internen Stundensatz, und den gibt es nicht — er ist ausdruecklich
 * Nicht-Ziel von #207.
 *
 * @param zeitraum der Zeitraum, fuer den „Abgerechnet" und die zweite Zeile von „Noch nicht
 *     abgerechnet" gelten; immer einer der waehlbaren
 * @param waehlbar die waehlbaren Jahre und Monate
 * @param inArbeit die Angebote im Status „bestellt" oder „erledigt", neueste zuerst
 * @param nichtAbgerechnet was aus erfasster Arbeitszeit noch abzurechnen ist
 * @param abgerechnet was im Zeitraum gestellt wurde, bei einem Jahr samt seinen Monaten
 * @param interneStundenImZeitraum die im gewaehlten Zeitraum auf interne Angebote gebuchten Stunden
 */
public record Startseitenstand(
    Zeitraum zeitraum,
    WaehlbareZeitraeume waehlbar,
    List<AngebotMitFirma> inArbeit,
    NichtAbgerechnet nichtAbgerechnet,
    Abgerechnet abgerechnet,
    BigDecimal interneStundenImZeitraum) {

  /** Nimmt die Liste als Kopie: Der Aufrufer darf seine Liste danach weiterverwenden. */
  public Startseitenstand {
    inArbeit = List.copyOf(inArbeit);
  }
}
