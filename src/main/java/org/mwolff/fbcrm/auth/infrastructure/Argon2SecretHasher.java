package org.mwolff.fbcrm.auth.infrastructure;

import org.mwolff.fbcrm.auth.domain.SecretHasher;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Hashing eines Geheimnisses (Einmalcode, bis zur Umstellung noch Passwort) mit Argon2id.
 *
 * <p>Die Parameter kommen aus {@link Argon2PasswordEncoder#defaultsForSpringSecurity_v5_8()} und
 * werden bewusst nicht selbst gewaehlt (CLAUDE-security.md); jeder Hash traegt sein eigenes Salt,
 * derselbe Klartext ergibt deshalb nie zweimal denselben Hash.
 */
@Component
public class Argon2SecretHasher implements SecretHasher {

  private final PasswordEncoder encoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();

  @Override
  public String hash(final String rawSecret) {
    return encoder.encode(rawSecret);
  }

  @Override
  public boolean matches(final String rawSecret, final String secretHash) {
    return encoder.matches(rawSecret, secretHash);
  }
}
