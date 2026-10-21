package org.mwolff.fbcrm.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.auth.domain.LoginCode;

/**
 * Die Uebersetzung zwischen Einmalcode und Zeile — in beide Richtungen.
 *
 * <p>Der Adapter steht hier gegen ein gemocktes Spring-Data-Repository, damit die Abbildung selbst
 * geprueft ist und nicht nur ihr Zusammenspiel mit der Datenbank ({@code
 * JpaLoginCodeRepositoryIT}).
 */
@ExtendWith(MockitoExtension.class)
class JpaLoginCodeRepositoryTest {

  private static final Instant AUSGESTELLT = Instant.parse("2026-10-20T10:00:00Z");
  private static final Instant ABLAUF = Instant.parse("2026-10-20T10:10:00Z");
  private static final String ADRESSE = "manne@example.org";

  @Mock private SpringDataLoginCodeRepository jpa;

  @Captor private ArgumentCaptor<LoginCodeEntity> gespeicherte;

  @InjectMocks private JpaLoginCodeRepository repository;

  private static LoginCodeEntity zeile(final Long id) {
    return new LoginCodeEntity(id, ADRESSE, "hash", ABLAUF, 3, AUSGESTELLT);
  }

  private static LoginCode code(final Long id) {
    return new LoginCode(id, ADRESSE, "hash", ABLAUF, 3, AUSGESTELLT);
  }

  @Test
  void save_thenWritesEveryFieldOfTheCodeIntoTheRow() {
    // Given
    when(jpa.save(any(LoginCodeEntity.class))).thenReturn(zeile(7L));

    // When
    repository.save(code(null));

    // Then
    verify(jpa).save(gespeicherte.capture());
    assertThat(gespeicherte.getValue())
        .satisfies(
            zeile -> assertThat(zeile.getId()).isNull(),
            zeile -> assertThat(zeile.getEmail()).isEqualTo(ADRESSE),
            zeile -> assertThat(zeile.getCodeHash()).isEqualTo("hash"),
            zeile -> assertThat(zeile.getExpiresAt()).isEqualTo(ABLAUF),
            zeile -> assertThat(zeile.getFailedAttempts()).isEqualTo(3),
            zeile -> assertThat(zeile.getCreatedAt()).isEqualTo(AUSGESTELLT));
  }

  @Test
  void save_thenReturnsTheCodeWithTheGeneratedId() {
    // Given
    when(jpa.save(any(LoginCodeEntity.class))).thenReturn(zeile(7L));

    // When
    final LoginCode gesichert = repository.save(code(null));

    // Then
    assertThat(gesichert).isEqualTo(code(7L));
  }

  @Test
  void findByEmail_givenAKnownAddress_thenTranslatesTheRow() {
    // Given
    when(jpa.findByEmailIgnoreCase(ADRESSE)).thenReturn(Optional.of(zeile(7L)));

    // When
    final Optional<LoginCode> gefunden = repository.findByEmail(ADRESSE);

    // Then
    assertThat(gefunden).contains(code(7L));
  }

  @Test
  void findByEmail_givenAnUnknownAddress_thenEmpty() {
    // Given
    when(jpa.findByEmailIgnoreCase("fremd@example.org")).thenReturn(Optional.empty());

    // When
    final Optional<LoginCode> gefunden = repository.findByEmail("fremd@example.org");

    // Then
    assertThat(gefunden).isEmpty();
  }

  @Test
  void deleteByEmail_thenDeletesIgnoringCase() {
    // When
    repository.deleteByEmail(ADRESSE);

    // Then
    verify(jpa).deleteByEmailIgnoreCase(ADRESSE);
  }

  @Test
  void deleteByEmail_thenWritesTheDeletionBeforeAnyFollowingInsert() {
    // When — ein Code mit IDENTITY-Id wird sofort geschrieben, das Loeschen sonst erst beim Flush.
    repository.deleteByEmail(ADRESSE);

    // Then
    final InOrder reihenfolge = inOrder(jpa);
    reihenfolge.verify(jpa).deleteByEmailIgnoreCase(ADRESSE);
    reihenfolge.verify(jpa).flush();
  }
}
