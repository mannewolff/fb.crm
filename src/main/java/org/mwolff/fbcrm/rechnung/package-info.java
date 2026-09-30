/**
 * Die Rechnung — vorerst nur die Einstellungen, die sie braucht: Muster der Rechnungsnummer,
 * naechste laufende Nummer, Mehrwertsteuersatz und Zahlungsziel (Plan #161, fachliche Quelle #159).
 *
 * <p>Ein eigenes Modul und kein Anhaengsel an {@code eigeneangaben} (Plan #161, E1): Die
 * Einstellungen verwendet nur die Rechnung, die in dasselbe Modul kommt, und {@code eigeneangaben}
 * beschreibt die Person, nicht Regeln der Anwendung.
 *
 * <p>Die Einstellungen sind ein Stammdatum — genau ein Satz je Instanz, jeder Wert vorbelegt.
 */
@NullMarked
package org.mwolff.fbcrm.rechnung;

import org.jspecify.annotations.NullMarked;
