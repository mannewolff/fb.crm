package org.mwolff.fbcrm.rechnung.web;

import java.math.BigDecimal;
import org.mwolff.fbcrm.rechnung.application.RechnungseinstellungenMitNummer;

/**
 * Die Rechnungseinstellungen mit ihren vier Werten (#159, Kriterium 5).
 *
 * <p>Das Muster geht als Text hinaus und nicht als geschachteltes Objekt: Die Maske hat dafuer ein
 * Textfeld, und das Wertobjekt ist eine Sache der Fachschicht.
 *
 * <p>Der Steuersatz geht als <b>Dezimalzahl</b> hinaus, wie {@code summe} in den Angebots-Antworten
 * — als Zeichenkette muesste das Frontend zwei Formen desselben Wertes lesen.
 *
 * <p>Der Zeitpunkt der letzten Aenderung fehlt bewusst: Die Maske zeigt ihn nicht.
 *
 * <p>Die naechste Nummer steht hier unveraendert neben den drei Einstellungen, obwohl sie seit #175
 * aus dem Nummernkreis kommt: Die Maske zeigt einen Bereich, und das JSON ist ihr Vertrag.
 *
 * @param nummerMuster Schreibweise der Rechnungsnummer
 * @param naechsteNummer die naechste laufende Nummer
 * @param steuersatz Mehrwertsteuersatz in Prozent
 * @param zahlungszielTage Zahlungsziel in Tagen
 */
public record RechnungseinstellungenResponse(
    String nummerMuster, int naechsteNummer, BigDecimal steuersatz, int zahlungszielTage) {

  /** Die Sicht der Oberflaeche auf die Einstellungen. */
  static RechnungseinstellungenResponse of(final RechnungseinstellungenMitNummer stand) {
    return new RechnungseinstellungenResponse(
        stand.einstellungen().nummerMuster().text(),
        stand.naechsteNummer(),
        stand.einstellungen().steuersatz(),
        stand.einstellungen().zahlungszielTage());
  }
}
