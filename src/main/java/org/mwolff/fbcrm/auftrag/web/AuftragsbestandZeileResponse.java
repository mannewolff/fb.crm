package org.mwolff.fbcrm.auftrag.web;

import java.math.BigDecimal;
import org.mwolff.fbcrm.auftrag.application.AuftragsbestandZeile;
import org.mwolff.fbcrm.auftrag.domain.Auftragsstatus;

/**
 * Eine Zeile des Auftragsbestands, wie die Oberflaeche sie liest (Kriterium 12).
 *
 * <p>Geld geht als Dezimaltext mit zwei Nachkommastellen hinaus, wie in {@code
 * PipelineZeileResponse}. {@code abgerechnet} zeigt bis Idee #7 ueberall 0,00 € und steht trotzdem
 * im Rumpf: Kriterium 12 verlangt die Spalte, und ihr Abstand zur Auftragssumme ist ab der ersten
 * Rechnung die Aussage der Ansicht.
 *
 * @param vorgangId technische Id des Vorgangs — daran haengt der Sprung aus der Zeile
 * @param vorgangNummer fortlaufende Vorgangsnummer; als Zahl, das {@code #} setzt die Oberflaeche
 * @param vorgangTitel Titel des Vorgangs
 * @param firma Name der Firma des Vorgangs
 * @param auftragId technische Id des Auftrags
 * @param nummer Auftragsnummer; ein Auftrag traegt sie ab dem Anlegen
 * @param status der Status des Auftrags
 * @param auftragssumme die Netto-Summe des Auftrags
 * @param abgerechnet der bereits abgerechnete Betrag
 * @param offenerRest die Auftragssumme abzueglich des abgerechneten Betrags
 */
public record AuftragsbestandZeileResponse(
    long vorgangId,
    long vorgangNummer,
    String vorgangTitel,
    String firma,
    long auftragId,
    String nummer,
    Auftragsstatus status,
    BigDecimal auftragssumme,
    BigDecimal abgerechnet,
    BigDecimal offenerRest) {

  /** Die Sicht der Oberflaeche auf eine Zeile des Auftragsbestands. */
  static AuftragsbestandZeileResponse of(final AuftragsbestandZeile zeile) {
    return new AuftragsbestandZeileResponse(
        zeile.vorgangId(),
        zeile.vorgangNummer(),
        zeile.vorgangTitel(),
        zeile.firma(),
        zeile.auftragId(),
        zeile.nummer(),
        zeile.status(),
        zeile.auftragssumme(),
        zeile.abgerechnet(),
        zeile.offenerRest());
  }
}
