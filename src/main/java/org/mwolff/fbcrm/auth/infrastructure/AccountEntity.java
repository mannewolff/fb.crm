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
import org.mwolff.fbcrm.auth.domain.Account;
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

  /**
   * Die Zeile zu einem Konto — jedes Feld kommt aus dem Fachobjekt (Plan #238, A5).
   *
   * <p>Die Zeile holt sich ihren Stand selbst, statt ihn als Liste von acht Parametern zu bekommen:
   * Der Adapter nennt dann am Aufruf nur noch, <i>was</i> abgebildet wird, und ein neues Feld des
   * Kontos landet hier und nicht zusaetzlich in jeder Aufrufstelle. Das waehrt gerade hier, wo
   * Passwort-Hash, Rolle und Sitzungs-Generation nebeneinander stehen: Kein Aufruf kann sie
   * vertauschen, den keiner mehr liest.
   *
   * @param account das Konto, dessen Stand die Zeile tragen soll
   * @return die Zeile; ihre Kennung ist die des Kontos und damit {@code null}, solange das Konto
   *     noch nicht geschrieben wurde
   */
  static AccountEntity aus(final Account account) {
    return new AccountEntity(account);
  }

  private AccountEntity(final Account account) {
    this.id = account.id();
    this.email = account.email();
    this.displayName = account.displayName();
    this.passwordHash = account.passwordHash();
    this.role = account.role();
    this.sessionGeneration = account.sessionGeneration();
    this.createdAt = account.createdAt();
    this.updatedAt = account.updatedAt();
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
