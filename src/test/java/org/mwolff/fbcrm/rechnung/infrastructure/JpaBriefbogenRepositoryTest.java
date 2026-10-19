package org.mwolff.fbcrm.rechnung.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.mwolff.fbcrm.rechnung.domain.Briefbogen;
import org.mwolff.fbcrm.rechnung.domain.Briefpapierblatt;
import org.mwolff.fbcrm.rechnung.domain.Randmasse;

/**
 * Der Adapter gegen ein gemocktes Spring-Data-Repository: Er liest die eine Zeile und schreibt sie
 * fort. Die Abbildung selbst prueft {@code BriefbogenEntityTest}.
 */
@ExtendWith(MockitoExtension.class)
class JpaBriefbogenRepositoryTest {

  private static final Instant GEAENDERT = Instant.parse("2026-10-19T12:00:00Z");

  private static final Briefbogen BRIEFBOGEN =
      new Briefbogen(
          new Briefpapierblatt("briefpapier/abc.pdf", "kopf.pdf", 2_048L, 1),
          true,
          false,
          new Randmasse(251, 202, 983, 204, 205, 206));

  @Mock private SpringDataBriefbogenRepository jpa;

  @Captor private ArgumentCaptor<BriefbogenEntity> gespeicherte;

  @InjectMocks private JpaBriefbogenRepository repository;

  @Test
  void lies_thenTranslatesTheRow() {
    // Given
    when(jpa.findById(BriefbogenEntity.ZEILE))
        .thenReturn(Optional.of(BriefbogenEntity.aus(BRIEFBOGEN, GEAENDERT)));

    // When / Then
    assertThat(repository.lies()).isEqualTo(BRIEFBOGEN);
  }

  @Test
  void speichere_thenWritesTheOneRow() {
    // When
    repository.speichere(BRIEFBOGEN, GEAENDERT);

    // Then
    verify(jpa).save(gespeicherte.capture());
    assertThat(gespeicherte.getValue())
        .satisfies(
            zeile -> assertThat(zeile.getId()).isEqualTo(BriefbogenEntity.ZEILE),
            zeile -> assertThat(zeile.alsBriefbogen()).isEqualTo(BRIEFBOGEN),
            zeile -> assertThat(zeile.getUpdatedAt()).isEqualTo(GEAENDERT));
  }
}
