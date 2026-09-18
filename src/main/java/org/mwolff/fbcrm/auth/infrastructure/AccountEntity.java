package org.mwolff.fbcrm.auth.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.auth.domain.Role;

/** Die Zeile der Tabelle {@code account} aus {@code V1__baseline.sql}. */
@Entity
@Table(name = "account")
class AccountEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private @Nullable Long id;

  @Column(name = "email", nullable = false, length = 320)
  private String email;

  @Column(name = "display_name", nullable = false, length = 200)
  private String displayName;

  @Column(name = "password_hash", nullable = false, length = 255)
  private String passwordHash;

  @Enumerated(EnumType.STRING)
  @Column(name = "role", nullable = false, length = 32)
  private Role role;

  @Column(name = "session_generation", nullable = false)
  private int sessionGeneration;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /*
   * JPA verlangt einen parameterlosen Konstruktor und fuellt die Felder danach selbst; NullAway
   * sieht diesen Weg nicht und meldete sonst nicht initialisierte Felder.
   */
  @SuppressWarnings("NullAway.Init")
  protected AccountEntity() {
    // Von Hibernate benutzt.
  }

  AccountEntity(
      final @Nullable Long id,
      final String email,
      final String displayName,
      final String passwordHash,
      final Role role,
      final int sessionGeneration,
      final Instant createdAt,
      final Instant updatedAt) {
    this.id = id;
    this.email = email;
    this.displayName = displayName;
    this.passwordHash = passwordHash;
    this.role = role;
    this.sessionGeneration = sessionGeneration;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  @Nullable Long getId() {
    return id;
  }

  String getEmail() {
    return email;
  }

  String getDisplayName() {
    return displayName;
  }

  String getPasswordHash() {
    return passwordHash;
  }

  Role getRole() {
    return role;
  }

  int getSessionGeneration() {
    return sessionGeneration;
  }

  Instant getCreatedAt() {
    return createdAt;
  }

  Instant getUpdatedAt() {
    return updatedAt;
  }
}
