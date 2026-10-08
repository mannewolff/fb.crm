package org.mwolff.fbcrm.rechnung.application;

import java.math.BigDecimal;

/**
 * Was der Freiberufler an einer Position seines Entwurfs eingetragen hat (#160, Kriterien 4 und 9).
 *
 * <p>Die Angebotsposition ist die Kennung — eine Rechnung fuehrt je Angebotsposition hoechstens
 * eine Zeile. Aenderbar sind Text und Menge; der Einzelpreis steht nicht hier, weil er an der
 * Rechnung nicht aenderbar ist (Frage 5 aus #160): Ein anderer Preis ist eine Aenderung am Angebot
 * und gehoert dorthin.
 *
 * @param angebotPositionId Kennung der Angebotsposition, um die es geht
 * @param bezeichnung die Leistung, wie sie auf der Rechnung stehen soll
 * @param menge die Menge, die jetzt abgerechnet wird; 0 laesst die Position wegfallen
 */
public record Abrechnungsangabe(long angebotPositionId, String bezeichnung, BigDecimal menge) {}
