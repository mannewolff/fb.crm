package org.mwolff.fbcrm.auth.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Die Zeile der Tabelle {@code password_reset_token} aus {@code V1__baseline.sql}.
 *
 * <p>Das Konto steht als blosse Id und nicht als {@code @ManyToOne}: Der Token wird ueber seinen
 * Hash gefunden, und was danach gebraucht wird, ist die Id — ein Verweis auf die Entitaet zoege das
 * Konto bei jedem Zugriff mit, ohne dass jemand danach gefragt haette.
 */
@Entity
@Table(name = "password_reset_token")
class PasswordResetTokenEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private @Nullable Long id;

  @Column(name = "account_id", nullable = false)
  private long accountId;

  @Column(name = "token_hash", nullable = false, length = 64)
  private String tokenHash;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "used_at")
  private @Nullable Instant usedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  /*
   * JPA verlangt einen parameterlosen Konstruktor und fuellt die Felder danach selbst; NullAway
   * sieht diesen Weg nicht und meldete sonst nicht initialisierte Felder.
   */
  @SuppressWarnings("NullAway.Init")
  protected PasswordResetTokenEntity() {
    // Von Hibernate benutzt.
  }

  PasswordResetTokenEntity(
      final @Nullable Long id,
      final long accountId,
      final String tokenHash,
      final Instant expiresAt,
      final @Nullable Instant usedAt,
      final Instant createdAt) {
    this.id = id;
    this.accountId = accountId;
    this.tokenHash = tokenHash;
    this.expiresAt = expiresAt;
    this.usedAt = usedAt;
    this.createdAt = createdAt;
  }

  @Nullable Long getId() {
    return id;
  }

  long getAccountId() {
    return accountId;
  }

  String getTokenHash() {
    return tokenHash;
  }

  Instant getExpiresAt() {
    return expiresAt;
  }

  @Nullable Instant getUsedAt() {
    return usedAt;
  }

  Instant getCreatedAt() {
    return createdAt;
  }
}
