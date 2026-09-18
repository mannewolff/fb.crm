package org.mwolff.fbcrm.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.auth.domain.Account;
import org.mwolff.fbcrm.auth.domain.Role;

/**
 * Die Uebersetzung zwischen Konto und Zeile — in beide Richtungen.
 *
 * <p>Der Adapter steht hier gegen ein gemocktes Spring-Data-Repository, damit die Abbildung selbst
 * geprueft ist und nicht nur ihr Zusammenspiel mit der Datenbank ({@code JpaAccountRepositoryIT}).
 */
@ExtendWith(MockitoExtension.class)
class JpaAccountRepositoryTest {

  private static final Instant ANGELEGT = Instant.parse("2026-09-01T08:00:00Z");
  private static final Instant GEAENDERT = Instant.parse("2026-09-18T12:00:00Z");

  @Mock private SpringDataAccountRepository jpa;

  @Captor private ArgumentCaptor<AccountEntity> gespeicherte;

  @InjectMocks private JpaAccountRepository repository;

  private static AccountEntity zeile(final Long id) {
    return new AccountEntity(
        id, "manne@example.org", "Manne", "hash", Role.ADMIN, 2, ANGELEGT, GEAENDERT);
  }

  private static Account konto(final Long id) {
    return new Account(
        id, "manne@example.org", "Manne", "hash", Role.ADMIN, 2, ANGELEGT, GEAENDERT);
  }

  @Test
  void save_thenWritesEveryFieldOfTheAccountIntoTheRow() {
    // Given
    when(jpa.save(any(AccountEntity.class))).thenReturn(zeile(11L));

    // When
    repository.save(konto(null));

    // Then
    verify(jpa).save(gespeicherte.capture());
    assertThat(gespeicherte.getValue())
        .satisfies(
            zeile -> assertThat(zeile.getId()).isNull(),
            zeile -> assertThat(zeile.getEmail()).isEqualTo("manne@example.org"),
            zeile -> assertThat(zeile.getDisplayName()).isEqualTo("Manne"),
            zeile -> assertThat(zeile.getPasswordHash()).isEqualTo("hash"),
            zeile -> assertThat(zeile.getRole()).isEqualTo(Role.ADMIN),
            zeile -> assertThat(zeile.getSessionGeneration()).isEqualTo(2),
            zeile -> assertThat(zeile.getCreatedAt()).isEqualTo(ANGELEGT),
            zeile -> assertThat(zeile.getUpdatedAt()).isEqualTo(GEAENDERT));
  }

  @Test
  void save_thenReturnsTheAccountWithTheGeneratedId() {
    // Given
    when(jpa.save(any(AccountEntity.class))).thenReturn(zeile(11L));

    // When
    final Account gesichert = repository.save(konto(null));

    // Then
    assertThat(gesichert).isEqualTo(konto(11L));
  }

  @Test
  void findById_givenAKnownAccount_thenTranslatesTheRow() {
    // Given
    when(jpa.findById(11L)).thenReturn(Optional.of(zeile(11L)));

    // When
    final Optional<Account> gefunden = repository.findById(11L);

    // Then
    assertThat(gefunden).contains(konto(11L));
  }

  @Test
  void findById_givenAnUnknownId_thenEmpty() {
    // Given
    when(jpa.findById(11L)).thenReturn(Optional.empty());

    // When
    final Optional<Account> gefunden = repository.findById(11L);

    // Then
    assertThat(gefunden).isEmpty();
  }

  @Test
  void findByEmail_givenAKnownAddress_thenTranslatesTheRow() {
    // Given
    when(jpa.findByEmailIgnoreCase("manne@example.org")).thenReturn(Optional.of(zeile(11L)));

    // When
    final Optional<Account> gefunden = repository.findByEmail("manne@example.org");

    // Then
    assertThat(gefunden).contains(konto(11L));
  }

  @Test
  void findByEmail_givenAnUnknownAddress_thenEmpty() {
    // Given
    when(jpa.findByEmailIgnoreCase("fremd@example.org")).thenReturn(Optional.empty());

    // When
    final Optional<Account> gefunden = repository.findByEmail("fremd@example.org");

    // Then
    assertThat(gefunden).isEmpty();
  }

  @Test
  void existsAnyAdmin_thenAsksForTheAdminRole() {
    // Given
    when(jpa.existsByRole(Role.ADMIN)).thenReturn(true);

    // When
    final boolean vorhanden = repository.existsAnyAdmin();

    // Then
    assertThat(vorhanden).isTrue();
  }

  @Test
  void existsAnyAdmin_givenNoAdminRow_thenFalse() {
    // Given
    when(jpa.existsByRole(Role.ADMIN)).thenReturn(false);

    // When
    final boolean vorhanden = repository.existsAnyAdmin();

    // Then
    assertThat(vorhanden).isFalse();
  }
}
