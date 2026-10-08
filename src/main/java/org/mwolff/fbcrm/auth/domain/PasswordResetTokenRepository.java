package org.mwolff.fbcrm.auth.domain;

import java.util.Optional;

/**
 * Port auf den Bestand der Reset-Tokens; die Umsetzung liegt in {@code auth.infrastructure}.
 *
 * <p>Nachgeschlagen wird ausschliesslich ueber den <b>Hash</b>. Eine Suche ueber das Token selbst
 * gibt es nicht — sie koennte es nicht geben, weil das Token nirgends steht.
 */
public interface PasswordResetTokenRepository {

  /** Legt den Token an oder schreibt ihn fort und liefert ihn mit gesetzter Id zurueck. */
  PasswordResetToken save(PasswordResetToken token);

  /** Der Token zu einem SHA-256-Hash, oder leer. */
  Optional<PasswordResetToken> findByTokenHash(String tokenHash);
}
