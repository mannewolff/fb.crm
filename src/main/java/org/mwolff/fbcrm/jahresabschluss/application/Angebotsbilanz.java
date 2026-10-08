package org.mwolff.fbcrm.jahresabschluss.application;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/**
 * Die Angebote eines Jahres (#287, Kriterien 7, 8 und 12; Plan #288, E7, E8): wie viele abgegeben
 * sind, wie viele davon angenommen und wie viele heute noch offen, und was sie netto wert sind.
 *
 * <p>Ein Angebot gehoert zum Jahr seines Angebotsdatums; angenommen und offen sagt sein heutiger
 * Status. Interne Angebote zaehlen nirgends — sie stehen in keiner der Statusmengen aus {@link
 * Angebotsblick}.
 *
 * @param abgegeben die Zahl der abgegebenen Angebote des Jahres
 * @param angenommen die Zahl derer, die heute bestellt, erledigt oder abgerechnet sind
 * @param offen die Zahl derer, die heute noch abgegeben sind
 * @param annahmequote angenommene durch abgegebene in Prozent, eine Nachkommastelle; {@code null}
 *     ohne abgegebenes Angebot (Kriterium 11)
 * @param volumenAbgegeben die Summe netto der abgegebenen Angebote, auf den Cent
 * @param volumenAngenommen die Summe netto der angenommenen Angebote, auf den Cent
 */
public record Angebotsbilanz(
    int abgegeben,
    int angenommen,
    int offen,
    @Nullable BigDecimal annahmequote,
    BigDecimal volumenAbgegeben,
    BigDecimal volumenAngenommen) {}
