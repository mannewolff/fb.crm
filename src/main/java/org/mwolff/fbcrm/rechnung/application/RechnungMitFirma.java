package org.mwolff.fbcrm.rechnung.application;

import java.math.BigDecimal;
import org.mwolff.fbcrm.rechnung.domain.Rechnung;

/**
 * Eine Zeile der Liste „Rechnungen": die Rechnung samt Firma und Bruttobetrag (#160, Kriterium 1).
 *
 * <p>Nummer, Datum und Zustand bringt die Rechnung selbst mit. Der Bruttobetrag steht daneben, weil
 * er nicht an der Rechnung haengt: Ein Entwurf hat noch keinen Steuersatz und rechnet mit dem der
 * aktuellen Einstellungen, eine gestellte Rechnung mit ihrem eigenen (Kriterium 14).
 *
 * @param rechnung die Rechnung
 * @param firmaName Name der Firma, an die die Rechnung geht
 * @param brutto der Bruttobetrag mit dem Steuersatz, der fuer diese Rechnung gilt
 */
public record RechnungMitFirma(Rechnung rechnung, String firmaName, BigDecimal brutto) {}
