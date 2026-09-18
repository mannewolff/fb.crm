package org.mwolff.fbcrm.auth.infrastructure;

import java.util.Optional;
import org.mwolff.fbcrm.auth.domain.PasswordResetToken;
import org.mwolff.fbcrm.auth.domain.PasswordResetTokenRepository;
import org.springframework.stereotype.Repository;

/**
 * Setzt den Port {@link PasswordResetTokenRepository} auf JPA um und uebersetzt in beide
 * Richtungen.
 */
@Repository
class JpaPasswordResetTokenRepository implements PasswordResetTokenRepository {

  private final SpringDataPasswordResetTokenRepository jpa;

  JpaPasswordResetTokenRepository(final SpringDataPasswordResetTokenRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public PasswordResetToken save(final PasswordResetToken token) {
    return toDomain(jpa.save(toEntity(token)));
  }

  @Override
  public Optional<PasswordResetToken> findByTokenHash(final String tokenHash) {
    return jpa.findByTokenHash(tokenHash).map(JpaPasswordResetTokenRepository::toDomain);
  }

  private static PasswordResetToken toDomain(final PasswordResetTokenEntity zeile) {
    return new PasswordResetToken(
        zeile.getId(),
        zeile.getAccountId(),
        zeile.getTokenHash(),
        zeile.getExpiresAt(),
        zeile.getUsedAt(),
        zeile.getCreatedAt());
  }

  private static PasswordResetTokenEntity toEntity(final PasswordResetToken token) {
    return new PasswordResetTokenEntity(
        token.id(),
        token.accountId(),
        token.tokenHash(),
        token.expiresAt(),
        token.usedAt(),
        token.createdAt());
  }
}
