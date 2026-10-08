package org.mwolff.fbcrm.arbeitszeit.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Zu der angefragten Kennung gibt es keinen Zeiteintrag.
 *
 * <p>404, weil die Kennung im Pfad steht: Angefragt ist dieser eine Eintrag, und den gibt es nicht
 * — weil er nie existierte oder weil er geloescht wurde (Plan #194, A5). Ein Unterschied zwischen
 * beidem waere hier ohne Wert, und Rechte je Eintrag gibt es nicht (CLAUDE.md, „Betriebsform").
 */
@ResponseStatus(code = HttpStatus.NOT_FOUND, reason = "Diesen Zeiteintrag gibt es nicht.")
public final class ZeiteintragNichtGefunden extends RuntimeException {}
