/**
 * Die Anwendungsfaelle rund um Firma und Ansprechpartner.
 *
 * <p>Hier liegen die Transaktionsgrenzen, die Normalisierung der Eingaben (E9) und die Exceptions,
 * die der {@code GlobalExceptionHandler} auf HTTP-Statuscodes abbildet — letztere bewusst hier und
 * nicht in {@code domain}, weil das Domaenenmodell framework-frei bleibt (CLAUDE-java.md §6.1).
 */
@NullMarked
package org.mwolff.fbcrm.firma.application;

import org.jspecify.annotations.NullMarked;
