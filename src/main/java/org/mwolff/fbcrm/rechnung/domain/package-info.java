/**
 * Die Einstellungen zur Rechnung als Fachobjekt, die Schreibweise der Rechnungsnummer als
 * Wertobjekt und der Port auf ihren Bestand — framework-frei (CLAUDE-java.md §6.1).
 *
 * <p>Dazu die Bausteine des Belegs: {@code Belegabsender} und {@code Belegempfaenger} als Kopien
 * dessen, was beim Stellen galt, und der Port {@code DokumentSpeicher} auf den Objektspeicher der
 * archivierten Belege.
 *
 * <p>Weder Spring noch JPA erscheinen hier; {@code ArchitectureTest} haelt das fest.
 */
@NullMarked
package org.mwolff.fbcrm.rechnung.domain;

import org.jspecify.annotations.NullMarked;
