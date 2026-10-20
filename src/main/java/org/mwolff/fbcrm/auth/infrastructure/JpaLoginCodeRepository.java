package org.mwolff.fbcrm.auth.infrastructure;

import java.util.Optional;
import org.mwolff.fbcrm.auth.domain.LoginCode;
import org.mwolff.fbcrm.auth.domain.LoginCodeRepository;
import org.springframework.stereotype.Repository;

/** Setzt den Port {@link LoginCodeRepository} auf JPA um und uebersetzt in beide Richtungen. */
@Repository
class JpaLoginCodeRepository implements LoginCodeRepository {

  private final SpringDataLoginCodeRepository jpa;

  JpaLoginCodeRepository(final SpringDataLoginCodeRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public Optional<LoginCode> findByEmail(final String email) {
    return jpa.findByEmailIgnoreCase(email).map(JpaLoginCodeRepository::toDomain);
  }

  @Override
  public LoginCode save(final LoginCode code) {
    return toDomain(jpa.save(toEntity(code)));
  }

  @Override
  public void deleteByEmail(final String email) {
    jpa.deleteByEmailIgnoreCase(email);
  }

  private static LoginCode toDomain(final LoginCodeEntity zeile) {
    return new LoginCode(
        zeile.getId(),
        zeile.getEmail(),
        zeile.getCodeHash(),
        zeile.getExpiresAt(),
        zeile.getFailedAttempts(),
        zeile.getCreatedAt());
  }

  private static LoginCodeEntity toEntity(final LoginCode code) {
    return new LoginCodeEntity(
        code.id(),
        code.email(),
        code.codeHash(),
        code.expiresAt(),
        code.failedAttempts(),
        code.createdAt());
  }
}
