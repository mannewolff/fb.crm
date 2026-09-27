package org.mwolff.fbcrm.angebot.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Der Zug am Nummernkreis der Belege ohne Datenbank: was gezogen, was zurueckgeschrieben und wie es
 * geschrieben wird.
 *
 * <p>Dass die Sperre wirkt, dass jedes Jahr bei 1 beginnt und dass ein Ruecklauf die Nummer wieder
 * freigibt, weist {@code BelegnummerIT} gegen eine echte PostgreSQL-Instanz nach.
 */
@ExtendWith(MockitoExtension.class)
class JpaBelegnummerRepositoryTest {

  @Mock private SpringDataAngebotNummernkreisRepository jpa;

  @Captor private ArgumentCaptor<AngebotNummernkreisEntity> fortgeschriebene;

  @InjectMocks private JpaBelegnummerRepository repository;

  @Test
  void zieheNummer_thenHandsOutTheStoredNumberInTheDocumentFormat() {
    // Given
    when(jpa.sperreUndLies(2026)).thenReturn(new AngebotNummernkreisEntity(2026, 1L));

    // When
    final String gezogen = repository.zieheNummer(2026);

    // Then
    assertThat(gezogen).isEqualTo("A-2026-001");
  }

  @Test
  void zieheNummer_givenAFourDigitCounter_thenDoesNotTruncateTheNumber() {
    // Given
    when(jpa.sperreUndLies(2026)).thenReturn(new AngebotNummernkreisEntity(2026, 1000L));

    // When
    final String gezogen = repository.zieheNummer(2026);

    // Then
    assertThat(gezogen).isEqualTo("A-2026-1000");
  }

  @Test
  void zieheNummer_thenCarriesTheYearOfTheRowIntoTheNumber() {
    // Given
    when(jpa.sperreUndLies(2027)).thenReturn(new AngebotNummernkreisEntity(2027, 1L));

    // When
    final String gezogen = repository.zieheNummer(2027);

    // Then
    assertThat(gezogen).isEqualTo("A-2027-001");
  }

  @Test
  void zieheNummer_thenWritesTheFollowingNumberBack() {
    // Given
    when(jpa.sperreUndLies(2026)).thenReturn(new AngebotNummernkreisEntity(2026, 7L));

    // When
    repository.zieheNummer(2026);

    // Then
    verify(jpa).save(fortgeschriebene.capture());
    assertThat(fortgeschriebene.getValue().getNaechste()).isEqualTo(8L);
  }

  @Test
  void zieheNummer_thenCreatesTheYearRowBeforeItLocksIt() {
    // Given — ohne die Zeile griffe die Sperre ins Leere; angelegt wird sie ohne Rueckfrage
    // (ON CONFLICT DO NOTHING), damit zwei Zuege im selben Jahr nicht kollidieren.
    when(jpa.sperreUndLies(2026)).thenReturn(new AngebotNummernkreisEntity(2026, 1L));

    // When
    repository.zieheNummer(2026);

    // Then
    final InOrder reihenfolge = inOrder(jpa);
    reihenfolge.verify(jpa).legeJahrAn(2026);
    reihenfolge.verify(jpa).sperreUndLies(2026);
    reihenfolge.verify(jpa).save(any(AngebotNummernkreisEntity.class));
  }
}
