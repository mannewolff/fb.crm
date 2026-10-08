package org.mwolff.fbcrm.rechnung.web;

import java.time.YearMonth;
import org.jspecify.annotations.Nullable;

/**
 * Der Rumpf beim Anlegen eines Rechnungsentwurfs (Issue #193, Antwort 7; Plan #194, A9).
 *
 * <p>Ein einziges, optionales Feld — und der ganze Rumpf darf fehlen: Das Angebot steht im Pfad,
 * und ohne Monat entsteht der Entwurf wie bisher aus den offenen Mengen. Der Weg ohne Rumpf bleibt
 * damit gueltig, und ein weggelassenes Feld bedeutet dasselbe wie ein weggelassener Rumpf (dieselbe
 * Form wie {@code angebot.web.AngebotAnlegenRequest}).
 *
 * <p>Der Monat als {@link YearMonth} und nicht als Text: Was kein Monat ist, laesst sich nicht
 * wandeln und kommt als 400 zurueck ({@code GlobalExceptionHandler}) — eine eigene Pruefung dafuer
 * waere eine zweite Abschrift derselben Regel.
 *
 * @param monat der Monat als {@code JJJJ-MM}, dessen Arbeitszeit die Mengen vorbelegt, oder {@code
 *     null} fuer einen Entwurf ohne Arbeitszeit
 */
public record RechnungAnlegenRequest(@Nullable YearMonth monat) {}
