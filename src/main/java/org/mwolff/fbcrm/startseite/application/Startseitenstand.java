package org.mwolff.fbcrm.startseite.application;

import java.time.YearMonth;
import java.util.List;
import org.mwolff.fbcrm.angebot.application.AngebotMitFirma;
import org.mwolff.fbcrm.rechnung.application.Monatsabrechnung;

/**
 * Was die Startseite zeigt: die drei Kennzahlen und der Monat, fuer den sie gelten (#206).
 *
 * <p>Ein Stand aus einer Transaktion und nicht drei Abrufe (Plan #208, E7): Kriterium 3 ist eine
 * Aussage ueber Werte, und drei Antworten koennten sich widersprechen.
 *
 * <p><b>Der Monat steht in der Antwort, auch wenn er gefragt war.</b> Wer keinen nennt oder einen
 * ausserhalb der zwoelf waehlbaren, bekommt den laufenden — und erfaehrt hier, welcher das ist (E8,
 * E18). Dazu stehen die zwoelf waehlbaren selbst darin: Die Wahl der Ansicht rendert genau diese
 * Liste, und der gezeigte Monat ist immer einer von ihnen (E17).
 *
 * <p>Die Zahl der Angebote in Arbeit traegt der Stand nicht als eigenes Feld — die Liste ist die
 * Wahrheit, und die Ansicht zaehlt sie. Bei {@link Monatsabrechnung} ist die Anzahl dagegen ein
 * Feld: Dort stehen die Rechnungen selbst nicht in der Antwort (E20).
 *
 * @param monat der Monat, fuer den „Abgerechnet" und die Monatszeile gelten
 * @param monate die zwoelf waehlbaren Monate, neuester zuerst; {@code monat} ist einer von ihnen
 * @param inArbeit die Angebote im Status „bestellt" oder „erledigt", neueste zuerst
 * @param nichtAbgerechnet was aus erfasster Arbeitszeit noch abzurechnen ist
 * @param abgerechnet Netto, Brutto und Anzahl der im Monat gestellten Rechnungen
 */
public record Startseitenstand(
    YearMonth monat,
    List<YearMonth> monate,
    List<AngebotMitFirma> inArbeit,
    NichtAbgerechnet nichtAbgerechnet,
    Monatsabrechnung abgerechnet) {

  /** Nimmt die Listen als Kopie: Der Aufrufer darf seine Listen danach weiterverwenden. */
  public Startseitenstand {
    monate = List.copyOf(monate);
    inArbeit = List.copyOf(inArbeit);
  }
}
