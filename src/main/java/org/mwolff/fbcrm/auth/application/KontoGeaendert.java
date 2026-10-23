package org.mwolff.fbcrm.auth.application;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.auth.domain.Account;
import org.mwolff.fbcrm.common.Bearbeitungskonflikt;

/**
 * Das Konto wurde seit dem Oeffnen verwaltet, und der Schritt beruht auf dem alten Stand (#411,
 * Kriterium 4; Plan #415, E19).
 *
 * <p>Gilt fuer das Aendern, das Stilllegen und das Wiederaktivieren gleichermassen. Ein Konto wird
 * nie geloescht, darum gibt es hier nur die Art „veraltet". Status und Abbildung nach 409 mit der
 * Erweiterung {@code konflikt} legt {@link Bearbeitungskonflikt} fest.
 *
 * <p><b>Der Zeitpunkt ist {@code updatedAt}.</b> Die Zeile kennt keinen eigenen Zeitpunkt der
 * Verwaltung; {@code updatedAt} schreibt auch das Abmelden fort. Nach einer Abmeldung der
 * betroffenen Person nennt die Meldung darum deren Zeitpunkt statt den der Verwaltung — der Name
 * des Urhebers stimmt in jedem Fall, denn ihn setzt allein die Verwaltung.
 */
public final class KontoGeaendert extends Bearbeitungskonflikt {

  /**
   * Der Konflikt mit allem, was die Meldung nennt.
   *
   * @param urheberKonto wer das Konto zuletzt verwaltet hat, oder {@code null}, wenn die Zeile es
   *     nicht weiss
   * @param handelndesKonto wer gerade abgewiesen wird
   * @param zeitpunkt wann die Zeile zuletzt geschrieben wurde
   */
  public KontoGeaendert(
      final @Nullable Long urheberKonto, final long handelndesKonto, final Instant zeitpunkt) {
    super(Art.VERALTET, urheberKonto, handelndesKonto, zeitpunkt);
  }

  /**
   * Weist einen Schritt ab, dessen vorgelegter Stand nicht der des Kontos ist.
   *
   * @param konto das Konto, wie es gerade gesperrt im Bestand steht
   * @param version der vorgelegte Stand aus {@code If-Match}
   * @param handelnder Kennung des handelnden Kontos
   * @throws KontoGeaendert wenn die beiden Staende voneinander abweichen
   */
  static void pruefe(final Account konto, final long version, final long handelnder) {
    Bearbeitungskonflikt.pruefe(
        version,
        konto.version(),
        () -> new KontoGeaendert(konto.geaendertVon(), handelnder, konto.updatedAt()));
  }
}
