package org.mwolff.fbcrm.firma.infrastructure;

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
import org.mwolff.fbcrm.firma.domain.Anschrift;
import org.mwolff.fbcrm.firma.domain.Firma;

/**
 * Die Uebersetzung zwischen Firma und Zeile — in beide Richtungen.
 *
 * <p>Der Adapter steht hier gegen ein gemocktes Spring-Data-Repository, damit die Abbildung selbst
 * geprueft ist und nicht nur ihr Zusammenspiel mit der Datenbank ({@code JpaFirmaRepositoryIT}).
 */
@ExtendWith(MockitoExtension.class)
class JpaFirmaRepositoryTest {

  private static final Instant ANGELEGT = Instant.parse("2026-09-01T08:00:00Z");
  private static final Instant GEAENDERT = Instant.parse("2026-09-18T12:00:00Z");

  @Mock private SpringDataFirmaRepository jpa;

  @Captor private ArgumentCaptor<FirmaEntity> gespeicherte;

  @InjectMocks private JpaFirmaRepository repository;

  private static FirmaEntity zeile(final Long id) {
    return new FirmaEntity(
        id,
        "Adler AG",
        "Am Wall 1",
        "28195",
        "Bremen",
        "Deutschland",
        "75/123/45678",
        "DE123456789",
        true,
        ANGELEGT,
        GEAENDERT);
  }

  private static Firma firma(final Long id) {
    return new Firma(
        id,
        "Adler AG",
        new Anschrift("Am Wall 1", "28195", "Bremen", "Deutschland"),
        "75/123/45678",
        "DE123456789",
        true,
        ANGELEGT,
        GEAENDERT);
  }

  @Test
  void save_thenWritesEveryFieldOfTheFirmaIntoTheRow() {
    // Given
    when(jpa.save(any(FirmaEntity.class))).thenReturn(zeile(11L));

    // When
    repository.save(firma(null));

    // Then
    verify(jpa).save(gespeicherte.capture());
    assertThat(gespeicherte.getValue())
        .satisfies(
            zeile -> assertThat(zeile.getId()).isNull(),
            zeile -> assertThat(zeile.getName()).isEqualTo("Adler AG"),
            zeile -> assertThat(zeile.getStrasse()).isEqualTo("Am Wall 1"),
            zeile -> assertThat(zeile.getPlz()).isEqualTo("28195"),
            zeile -> assertThat(zeile.getOrt()).isEqualTo("Bremen"),
            zeile -> assertThat(zeile.getLand()).isEqualTo("Deutschland"),
            zeile -> assertThat(zeile.getSteuernummer()).isEqualTo("75/123/45678"),
            zeile -> assertThat(zeile.getUmsatzsteuerId()).isEqualTo("DE123456789"),
            zeile -> assertThat(zeile.isAktiv()).isTrue(),
            zeile -> assertThat(zeile.getCreatedAt()).isEqualTo(ANGELEGT),
            zeile -> assertThat(zeile.getUpdatedAt()).isEqualTo(GEAENDERT));
  }

  @Test
  void save_givenARetiredFirmaWithoutOptionalValues_thenWritesThemAsAbsent() {
    // Given
    final Firma ohneAngaben =
        new Firma(
            null,
            "Adler AG",
            new Anschrift(null, null, null, null),
            null,
            null,
            false,
            ANGELEGT,
            GEAENDERT);
    when(jpa.save(any(FirmaEntity.class))).thenReturn(zeile(11L));

    // When
    repository.save(ohneAngaben);

    // Then
    verify(jpa).save(gespeicherte.capture());
    assertThat(gespeicherte.getValue())
        .satisfies(
            zeile -> assertThat(zeile.getStrasse()).isNull(),
            zeile -> assertThat(zeile.getPlz()).isNull(),
            zeile -> assertThat(zeile.getOrt()).isNull(),
            zeile -> assertThat(zeile.getLand()).isNull(),
            zeile -> assertThat(zeile.getSteuernummer()).isNull(),
            zeile -> assertThat(zeile.getUmsatzsteuerId()).isNull(),
            zeile -> assertThat(zeile.isAktiv()).isFalse());
  }

  @Test
  void save_thenReturnsTheFirmaWithTheGeneratedId() {
    // Given
    when(jpa.save(any(FirmaEntity.class))).thenReturn(zeile(11L));

    // When
    final Firma gesichert = repository.save(firma(null));

    // Then
    assertThat(gesichert).isEqualTo(firma(11L));
  }

  @Test
  void findById_givenAKnownFirma_thenTranslatesTheRow() {
    // Given
    when(jpa.findById(11L)).thenReturn(Optional.of(zeile(11L)));

    // When
    final Optional<Firma> gefunden = repository.findById(11L);

    // Then
    assertThat(gefunden).contains(firma(11L));
  }

  @Test
  void findById_givenAnUnknownId_thenEmpty() {
    // Given
    when(jpa.findById(11L)).thenReturn(Optional.empty());

    // When
    final Optional<Firma> gefunden = repository.findById(11L);

    // Then
    assertThat(gefunden).isEmpty();
  }

  @Test
  void uebersicht_thenPassesSearchTextAndSwitchThrough() {
    // Given
    when(jpa.uebersicht("adler", true)).thenReturn(List.of(zeile(11L)));

    // When
    final List<Firma> zeilen = repository.uebersicht("adler", true);

    // Then
    assertThat(zeilen).containsExactly(firma(11L));
  }

  @Test
  void uebersicht_givenNoMatch_thenEmptyList() {
    // Given
    when(jpa.uebersicht("fremd", false)).thenReturn(List.of());

    // When
    final List<Firma> zeilen = repository.uebersicht("fremd", false);

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
