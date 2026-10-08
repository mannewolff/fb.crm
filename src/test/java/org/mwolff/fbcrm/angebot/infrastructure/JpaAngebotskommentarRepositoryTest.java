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
import org.mwolff.fbcrm.angebot.domain.Angebotskommentar;

/**
 * Die Uebersetzung zwischen Kommentar und Zeile — in beide Richtungen.
 *
 * <p>Der Adapter steht hier gegen ein gemocktes Spring-Data-Repository, damit die Abbildung selbst
 * geprueft ist. Dass sein SQL-Weg traegt, belegt der Integrationstest des Folgepakets.
 */
@ExtendWith(MockitoExtension.class)
class JpaAngebotskommentarRepositoryTest {

  private static final Instant ANGELEGT = Instant.parse("2026-09-28T08:00:00Z");
  private static final Instant GEAENDERT = Instant.parse("2026-09-30T14:05:00Z");
  private static final String TEXT = "Kunde ruft zurueck.";

  @Mock private SpringDataAngebotKommentarRepository zeilen;

  @Captor private ArgumentCaptor<AngebotKommentarEntity> gespeicherte;

  @InjectMocks private JpaAngebotskommentarRepository repository;

  private static Angebotskommentar kommentar() {
    return new Angebotskommentar(7L, 11L, TEXT, ANGELEGT, GEAENDERT);
  }

  private static AngebotKommentarEntity zeile(final long id, final String text) {
    return new AngebotKommentarEntity(Long.valueOf(id), 11L, text, ANGELEGT, GEAENDERT);
  }

  @Test
  void save_thenWritesEveryFieldIntoTheRow() {
    // Given
    when(zeilen.save(any(AngebotKommentarEntity.class))).thenReturn(zeile(7L, TEXT));

    // When
    repository.save(kommentar());

    // Then
    verify(zeilen).save(gespeicherte.capture());
    assertThat(gespeicherte.getValue())
        .satisfies(
            zeile -> assertThat(zeile.getId()).isEqualTo(7L),
            zeile -> assertThat(zeile.getAngebotId()).isEqualTo(11L),
            zeile -> assertThat(zeile.getText()).isEqualTo(TEXT),
            zeile -> assertThat(zeile.getCreatedAt()).isEqualTo(ANGELEGT),
            zeile -> assertThat(zeile.getUpdatedAt()).isEqualTo(GEAENDERT));
  }

  @Test
  void save_givenAnUnsavedComment_thenWritesTheRowWithoutAnIdAndReturnsTheGeneratedOne() {
    // Given
    when(zeilen.save(any(AngebotKommentarEntity.class))).thenReturn(zeile(7L, TEXT));

    // When
    final Angebotskommentar gesichert =
        repository.save(new Angebotskommentar(null, 11L, TEXT, ANGELEGT, ANGELEGT));

    // Then
    verify(zeilen).save(gespeicherte.capture());
    assertThat(gespeicherte.getValue().getId()).isNull();
    assertThat(gesichert.id()).isEqualTo(7L);
  }

  @Test
  void findById_thenTranslatesTheRow() {
    // Given
    when(zeilen.findById(7L)).thenReturn(Optional.of(zeile(7L, TEXT)));

    // When
    final Optional<Angebotskommentar> gefunden = repository.findById(7L);

    // Then
    assertThat(gefunden).contains(kommentar());
  }

  @Test
  void findById_givenAnUnknownId_thenEmpty() {
    // Given
    when(zeilen.findById(7L)).thenReturn(Optional.empty());

    // When
    final Optional<Angebotskommentar> gefunden = repository.findById(7L);

    // Then
    assertThat(gefunden).isEmpty();
  }

  @Test
  void findByAngebot_thenTranslatesEveryRowOfThatOffer() {
    // Given
    when(zeilen.findByAngebot(11L))
        .thenReturn(List.of(zeile(7L, TEXT), zeile(8L, "Termin steht.")));

    // When
    final List<Angebotskommentar> gefunden = repository.findByAngebot(11L);

    // Then
    assertThat(gefunden)
        .extracting(Angebotskommentar::id, Angebotskommentar::text)
        .containsExactly(tuple(7L, TEXT), tuple(8L, "Termin steht."));
  }

  @Test
  void findByAngebot_givenAnOfferWithoutComments_thenEmpty() {
    // Given
    when(zeilen.findByAngebot(11L)).thenReturn(List.of());

    // When
    final List<Angebotskommentar> gefunden = repository.findByAngebot(11L);

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
