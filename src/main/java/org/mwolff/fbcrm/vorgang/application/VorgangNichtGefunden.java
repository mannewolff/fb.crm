package org.mwolff.fbcrm.vorgang.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Zu der angefragten Kennung gibt es keinen Vorgang.
 *
 * <p>404 und nicht 403: Ein Vorgang wird nie geloescht — er wird hoechstens abgeschlossen (E5) —,
 * also ist eine unbekannte Kennung entweder erfunden oder veraltet. In beiden Faellen ist die
 * Ressource schlicht nicht da; dieselbe Abwaegung wie bei {@code FirmaNichtGefunden}.
 */
@ResponseStatus(code = HttpStatus.NOT_FOUND, reason = "Diesen Vorgang gibt es nicht.")
public final class VorgangNichtGefunden extends RuntimeException {}
