/**
 * Die Anwendungsschicht des Jahresabschlusses: die Uebersicht aller Jahre mit Daten und ihre
 * Hauptzahlen (#287; Plan #288, E1).
 *
 * <p><b>Dieses Modul hat keinen eigenen Bestand.</b> Es gibt kein {@code domain} und kein {@code
 * infrastructure}, keine Entitaet und keine Migration — wie die Startseite ist der Jahresabschluss
 * eine Ansicht auf das, was andere Module fuehren. Er rechnet daraus, und was er rechnet, speichert
 * er nicht: Alle Zahlen entstehen aus dem heutigen Bestand (#287, Kriterium 13).
 *
 * <p><b>Ein eigenes Modul neben {@code startseite}</b> (E1): Die Startseite ist der Stand von heute
 * in einem der beiden juengsten Zeitraeume, der Jahresabschluss die Auswertung jedes Jahres mit
 * Daten. Ein gemeinsamer Anwendungsfall truege zwei Fragen.
 *
 * <p><b>Die Richtung der Abhaengigkeiten ist {@code jahresabschluss} → {@code angebot}, {@code
 * arbeitszeit}, {@code rechnung}, {@code common}; nichts zeigt zurueck</b> (E20). Gefragt wird
 * ausschliesslich an den Tueren, die jene Module nach draussen stellen — {@code
 * AngeboteUebersichtUseCase} und {@code Rechnungsauskunft}. Kein Port auf einen fremden Bestand
 * wird von hier aus aufgerufen.
 *
 * <p><b>Die Regeln bleiben, wo sie herkommen.</b> Jeder Geldbetrag entsteht nach {@code
 * common.Geldrechnung}. Hier steht nur, was allein der Jahresabschluss behauptet: welche Angebote
 * abgegeben und welche angenommen sind, welches Jahr noch laeuft und wie eine Quote gerundet wird
 * (E7, E19, E10).
 */
@NullMarked
package org.mwolff.fbcrm.jahresabschluss.application;

import org.jspecify.annotations.NullMarked;
