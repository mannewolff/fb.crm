package org.mwolff.fbcrm.rechnung.domain;

import java.math.BigDecimal;

/**
 * Die Einstellungen, mit denen eine Rechnung entsteht (fachliche Quelle #159, Kriterium 1).
 *
 * <p>Genau ein Satz je Instanz; der Bestand haelt ihn an einer festen Zeile, deshalb traegt der
 * Record keine Kennung. Der Zeitpunkt der letzten Aenderung fehlt hier aus demselben Grund, aus dem
 * die Domaene keine Uhr kennt (CLAUDE-java.md §6.2) — er kommt von aussen und geht am Port mit.
 *
 * <p>Die naechste laufende Nummer steht <b>nicht</b> hier: Sie gehoert seit #175 dem {@link
 * Nummernkreis}, der je Zaehlerjahr zaehlt. Ein einzelner Wert an den Einstellungen koennte den
 * Jahreswechsel nicht abbilden (#160, Kriterium 16). Ueber HTTP stehen beide weiter nebeneinander —
 * die Maske zeigt einen Bereich.
 *
 * @param nummerMuster Schreibweise der Rechnungsnummer
 * @param steuersatz Mehrwertsteuersatz in Prozent, 0 bis 100
 * @param zahlungszielTage Zahlungsziel in Tagen, ab 0
 */
public record Rechnungseinstellungen(
    Nummernmuster nummerMuster, BigDecimal steuersatz, int zahlungszielTage) {}
