/**
 * Die Anwendungsfaelle rund um das Angebot: anlegen, fortschreiben, verwerfen, lesen.
 *
 * <p>Hier liegen auch die Ausnahmen, die der {@code GlobalExceptionHandler} auf HTTP-Statuscodes
 * abbildet — bewusst hier und nicht in {@code domain}, weil das Domaenenmodell framework-frei
 * bleibt (CLAUDE-java.md §6.1). {@code AngebotNichtAenderbar} wirft die Domaene selbst und
 * importiert sie deshalb aus diesem Paket zurueck; der Import geht nur in diese Richtung.
 */
@NullMarked
package org.mwolff.fbcrm.angebot.application;

import org.jspecify.annotations.NullMarked;
