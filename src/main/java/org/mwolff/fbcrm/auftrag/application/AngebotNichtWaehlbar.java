package org.mwolff.fbcrm.auftrag.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Das Angebot ist nicht angenommen und steht darum als Quelle eines Auftrags nicht zur Wahl
 * (Kriterium 1).
 *
 * <p>Der Name folgt dem Bestand: {@code vorgang.application.FirmaNichtWaehlbar} und {@code
 * AnsprechpartnerNichtWaehlbar} stehen fuer genau diese Lage — ein Gegenstand, der als Quelle nicht
 * in Frage kommt.
 *
 * <p>409 und nicht 403: Die Anfrage ist in Ordnung, nur der Zustand des Angebots passt nicht — und
 * er kann sich hinter dem Ruecken des Anwenders geaendert haben, etwa weil ein spaeteres Angebot
 * dieses abgeloest hat.
 */
@ResponseStatus(
    code = HttpStatus.CONFLICT,
    reason = "Nur aus einem angenommenen Angebot entsteht ein Auftrag.")
public final class AngebotNichtWaehlbar extends RuntimeException {}
