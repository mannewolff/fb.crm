package org.mwolff.fbcrm.rechnung.application;

import java.math.BigDecimal;
import java.util.Objects;
import org.mwolff.fbcrm.rechnung.application.RechnungDruckdaten.Druckposition;
import org.mwolff.fbcrm.rechnung.domain.Rechnung;
import org.mwolff.fbcrm.rechnung.domain.Rechnungsposition;

/**
 * Macht aus einer gestellten Rechnung das, was auf ihrem Beleg steht (Plan #169, E7).
 *
 * <p>Die Uebersetzung steht fuer sich und nicht im Anwendungsfall: Sie ist eine reine Rechnung ohne
 * Bestand und ohne Uhr, und genau so ist sie auch pruefbar. {@link RechnungDruckdaten} bleibt
 * dafuer, was es ist — eine Beschreibung des Dokuments, die weder eine Rechnung noch eine Uhr
 * kennt.
 *
 * <p><b>Jede Angabe kommt aus der festgeschriebenen Rechnung</b> und keine aus den Einstellungen
 * von jetzt: Steuersatz, Zahlungsziel und die beiden Kopien von Empfaenger und Absender stehen ab
 * dem Stellen an ihr (#160, Kriterium 14). Netto, Steuer und Brutto rechnet die Rechnung selbst
 * nach der einen Regel in {@code Geldrechnung}; sie gehen als fertige Zahlen hinein, damit auf dem
 * Beleg die Zahl steht, mit der gerechnet wurde.
 *
 * <p>Die {@code requireNonNull} sind kein Misstrauen gegen den Bestand, sondern die Stelle, an der
 * aus „kann fehlen" ein „steht fest" wird: Eine gestellte Rechnung traegt Nummer, Satz, Ziel und
 * beide Kopien — der CHECK {@code rechnung_gestellt} in {@code V18__rechnung.sql} laesst nichts
 * anderes zu —, aber die Felder sind an der Rechnung als Ganzes {@code @Nullable}, weil ein Entwurf
 * sie noch nicht hat.
 */
final class Rechnungsbeleg {

  private Rechnungsbeleg() {}

  /**
   * Alles, was auf dem Dokument dieser Rechnung steht.
   *
   * @param gestellt eine gestellte Rechnung
   */
  static RechnungDruckdaten druckdaten(final Rechnung gestellt) {
    final BigDecimal satz = Objects.requireNonNull(gestellt.steuersatz());
    return new RechnungDruckdaten(
        Objects.requireNonNull(gestellt.absender()),
        Objects.requireNonNull(gestellt.empfaenger()),
        Objects.requireNonNull(gestellt.nummer()),
        gestellt.rechnungDatum(),
        gestellt.leistungszeitraum(),
        gestellt.positionen().stream().map(Rechnungsbeleg::zeile).toList(),
        gestellt.netto(),
        satz,
        gestellt.steuer(satz),
        gestellt.brutto(satz),
        Objects.requireNonNull(gestellt.zahlungszielTage()));
  }

  private static Druckposition zeile(final Rechnungsposition position) {
    return new Druckposition(
        position.menge(),
        position.einheit(),
        position.bezeichnung(),
        position.einzelpreis(),
        position.betrag());
  }
}
