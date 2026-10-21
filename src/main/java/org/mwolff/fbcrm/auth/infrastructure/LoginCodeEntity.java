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
 * Die Zeile der Tabelle {@code login_code} aus {@code V27__login_code.sql}.
 *
 * <p>Die Spalte {@code code_hash} traegt den Argon2id-Hash, nie den Code selbst (E2).
 */
@Entity
@Table(name = "login_code")
class LoginCodeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private @Nullable Long id;

  @Column(name = "email", nullable = false, length = 320)
  private String email;

  @Column(name = "code_hash", nullable = false)
  private String codeHash;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "failed_attempts", nullable = false)
  private int failedAttempts;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  /*
   * JPA verlangt einen parameterlosen Konstruktor und fuellt die Felder danach selbst; NullAway
   * sieht diesen Weg nicht und meldete sonst nicht initialisierte Felder.
   */
  @SuppressWarnings("NullAway.Init")
  protected LoginCodeEntity() {
    // Von Hibernate benutzt.
  }

  LoginCodeEntity(
      final @Nullable Long id,
      final String email,
      final String codeHash,
      final Instant expiresAt,
      final int failedAttempts,
      final Instant createdAt) {
    this.id = id;
    this.email = email;
    this.codeHash = codeHash;
    this.expiresAt = expiresAt;
    this.failedAttempts = failedAttempts;
    this.createdAt = createdAt;
  }

  @Nullable Long getId() {
    return id;
  }

  String getEmail() {
    return email;
  }

  String getCodeHash() {
    return codeHash;
  }

  Instant getExpiresAt() {
    return expiresAt;
  }

  int getFailedAttempts() {
    return failedAttempts;
  }

  Instant getCreatedAt() {
    return createdAt;
  }
}
