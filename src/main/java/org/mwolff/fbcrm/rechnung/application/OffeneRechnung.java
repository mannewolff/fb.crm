package org.mwolff.fbcrm.rechnung.application;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Eine Rechnung, auf die noch Geld fehlt (Issue #285).
 *
 * <p>„Offen" heisst {@link org.mwolff.fbcrm.rechnung.domain.Rechnungszustand#istOffen()} — gestellt
 * und noch nicht bezahlt, fuer die von fb.crm geschriebene Rechnung wie fuer die nachgetragene
 * gleich. Eine bezahlte, eine abgeschriebene und ein Entwurf stehen hier nicht: Die ersten beiden
 * sind ausgegangen, der dritte ist noch gar nicht draussen.
 *
 * <p><b>Die Zeile traegt ihre Art</b>, wie {@link Rechnungslistenzeile}: Erst Art und Kennung
 * zusammen bezeichnen eine Rechnung, denn die beiden Arten haben zwei Kennungsraeume — und die
 * Oberflaeche waehlt daran den Weg (Plan #259, E18).
 *
 * <p><b>Netto und nicht Brutto</b> (Entscheidung am Issue): Die Startseite rechnet durchweg netto,
 * und darum ist {@link Rechnungslistenzeile} hier nicht wiederverwendbar — sie traegt nur Brutto.
 * Mehr als diese sechs Angaben braucht die Startseite nicht; den Zustand traegt die Zeile nicht,
 * denn jede Zeile dieser Liste hat denselben.
 *
 * <p>Die Nummer steht ohne {@code null} da: Eine gestellte Rechnung traegt sie, und eine
 * nachgetragene wird nur mit ihr erfasst.
 *
 * @param nachgetragen ob die Rechnung nachgetragen und nicht von fb.crm geschrieben ist
 * @param id Kennung der Rechnung im Raum ihrer Art
 * @param nummer die Rechnungsnummer
 * @param firmaName der heutige Name der Firma, an die die Rechnung geht
 * @param rechnungDatum Datum der Rechnung
 * @param netto der Nettobetrag
 */
public record OffeneRechnung(
    boolean nachgetragen,
    long id,
    String nummer,
    String firmaName,
    LocalDate rechnungDatum,
    BigDecimal netto) {}
