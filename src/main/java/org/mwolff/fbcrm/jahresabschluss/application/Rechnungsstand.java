package org.mwolff.fbcrm.jahresabschluss.application;

import java.math.BigDecimal;

/**
 * Die Rechnungen eines Jahres (#287, Kriterium 5): wie viele gestellt sind und davon, wie viele
 * heute noch offen und wie viele abgeschrieben sind.
 *
 * <p><b>Offen und abgeschrieben sagt der heutige Zustand der Rechnung</b> und nicht ein Vergleich
 * mit dem Jahresende: Ohne Zahlungsdatum ist der Stand zum 31. Dezember nicht feststellbar (#287,
 * Antwort 2). Entwuerfe zaehlen nirgends.
 *
 * @param anzahl die Zahl der gestellten Rechnungen des Jahres — dieselbe Menge wie in {@link
 *     Einnahmen}
 * @param offenAnzahl die Zahl derer, die heute noch offen sind
 * @param offenNetto ihre Summe netto, auf den Cent; 0,00 ohne offene Rechnung
 * @param abgeschriebenAnzahl die Zahl derer, die abgeschrieben sind
 * @param abgeschriebenNetto ihre Summe netto, auf den Cent; 0,00 ohne abgeschriebene Rechnung
 */
public record Rechnungsstand(
    int anzahl,
    int offenAnzahl,
    BigDecimal offenNetto,
    int abgeschriebenAnzahl,
    BigDecimal abgeschriebenNetto) {}
