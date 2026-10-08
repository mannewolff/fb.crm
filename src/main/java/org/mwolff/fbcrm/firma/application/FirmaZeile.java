package org.mwolff.fbcrm.firma.application;

import org.jspecify.annotations.Nullable;

/**
 * Eine Zeile der Firmenuebersicht.
 *
 * <p>Sie traegt genau das, was die Liste zeigt — nicht die ganze Firma: Anschrift, Steuernummer und
 * Zeitstempel gehoeren in die Detailansicht und haetten in einer Liste von hundert Zeilen nur
 * Gewicht ohne Nutzen.
 *
 * @param id technische Id der Firma
 * @param name Name der Firma
 * @param ort Ort aus der Anschrift, oder {@code null}
 * @param aktiveAnsprechpartner Zahl der <b>aktiven</b> Ansprechpartner dieser Firma
 * @param aktiv {@code false}, solange die Firma stillgelegt ist
 */
public record FirmaZeile(
    long id, String name, @Nullable String ort, long aktiveAnsprechpartner, boolean aktiv) {}
