package org.mwolff.fbcrm.angebot.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.angebot.application.PipelineZeile;

/**
 * Eine Zeile der Pipeline, wie die Oberflaeche sie liest (Kriterium 23).
 *
 * <p>Die Felder stehen so im Plan, damit Parser und Record nicht getrennt entstehen. {@code
 * wahrscheinlichkeit == null} <b>ist</b> die Kennzeichnung „nicht eingeschaetzt" (F2) und wird
 * nicht zusaetzlich als eigenes Feld uebertragen — zwei Quellen fuer eine Aussage driften
 * auseinander.
 *
 * @param angebotId technische Id des Angebots
 * @param nummer Angebotsnummer; in der Pipeline steht nur Versendetes, und das traegt seine Nummer
 * @param vorgangId technische Id des Vorgangs — daran haengt der Sprung aus der Zeile
 * @param vorgangNummer fortlaufende Vorgangsnummer; als Zahl, das {@code #} setzt die Oberflaeche
 * @param vorgangTitel Titel des Vorgangs
 * @param firma Name der Firma des Vorgangs
 * @param summe die Netto-Summe des Angebots
 * @param wahrscheinlichkeit Abschlusswahrscheinlichkeit in Prozent, oder {@code null}
 * @param gewichteteSumme die gewichtete Summe, auf Cent gerundet
 * @param entscheidungErwartetAm erwarteter Entscheidungszeitpunkt, oder {@code null}
 */
public record PipelineZeileResponse(
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

  /** Die Sicht der Oberflaeche auf eine Zeile der Pipeline. */
  static PipelineZeileResponse of(final PipelineZeile zeile) {
    return new PipelineZeileResponse(
        zeile.angebotId(),
        zeile.nummer(),
        zeile.vorgangId(),
        zeile.vorgangNummer(),
        zeile.vorgangTitel(),
        zeile.firma(),
        zeile.summe(),
        zeile.wahrscheinlichkeit(),
        zeile.gewichteteSumme(),
        zeile.entscheidungErwartetAm());
  }
}
