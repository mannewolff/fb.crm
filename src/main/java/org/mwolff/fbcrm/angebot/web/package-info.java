/**
 * Die HTTP-Seite des Angebots: die beiden Controller und die Records von Anfrage und Antwort.
 *
 * <p>Geprueft wird hier nur die <b>Form</b> — Laengen, Vorzeichen und Nachkommastellen, dieselben
 * wie im Schema. Was ein Zustand zulaesst, entscheidet {@code angebot.domain}; welcher Kunde zur
 * Wahl steht, entscheidet {@code angebot.application}.
 *
 * <p>Die Antworten tragen {@code stand}, {@code summe} und je Position den {@code betrag} als
 * gerechnete Werte: Keiner davon steht in einer Spalte (E4, E5).
 */
@NullMarked
package org.mwolff.fbcrm.angebot.web;

import org.jspecify.annotations.NullMarked;
