package org.mwolff.fbcrm.rechnung.domain;

import java.math.BigDecimal;

/**
 * Die Einstellungen, mit denen eine Rechnung entsteht (fachliche Quelle #159, Kriterium 1).
 *
 * <p>Genau ein Satz je Instanz; der Bestand haelt ihn an einer festen Zeile, deshalb traegt der
 * Record keine Kennung. Der Zeitpunkt der letzten Aenderung fehlt hier aus demselben Grund, aus dem
 * die Domaene keine Uhr kennt (CLAUDE-java.md §6.2) — er kommt von aussen und geht am Port mit.
 *
 * <p>Das Jahr, fuer das die naechste Nummer gilt, fehlt bewusst: Der Jahreswechsel gehoert zu #160
 * (Plan #161, E3).
 *
 * @param nummerMuster Schreibweise der Rechnungsnummer
 * @param naechsteNummer die naechste laufende Nummer, ab 1
 * @param steuersatz Mehrwertsteuersatz in Prozent, 0 bis 100
 * @param zahlungszielTage Zahlungsziel in Tagen, ab 0
 */
public record Rechnungseinstellungen(
    Nummernmuster nummerMuster, int naechsteNummer, BigDecimal steuersatz, int zahlungszielTage) {}
