/**
 * Die Anwendungsfaelle der Rechnung: die Einstellungen lesen und pflegen, die Liste aller
 * Rechnungen, die abrechenbaren Angebote und der Entwurf — anlegen, lesen, aendern, loeschen.
 *
 * <p>Hier liegen die Transaktionsgrenzen und die Uhr, aus der Zeitpunkt und Datum kommen; ein Datum
 * rechnet dabei gegen die Geschaeftszone und nicht gegen UTC (E12).
 *
 * <p>Hier liegen auch die fachlichen Ausnahmen mit ihrem Statuscode. Sie stehen in dieser Schicht
 * und nicht in {@code domain}, weil {@code @ResponseStatus} eine Spring-Annotation ist, die das
 * framework-freie Domaenenmodell nicht tragen darf (CLAUDE-java.md §6.1) — auch dann, wenn die
 * Domaene sie selbst wirft, wie {@code RechnungszustandPasstNicht}.
 *
 * <p>Zu normalisieren gibt es nichts, anders als bei {@code eigeneangaben.application}: Die Werte
 * der Einstellungen sind Zahlen, und das Muster ist bereits an der Schnittstelle geprueft.
 *
 * <p>Hier liegt die eine Tuer dieses Moduls nach draussen: {@code Rechnungsauskunft}. Sie sagt
 * anderen Modulen, was gestellt wurde — die Summe eines Monats und die abgerechneten Mengen je
 * Angebotsposition (#206, Kriterien 5 und 7). Sie kennt dabei <b>nur gestellte</b> Rechnungen,
 * waehrend {@code Abrechnungsstand} Entwuerfe mitzaehlt; welche der beiden Groessen gemeint ist,
 * entscheidet die Frage: was noch abzurechnen waere oder was schon draussen ist.
 *
 * <p>Hier liegt auch die Sprache des Belegdrucks: das {@code Druckelement} — Text, Linie, Flaeche —
 * mit {@code Schrift}, {@code Farbe} und {@code Ausrichtung} und der Port {@code Belegdrucker}, der
 * eine Elementfolge als PDF wegschreibt. Sie gehoert in diese Schicht und nicht in {@code domain} —
 * der Satz eines Belegs ist kein Begriff des Geschaefts, sondern eine Rechnung ueber dessen Daten.
 */
@NullMarked
package org.mwolff.fbcrm.rechnung.application;

import org.jspecify.annotations.NullMarked;
