package org.mwolff.fbcrm.rechnung.application;

import java.util.List;

/**
 * Der Abrechnungsstand eines Angebots samt seinen Rechnungen (#160, Kriterium 26; Issue #193,
 * Kriterien 7, 8, 11).
 *
 * <p>Zwei Haelften, eine Frage: „Was ist von diesem Angebot abgerechnet?" Die erste Liste antwortet
 * je Position — angeboten, abgerechnet, offen, Ueberschreitung, dazu angefallen aus der
 * Zeiterfassung —, die zweite antwortet mit den Belegen, aus denen das entstanden ist.
 *
 * <p>Je Position <b>eine</b> Zeile und keine zweite Liste neben der ersten: Alles, was die Ansicht
 * zu einer Position zeigt, steht in ihrer {@link Positionsabrechnung}. Eine Abbildung von der
 * Kennung auf die Arbeitszeit daneben waere derselbe Inhalt, nur nachzuschlagen — und der Leser
 * muesste eine Position behandeln, die darin fehlt.
 *
 * @param positionen je Position des Angebots eine Zeile, in der Reihenfolge des Angebots
 * @param rechnungen die Rechnungen dieses Angebots, neueste zuerst
 */
public record Angebotsabrechnung(
    List<Positionsabrechnung> positionen, List<RechnungMitBetrag> rechnungen) {

  /** Nimmt die Listen als Kopie: Der Aufrufer darf seine Listen danach weiterverwenden. */
  public Angebotsabrechnung {
    positionen = List.copyOf(positionen);
    rechnungen = List.copyOf(rechnungen);
  }
}
