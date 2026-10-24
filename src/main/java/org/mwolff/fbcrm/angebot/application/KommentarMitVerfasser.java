package org.mwolff.fbcrm.angebot.application;

import org.mwolff.fbcrm.angebot.domain.Angebotskommentar;

/**
 * Ein Kommentar mit dem aktuellen Namen seines Verfassers (Plan #414, A7; fachliche Quelle #409,
 * Kriterium 11).
 *
 * <p>Der Name steht nicht im Kommentar, sondern wird beim Lesen aus dem Konto geholt: Nach einer
 * Umbenennung zeigen auch die bisherigen Kommentare den neuen Namen. Dieser Record traegt beides
 * zusammen aus dem Anwendungsfall zur Schnittstelle.
 *
 * @param kommentar der Kommentar
 * @param verfasser der aktuelle Anzeigename des Kontos, das ihn geschrieben hat
 */
public record KommentarMitVerfasser(Angebotskommentar kommentar, String verfasser) {}
