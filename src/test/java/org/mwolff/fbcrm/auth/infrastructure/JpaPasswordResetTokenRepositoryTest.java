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
import org.mwolff.fbcrm.auth.domain.PasswordResetToken;

/**
 * Die Uebersetzung zwischen Reset-Token und Zeile — in beide Richtungen.
 *
 * <p>Der Adapter steht hier gegen ein gemocktes Spring-Data-Repository, damit die Abbildung selbst
 * geprueft ist und nicht nur ihr Zusammenspiel mit der Datenbank ({@code
 * PasswordResetControllerIT}).
 */
@ExtendWith(MockitoExtension.class)
class JpaPasswordResetTokenRepositoryTest {

  private static final Instant AUSGESTELLT = Instant.parse("2026-09-18T10:00:00Z");
  private static final Instant ABLAUF = Instant.parse("2026-09-18T11:00:00Z");
  private static final Instant BENUTZT = Instant.parse("2026-09-18T10:30:00Z");

  @Mock private SpringDataPasswordResetTokenRepository jpa;

  @Captor private ArgumentCaptor<PasswordResetTokenEntity> gespeicherte;

  @InjectMocks private JpaPasswordResetTokenRepository repository;

  private static PasswordResetTokenEntity zeile(final Long id) {
    return new PasswordResetTokenEntity(id, 11L, "hash", ABLAUF, BENUTZT, AUSGESTELLT);
  }

  private static PasswordResetToken token(final Long id) {
    return new PasswordResetToken(id, 11L, "hash", ABLAUF, BENUTZT, AUSGESTELLT);
  }

  @Test
  void save_thenWritesEveryFieldOfTheTokenIntoTheRow() {
    // Given
    when(jpa.save(any(PasswordResetTokenEntity.class))).thenReturn(zeile(7L));

    // When
    repository.save(token(null));

    // Then
    verify(jpa).save(gespeicherte.capture());
    assertThat(gespeicherte.getValue())
        .satisfies(
            zeile -> assertThat(zeile.getId()).isNull(),
            zeile -> assertThat(zeile.getAccountId()).isEqualTo(11L),
            zeile -> assertThat(zeile.getTokenHash()).isEqualTo("hash"),
            zeile -> assertThat(zeile.getExpiresAt()).isEqualTo(ABLAUF),
            zeile -> assertThat(zeile.getUsedAt()).isEqualTo(BENUTZT),
            zeile -> assertThat(zeile.getCreatedAt()).isEqualTo(AUSGESTELLT));
  }

  @Test
  void save_thenReturnsTheTokenWithTheGeneratedId() {
    // Given
    when(jpa.save(any(PasswordResetTokenEntity.class))).thenReturn(zeile(7L));

    // When
    final PasswordResetToken gesichert = repository.save(token(null));

    // Then
    assertThat(gesichert).isEqualTo(token(7L));
  }

  @Test
  void findByTokenHash_givenAKnownHash_thenTranslatesTheRow() {
    // Given
    when(jpa.findByTokenHash("hash")).thenReturn(Optional.of(zeile(7L)));

    // When
    final Optional<PasswordResetToken> gefunden = repository.findByTokenHash("hash");

    // Then
    assertThat(gefunden).contains(token(7L));
  }

  @Test
  void findByTokenHash_givenAnUnknownHash_thenEmpty() {
    // Given
    when(jpa.findByTokenHash("fremd")).thenReturn(Optional.empty());

    // When
    final Optional<PasswordResetToken> gefunden = repository.findByTokenHash("fremd");

    // Then
    assertThat(gefunden).isEmpty();
  }
}
