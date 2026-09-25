/**
 * Die Anwendungsfaelle rund um den Vorgang und seine Historie.
 *
 * <p>Hier liegen die Transaktionsgrenzen, die Lesemodelle der drei Ansichten und die Exceptions,
 * die der {@code GlobalExceptionHandler} auf HTTP-Statuscodes abbildet — letztere bewusst hier und
 * nicht in {@code domain}, weil das Domaenenmodell framework-frei bleibt (CLAUDE-java.md §6.1).
 *
 * <p>Die Schicht liest auch {@code firma}: Ein Vorgang haengt an einer Firma und einem
 * Ansprechpartner, und die Ansicht zeigt beide mit Namen und Stilllegungsstand (Kriterium 23). Der
 * Weg geht nur in diese Richtung — {@code firma} weiss nichts vom Vorgang (E1, E2).
 */
@NullMarked
package org.mwolff.fbcrm.vorgang.application;

import org.jspecify.annotations.NullMarked;
