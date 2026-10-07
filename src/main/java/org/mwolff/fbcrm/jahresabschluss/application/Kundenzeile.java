package org.mwolff.fbcrm.jahresabschluss.application;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/**
 * Ein Kunde des Jahres mit seinem Umsatz (#287, Kriterium 9; Plan #288, E10, E22).
 *
 * <p>Ein Kunde ist eine Firma, erkannt an ihrer Kennung; gezeigt wird ihr heutiger Name.
 *
 * @param firmaName der heutige Name der Firma
 * @param netto die Summe netto ihrer gestellten Rechnungen des Jahres, auf den Cent
 * @param anteil ihr Anteil an den Einnahmen netto des Jahres in Prozent, eine Nachkommastelle;
 *     {@code null} ohne Umsatz (Kriterium 11)
 */
public record Kundenzeile(String firmaName, BigDecimal netto, @Nullable BigDecimal anteil) {}
