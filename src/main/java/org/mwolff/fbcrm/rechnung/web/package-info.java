/**
 * Die HTTP-Seite der Rechnung: die Controller, die Records von Anfrage und Antwort und die
 * Pruefregel des Nummernmusters.
 *
 * <p>Geprueft wird hier die <b>Form</b> — Laengen, Vorzeichen und Nachkommastellen, dieselben wie
 * im Schema, und die Schreibweise des Musters. Die Regel des Musters selbst steht in {@code
 * rechnung.domain}; die Pruefregel befragt sie nur, damit ihre Verletzung als Meldung am Feld
 * ankommt und nicht als gescheiterter Record-Konstruktor. Was ein Zustand zulaesst, entscheidet
 * {@code rechnung.domain}; welches Angebot abgerechnet werden darf, entscheidet {@code
 * rechnung.application}.
 *
 * <p>Die Antworten tragen {@code netto}, {@code steuer}, {@code brutto} und je Zeile {@code offen}
 * und {@code ueberschreitung} als gerechnete Werte: Keiner davon steht in einer Spalte (E5).
 */
@NullMarked
package org.mwolff.fbcrm.rechnung.web;

import org.jspecify.annotations.NullMarked;
