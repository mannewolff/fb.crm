package org.mwolff.fbcrm.jahresabschluss.application;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/**
 * Netto und Umsatzsteuer der Rechnungen eines Jahres zu einem Steuersatz (#287, Kriterium 6; Plan
 * #288, E13).
 *
 * @param satz der Steuersatz in Prozent mit zwei Nachkommastellen; {@code null} fuer die
 *     nachgetragenen Rechnungen — „Steuersatz nicht erfasst"
 * @param netto die Summe netto der Rechnungen zu diesem Satz, auf den Cent
 * @param umsatzsteuer ihre Summe brutto minus ihre Summe netto
 */
public record Steuerzeile(@Nullable BigDecimal satz, BigDecimal netto, BigDecimal umsatzsteuer) {}
