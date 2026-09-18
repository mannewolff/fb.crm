package org.mwolff.fbcrm.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Nimmt eine Methode, einen Konstruktor oder einen Typ aus der Abdeckungsmessung heraus.
 *
 * <p>Der Name traegt bewusst das Wort {@code Generated}: JaCoCo filtert Elemente, deren Annotation
 * es im Namen enthaelt und die mindestens {@code CLASS}-Retention hat. PIT liest dieselbe
 * Annotation ueber das {@code FANN}-Feature im Profil {@code pit}.
 *
 * <p>Einzusetzen ist sie <b>methodengenau</b> und nur dort, wo ein Zweig nachweislich nicht
 * erreichbar ist — nie, um eine Luecke zu verstecken (CLAUDE-java.md §5.4).
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR})
public @interface ExcludeFromJacocoGeneratedReport {}
