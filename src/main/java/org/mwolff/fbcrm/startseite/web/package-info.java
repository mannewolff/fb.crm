/**
 * Die HTTP-Seite der Startseite: der eine Leseweg und der Record seiner Antwort (Issue #214).
 *
 * <p><b>Ein Weg und nicht drei</b> (Plan #208, E7): Die Kennzahlen entstehen in einer Transaktion,
 * und die Ansicht hat genau einen Ladezustand. Drei Abrufe koennten sich widersprechen — Kriterium
 * 3 aus #206 ist eine Aussage ueber Werte, die zusammen gelten.
 *
 * <p>Gewandelt wird hier, entschieden nicht: Es gibt keinen Rumpf, und der einzige Parameter ist
 * der Zeitraum, Monat oder Jahr (Plan #274, E1). Seine Wandlung liegt in {@code ZeitraumConverter}
 * — ein Wert, der kein Zeitraum ist, laesst sich nicht wandeln und kommt als 400 zurueck ({@code
 * GlobalExceptionHandler}, E3). Welcher Zeitraum gilt, wenn er fehlt oder nicht zur Wahl steht,
 * entscheidet weiter {@code startseite.application} an seiner Uhr und am Bestand (Plan #208, E8,
 * E18; Plan #274, E17) — der Controller entscheidet nichts (CLAUDE-java.md §6.3).
 *
 * <p><b>Kein Eintrag in {@code SecurityConfig}</b>: Der Weg liegt unter {@code /api/startseite} und
 * faellt damit unter das bestehende {@code /api/**} fuer angemeldete Benutzer.
 */
@NullMarked
package org.mwolff.fbcrm.startseite.web;

import org.jspecify.annotations.NullMarked;
