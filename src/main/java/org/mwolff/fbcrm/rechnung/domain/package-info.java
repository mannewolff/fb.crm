/**
 * Die Rechnung mit ihren Positionen und ihrem Zustand, die Einstellungen zur Rechnung, die
 * Schreibweise der Rechnungsnummer als Wertobjekt und die Ports auf ihren Bestand — framework-frei
 * (CLAUDE-java.md §6.1).
 *
 * <p>Dazu der Abrechnungsstand: ein reiner Rechner, der aus den Positionen eines Angebots und den
 * Rechnungen dieses Angebots sagt, was abgerechnet und was noch offen ist (Plan #169, E6). Er kennt
 * keinen Bestand — dieselbe Rechnung speist die Entwurfsmaske, die Angebotsansicht und die Liste
 * der abrechenbaren Angebote.
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
