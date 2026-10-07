package org.mwolff.fbcrm.jahresabschluss.application;

import java.time.Year;
import java.util.List;

/**
 * Der vollstaendige Abschluss eines Jahres (#287; Plan #288, E2) — hier seine Rechnungsseite:
 * Einnahmen, Rechnungsstand und Umsatzsteuer je Satz (Kriterien 4 bis 6).
 *
 * @param jahr das Kalenderjahr
 * @param laeuftNoch ob es das laufende Jahr in der Geschaeftszone ist (E19)
 * @param einnahmen netto, brutto und die Umsatzsteuer dazwischen
 * @param rechnungsstand die Zahl der gestellten Rechnungen und davon die offenen und
 *     abgeschriebenen
 * @param steuerzeilen netto und Umsatzsteuer je Steuersatz, die Saetze aufsteigend und die
 *     nachgetragenen Rechnungen zuletzt; leer, wo die Aufteilung nur eine Zeile ergaebe (E14)
 */
public record Jahresabschluss(
    Year jahr,
    boolean laeuftNoch,
    Einnahmen einnahmen,
    Rechnungsstand rechnungsstand,
    List<Steuerzeile> steuerzeilen) {

  /** Nimmt die Steuerzeilen als Kopie, damit der Abschluss sich nicht von aussen aendert. */
  public Jahresabschluss {
    steuerzeilen = List.copyOf(steuerzeilen);
  }
}
