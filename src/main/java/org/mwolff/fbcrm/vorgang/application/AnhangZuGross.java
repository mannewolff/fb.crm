package org.mwolff.fbcrm.vorgang.application;

import org.mwolff.fbcrm.common.Uploadgrenze;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Die hochgeladene Datei ueberschreitet die Grenze aus {@link Uploadgrenze} (Kriterium 18, E10).
 *
 * <p>Der dritte der drei Riegel und der letzte vor dem Objektspeicher: Die Maske prueft vor dem
 * Absenden, die Bean Validation der Anfrage meldet am Feld, und diese Ausnahme haelt den
 * Anwendungsfall auch dann an, wenn er an beiden vorbei aufgerufen wird. Sie wirft, <b>bevor</b>
 * abgelegt wird — eine abgewiesene Datei soll keine Waise hinterlassen.
 */
@ResponseStatus(code = HttpStatus.BAD_REQUEST, reason = Uploadgrenze.MELDUNG)
public final class AnhangZuGross extends RuntimeException {}
