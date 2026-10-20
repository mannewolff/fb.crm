package org.mwolff.fbcrm.auth.domain;

import java.util.Optional;

/**
 * Port auf den Bestand der Einmalcodes; die Umsetzung liegt in {@code auth.infrastructure}.
 *
 * <p>Nachgeschlagen wird ausschliesslich ueber die <b>Adresse</b>, ohne Ruecksicht auf Gross- und
 * Kleinschreibung. Eine Suche ueber den Hash gibt es nicht: Argon2id salzt jeden Hash, derselbe
 * Code ergibt also jedes Mal einen anderen (E2).
 */
public interface LoginCodeRepository {

  /** Der Code zu einer Adresse, oder leer. */
  Optional<LoginCode> findByEmail(String email);

  /** Legt den Code an oder schreibt ihn fort und liefert ihn mit gesetzter Id zurueck. */
  LoginCode save(LoginCode code);

  /** Entfernt den Code zu einer Adresse; gibt es keinen, geschieht nichts. */
  void deleteByEmail(String email);
}
