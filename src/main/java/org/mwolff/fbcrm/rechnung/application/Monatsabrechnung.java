package org.mwolff.fbcrm.rechnung.application;

import java.math.BigDecimal;

/**
 * Was in einem Monat gestellt wurde: Netto, Brutto und die Zahl der Rechnungen (#206, Kriterium 7).
 *
 * <p>Netto ist die fuehrende Angabe, Brutto steht daneben (#206, Antwort 5). Beide sind die Summe
 * der <b>je Rechnung</b> gerundeten Betraege und nicht aus der Monatssumme gerechnet: Der Betrag,
 * der auf dem Beleg steht, ist der je Rechnung — und nur so trifft die Kennzahl den Cent, den die
 * Rechnungsliste zeigt (Kriterium 9).
 *
 * <p>Entwuerfe stehen nicht darin; gezaehlt wird allein, was gestellt ist (Antwort 6).
 *
 * @param netto die Summe der Netto-Betraege der im Monat gestellten Rechnungen
 * @param brutto die Summe ihrer Brutto-Betraege, jeder mit dem Satz seiner Rechnung
 * @param anzahl die Zahl dieser Rechnungen
 */
public record Monatsabrechnung(BigDecimal netto, BigDecimal brutto, int anzahl) {}
