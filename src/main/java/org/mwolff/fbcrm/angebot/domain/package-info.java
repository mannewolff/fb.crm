/**
 * Angebot, Position und die Ports auf ihren Bestand — framework-frei (CLAUDE-java.md §6.1).
 *
 * <p>Weder Spring noch JPA erscheinen hier; {@code ArchitectureTest} haelt das fest. Die eine
 * Ausnahme ist die Richtung nach {@code angebot.application}: Die Domaenenmethoden werfen {@code
 * AngebotNichtAenderbar}, und diese Ausnahme traegt ihren HTTP-Statuscode und liegt deshalb dort.
 * Der Import geht nur in diese Richtung und bleibt im Modul.
 */
@NullMarked
package org.mwolff.fbcrm.angebot.domain;

import org.jspecify.annotations.NullMarked;
