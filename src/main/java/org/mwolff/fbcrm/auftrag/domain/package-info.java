/**
 * Auftrag, Position, Status, Nummer und die Ports auf ihren Bestand — framework-frei
 * (CLAUDE-java.md §6.1).
 *
 * <p>Ein eigenes Modul und nicht ein Teil von {@code angebot} (E1): Kapitel 03 der Spezifikation
 * fuehrt Angebot, Auftrag und Rechnung als eigenstaendige Dokumente, und der Auftrag hat einen
 * eigenen Lebenszyklus, eine eigene Nummer und eine eigene Auswertung — das Angebot ist nur seine
 * Quelle. Nach {@code vorgang} gehoert er auch nicht: Dann wuesste die Klammer von ihrem Inhalt.
 * Die Richtung der Abhaengigkeiten ist darum {@code auftrag → angebot, vorgang, firma, common}, und
 * in dieser Schicht verweist der Auftrag auf beide nur mit deren Kennung.
 *
 * <p>Weder Spring noch JPA erscheinen hier; {@code ArchitectureTest} haelt das fest.
 */
@NullMarked
package org.mwolff.fbcrm.auftrag.domain;

import org.jspecify.annotations.NullMarked;
