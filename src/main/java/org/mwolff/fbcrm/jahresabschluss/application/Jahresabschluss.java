package org.mwolff.fbcrm.jahresabschluss.application;

import java.time.Year;
import java.util.List;

/**
 * Der vollstaendige Abschluss eines Jahres (#287; Plan #288, E2): Einnahmen, Rechnungsstand und
 * Umsatzsteuer je Satz (Kriterien 4 bis 6), die Angebotsbilanz (Kriterien 7, 8 und 12), der Umsatz
 * je Kunde (Kriterium 9) und die Arbeitszeit (Kriterium 10).
 *
 * @param jahr das Kalenderjahr
 * @param laeuftNoch ob es das laufende Jahr in der Geschaeftszone ist (E19)
 * @param einnahmen netto, brutto und die Umsatzsteuer dazwischen
 * @param rechnungsstand die Zahl der gestellten Rechnungen und davon die offenen und
 *     abgeschriebenen
 * @param steuerzeilen netto und Umsatzsteuer je Steuersatz, die Saetze aufsteigend und die
 *     nachgetragenen Rechnungen zuletzt; leer, wo die Aufteilung nur eine Zeile ergaebe (E14)
 * @param angebotsbilanz Zahlen, Annahmequote und Volumen der abgegebenen Angebote des Jahres
 * @param kunden je Firma ihr Umsatz und Anteil, absteigend nach Netto und bei gleichem Betrag nach
 *     Namen (E21, E22); leer ohne Rechnung
 * @param arbeitszeit die Stunden des Jahres und der Erloes je Stunde
 */
public record Jahresabschluss(
    Year jahr,
    boolean laeuftNoch,
    Einnahmen einnahmen,
    Rechnungsstand rechnungsstand,
    List<Steuerzeile> steuerzeilen,
    Angebotsbilanz angebotsbilanz,
    List<Kundenzeile> kunden,
    Jahresarbeitszeit arbeitszeit) {

  /** Nimmt die Listen als Kopie, damit der Abschluss sich nicht von aussen aendert. */
  public Jahresabschluss {
    steuerzeilen = List.copyOf(steuerzeilen);
    kunden = List.copyOf(kunden);
  }
}
