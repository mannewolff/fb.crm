package org.mwolff.fbcrm.vorgang.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.vorgang.domain.Vorgang;

/**
 * Die Uebersetzung zwischen Vorgang und Zeile — in beide Richtungen.
 *
 * <p>Der Adapter steht hier gegen ein gemocktes Spring-Data-Repository, damit die Abbildung selbst
 * geprueft ist und nicht nur ihr Zusammenspiel mit der Datenbank ({@code JpaVorgangRepositoryIT}).
 */
@ExtendWith(MockitoExtension.class)
class JpaVorgangRepositoryTest {

  private static final Instant ANGELEGT = Instant.parse("2026-09-01T08:00:00Z");
  private static final Instant GEAENDERT = Instant.parse("2026-09-18T12:00:00Z");

  @Mock private SpringDataVorgangRepository jpa;

  @Captor private ArgumentCaptor<VorgangEntity> gespeicherte;

  @InjectMocks private JpaVorgangRepository repository;

  private static VorgangEntity zeile(final Long id) {
    return new VorgangEntity(id, 12L, "Website-Relaunch", 5L, 7L, false, ANGELEGT, GEAENDERT);
  }

  private static Vorgang vorgang(final Long id) {
    return new Vorgang(id, 12L, "Website-Relaunch", 5L, 7L, false, ANGELEGT, GEAENDERT);
  }

  @Test
  void save_thenWritesEveryFieldOfTheVorgangIntoTheRow() {
    // Given
    when(jpa.save(any(VorgangEntity.class))).thenReturn(zeile(11L));

    // When
    repository.save(vorgang(null));

    // Then
    verify(jpa).save(gespeicherte.capture());
    assertThat(gespeicherte.getValue())
        .satisfies(
            zeile -> assertThat(zeile.getId()).isNull(),
            zeile -> assertThat(zeile.getNummer()).isEqualTo(12L),
            zeile -> assertThat(zeile.getTitel()).isEqualTo("Website-Relaunch"),
            zeile -> assertThat(zeile.getFirmaId()).isEqualTo(5L),
            zeile -> assertThat(zeile.getAnsprechpartnerId()).isEqualTo(7L),
            zeile -> assertThat(zeile.isAbgeschlossen()).isFalse(),
            zeile -> assertThat(zeile.getCreatedAt()).isEqualTo(ANGELEGT),
            zeile -> assertThat(zeile.getUpdatedAt()).isEqualTo(GEAENDERT));
  }

  @Test
  void save_givenAClosedVorgangWithoutAnsprechpartner_thenWritesThemAsAbsent() {
    // Given
    final Vorgang ohneAnsprechpartner =
        new Vorgang(null, 12L, "Website-Relaunch", 5L, null, true, ANGELEGT, GEAENDERT);
    when(jpa.save(any(VorgangEntity.class))).thenReturn(zeile(11L));

    // When
    repository.save(ohneAnsprechpartner);

    // Then
    verify(jpa).save(gespeicherte.capture());
    assertThat(gespeicherte.getValue())
        .satisfies(
            zeile -> assertThat(zeile.getAnsprechpartnerId()).isNull(),
            zeile -> assertThat(zeile.isAbgeschlossen()).isTrue());
  }

  @Test
  void save_thenReturnsTheVorgangWithTheGeneratedId() {
    // Given
    when(jpa.save(any(VorgangEntity.class))).thenReturn(zeile(11L));

    // When
    final Vorgang gesichert = repository.save(vorgang(null));

    // Then
    assertThat(gesichert).isEqualTo(vorgang(11L));
  }

  @Test
  void findById_givenAKnownVorgang_thenTranslatesTheRow() {
    // Given
    when(jpa.findById(11L)).thenReturn(Optional.of(zeile(11L)));

    // When
    final Optional<Vorgang> gefunden = repository.findById(11L);

    // Then
    assertThat(gefunden).contains(vorgang(11L));
  }

  @Test
  void findById_givenAVorgangWithoutAnsprechpartner_thenTranslatesItAsAbsent() {
    // Given
    final VorgangEntity ohneAnsprechpartner =
        new VorgangEntity(11L, 12L, "Website-Relaunch", 5L, null, true, ANGELEGT, GEAENDERT);
    when(jpa.findById(11L)).thenReturn(Optional.of(ohneAnsprechpartner));

    // When
    final Optional<Vorgang> gefunden = repository.findById(11L);

    // Then
    assertThat(gefunden)
        .hasValueSatisfying(
            vorgang -> {
              assertThat(vorgang.ansprechpartnerId()).isNull();
              assertThat(vorgang.abgeschlossen()).isTrue();
            });
  }

  @Test
  void findById_givenAnUnknownId_thenEmpty() {
    // Given
    when(jpa.findById(11L)).thenReturn(Optional.empty());

    // When
    final Optional<Vorgang> gefunden = repository.findById(11L);

    // Then
    assertThat(gefunden).isEmpty();
  }

  @Test
  void uebersicht_thenPassesSearchTextNumberAndSwitchThroughWithoutAFirma() {
    // Given
    when(jpa.uebersicht("relaunch", 12L, true, null)).thenReturn(List.of(zeile(11L)));

    // When
    final List<Vorgang> zeilen = repository.uebersicht("relaunch", 12L, true);

    // Then
    assertThat(zeilen).containsExactly(vorgang(11L));
  }

  @Test
  void uebersicht_givenNoNumberInTheSearchText_thenPassesNoNumber() {
    // Given
    when(jpa.uebersicht("relaunch", null, false, null)).thenReturn(List.of(zeile(11L)));

    // When
    final List<Vorgang> zeilen = repository.uebersicht("relaunch", null, false);

    // Then
    assertThat(zeilen).containsExactly(vorgang(11L));
  }

  @Test
  void uebersicht_givenNoMatch_thenEmptyList() {
    // Given
    when(jpa.uebersicht("fremd", null, false, null)).thenReturn(List.of());

    // When
    final List<Vorgang> zeilen = repository.uebersicht("fremd", null, false);

    // Then
    assertThat(zeilen).isEmpty();
  }

  @Test
  void findByFirma_thenAsksForEveryVorgangOfThatFirmaWithoutSearchText() {
    // Given
    when(jpa.uebersicht("", null, true, 5L)).thenReturn(List.of(zeile(11L)));

    // When
    final List<Vorgang> zeilen = repository.findByFirma(5L);

    // Then
    assertThat(zeilen).containsExactly(vorgang(11L));
  }

  @Test
  void findByFirma_givenNoVorgang_thenEmptyList() {
    // Given
    when(jpa.uebersicht("", null, true, 5L)).thenReturn(List.of());

    // When
    final List<Vorgang> zeilen = repository.findByFirma(5L);

    // Then
    assertThat(zeilen).isEmpty();
  }

  @Test
  void zaehleAlle_thenReportsTheCountOfTheTable() {
    // Given
    when(jpa.count()).thenReturn(3L);

    // When
    final long anzahl = repository.zaehleAlle();

    // Then
    assertThat(anzahl).isEqualTo(3L);
  }
}
