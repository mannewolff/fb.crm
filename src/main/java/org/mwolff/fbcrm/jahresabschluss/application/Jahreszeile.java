package org.mwolff.fbcrm.jahresabschluss.application;

import java.math.BigDecimal;
import java.time.Year;
import org.jspecify.annotations.Nullable;

/**
 * Ein Jahr der Uebersicht mit seinen drei Hauptzahlen (#287, Kriterien 1 und 3).
 *
 * @param jahr das Kalenderjahr
 * @param laeuftNoch ob es das laufende Jahr in der Geschaeftszone ist (Plan #288, E19)
 * @param einnahmenNetto die Summe netto der gestellten Rechnungen mit Rechnungsdatum im Jahr, auf
 *     den Cent
 * @param anzahlRechnungen die Zahl dieser Rechnungen
 * @param annahmequote angenommene durch abgegebene Angebote des Jahres in Prozent, eine
 *     Nachkommastelle; {@code null} ohne abgegebenes Angebot (Kriterium 11)
 */
public record Jahreszeile(
    Year jahr,
    boolean laeuftNoch,
    BigDecimal einnahmenNetto,
    int anzahlRechnungen,
    @Nullable BigDecimal annahmequote) {}
