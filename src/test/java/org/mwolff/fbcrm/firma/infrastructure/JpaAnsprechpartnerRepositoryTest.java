package org.mwolff.fbcrm.firma.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.firma.domain.Ansprechpartner;

/**
 * Die Uebersetzung zwischen Ansprechpartner und Zeile — in beide Richtungen.
 *
 * <p>Der Adapter steht hier gegen ein gemocktes Spring-Data-Repository, damit die Abbildung selbst
 * geprueft ist und nicht nur ihr Zusammenspiel mit der Datenbank ({@code
 * JpaAnsprechpartnerRepositoryIT}).
 */
@ExtendWith(MockitoExtension.class)
class JpaAnsprechpartnerRepositoryTest {

  private static final Instant ANGELEGT = Instant.parse("2026-09-01T08:00:00Z");
  private static final Instant GEAENDERT = Instant.parse("2026-09-18T12:00:00Z");

  /**
   * Ein Halt fuer die Projektion statt eines Mocks: Sie traegt zwei Zahlen und kein Verhalten, und
   * ein Mock dafuer waere ein Mock auf einen Werttyp (CLAUDE-java.md §4).
   */
  private record Gezaehlt(long firmaId, long anzahl)
      implements SpringDataAnsprechpartnerRepository.AktiveJeFirma {

    @Override
    public long getFirmaId() {
      return firmaId;
    }

    @Override
    public long getAnzahl() {
      return anzahl;
    }
  }

  @Mock private SpringDataAnsprechpartnerRepository jpa;

  @Captor private ArgumentCaptor<AnsprechpartnerEntity> gespeicherte;

  @InjectMocks private JpaAnsprechpartnerRepository repository;

  private static AnsprechpartnerEntity zeile(final Long id) {
    return new AnsprechpartnerEntity(
        id,
        7L,
        "Max",
        "Mustermann",
        "Einkauf",
        "max@firma.de",
        "0421 123456",
        "0170 123456",
        true,
        ANGELEGT,
        GEAENDERT);
  }

  private static Ansprechpartner ansprechpartner(final Long id) {
    return new Ansprechpartner(
        id,
        7L,
        "Max",
        "Mustermann",
        "Einkauf",
        "max@firma.de",
        "0421 123456",
        "0170 123456",
        true,
        ANGELEGT,
        GEAENDERT);
  }

  @Test
  void save_thenWritesEveryFieldOfTheAnsprechpartnerIntoTheRow() {
    // Given
    when(jpa.save(any(AnsprechpartnerEntity.class))).thenReturn(zeile(3L));

    // When
    repository.save(ansprechpartner(null));

    // Then
    verify(jpa).save(gespeicherte.capture());
    assertThat(gespeicherte.getValue())
        .satisfies(
            zeile -> assertThat(zeile.getId()).isNull(),
            zeile -> assertThat(zeile.getFirmaId()).isEqualTo(7L),
            zeile -> assertThat(zeile.getVorname()).isEqualTo("Max"),
            zeile -> assertThat(zeile.getNachname()).isEqualTo("Mustermann"),
            zeile -> assertThat(zeile.getRolle()).isEqualTo("Einkauf"),
            zeile -> assertThat(zeile.getEmail()).isEqualTo("max@firma.de"),
            zeile -> assertThat(zeile.getTelefonFestnetz()).isEqualTo("0421 123456"),
            zeile -> assertThat(zeile.getTelefonMobil()).isEqualTo("0170 123456"),
            zeile -> assertThat(zeile.isAktiv()).isTrue(),
            zeile -> assertThat(zeile.getCreatedAt()).isEqualTo(ANGELEGT),
            zeile -> assertThat(zeile.getUpdatedAt()).isEqualTo(GEAENDERT));
  }

  @Test
  void save_givenARetiredAnsprechpartnerWithoutOptionalValues_thenWritesThemAsAbsent() {
    // Given
    final Ansprechpartner ohneAngaben =
        new Ansprechpartner(
            null, 7L, null, "Mustermann", null, null, null, null, false, ANGELEGT, GEAENDERT);
    when(jpa.save(any(AnsprechpartnerEntity.class))).thenReturn(zeile(3L));

    // When
    repository.save(ohneAngaben);

    // Then
    verify(jpa).save(gespeicherte.capture());
    assertThat(gespeicherte.getValue())
        .satisfies(
            zeile -> assertThat(zeile.getVorname()).isNull(),
            zeile -> assertThat(zeile.getRolle()).isNull(),
            zeile -> assertThat(zeile.getEmail()).isNull(),
            zeile -> assertThat(zeile.getTelefonFestnetz()).isNull(),
            zeile -> assertThat(zeile.getTelefonMobil()).isNull(),
            zeile -> assertThat(zeile.isAktiv()).isFalse());
  }

  @Test
  void save_thenReturnsTheAnsprechpartnerWithTheGeneratedId() {
    // Given
    when(jpa.save(any(AnsprechpartnerEntity.class))).thenReturn(zeile(3L));

    // When
    final Ansprechpartner gesichert = repository.save(ansprechpartner(null));

    // Then
    assertThat(gesichert).isEqualTo(ansprechpartner(3L));
  }

  @Test
  void findById_givenAKnownAnsprechpartner_thenTranslatesTheRow() {
    // Given
    when(jpa.findById(3L)).thenReturn(Optional.of(zeile(3L)));

    // When
    final Optional<Ansprechpartner> gefunden = repository.findById(3L);

    // Then
    assertThat(gefunden).contains(ansprechpartner(3L));
  }

  @Test
  void findById_givenAnUnknownId_thenEmpty() {
    // Given
    when(jpa.findById(3L)).thenReturn(Optional.empty());

    // When
    final Optional<Ansprechpartner> gefunden = repository.findById(3L);

    // Then
    assertThat(gefunden).isEmpty();
  }

  @Test
  void findByFirma_thenTranslatesEveryRow() {
    // Given
    when(jpa.findByFirma(7L)).thenReturn(List.of(zeile(3L)));

    // When
    final List<Ansprechpartner> zeilen = repository.findByFirma(7L);

    // Then
    assertThat(zeilen).containsExactly(ansprechpartner(3L));
  }

  @Test
  void findByFirma_givenAFirmaWithoutAnsprechpartner_thenEmptyList() {
    // Given
    when(jpa.findByFirma(8L)).thenReturn(List.of());

    // When
    final List<Ansprechpartner> zeilen = repository.findByFirma(8L);

    // Then
    assertThat(zeilen).isEmpty();
  }

  @Test
  void zaehleAktiveJeFirma_thenReportsTheCountedRows() {
    // Given
    when(jpa.zaehleAktiveJeFirma(List.of(7L, 8L))).thenReturn(List.of(new Gezaehlt(7L, 2L)));

    // When
    final Map<Long, Long> anzahlen = repository.zaehleAktiveJeFirma(List.of(7L, 8L));

    // Then
    assertThat(anzahlen).containsExactly(entry(7L, 2L), entry(8L, 0L));
  }

  @Test
  void zaehleAktiveJeFirma_givenNoIds_thenAnEmptyMap() {
    // Given
    when(jpa.zaehleAktiveJeFirma(List.of())).thenReturn(List.of());

    // When
    final Map<Long, Long> anzahlen = repository.zaehleAktiveJeFirma(List.of());

    // Then
    assertThat(anzahlen).isEmpty();
  }
}
