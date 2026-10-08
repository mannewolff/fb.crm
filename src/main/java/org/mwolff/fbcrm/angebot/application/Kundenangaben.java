package org.mwolff.fbcrm.angebot.application;

import org.jspecify.annotations.Nullable;

/**
 * Wem ein Angebot gilt, in Worten: der Name der Firma und der des Ansprechpartners (Issue #126).
 *
 * <p>Die Angaben kommen aus dem Bestand von heute und sind keine Kopie: Benennt sich die Firma um,
 * zeigt auch jedes ihrer Angebote den neuen Namen.
 *
 * @param firmaName Name der Firma
 * @param ansprechpartnerName Name des Ansprechpartners, oder {@code null}, wenn das Angebot keinen
 *     traegt
 */
public record Kundenangaben(String firmaName, @Nullable String ansprechpartnerName) {}
