package org.mwolff.fbcrm.angebot.application;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.common.Bearbeitungskonflikt;

/**
 * Die Anlage wurde inzwischen geloescht; ein zweites Loeschen wird abgewiesen (#411, Kriterium 6;
 * Plan #415, E7, E16).
 *
 * <p>Urheber und Zeitpunkt stammen aus dem Kennzeichen an der Zeile. Statt des frueheren 404
 * antwortet der Weg 409 mit {@code konflikt.art = "gelöscht"}, damit die Meldung beides nennen kann
 * (E16).
 */
public final class AngebotsanlageGeloescht extends Bearbeitungskonflikt {

  /**
   * Der Konflikt mit allem, was die Meldung nennt.
   *
   * @param urheberKonto wer die Anlage geloescht hat, oder {@code null}, wenn die Zeile es nicht
   *     weiss
   * @param handelndesKonto wer gerade abgewiesen wird
   * @param zeitpunkt wann die Anlage geloescht wurde
   */
  public AngebotsanlageGeloescht(
      final @Nullable Long urheberKonto, final long handelndesKonto, final Instant zeitpunkt) {
    super(Art.GELOESCHT, urheberKonto, handelndesKonto, zeitpunkt);
  }
}
