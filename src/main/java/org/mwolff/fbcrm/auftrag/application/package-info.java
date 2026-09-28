/**
 * Die Anwendungsfaelle rund um den Auftrag: anlegen und lesen.
 *
 * <p>Hier liegen auch die Ausnahmen, die der {@code GlobalExceptionHandler} auf HTTP-Statuscodes
 * abbildet — bewusst hier und nicht in {@code domain}, weil das Domaenenmodell framework-frei
 * bleibt (CLAUDE-java.md §6.1).
 *
 * <p><b>Hier liegt auch, was die Angebotsansicht ueber den Auftrag wissen muss</b> (Plan E3): ob zu
 * einem Angebot schon einer besteht und ob das Anlegen heute zulaessig ist. Die Auskunft steht in
 * diesem Modul und nicht in {@code angebot}, damit die Abhaengigkeit ihre Richtung behaelt — ein
 * Rueckverweis waere die Kante, die {@code ArchitectureTest.modules_thenFreeOfCycles} abweist.
 *
 * <p><b>Was der Absender nicht aendern darf, liest diese Schicht aus dem Angebot</b> (Plan E7,
 * Kriterium 2): Bezeichnung, Abrechnungsmodus, Einheit und Einzelpreis einer uebernommenen Position
 * kommen aus der Quelle und nie aus der Anfrage.
 */
@NullMarked
package org.mwolff.fbcrm.auftrag.application;

import org.jspecify.annotations.NullMarked;
