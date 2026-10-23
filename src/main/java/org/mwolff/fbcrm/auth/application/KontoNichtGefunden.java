package org.mwolff.fbcrm.auth.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Zu der angefragten Kennung gibt es kein Konto.
 *
 * <p>404 und nicht 401 wie bei {@link UnknownAccount}: Hier fragt ein C-Level nach einem fremden
 * Konto, seine eigene Sitzung ist davon nicht beruehrt. Ein Konto wird nie geloescht, sondern
 * stillgelegt — eine unbekannte Kennung ist also erfunden oder veraltet.
 */
@ResponseStatus(code = HttpStatus.NOT_FOUND, reason = "Dieses Konto gibt es nicht.")
public final class KontoNichtGefunden extends RuntimeException {}
