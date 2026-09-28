/**
 * Die HTTP-Seite des Auftrags: die beiden Controller und die Records von Anfrage und Antwort.
 *
 * <p>Geprueft wird hier nur die <b>Form</b> — Vorzeichen, Nachkommastellen, Laengen und die Regel
 * des Leistungszeitraums, dieselben wie im Schema. Ob es den gewaehlten Platz im Angebot gibt und
 * ob die Menge die vereinbarte uebersteigt, entscheidet {@code auftrag.application}: Dazu braucht
 * es das Angebot, und das kennt diese Schicht nicht.
 *
 * <p><b>Zwei der drei Wege liegen unter {@code /api/angebote}</b> und trotzdem in diesem Modul
 * (Plan E3): Die Angebotsansicht braucht die Auskunft, ob zu ihrem Angebot ein Auftrag besteht und
 * ob sich heute einer anlegen laesst. Gehalten werden die Wege vom juengeren Modul — dieselbe
 * Richtung, die {@code angebot.web.VorgangAngeboteController} gegenueber {@code vorgang} nimmt; ein
 * Rueckverweis in {@code AngebotResponse} waere die Kante, die {@code
 * ArchitectureTest.modules_thenFreeOfCycles} abweist.
 *
 * <p><b>Der Rumpf des Anlegens traegt kein Preisfeld</b> (Plan E7): Was der Absender nicht aendern
 * darf, kommt gar nicht erst vor.
 *
 * <p>Die Antworten tragen die Summe und je Position den Betrag als gerechnete Werte: Keiner davon
 * steht in einer Spalte (E11).
 */
@NullMarked
package org.mwolff.fbcrm.auftrag.web;

import org.jspecify.annotations.NullMarked;
