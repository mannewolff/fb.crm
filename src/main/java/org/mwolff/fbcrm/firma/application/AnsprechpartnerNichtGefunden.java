package org.mwolff.fbcrm.firma.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Unter der angefragten Firma gibt es diesen Ansprechpartner nicht.
 *
 * <p>Das gilt auch dann, wenn es die Kennung gibt, sie aber zu einer anderen Firma gehoert: Jeder
 * Weg zum Ansprechpartner liegt unter {@code /api/firmen/{firmaId}/ansprechpartner}, und unter
 * dieser Firma ist er dann schlicht nicht da (Kriterium 12, E7).
 *
 * <p>404 und nicht 403: Ein Unterschied zwischen „gibt es nicht" und „gehoert einem anderen" waere
 * eine Auskunft ueber fremden Bestand, die niemand braucht.
 */
@ResponseStatus(code = HttpStatus.NOT_FOUND, reason = "Diesen Ansprechpartner gibt es nicht.")
public final class AnsprechpartnerNichtGefunden extends RuntimeException {}
