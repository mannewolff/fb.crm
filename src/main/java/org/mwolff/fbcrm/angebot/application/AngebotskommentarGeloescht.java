package org.mwolff.fbcrm.angebot.application;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.common.Bearbeitungskonflikt;

/**
 * Der Kommentar wurde inzwischen geloescht; jede Aktion darauf wird abgewiesen, und es entsteht
 * nichts neu (#411, Kriterium 6; Plan #415, E16, E17).
 *
 * <p>Urheber und Zeitpunkt stammen aus dem Kennzeichen an der Zeile. Statt 404 antwortet der Weg
 * 409 mit {@code konflikt.art = "gelöscht"}, damit die Meldung beides nennen kann (E16).
 */
public final class AngebotskommentarGeloescht extends Bearbeitungskonflikt {

  /**
   * Der Konflikt mit allem, was die Meldung nennt.
   *
   * @param urheberKonto wer den Kommentar geloescht hat, oder {@code null}, wenn die Zeile es nicht
   *     weiss
   * @param handelndesKonto wer gerade abgewiesen wird
   * @param zeitpunkt wann der Kommentar geloescht wurde
   */
  public AngebotskommentarGeloescht(
      final @Nullable Long urheberKonto, final long handelndesKonto, final Instant zeitpunkt) {
    super(Art.GELOESCHT, urheberKonto, handelndesKonto, zeitpunkt);
  }
}
