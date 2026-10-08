package org.mwolff.fbcrm.firma.application;

import java.util.List;
import org.mwolff.fbcrm.firma.domain.Ansprechpartner;
import org.mwolff.fbcrm.firma.domain.Firma;

/**
 * Eine Firma und ihre Ansprechpartner — aktive und stillgelegte gemeinsam.
 *
 * <p>Beides in einer Antwort, weil die Detailansicht beides in einem Zug zeigt; einen eigenen Weg
 * {@code GET …/ansprechpartner} gibt es deshalb nicht. Welche Ansprechpartner wo erscheinen,
 * entscheidet die Oberflaeche am Schalter {@code aktiv} (Kriterium 10).
 *
 * @param firma die Firma
 * @param ansprechpartner ihre Ansprechpartner, sortiert nach Nachnamen ohne Ruecksicht auf Gross-
 *     und Kleinschreibung und bei gleichem Nachnamen nach Id
 */
public record FirmaMitAnsprechpartnern(Firma firma, List<Ansprechpartner> ansprechpartner) {}
