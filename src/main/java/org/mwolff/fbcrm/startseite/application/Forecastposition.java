package org.mwolff.fbcrm.startseite.application;

import java.math.BigDecimal;
import org.mwolff.fbcrm.angebot.application.AngebotMitFirma;
import org.mwolff.fbcrm.angebot.domain.Angebotsposition;

/**
 * Eine Zeile unter einem Jahr des Forecasts: eine noch nicht vollstaendig abgerechnete Position
 * (#302, Kriterium 5).
 *
 * <p>Firma, Angebot, Position und Restbetrag. Angebot und Position kommen vollstaendig mit, nach
 * dem Muster von {@link Angebotsanteil}: Die Ansicht braucht daraus den Firmennamen, die
 * Bezeichnung und die Kennung fuer den Weg zum Angebot, und ein eigener Ausschnitt waere eine
 * zweite Form derselben Zeile.
 *
 * @param angebot das Angebot samt dem Namen seiner Firma
 * @param position die Position, deren Rest hier steht
 * @param netto ihr Restbetrag: offene Menge mal Einzelpreis, netto und auf den Cent
 */
public record Forecastposition(
    AngebotMitFirma angebot, Angebotsposition position, BigDecimal netto) {}
