package org.mwolff.fbcrm.angebot.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.vorgang.domain.Vorgang;

/**
 * Eine Zeile der Pipeline: ein offenes Angebot mit dem Vorgang, der es traegt (Kriterium 23).
 *
 * <p><b>Die Gewichtung steht hier</b>, weil sie zu einer Zeile gehoert und nicht zur Summe: Sie
 * rechnet {@code summe × wahrscheinlichkeit / 100} und rundet kaufmaennisch auf den Cent — genau
 * wie die Position ihren Betrag rundet (E5, E20). Addiert wird erst danach ({@link Pipeline}); die
 * Summe der Runden ist nicht die Rundung der Summe, und Kriterium 5 schliesst die zweite Lesart
 * aus.
 *
 * <p><b>Ein Vorgang ohne Einschaetzung zaehlt mit 50 %</b> (F2) — aber nur in der Rechnung: {@link
 * #wahrscheinlichkeit} bleibt {@code null}, und dieses {@code null} <b>ist</b> die Kennzeichnung
 * „nicht eingeschaetzt". Ein zweites Feld daneben waere eine zweite Quelle fuer eine Aussage, und
 * zwei Quellen driften auseinander.
 *
 * <p>Die Nummer steht ohne {@code null}: In der Pipeline steht nur, was versendet wurde, und ein
 * versendetes Angebot traegt seine Nummer (Kriterium 11).
 *
 * @param angebotId technische Id des Angebots
 * @param nummer Angebotsnummer aus dem Nummernkreis
 * @param vorgangId technische Id des Vorgangs
 * @param vorgangNummer fortlaufende Vorgangsnummer; als Zahl, das {@code #} setzt die Oberflaeche
 * @param vorgangTitel Titel des Vorgangs
 * @param firma Name der Firma des Vorgangs
 * @param summe die Netto-Summe des Angebots, gerechnet (E5)
 * @param wahrscheinlichkeit Abschlusswahrscheinlichkeit des Vorgangs in Prozent, oder {@code null}
 *     fuer „nicht eingeschaetzt"
 * @param gewichteteSumme die mit der Wahrscheinlichkeit gewichtete Summe, auf Cent gerundet
 * @param entscheidungErwartetAm Tag, an dem die Entscheidung erwartet wird, oder {@code null}
 */
public record PipelineZeile(
    long angebotId,
    String nummer,
    long vorgangId,
    long vorgangNummer,
    String vorgangTitel,
    String firma,
    BigDecimal summe,
    @Nullable Integer wahrscheinlichkeit,
    BigDecimal gewichteteSumme,
    @Nullable LocalDate entscheidungErwartetAm) {

  /** Womit ein Vorgang ohne Einschaetzung in die gewichtete Pipeline eingeht (F2). */
  static final int OHNE_EINSCHAETZUNG = 50;

  /** Der Nenner der Prozentrechnung. */
  private static final BigDecimal HUNDERT = BigDecimal.valueOf(100L);

  /**
   * Die Zeile zu einem offenen Angebot.
   *
   * @param angebot das offene Angebot samt seinen Positionen
   * @param vorgang der Vorgang, an dem es haengt
   * @param firma Name der Firma des Vorgangs
   */
  static PipelineZeile of(final Angebot angebot, final Vorgang vorgang, final String firma) {
    final BigDecimal summe = angebot.summe();
    final Integer wahrscheinlichkeit = vorgang.abschlusswahrscheinlichkeit();
    return new PipelineZeile(
        angebot.requireId(),
        Objects.requireNonNull(angebot.nummer()),
        vorgang.requireId(),
        vorgang.nummer(),
        vorgang.titel(),
        firma,
        summe,
        wahrscheinlichkeit,
        gewichtet(summe, wahrscheinlichkeit),
        vorgang.entscheidungErwartetAm());
  }

  /* Je Angebot gerundet, kaufmaennisch wie der Positionsbetrag — HALF_EVEN waere hier falsch (E5). */
  private static BigDecimal gewichtet(
      final BigDecimal summe, final @Nullable Integer wahrscheinlichkeit) {
    final int prozent = wahrscheinlichkeit == null ? OHNE_EINSCHAETZUNG : wahrscheinlichkeit;
    return summe.multiply(BigDecimal.valueOf(prozent)).divide(HUNDERT, 2, RoundingMode.HALF_UP);
  }
}
