/**
 * Die Anwendungsfaelle rund um das Angebot: anlegen, fortschreiben, verwerfen, lesen.
 *
 * <p>Hier liegen auch die Ausnahmen, die der {@code GlobalExceptionHandler} auf HTTP-Statuscodes
 * abbildet — bewusst hier und nicht in {@code domain}, weil das Domaenenmodell framework-frei
 * bleibt (CLAUDE-java.md §6.1). {@code AngebotNichtAenderbar} wirft die Domaene selbst und
 * importiert sie deshalb aus diesem Paket zurueck; der Import geht nur in diese Richtung.
 *
 * <p>Hier liegt auch der Satz des Belegs: {@code AngebotDruckdaten} sagt, was darauf steht, {@code
 * Beleglayout} rechnet daraus die Druckzeilen, und der Port {@code Belegdrucker} schreibt sie weg.
 * Der Satz gehoert in diese Schicht und nicht in {@code domain} — er ist kein Begriff des
 * Geschaefts, sondern eine Rechnung ueber dessen Daten (E10).
 *
 * <p>Versenden, Reaktion und Pipeline folgen mit den naechsten Paketen.
 */
@NullMarked
package org.mwolff.fbcrm.angebot.application;

import org.jspecify.annotations.NullMarked;
