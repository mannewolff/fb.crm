package org.mwolff.fbcrm.vorgang.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Der Zug am Nummernkreis ohne Datenbank: Was gezogen wird und was zurueckgeschrieben wird.
 *
 * <p>Dass die Sperre wirkt und ein Ruecklauf die Nummer wieder freigibt, weist {@code
 * JpaNummernkreisRepositoryIT} gegen eine echte PostgreSQL-Instanz nach.
 */
@ExtendWith(MockitoExtension.class)
class JpaNummernkreisRepositoryTest {

  @Mock private SpringDataNummernkreisRepository jpa;

  @Captor private ArgumentCaptor<NummernkreisEntity> fortgeschriebene;

  @InjectMocks private JpaNummernkreisRepository repository;

  @Test
  void naechsteNummer_thenHandsOutTheStoredNumber() {
    // Given
    when(jpa.sperreUndLies()).thenReturn(new NummernkreisEntity((short) 1, 7L));

    // When
    final long gezogen = repository.naechsteNummer();

    // Then
    assertThat(gezogen).isEqualTo(7L);
  }

  @Test
  void naechsteNummer_thenWritesTheFollowingNumberBack() {
    // Given
    when(jpa.sperreUndLies()).thenReturn(new NummernkreisEntity((short) 1, 7L));

    // When
    repository.naechsteNummer();

    // Then
    verify(jpa).save(fortgeschriebene.capture());
    assertThat(fortgeschriebene.getValue().getNaechste()).isEqualTo(8L);
  }

  @Test
  void naechsteNummer_givenAFreshCounter_thenStartsAtOne() {
    // Given
    when(jpa.sperreUndLies()).thenReturn(new NummernkreisEntity((short) 1, 1L));

    // When
    final long gezogen = repository.naechsteNummer();

    // Then
    assertThat(gezogen).isEqualTo(1L);
    verify(jpa).save(any(NummernkreisEntity.class));
  }
}
