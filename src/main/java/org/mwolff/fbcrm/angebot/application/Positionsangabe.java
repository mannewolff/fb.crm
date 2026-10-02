package org.mwolff.fbcrm.angebot.application;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.angebot.domain.Angebotsposition;
import org.mwolff.fbcrm.common.Abrechnungsmodus;
import org.mwolff.fbcrm.common.Einheit;
import org.mwolff.fbcrm.common.Identifiable;

/**
 * Eine Position, wie sie eingereicht wird — vier Angaben duerfen dabei fehlen (Issue #227, E7).
 *
 * <p><b>Warum es diesen Zwischentyp gibt.</b> Ob Menge, Einheit, Preis und Abrechnungsart Pflicht
 * sind, haengt an der Art des Angebots: Ein Angebot an einen Kunden braucht alle vier, die interne
 * Arbeit keine davon (Kriterium 3 von #207). Das ist keine Aussage ueber ein einzelnes Feld,
 * sondern eine ueber das Nachbarfeld {@code intern} — und ein Nachbarfeld sieht die Bean-Validation
 * nicht. Die Pflicht entscheidet darum {@link AngebotAendernUseCase} nach der Zielart, und bis
 * dahin reisen die vier Angaben hier, wo sie fehlen <em>duerfen</em>.
 *
 * <p><b>In {@link Angebotsposition} duerfen sie es nicht.</b> Dort rechnen {@code betrag()}, {@code
 * Angebot.summe()}, {@code Positionsstand} und jeder Rechnungsentwurf mit ihnen; nullable Spalten
 * waeren vier neue Fehlerpfade in jedem Betrag (E8). Der Anwendungsfall macht aus jeder Angabe
 * darum eine vollstaendige Position — nach der Zielart und nach dem Zusammenfuehren mit der
 * gespeicherten Position.
 *
 * <p>Sie ist {@link Identifiable}, weil die Pruefung der Bindung die Kennung als Zahl braucht —
 * dieselbe Ueberlegung wie bei {@link Angebotsposition}.
 *
 * @param id Kennung der Position, die fortgeschrieben werden soll, oder {@code null} fuer eine neue
 * @param bezeichnung die Leistung; nie leer, das prueft der Eingang der Maske
 * @param abrechnungsmodus nach Aufwand oder zum Festpreis, oder {@code null}
 * @param menge Menge in der angegebenen Einheit, oder {@code null}
 * @param einheit Einheit der Menge, oder {@code null}
 * @param einzelpreis Netto-Preis je Einheit, oder {@code null}
 */
public record Positionsangabe(
    @Nullable Long id,
    String bezeichnung,
    @Nullable Abrechnungsmodus abrechnungsmodus,
    @Nullable BigDecimal menge,
    @Nullable Einheit einheit,
    @Nullable BigDecimal einzelpreis)
    implements Identifiable {}
