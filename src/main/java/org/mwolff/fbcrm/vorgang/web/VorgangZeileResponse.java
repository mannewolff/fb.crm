package org.mwolff.fbcrm.vorgang.web;

import java.time.Instant;
import org.mwolff.fbcrm.vorgang.application.VorgangZeile;
import org.mwolff.fbcrm.vorgang.domain.Phase;

/**
 * Eine Zeile der Vorgangsliste, so wie Uebersicht und Firmenansicht sie zeigen (Kriterien 2, 12).
 *
 * <p>Die Nummer geht als <b>Zahl</b> hinaus; das {@code #} setzt die Oberflaeche. So bleibt sie
 * sortier- und vergleichbar, und die Schreibweise steht an einer Stelle.
 *
 * @param id technische Id des Vorgangs
 * @param nummer fortlaufende Vorgangsnummer
 * @param titel Titel des Vorgangs
 * @param firma Name der zugeordneten Firma
 * @param phase abgeleitete Phase (E4)
 * @param abgeschlossen {@code true}, solange der Vorgang abgeschlossen ist
 * @param letzteAktivitaet Zeitpunkt des juengsten Eintrags, sonst der des Anlegens (Kriterium 2)
 */
public record VorgangZeileResponse(
    long id,
    long nummer,
    String titel,
    String firma,
    Phase phase,
    boolean abgeschlossen,
    Instant letzteAktivitaet) {

  /** Die Sicht der Oberflaeche auf eine Zeile der Liste. */
  static VorgangZeileResponse of(final VorgangZeile zeile) {
    return new VorgangZeileResponse(
        zeile.id(),
        zeile.nummer(),
        zeile.titel(),
        zeile.firmaName(),
        zeile.phase(),
        zeile.abgeschlossen(),
        zeile.letzteAktivitaet());
  }
}
