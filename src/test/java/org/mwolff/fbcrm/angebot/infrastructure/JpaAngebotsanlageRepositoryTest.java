package org.mwolff.fbcrm.angebot.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
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
import org.mwolff.fbcrm.angebot.domain.Angebotsanlage;
import org.mwolff.fbcrm.angebot.domain.Vorschauart;

/**
 * Die Uebersetzung zwischen Anlage und Zeile — in beide Richtungen.
 *
 * <p>Der Adapter steht hier gegen ein gemocktes Spring-Data-Repository, damit die Abbildung selbst
 * geprueft ist; dass sein SQL-Weg traegt, belegt {@code AngebotAnlageSchemaIT}.
 */
@ExtendWith(MockitoExtension.class)
class JpaAngebotsanlageRepositoryTest {

  private static final Instant ANGELEGT = Instant.parse("2026-09-30T08:00:00Z");
  private static final String NAME = "Bericht.pdf";
  private static final String SCHLUESSEL = "angebot/11/anlage/3f2c";

  @Mock private SpringDataAngebotAnlageRepository zeilen;

  @Captor private ArgumentCaptor<AngebotAnlageEntity> gespeicherte;

  @InjectMocks private JpaAngebotsanlageRepository repository;

  private static Angebotsanlage anlage() {
    return new Angebotsanlage(7L, 11L, NAME, 1024L, Vorschauart.PDF, SCHLUESSEL, ANGELEGT);
  }

  private static AngebotAnlageEntity zeile(final long id, final String name) {
    return new AngebotAnlageEntity(
        Long.valueOf(id), 11L, name, 1024L, Vorschauart.PDF, SCHLUESSEL, ANGELEGT);
  }

  @Test
  void save_thenWritesEveryFieldIntoTheRow() {
    // Given
    when(zeilen.save(any(AngebotAnlageEntity.class))).thenReturn(zeile(7L, NAME));

    // When
    repository.save(anlage());

    // Then
    verify(zeilen).save(gespeicherte.capture());
    assertThat(gespeicherte.getValue())
        .satisfies(
            zeile -> assertThat(zeile.getId()).isEqualTo(7L),
            zeile -> assertThat(zeile.getAngebotId()).isEqualTo(11L),
            zeile -> assertThat(zeile.getDateiName()).isEqualTo(NAME),
            zeile -> assertThat(zeile.getGroesse()).isEqualTo(1024L),
            zeile -> assertThat(zeile.getVorschauArt()).isEqualTo(Vorschauart.PDF),
            zeile -> assertThat(zeile.getObjektSchluessel()).isEqualTo(SCHLUESSEL),
            zeile -> assertThat(zeile.getCreatedAt()).isEqualTo(ANGELEGT));
  }

  @Test
  void save_givenAnUnsavedAttachment_thenWritesTheRowWithoutAnIdAndReturnsTheGeneratedOne() {
    // Given
    when(zeilen.save(any(AngebotAnlageEntity.class))).thenReturn(zeile(7L, NAME));

    // When
    final Angebotsanlage gesichert =
        repository.save(
            new Angebotsanlage(null, 11L, NAME, 1024L, Vorschauart.PDF, SCHLUESSEL, ANGELEGT));

    // Then
    verify(zeilen).save(gespeicherte.capture());
    assertThat(gespeicherte.getValue().getId()).isNull();
    assertThat(gesichert.id()).isEqualTo(7L);
  }

  @Test
  void save_givenNoPreviewType_thenKeepsItEmptyInBothDirections() {
    // Given
    final AngebotAnlageEntity ohneVorschau =
        new AngebotAnlageEntity(7L, 11L, "Zahlen.xlsx", 4096L, null, SCHLUESSEL, ANGELEGT);
    when(zeilen.save(any(AngebotAnlageEntity.class))).thenReturn(ohneVorschau);

    // When
    final Angebotsanlage gesichert =
        repository.save(
            new Angebotsanlage(7L, 11L, "Zahlen.xlsx", 4096L, null, SCHLUESSEL, ANGELEGT));

    // Then
    verify(zeilen).save(gespeicherte.capture());
    assertThat(gespeicherte.getValue().getVorschauArt()).isNull();
    assertThat(gesichert.vorschauArt()).isNull();
  }

  @Test
  void findById_thenTranslatesTheRow() {
    // Given
    when(zeilen.findById(7L)).thenReturn(Optional.of(zeile(7L, NAME)));

    // When
    final Optional<Angebotsanlage> gefunden = repository.findById(7L);

    // Then
    assertThat(gefunden).contains(anlage());
  }

  @Test
  void findById_givenAnUnknownId_thenEmpty() {
    // Given
    when(zeilen.findById(7L)).thenReturn(Optional.empty());

    // When
    final Optional<Angebotsanlage> gefunden = repository.findById(7L);

    // Then
    assertThat(gefunden).isEmpty();
  }

  @Test
  void findByAngebot_thenTranslatesEveryRowOfThatOffer() {
    // Given
    when(zeilen.findByAngebot(11L)).thenReturn(List.of(zeile(7L, NAME), zeile(8L, "Skizze.png")));

    // When
    final List<Angebotsanlage> gefunden = repository.findByAngebot(11L);

    // Then
    assertThat(gefunden)
        .extracting(Angebotsanlage::id, Angebotsanlage::dateiName)
        .containsExactly(tuple(7L, NAME), tuple(8L, "Skizze.png"));
  }

  @Test
  void findByAngebot_givenAnOfferWithoutAttachments_thenEmpty() {
    // Given
    when(zeilen.findByAngebot(11L)).thenReturn(List.of());

    // When
    final List<Angebotsanlage> gefunden = repository.findByAngebot(11L);

    // Then
    assertThat(gefunden).isEmpty();
  }

  @Test
  void deleteById_thenRemovesTheRow() {
    // When
    repository.deleteById(7L);

    // Then
    verify(zeilen).deleteById(7L);
  }
}
