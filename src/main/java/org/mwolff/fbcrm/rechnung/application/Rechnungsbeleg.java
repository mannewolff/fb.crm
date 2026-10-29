package org.mwolff.fbcrm.rechnung.application;

import java.math.BigDecimal;
import java.util.Objects;
import org.mwolff.fbcrm.common.Iban;
import org.mwolff.fbcrm.rechnung.application.ERechnungsdaten.Kaeufer;
import org.mwolff.fbcrm.rechnung.application.ERechnungsdaten.Position;
import org.mwolff.fbcrm.rechnung.application.ERechnungsdaten.Verkaeufer;
import org.mwolff.fbcrm.rechnung.application.RechnungDruckdaten.Druckposition;
import org.mwolff.fbcrm.rechnung.domain.Belegabsender;
import org.mwolff.fbcrm.rechnung.domain.Belegempfaenger;
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
 *
 * <p>Aus derselben Rechnung entstehen auch die {@link ERechnungsdaten} (Plan #461, E19): ein Record
 * je Ausgabe. Fuer sie kommen IBAN, Kontoinhaber und das Laenderkuerzel des Kunden dazu, die eine
 * E-Rechnung verlangt; dass sie vorliegen, prueft das Stellen, bevor es festschreibt.
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

  /**
   * Alles, was in der E-Rechnung dieser Rechnung steht (Plan #461, E19).
   *
   * <p>Die Faelligkeit ist das Rechnungsdatum plus das Zahlungsziel, die Zahlungsbedingung der
   * Satz, der gedruckt wird (E8).
   *
   * @param gestellt eine gestellte Rechnung, deren Kopien IBAN, Kontoinhaber und Laenderkuerzel
   *     tragen
   */
  static ERechnungsdaten erechnungsdaten(final Rechnung gestellt) {
    final BigDecimal satz = Objects.requireNonNull(gestellt.steuersatz());
    final int zahlungsziel = Objects.requireNonNull(gestellt.zahlungszielTage());
    return new ERechnungsdaten(
        Objects.requireNonNull(gestellt.nummer()),
        gestellt.rechnungDatum(),
        gestellt.leistungszeitraum(),
        verkaeufer(Objects.requireNonNull(gestellt.absender())),
        kaeufer(Objects.requireNonNull(gestellt.empfaenger())),
        gestellt.positionen().stream().map(Rechnungsbeleg::position).toList(),
        gestellt.netto(),
        satz,
        gestellt.steuer(satz),
        gestellt.brutto(satz),
        gestellt.rechnungDatum().plusDays(zahlungsziel),
        Belegschreibweisen.zahlungssatz(zahlungsziel));
  }

  private static Verkaeufer verkaeufer(final Belegabsender absender) {
    return new Verkaeufer(
        absender.name(),
        absender.anschrift(),
        absender.steuernummer(),
        absender.umsatzsteuerId(),
        new Iban(Objects.requireNonNull(absender.iban())),
        absender.bic(),
        Objects.requireNonNull(absender.kontoinhaber()));
  }

  private static Kaeufer kaeufer(final Belegempfaenger empfaenger) {
    return new Kaeufer(
        empfaenger.firma(),
        empfaenger.anschrift(),
        Objects.requireNonNull(empfaenger.landKuerzel()));
  }

  private static Position position(final Rechnungsposition position) {
    return new Position(
        position.bezeichnung(),
        position.menge(),
        position.einheit(),
        position.einzelpreis(),
        position.betrag());
  }
}
