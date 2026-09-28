package org.mwolff.fbcrm.auftrag.application;

import java.math.BigDecimal;
import java.util.stream.Stream;
import org.mwolff.fbcrm.auftrag.domain.Auftrag;
import org.mwolff.fbcrm.auftrag.domain.Auftragsstatus;
import org.mwolff.fbcrm.common.Geldrechnung;
import org.mwolff.fbcrm.vorgang.domain.Vorgang;

/**
 * Eine Zeile des Auftragsbestands: ein laufender Auftrag mit dem Vorgang, der ihn traegt (Kriterium
 * 12).
 *
 * <p><b>Der abgerechnete Betrag ist ein Parameter und wird hier nicht gerechnet</b> (Plan E12). Was
 * abgerechnet ist, weiss die Rechnung — und die gibt es noch nicht (Idee #7). Die Zeile traegt die
 * Spalte trotzdem, weil Kriterium 12 sie ausdruecklich verlangt und sie die Definition aus Kapitel
 * 07 der Spezifikation abbildet: Bis zur ersten Rechnung zeigen „Beauftragt" und „Noch offen"
 * denselben Betrag, danach sagt ihr Abstand, was abgerechnet ist.
 *
 * <p><b>Der offene Rest entsteht hier</b>, weil er zu einer Zeile gehoert und nicht zur Summe:
 * Auftragssumme abzueglich abgerechnetem Betrag, mit derselben Regel wie jeder andere Betrag (E5).
 * Addiert wird erst danach ({@link Auftragsbestand}).
 *
 * <p>Die Nummer steht ohne {@code null}: Ein Auftrag traegt sie ab dem Anlegen und hat keinen
 * Entwurf (F6).
 *
 * @param vorgangId technische Id des Vorgangs — daran haengt der Sprung aus der Zeile
 * @param vorgangNummer fortlaufende Vorgangsnummer; als Zahl, das {@code #} setzt die Oberflaeche
 * @param vorgangTitel Titel des Vorgangs
 * @param firma Name der Firma des Vorgangs
 * @param auftragId technische Id des Auftrags
 * @param nummer Auftragsnummer aus dem Nummernkreis
 * @param status der Status des Auftrags (Kriterium 7)
 * @param auftragssumme die Netto-Summe des Auftrags, gerechnet (E11)
 * @param abgerechnet der bereits abgerechnete Betrag; bis Idee #7 ueberall 0,00 €
 * @param offenerRest die Auftragssumme abzueglich des abgerechneten Betrags
 */
public record AuftragsbestandZeile(
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

  /**
   * Die Zeile zu einem laufenden Auftrag.
   *
   * @param auftrag der Auftrag samt seinen Positionen
   * @param vorgang der Vorgang, an dem er haengt
   * @param firma Name der Firma des Vorgangs
   * @param abgerechnet der bereits abgerechnete Betrag; er kommt von aussen (E12)
   */
  static AuftragsbestandZeile of(
      final Auftrag auftrag,
      final Vorgang vorgang,
      final String firma,
      final BigDecimal abgerechnet) {
    final BigDecimal summe = auftrag.summe();
    /* Auch der uebergebene Betrag laeuft durch die eine Regel: Er steht als Geld in der Zeile. */
    final BigDecimal betrag = Geldrechnung.summe(Stream.of(abgerechnet));
    return new AuftragsbestandZeile(
        vorgang.requireId(),
        vorgang.nummer(),
        vorgang.titel(),
        firma,
        auftrag.requireId(),
        auftrag.nummer(),
        auftrag.status(),
        summe,
        betrag,
        Geldrechnung.summe(Stream.of(summe, betrag.negate())));
  }
}
