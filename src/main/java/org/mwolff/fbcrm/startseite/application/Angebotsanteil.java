package org.mwolff.fbcrm.startseite.application;

import java.math.BigDecimal;
import org.mwolff.fbcrm.angebot.application.AngebotMitFirma;

/**
 * Was ein einzelnes Angebot zur Kennzahl „Noch nicht abgerechnet" beitraegt (#206, Kriterium 5).
 *
 * <p>Die Zeile unter dem Betrag: Firma, Angebot und der Anteil in Euro, netto. Das Angebot kommt
 * vollstaendig mit — die Ansicht braucht daraus den Firmennamen, das Datum und die Kennung fuer den
 * Weg dorthin, und ein eigener Ausschnitt waere eine zweite Form derselben Zeile.
 *
 * <p>Nur Angebote mit einem Anteil ueber 0,00 € stehen in der Liste (Plan #208, E11): Kriterium 5
 * sagt „je beitragendem Angebot", und das Beispiel aus #193 — 20 Stunden angeboten, 22 erfasst, 20
 * abgerechnet — ist genau der Fall, der nichts beitraegt.
 *
 * @param angebot das Angebot samt dem Namen seiner Firma
 * @param betrag sein Anteil am Betrag, netto und auf den Cent
 */
public record Angebotsanteil(AngebotMitFirma angebot, BigDecimal betrag) {}
