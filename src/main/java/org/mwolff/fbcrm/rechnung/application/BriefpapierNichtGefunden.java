package org.mwolff.fbcrm.rechnung.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Es ist kein Briefpapier hinterlegt, das sich entfernen liesse (Plan #363).
 *
 * <p>404 wie beim Original einer nachgetragenen Rechnung ({@link NachtragNichtGefunden}): Nicht der
 * Zustand ist das Hindernis, sondern die Sache fehlt.
 */
@ResponseStatus(code = HttpStatus.NOT_FOUND, reason = "Es ist kein Briefpapier hinterlegt.")
public final class BriefpapierNichtGefunden extends RuntimeException {}
