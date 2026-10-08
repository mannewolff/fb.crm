package org.mwolff.fbcrm.jahresabschluss.application;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/**
 * Die Arbeitszeit eines Jahres (#287, Kriterium 10; Plan #288, E8, E9, E12): die Stunden mit
 * Arbeitstag im Jahr, getrennt nach Kundenarbeit und internen Projekten, und der Erloes je Stunde.
 *
 * <p>Die Stunden sind nicht gerundet — eine Stundenzahl ist kein Betrag. Ohne einen einzigen
 * Eintrag steht 0 da.
 *
 * @param kundenStunden die Stunden an Angeboten fuer Kunden
 * @param interneStunden die Stunden an internen Projekten
 * @param erloesJeStunde Einnahmen netto durch Kundenstunden, kaufmaennisch auf den Cent; {@code
 *     null} ohne Kundenstunden (Kriterium 11)
 */
public record Jahresarbeitszeit(
    BigDecimal kundenStunden, BigDecimal interneStunden, @Nullable BigDecimal erloesJeStunde) {}
