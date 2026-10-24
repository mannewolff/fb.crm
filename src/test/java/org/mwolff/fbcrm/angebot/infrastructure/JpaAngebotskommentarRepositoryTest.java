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
  private static final long VERFASSER = 3L;
  private static final long URHEBER = 5L;
  private static final Instant GELOESCHT = Instant.parse("2026-10-01T10:00:00Z");

  @Mock private SpringDataAngebotKommentarRepository zeilen;

  @Captor private ArgumentCaptor<AngebotKommentarEntity> gespeicherte;

  @InjectMocks private JpaAngebotskommentarRepository repository;

  private static Angebotskommentar kommentar() {
    return new Angebotskommentar(7L, 11L, VERFASSER, TEXT, ANGELEGT, GEAENDERT);
  }

  private static AngebotKommentarEntity zeile(final long id, final String text) {
    return new AngebotKommentarEntity(
        new Angebotskommentar(Long.valueOf(id), 11L, VERFASSER, text, ANGELEGT, GEAENDERT));
  }

  /** Ein Kommentar mit Stand, Urheber und Loeschkennzeichen (Plan #415, E1, E17). */
  private static Angebotskommentar gekennzeichnet() {
    return new Angebotskommentar(
        7L, 11L, VERFASSER, TEXT, ANGELEGT, GEAENDERT, 4L, URHEBER, GELOESCHT, URHEBER);
  }

  @Test
  void save_thenWritesEveryFieldIntoTheRow() {
    // Given
    when(zeilen.saveAndFlush(any(AngebotKommentarEntity.class))).thenReturn(zeile(7L, TEXT));

    // When
    repository.save(kommentar());

    // Then
    verify(zeilen).saveAndFlush(gespeicherte.capture());
    assertThat(gespeicherte.getValue())
        .satisfies(
            zeile -> assertThat(zeile.getId()).isEqualTo(7L),
            zeile -> assertThat(zeile.getAngebotId()).isEqualTo(11L),
            zeile -> assertThat(zeile.getVerfasserId()).isEqualTo(VERFASSER),
            zeile -> assertThat(zeile.getText()).isEqualTo(TEXT),
            zeile -> assertThat(zeile.getCreatedAt()).isEqualTo(ANGELEGT),
            zeile -> assertThat(zeile.getUpdatedAt()).isEqualTo(GEAENDERT));
  }

  @Test
  void save_givenAnUnsavedComment_thenWritesTheRowWithoutAnIdAndReturnsTheGeneratedOne() {
    // Given
    when(zeilen.saveAndFlush(any(AngebotKommentarEntity.class))).thenReturn(zeile(7L, TEXT));

    // When
    final Angebotskommentar gesichert =
        repository.save(new Angebotskommentar(null, 11L, VERFASSER, TEXT, ANGELEGT, ANGELEGT));

    // Then
    verify(zeilen).saveAndFlush(gespeicherte.capture());
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
  void save_thenWritesStandUrheberAndKennzeichenIntoTheRow() {
    // Given — E1, E17: der vorgelegte Stand geht mit, damit Hibernate einen alten Stand bemerkt.
    when(zeilen.saveAndFlush(any(AngebotKommentarEntity.class)))
        .thenAnswer(aufruf -> aufruf.getArgument(0));

    // When
    final Angebotskommentar gesichert = repository.save(gekennzeichnet());

    // Then — und zurueck uebersetzt traegt der Kommentar dieselben Angaben.
    verify(zeilen).saveAndFlush(gespeicherte.capture());
    assertThat(gespeicherte.getValue())
        .satisfies(
            zeile -> assertThat(zeile.getVersion()).isEqualTo(4L),
            zeile -> assertThat(zeile.getGeaendertVon()).isEqualTo(URHEBER),
            zeile -> assertThat(zeile.getGeloeschtAm()).isEqualTo(GELOESCHT),
            zeile -> assertThat(zeile.getGeloeschtVon()).isEqualTo(URHEBER));
    assertThat(gesichert).isEqualTo(gekennzeichnet());
  }

  @Test
  void findById_givenADeletedComment_thenTranslatesItWithItsKennzeichen() {
    // Given — E16: der Anwendungsfall braucht Urheber und Zeitpunkt der Loeschung.
    when(zeilen.findById(7L)).thenReturn(Optional.of(new AngebotKommentarEntity(gekennzeichnet())));

    // When
    final Optional<Angebotskommentar> gefunden = repository.findById(7L);

    // Then
    assertThat(gefunden).contains(gekennzeichnet());
  }
}
