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
 * <p><b>Nach draussen gibt es genau zwei Tueren</b>, und beide liegen hier: {@link
 * org.mwolff.fbcrm.arbeitszeit.application.Arbeitszeitauskunft} sagt einem fremden Modul die
 * Stunden — die eines Angebots ({@code angefallen}, {@code imMonat}) und die ueber alle Angebote
 * ({@code alleAngefallen}, {@code alleImZeitraum}, Issue #211) samt den Monaten, in denen
 * ueberhaupt Zeit erfasst ist ({@code monateMitEintragImZeitraum}, Plan #274, E6) —, und {@code
 * Buchbarkeit} sagt ihm, welche Position sie tragen darf. {@code rechnung} belegt damit seine
 * Entwuerfe vor (Issue #199); die Richtung ist {@code rechnung} → {@code arbeitszeit}, und dieses
 * Modul kennt die Rechnung nicht. Der Port auf den Bestand der Zeiten bleibt dabei innen: Wer ihn
 * von aussen aufriefe, muesste die Positionen eines Angebots selbst zusammentragen.
 *
 * <p><b>Daneben steht eine umgekehrte Richtung und keine dritte Tuer</b> (Issue #228): {@link
 * org.mwolff.fbcrm.arbeitszeit.application.AngebotZeitbindung} setzt den Port {@code
 * angebot.application.Zeitbindung} um und sagt dem Angebot, welche seiner Positionen erfasste Zeit
 * tragen. Gefragt wird dort, gefragt <em>hat</em> aber {@code angebot} — nach draussen fragt dieses
 * Modul weiter nur ueber {@code Arbeitszeitauskunft}. Der Port haengt im fremden Modul, weil die
 * Richtung {@code arbeitszeit} → {@code angebot} bleiben muss; umgekehrt zeigten beide aufeinander.
 *
 * <p><b>Die Meldungen am Feld entstehen hier</b> und nicht in {@code arbeitszeit.domain}: Nur diese
 * Schicht kennt die Feldnamen der Schnittstelle (A19). Das Domaenenmodell prueft dieselben Regeln
 * als Invariante und wirft dafuer eine {@code IllegalArgumentException} — die sieht niemand, weil
 * der Anwendungsfall vorher abweist.
 */
@NullMarked
package org.mwolff.fbcrm.arbeitszeit.application;

import org.jspecify.annotations.NullMarked;
