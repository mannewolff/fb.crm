/**
 * Der Vorgang als Klammer der Geschaeftschance und seine durchgehende Historie (Plan #56).
 *
 * <p>Eigenes Modul neben {@code firma}: Der Vorgang ist nach Spezifikation R2 eine eigenstaendige
 * Klammer mit eigenem Lebenszyklus, die Firma bleibt Stammdatum. Das Modul liest die Firma, die
 * Firma weiss nichts vom Vorgang — sonst entstuende ein Paketzyklus (E1, E2).
 */
@NullMarked
package org.mwolff.fbcrm.vorgang;

import org.jspecify.annotations.NullMarked;
