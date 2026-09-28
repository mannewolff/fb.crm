package org.mwolff.fbcrm.auftrag.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Zu diesem Angebot gibt es schon einen Auftrag (F9).
 *
 * <p>Die Anwendung prueft es, und die Datenbank haelt es zu: {@code UNIQUE} auf {@code
 * auftrag.angebot_id}. Ohne die Pruefung antwortete ein zweiter Versuch mit einem 500 aus dem
 * Schluesselverstoss, und der verbrauchte dabei eine Auftragsnummer.
 *
 * <p>409, weil der Zustand nicht passt und nicht die Anfrage: Zwischen dem Laden der Ansicht und
 * dem Druck auf die Taste kann der Auftrag entstanden sein.
 */
@ResponseStatus(
    code = HttpStatus.CONFLICT,
    reason = "Zu diesem Angebot gibt es bereits einen Auftrag.")
public final class AuftragBereitsVorhanden extends RuntimeException {}
