package org.mwolff.fbcrm.rechnung.application;

import java.math.BigDecimal;
import org.mwolff.fbcrm.rechnung.domain.Rechnung;

/**
 * Eine Rechnung in der Liste am Angebot: die Rechnung samt ihrem Bruttobetrag (#160, Kriterium 26).
 *
 * <p>Nummer, Datum und Zustand bringt die Rechnung selbst mit. Der Bruttobetrag steht daneben, weil
 * er nicht an der Rechnung haengt, sondern am Steuersatz, der fuer sie gilt ({@link
 * GeltenderSteuersatz}).
 *
 * <p>Ohne Firmennamen, anders als {@link Rechnungslistenzeile}: Am Angebot ist die Firma bereits
 * bekannt, und sie je Zeile zu wiederholen hiesse, dieselbe Angabe mehrfach zu nennen.
 *
 * @param rechnung die Rechnung
 * @param brutto der Bruttobetrag mit dem Steuersatz, der fuer diese Rechnung gilt
 */
public record RechnungMitBetrag(Rechnung rechnung, BigDecimal brutto) {}
