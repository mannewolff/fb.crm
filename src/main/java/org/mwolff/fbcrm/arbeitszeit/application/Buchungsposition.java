package org.mwolff.fbcrm.arbeitszeit.application;

import java.time.LocalDate;

/**
 * Eine Angebotsposition, wie die Zeiterfassung sie nennt (Plan #194, A15, A20).
 *
 * <p>Beide Lesewege dieses Pakets zeigen dieselben Angaben: die Monatsliste an jeder Zeile und die
 * Auswahlliste des Dialogs an jedem Eintrag. Darum ein Begriff und nicht zwei — zwei liefen beim
 * ersten Nachziehen auseinander, und die Oberflaeche muesste zwei Formen derselben Aussage lesen.
 *
 * <p><b>Das Angebot und die Firma stehen mit darin</b>, weil die Position allein nichts sagt: Die
 * Zeile der Monatsliste nennt den Kunden (Issue #193, Kriterium 5), und die Auswahlliste gruppiert
 * nach Firma und Angebot (A15). Die Angaben kommen angereichert und nicht als Kennung, nach der die
 * Oberflaeche noch einmal fragen muesste.
 *
 * <p><b>Das Kennzeichen {@code intern} steht mit darin</b> (Issue #229, Kriterien 2 und 5 von
 * #207): Die Ansicht soll die eigene Arbeit von der fuer einen Kunden unterscheiden koennen, und
 * zwar an jeder Stelle, an der sie eine Position nennt.
 *
 * <p>Ohne Menge, Einheit und Preis: Das ist der Abrechnungsstand des Angebots, nicht die
 * Zeiterfassung.
 *
 * @param id Kennung der Angebotsposition
 * @param bezeichnung die Leistung, wie das Angebot sie nennt
 * @param angebotId Kennung des Angebots, zu dem die Position gehoert
 * @param angebotDatum Datum dieses Angebots
 * @param intern ob das Angebot die eigene interne Arbeit festhaelt (Kriterium 2 von #207)
 * @param firmaName Name der Firma, an die das Angebot geht
 */
public record Buchungsposition(
    long id,
    String bezeichnung,
    long angebotId,
    LocalDate angebotDatum,
    boolean intern,
    String firmaName) {}
