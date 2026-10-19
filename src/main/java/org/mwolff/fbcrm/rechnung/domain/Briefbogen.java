package org.mwolff.fbcrm.rechnung.domain;

import org.jspecify.annotations.Nullable;

/**
 * Der Briefbogen als Ganzes: das Briefpapier, die beiden Schalter und die sechs Raender (Plan #363,
 * E1).
 *
 * <p>Genau einer je Instanz; der Bestand haelt ihn an einer festen Zeile, deshalb traegt der Record
 * keine Kennung. Er wird nicht in die Rechnung festgeschrieben, sondern wirkt nur im Augenblick des
 * Drucks (E2). Entfernt der Betreiber sein Briefpapier, faellt allein {@code blatt} weg; Schalter
 * und Raender bleiben (fachliche Quelle #361, Kriterium 16; E15).
 *
 * @param blatt das hinterlegte Briefpapier, {@code null} ohne Briefpapier
 * @param absenderzeile ob die kleine Absenderzeile ueber der Anschrift eingedruckt wird
 * @param steuerBankZeile ob die Zeile mit Steuer- und Bankangaben eingedruckt wird
 * @param raender die sechs Raender
 */
public record Briefbogen(
    @Nullable Briefpapierblatt blatt,
    boolean absenderzeile,
    boolean steuerBankZeile,
    Randmasse raender) {}
