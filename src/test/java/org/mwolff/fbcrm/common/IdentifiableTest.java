package org.mwolff.fbcrm.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class IdentifiableTest {

  @Test
  void requireId_givenPersistedInstance_thenReturnsId() {
    // Given
    final Identifiable persisted = new Beispiel(42L);

    // When
    final long id = persisted.requireId();

    // Then
    assertThat(id).isEqualTo(42L);
  }

  @Test
  void requireId_givenTransientInstance_thenThrowsIllegalState() {
    // Given
    final Identifiable transientInstance = new Beispiel(null);

    // When / Then
    assertThatThrownBy(transientInstance::requireId)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Beispiel");
  }

  private record Beispiel(@Nullable Long id) implements Identifiable {}
}
