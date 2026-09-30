/**
 * Die HTTP-Seite der Rechnungseinstellungen: der Controller, die Records von Anfrage und Antwort
 * und die Pruefregel des Nummernmusters.
 *
 * <p>Geprueft wird hier die <b>Form</b> — die Grenzen der Zahlen, dieselben wie im Schema, und die
 * Schreibweise des Musters. Die Regel des Musters selbst steht in {@code rechnung.domain}; die
 * Pruefregel befragt sie nur, damit ihre Verletzung als Meldung am Feld ankommt und nicht als
 * gescheiterter Record-Konstruktor.
 */
@NullMarked
package org.mwolff.fbcrm.rechnung.web;

import org.jspecify.annotations.NullMarked;
