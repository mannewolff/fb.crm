package org.mwolff.fbcrm.rechnung.application;

import org.mwolff.fbcrm.rechnung.domain.Rechnungseinstellungen;

/**
 * Was die Maske „Rechnung" unter Administration auf einmal zeigt: die Einstellungen und die
 * naechste laufende Nummer (#159, Kriterium 5).
 *
 * <p>Zwei Bestaende in einer Antwort, seit die Nummer dem Nummernkreis gehoert und nicht mehr den
 * Einstellungen (#175). Die Nummer ist die des Zaehlerjahrs, das zum <b>gespeicherten</b> Muster
 * gehoert — ein Muster ohne Jahres-Platzhalter zaehlt unter 0, eines mit Jahr im laufenden Jahr der
 * Geschaeftszone.
 *
 * @param einstellungen die gespeicherten Einstellungen
 * @param naechsteNummer der Stand des zugehoerigen Zaehlers, ab 1
 */
public record RechnungseinstellungenMitNummer(
    Rechnungseinstellungen einstellungen, int naechsteNummer) {}
