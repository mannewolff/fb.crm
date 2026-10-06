package org.mwolff.fbcrm.startseite.application;

import java.time.YearMonth;
import org.mwolff.fbcrm.rechnung.application.Monatsabrechnung;

/**
 * Eine Zeile der Monatsliste unter „Abgerechnet" bei Jahreswahl (#273, Kriterium 7; Plan #274,
 * E11): ein Monat mit mindestens einer gestellten Rechnung und was in ihm gestellt wurde.
 *
 * @param monat der Monat des Rechnungsdatums
 * @param abrechnung Netto, Brutto und Anzahl der in ihm gestellten Rechnungen
 */
public record Abrechnungsmonat(YearMonth monat, Monatsabrechnung abrechnung) {}
