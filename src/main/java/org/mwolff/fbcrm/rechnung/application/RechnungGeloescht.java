package org.mwolff.fbcrm.rechnung.application;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.common.Bearbeitungskonflikt;

/**
 * Der Rechnungsentwurf wurde inzwischen geloescht; jede Aktion darauf wird abgewiesen, und es
 * entsteht nichts neu (#411, Kriterium 6; Plan #415, E16, E17).
 *
 * <p>Urheber und Zeitpunkt stammen aus dem Kennzeichen an der Zeile. Statt 404 antwortet der Weg
 * 409 mit {@code konflikt.art = "gelöscht"}, damit die Meldung beides nennen kann (E16).
 */
public final class RechnungGeloescht extends Bearbeitungskonflikt {

  /**
   * Der Konflikt mit allem, was die Meldung nennt.
   *
   * @param urheberKonto wer die Rechnung geloescht hat, oder {@code null}, wenn die Zeile es nicht
   *     weiss
   * @param handelndesKonto wer gerade abgewiesen wird
   * @param zeitpunkt wann die Rechnung geloescht wurde
   */
  public RechnungGeloescht(
      final @Nullable Long urheberKonto, final long handelndesKonto, final Instant zeitpunkt) {
    super(Art.GELOESCHT, urheberKonto, handelndesKonto, zeitpunkt);
  }
}
