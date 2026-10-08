package org.mwolff.fbcrm.jahresabschluss.application;

import java.math.BigDecimal;

/**
 * Was ein Jahr eingebracht hat (#287, Kriterium 4): die gestellten Rechnungen mit Rechnungsdatum im
 * Jahr — gestellt, bezahlt und abgeschrieben, geschriebene wie nachgetragene —, gezaehlt nach
 * Rechnungsdatum und nicht nach Zahlungseingang.
 *
 * <p>Netto und Brutto sind die Summen der <b>je Rechnung</b> gerundeten Betraege (Plan #288, E5);
 * so treffen sie den Cent, den „Abgerechnet" auf der Startseite fuer dasselbe Jahr zeigt.
 *
 * @param netto die Summe netto, auf den Cent
 * @param brutto die Summe brutto, auf den Cent
 * @param umsatzsteuer die enthaltene Umsatzsteuer: Brutto minus Netto
 */
public record Einnahmen(BigDecimal netto, BigDecimal brutto, BigDecimal umsatzsteuer) {}
