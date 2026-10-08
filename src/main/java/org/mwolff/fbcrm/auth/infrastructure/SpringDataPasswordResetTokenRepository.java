package org.mwolff.fbcrm.auth.infrastructure;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring-Data-Zugriff auf {@code password_reset_token}.
 *
 * <p>Die Suche laeuft ueber den Hash und trifft damit den eindeutigen Index {@code
 * password_reset_token_hash_key}. Eine Suche ueber das Token selbst gibt es nicht — sie koennte es
 * nicht geben, weil das Token nirgends steht.
 */
interface SpringDataPasswordResetTokenRepository
    extends JpaRepository<PasswordResetTokenEntity, Long> {

  Optional<PasswordResetTokenEntity> findByTokenHash(String tokenHash);
}
