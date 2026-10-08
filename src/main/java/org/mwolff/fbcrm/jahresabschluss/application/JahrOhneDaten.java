package org.mwolff.fbcrm.jahresabschluss.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Zu dem angefragten Jahr gibt es keinen Jahresabschluss: Es kennt weder eine gestellte Rechnung
 * noch ein abgegebenes Angebot (#287, Kriterium 1; Plan #288, E3).
 *
 * <p>404 und kein Abschluss aus lauter Nullen: Eine Seite voller Nullen behauptete einen Abschluss,
 * den es nicht gibt. Der Rueckfall der Startseite auf das laufende Jahr passt hier nicht — dort
 * gibt es immer einen gueltigen Ersatz, hier nicht. Das Jahr steht im Pfad, die angefragte
 * Ressource ist also der Abschluss selbst.
 */
@ResponseStatus(code = HttpStatus.NOT_FOUND, reason = "Fuer dieses Jahr gibt es keinen Abschluss.")
public final class JahrOhneDaten extends RuntimeException {}
