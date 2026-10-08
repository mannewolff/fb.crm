package org.mwolff.fbcrm.auth.infrastructure;

import org.mwolff.fbcrm.auth.domain.PasswordHasher;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Passwort-Hashing mit Argon2id.
 *
 * <p>Die Parameter kommen aus {@link Argon2PasswordEncoder#defaultsForSpringSecurity_v5_8()} und
 * werden bewusst nicht selbst gewaehlt (CLAUDE-security.md); jeder Hash traegt sein eigenes Salt,
 * derselbe Klartext ergibt deshalb nie zweimal denselben Hash.
 */
@Component
public class Argon2PasswordHasher implements PasswordHasher {

  private final PasswordEncoder encoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();

  @Override
  public String hash(final String rawPassword) {
    return encoder.encode(rawPassword);
  }

  @Override
  public boolean matches(final String rawPassword, final String passwordHash) {
    return encoder.matches(rawPassword, passwordHash);
  }
}
