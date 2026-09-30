package org.mwolff.fbcrm.rechnung.web;

import java.math.BigDecimal;
import org.mwolff.fbcrm.rechnung.domain.Rechnungseinstellungen;

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
 * @param nummerMuster Schreibweise der Rechnungsnummer
 * @param naechsteNummer die naechste laufende Nummer
 * @param steuersatz Mehrwertsteuersatz in Prozent
 * @param zahlungszielTage Zahlungsziel in Tagen
 */
public record RechnungseinstellungenResponse(
    String nummerMuster, int naechsteNummer, BigDecimal steuersatz, int zahlungszielTage) {

  /** Die Sicht der Oberflaeche auf die Einstellungen. */
  static RechnungseinstellungenResponse of(final Rechnungseinstellungen einstellungen) {
    return new RechnungseinstellungenResponse(
        einstellungen.nummerMuster().text(),
        einstellungen.naechsteNummer(),
        einstellungen.steuersatz(),
        einstellungen.zahlungszielTage());
  }
}
