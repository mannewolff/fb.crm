package org.mwolff.fbcrm.startseite.application;

import java.math.BigDecimal;
import java.util.List;
import org.mwolff.fbcrm.common.Geldrechnung;
import org.mwolff.fbcrm.rechnung.application.OffeneRechnung;

/**
 * Die Kennzahl „Offene Rechnungen": worauf noch Geld fehlt (Issue #285).
 *
 * <p><b>Der Stand von heute und kein Zeitraum</b>, wie „Angebote in Arbeit" und „Noch nicht
 * abgerechnet": Eine Rechnung ist offen oder nicht, und das Rechnungsdatum sagt darueber nichts.
 * Eine nach Zeitraum beschnittene Liste beantwortete eine andere Frage — „was wurde im Oktober
 * gestellt und ist noch offen" — und liesse gerade die aeltesten Posten verschwinden, auf die es
 * ankommt.
 *
 * <p><b>Die Anzahl steht als Feld daneben</b>, obwohl die Liste sie traegt: Die Kachel nennt sie im
 * Wort („1 Rechnung"), und die Ansicht soll dafuer keine zweite Wahrheit rechnen. Bei „Angebote in
 * Arbeit" ist es umgekehrt — dort gibt es keine Zahl neben der Liste (Plan #208, E20); hier steht
 * sie, weil die Kachel aus Betrag <b>und</b> Anzahl besteht.
 *
 * <p><b>Gerechnet wird nach {@link Geldrechnung}</b>: Die Summe entsteht aus den schon je Rechnung
 * auf den Cent gerundeten Betraegen — dieselbe Zahl in jeder Gruppierung, und darum trifft sie den
 * Cent der Rechnungsliste. Ohne eine offene Rechnung steht 0,00 da und nicht etwa nichts: Die
 * Kachel behaelt ihre Gestalt in der Reihe (Entscheidung am Issue).
 *
 * @param netto die Summe der Netto-Betraege aller offenen Rechnungen; 0,00 ohne eine solche
 * @param anzahl die Zahl dieser Rechnungen
 * @param rechnungen diese Rechnungen selbst, aelteste zuerst
 */
public record OffeneRechnungen(BigDecimal netto, int anzahl, List<OffeneRechnung> rechnungen) {

  /** Nimmt die Liste als Kopie: Der Aufrufer darf seine Liste danach weiterverwenden. */
  public OffeneRechnungen {
    rechnungen = List.copyOf(rechnungen);
  }

  /**
   * Die Kennzahl aus den offenen Rechnungen, wie die Rechnung sie liefert.
   *
   * @param offene die offenen Rechnungen beider Arten, aelteste zuerst; auch keine
   * @return ihre Summe netto, ihre Zahl und sie selbst in derselben Reihenfolge
   */
  static OffeneRechnungen aus(final List<OffeneRechnung> offene) {
    return new OffeneRechnungen(
        Geldrechnung.summe(offene.stream().map(OffeneRechnung::netto)), offene.size(), offene);
  }
}
