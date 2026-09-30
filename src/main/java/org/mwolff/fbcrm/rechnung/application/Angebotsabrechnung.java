package org.mwolff.fbcrm.rechnung.application;

import java.util.List;
import org.mwolff.fbcrm.rechnung.domain.Abrechnungsstand;

/**
 * Der Abrechnungsstand eines Angebots samt seinen Rechnungen (#160, Kriterium 26).
 *
 * <p>Zwei Haelften, eine Frage: „Was ist von diesem Angebot abgerechnet?" Der {@link
 * Abrechnungsstand} antwortet je Position — angeboten, abgerechnet, offen, Ueberschreitung —, die
 * Liste antwortet mit den Belegen, aus denen das entstanden ist.
 *
 * @param stand je Position des Angebots ein Stand, in der Reihenfolge des Angebots
 * @param rechnungen die Rechnungen dieses Angebots, neueste zuerst
 */
public record Angebotsabrechnung(Abrechnungsstand stand, List<RechnungMitBetrag> rechnungen) {

  /** Nimmt die Liste als Kopie: Der Aufrufer darf seine Liste danach weiterverwenden. */
  public Angebotsabrechnung {
    rechnungen = List.copyOf(rechnungen);
  }
}
