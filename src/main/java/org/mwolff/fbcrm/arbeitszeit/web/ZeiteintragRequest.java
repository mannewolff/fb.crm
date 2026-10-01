package org.mwolff.fbcrm.arbeitszeit.web;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Die Eingaben des Dialogs — beim Erfassen und beim Aendern derselbe Rumpf (Issue #193, Kriterien 1
 * und 6).
 *
 * <p>Vier Angaben, und keine davon darf fehlen: Ohne Tag, Beginn, Ende und Position gibt es keinen
 * Eintrag. Die Kennungen als {@link Long} und nicht als {@code long}, damit ein fehlendes Feld als
 * Meldung am Feld erscheint und nicht stillschweigend zur Kennung 0 wird.
 *
 * <p><b>Kein Feld fuer die Dauer.</b> Sie wird aus Beginn und Ende gerechnet (Plan #194, A3); als
 * Feld hiesse es, dem Absender eine Aussage zu glauben, die die Anwendung selbst kennt.
 *
 * <p>Dass die Uhrzeiten auf einer Viertelstunde liegen, prueft hier <b>nichts</b>: Das ist eine
 * fachliche Regel, und ihre Meldung gehoert an dasjenige der beiden Felder, das sie verletzt (A19).
 *
 * @param angebotPositionId Kennung der Position, auf die gebucht wird
 * @param tag der Tag, an dem gearbeitet wurde
 * @param von Beginn
 * @param bis Ende
 */
public record ZeiteintragRequest(
    @NotNull Long angebotPositionId,
    @NotNull LocalDate tag,
    @NotNull LocalTime von,
    @NotNull LocalTime bis) {}
