package org.mwolff.fbcrm.auftrag.application;

import java.time.LocalDate;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.auftrag.domain.Auftragsstatus;

/**
 * Die vier Angaben, die sich an einem Auftrag pflegen lassen (Kriterium 7).
 *
 * <p><b>Ohne Positionen.</b> Sie stehen ab der Anlage fest (F3), und was sich nicht aendern darf,
 * taucht in der Signatur nicht auf — das ist die staerkere Zusage als eine Pruefung, die man
 * umgehen koennte.
 *
 * <p><b>Der Status steht hier neben den drei anderen</b> und nicht auf einem eigenen Weg (Plan E8):
 * Kriterium 7 nennt ihn in einem Atemzug mit ihnen und laesst ihn in jede Richtung frei setzen
 * (F6). Eigene Wege je Zielzustand waeren das Muster von „annehmen" und „ablehnen" am Angebot —
 * dort ist der Uebergang ein Lebenszyklusschritt mit eigenem Verb.
 *
 * @param auftragDatum Datum des Auftrags
 * @param kundenbestellnummer Bestellnummer des Kunden, oder {@code null}
 * @param leistungAb erster Tag des Leistungszeitraums, oder {@code null}
 * @param leistungBis letzter Tag des Leistungszeitraums, oder {@code null}
 * @param status der Status, in den der Auftrag gesetzt wird
 */
public record AuftragPflegedaten(
    LocalDate auftragDatum,
    @Nullable String kundenbestellnummer,
    @Nullable LocalDate leistungAb,
    @Nullable LocalDate leistungBis,
    Auftragsstatus status) {}
