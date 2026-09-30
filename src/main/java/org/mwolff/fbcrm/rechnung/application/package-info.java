/**
 * Die beiden Anwendungsfaelle der Rechnungseinstellungen: lesen und pflegen.
 *
 * <p>Hier liegen die Transaktionsgrenzen und die Uhr, aus der der Zeitpunkt der Aenderung kommt.
 * Eine eigene Exception gibt es nicht — es gibt keine Lage, in der die Einstellungen fehlen
 * koennten (die Migration hat die eine Zeile mit ihren Vorbelegungen angelegt).
 *
 * <p>Zu normalisieren gibt es nichts, anders als bei {@code eigeneangaben.application}: Drei der
 * vier Werte sind Zahlen, und das Muster ist bereits an der Schnittstelle geprueft.
 */
@NullMarked
package org.mwolff.fbcrm.rechnung.application;

import org.jspecify.annotations.NullMarked;
