package org.mwolff.fbcrm.vorgang.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Unter der angefragten Adresse gibt es keinen Eintrag.
 *
 * <p>Dieselbe Antwort fuer drei Lagen: Den Eintrag gibt es nicht, es gibt ihn, aber er gehoert zu
 * einem anderen Vorgang, oder den Vorgang im Pfad gibt es nicht. Ein Eintrag wird nie geloescht,
 * und adressiert ist er ueber seinen Vorgang — unter {@code
 * /api/vorgaenge/{id}/eintraege/{eintragId}} ist er in allen drei Faellen schlicht nicht da.
 * Dieselbe Abwaegung wie bei {@link VorgangNichtGefunden}.
 */
@ResponseStatus(code = HttpStatus.NOT_FOUND, reason = "Diesen Eintrag gibt es nicht.")
public final class EintragNichtGefunden extends RuntimeException {}
