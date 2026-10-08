/**
 * Der Zeiteintrag und der Port auf seinen Bestand — framework-frei (CLAUDE-java.md §6.1).
 *
 * <p>Ein Zeiteintrag ist Tag, von und bis auf einer Angebotsposition. Er rechnet seine Dauer selbst
 * — gespeichert wird sie nicht, weil sie ein zweiter Wahrheitsort neben von und bis waere (Plan
 * #194, A3), dieselbe Ueberlegung wie bei {@code rechnung.domain.Positionsstand} und der offenen
 * Menge. Und er sagt, ob er sich mit einem anderen Eintrag ueberschneidet (A8); welche Eintraege er
 * dafuer zu sehen bekommt, entscheidet die Anwendungsschicht.
 *
 * <p>Raster und {@code bis > von} haelt der kompakte Konstruktor als <b>Invariante</b>: Hier gibt
 * es dafuer nur eine {@code IllegalArgumentException}. Die Meldung am Feld, die der Nutzer liest,
 * kommt aus der Anwendungsschicht (A19) — das Domaenenmodell kennt keine Feldnamen einer
 * Schnittstelle.
 *
 * <p>Weder Spring noch JPA erscheinen hier; {@code ArchitectureTest} haelt das fest.
 */
@NullMarked
package org.mwolff.fbcrm.arbeitszeit.domain;

import org.jspecify.annotations.NullMarked;
