package org.mwolff.fbcrm.rechnung.application;

import java.math.BigDecimal;
import org.mwolff.fbcrm.rechnung.domain.Positionsstand;

/**
 * Eine Position des Angebots mit allem, was ihre Zeile zeigt (Issue #193, Kriterien 7, 8, 11; Plan
 * #194, A6, A12).
 *
 * <p>Drei Angaben nebeneinander: angeboten und abgerechnet rechnet der {@link Positionsstand},
 * angefallen kommt aus der Zeiterfassung. Der Stand bleibt damit ein reiner Rechner ohne Wissen
 * ueber die Arbeitszeit — die beiden Haelften treffen sich erst hier, in der Anwendungsschicht.
 *
 * <p><b>{@code buchbar} haengt nicht am Status des Angebots</b>, sondern allein an der Position
 * ({@code Buchbarkeit}). Sonst verschwaenden angefallene Stunden und Ueberschreitung in dem
 * Augenblick, in dem das Stellen der letzten Rechnung das Angebot auf „abgerechnet" setzt — genau
 * dann, wenn Kriterium 8 sie sehen will.
 *
 * @param stand angeboten, abgerechnet, offen und Ueberschreitung dieser Position
 * @param buchbar ob die Position Stunden traegt — nach Aufwand und in der Einheit Stunde
 * @param angefallen die insgesamt erfassten Stunden, {@code 0} an einer nicht buchbaren Position
 */
public record Positionsabrechnung(Positionsstand stand, boolean buchbar, BigDecimal angefallen) {}
