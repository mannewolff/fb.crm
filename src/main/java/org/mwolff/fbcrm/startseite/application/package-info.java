/**
 * Die Anwendungsschicht der Startseite: die drei Kennzahlen und der Zeitraum, Monat oder Jahr, fuer
 * den sie gelten (#206, #273; Plan #208, E1; Plan #274, E17).
 *
 * <p><b>Dieses Modul hat keinen eigenen Bestand.</b> Es gibt kein {@code domain} und kein {@code
 * infrastructure}, keine Entitaet und keine Migration — die Startseite ist eine Ansicht auf das,
 * was andere Module fuehren. Sie rechnet daraus, und was sie rechnet, speichert sie nicht: Ein
 * gespeicherter Kennzahlenstand waere ein zweiter Wahrheitsort, der bei jeder Aenderung an einem
 * Angebot, einem Zeiteintrag und jeder Rechnung nachzuziehen waere.
 *
 * <p><b>Die Richtung der Abhaengigkeiten ist {@code startseite} → {@code angebot}, {@code
 * arbeitszeit}, {@code rechnung}, {@code common}; nichts zeigt zurueck.</b> Darum liegt die Ansicht
 * in einem eigenen Modul und nicht in {@code rechnung}: Sie liest aus vier Modulen und gehoert
 * keinem von ihnen. Gefragt wird ausschliesslich an den Tueren, die jene Module nach draussen
 * stellen — {@code AngeboteUebersichtUseCase}, {@code Arbeitszeitauskunft} und {@code
 * Rechnungsauskunft}. Kein Port auf einen fremden Bestand wird von hier aus aufgerufen.
 *
 * <p><b>Die Regeln bleiben, wo sie herkommen.</b> Was an einer Position noch nicht abgerechnet ist,
 * rechnet {@code rechnung.domain.Positionsstand}, und jeder Geldbetrag entsteht nach {@code
 * common.Geldrechnung} — je Position auf den Cent, dann addiert. Hier steht nur, was allein die
 * Startseite behauptet: welche Angebote „in Arbeit" sind, welche Positionen in Kennzahl 2 eingehen,
 * welche Zeitraeume zur Wahl stehen und welcher gilt (Plan #208, E22, E12, E8; Plan #274, E8, E9).
 */
@NullMarked
package org.mwolff.fbcrm.startseite.application;

import org.jspecify.annotations.NullMarked;
