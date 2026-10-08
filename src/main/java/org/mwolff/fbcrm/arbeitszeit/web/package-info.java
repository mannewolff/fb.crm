/**
 * Die HTTP-Seite der Arbeitszeit: der Controller und die Records von Anfrage und Antwort.
 *
 * <p>Geprueft wird hier nur die <b>Form</b> — dass Tag, Uhrzeiten und Position ueberhaupt da sind.
 * Ob eine Uhrzeit auf einer Viertelstunde liegt, ob auf die Position gebucht werden darf und ob der
 * Zeitraum frei ist, entscheidet {@code arbeitszeit.application}: Das sind fachliche Regeln, und
 * ihre Meldungen nennen Werte aus dem Bestand (Plan #194, A19).
 *
 * <p>Die Antwort traegt die Dauer als gerechneten Wert: Gespeichert ist sie nicht (A3).
 *
 * <p>Keine eigene Regel in {@code SecurityConfig} — die Pfade fallen unter das bestehende {@code
 * /api/**} fuer angemeldete Benutzer.
 */
@NullMarked
package org.mwolff.fbcrm.arbeitszeit.web;

import org.jspecify.annotations.NullMarked;
