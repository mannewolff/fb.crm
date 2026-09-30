/**
 * Die beiden Anwendungsfaelle der Rechnungseinstellungen: lesen und pflegen.
 *
 * <p>Hier liegen die Transaktionsgrenzen und die Uhr, aus der der Zeitpunkt der Aenderung kommt.
 * Eine eigene Exception gibt es nicht — es gibt keine Lage, in der die Einstellungen fehlen
 * koennten (die Migration hat die eine Zeile mit ihren Vorbelegungen angelegt).
 *
 * <p>Zu normalisieren gibt es nichts, anders als bei {@code eigeneangaben.application}: Drei der
 * vier Werte sind Zahlen, und das Muster ist bereits an der Schnittstelle geprueft.
 *
 * <p>Hier liegt auch die Sprache des Belegdrucks: die {@code Druckzeile} mit ihrer {@code Schrift}
 * und der Port {@code Belegdrucker}, der eine Zeilenfolge als PDF wegschreibt. Sie gehoert in diese
 * Schicht und nicht in {@code domain} — der Satz eines Belegs ist kein Begriff des Geschaefts,
 * sondern eine Rechnung ueber dessen Daten.
 */
@NullMarked
package org.mwolff.fbcrm.rechnung.application;

import org.jspecify.annotations.NullMarked;
