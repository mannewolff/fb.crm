package org.mwolff.fbcrm.auth.web;

/**
 * Ob die Instanz schon eingerichtet ist (E11).
 *
 * <p><b>Genau ein Feld, und dabei bleibt es.</b> Diese Antwort geht an jeden, der den Pfad kennt —
 * sie ist der einzige Endpunkt, den eine Instanz ohne Konto ohne Sitzung beantworten muss. Jedes
 * weitere Feld — Version, Adresse des Betreibers, Zeitpunkt der Einrichtung — waere eine Auskunft
 * an jeden Scanner im Netz. {@code SetupStatusIT} zaehlt die Felder nach.
 *
 * @param initialized ob es bereits ein Konto gibt
 */
public record SetupStatusResponse(boolean initialized) {}
