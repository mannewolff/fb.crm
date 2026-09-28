package org.mwolff.fbcrm.auftrag.application;

import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Die Eingaben beim Anlegen eines Auftrags (Kriterien 3, 4; F2).
 *
 * <p><b>Ohne Vorgang und ohne Angebot.</b> Beide stehen im Pfad beziehungsweise ergeben sich aus
 * ihm: Der Auftrag entsteht an einem Angebot, und an welchem Vorgang er haengt, weiss das Angebot.
 *
 * <p><b>Ohne Status.</b> Ein frischer Auftrag ist {@code OFFEN} (Kriterium 6); ein Feld dafuer
 * waere eine Wahl, die es beim Anlegen nicht gibt.
 *
 * @param auftragDatum Datum des Auftrags, oder {@code null} — dann setzt der Anwendungsfall den
 *     heutigen Tag in {@code common.Geschaeftszone}, nicht den des Browsers
 * @param kundenbestellnummer Bestellnummer des Kunden, oder {@code null}
 * @param leistungAb erster Tag des Leistungszeitraums, oder {@code null}
 * @param leistungBis letzter Tag des Leistungszeitraums, oder {@code null}
 * @param positionen die uebernommenen Positionen in der gewuenschten Reihenfolge
 */
public record AuftragDaten(
    @Nullable LocalDate auftragDatum,
    @Nullable String kundenbestellnummer,
    @Nullable LocalDate leistungAb,
    @Nullable LocalDate leistungBis,
    List<AuftragPositionwahl> positionen) {

  /** Nimmt die Positionen als Kopie: Der Aufrufer darf seine Liste danach weiterverwenden. */
  public AuftragDaten {
    positionen = List.copyOf(positionen);
  }
}
