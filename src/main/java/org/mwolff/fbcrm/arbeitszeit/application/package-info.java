/**
 * Die Anwendungsfaelle der Arbeitszeit und die Regeln, die beim Buchen gelten.
 *
 * <p>Hier stehen die drei Schreibwege eines Zeiteintrags — anlegen, aendern, loeschen — und die
 * beiden Begriffe der Buchbarkeit ({@link org.mwolff.fbcrm.arbeitszeit.application.Buchbarkeit},
 * Plan #194, A6). Die Richtung der Abhaengigkeit ist {@code arbeitszeit} → {@code angebot} und
 * {@code arbeitszeit} → {@code firma} (A1): Die Zeit wird auf eine Angebotsposition gebucht und
 * muss deren Abrechnungsart, Einheit und den Status ihres Angebots kennen, und die Meldung der
 * Ueberschneidung nennt die Firma. Umgekehrt zeigt nichts.
 *
 * <p><b>Die Meldungen am Feld entstehen hier</b> und nicht in {@code arbeitszeit.domain}: Nur diese
 * Schicht kennt die Feldnamen der Schnittstelle (A19). Das Domaenenmodell prueft dieselben Regeln
 * als Invariante und wirft dafuer eine {@code IllegalArgumentException} — die sieht niemand, weil
 * der Anwendungsfall vorher abweist.
 */
@NullMarked
package org.mwolff.fbcrm.arbeitszeit.application;

import org.jspecify.annotations.NullMarked;
