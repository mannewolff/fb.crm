package org.mwolff.fbcrm.arbeitszeit.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.arbeitszeit.domain.Zeiteintrag;

/**
 * Die Uebersetzung zwischen Zeiteintrag und Zeile — in beide Richtungen, und die vier Summen.
 *
 * <p>Der Adapter steht hier gegen eine gemockte Spring-Data-Schnittstelle, damit die Abbildung
 * selbst geprueft ist und nicht nur ihr Zusammenspiel mit der Datenbank ({@code
 * JpaZeiteintragRepositoryIT}). Gegenstand ist vor allem: Die Summen entstehen aus den Minuten der
 * Zeilen und werden erst danach in Stunden umgerechnet (Plan #194, E5), jede angefragte Position
 * steht im Ergebnis — auch die ohne Eintrag —, und der Monat wird zu seinem ersten und letzten Tag.
 *
 * <p>Die beiden Summen <b>ohne</b> Positionsmenge (Issue #211) stehen daneben und zeigen genau den
 * Unterschied: Sie fragen nach nichts und liefern nur die Positionen, zu denen es einen Eintrag
 * gibt — eine Position ohne Zeit fehlt darin, statt mit {@code 0.00} darin zu stehen.
 */
@ExtendWith(MockitoExtension.class)
class JpaZeiteintragRepositoryTest {

  private static final long EINTRAG_ID = 42L;
  private static final long KONZEPTION = 7L;
  private static final long BERATUNG = 8L;
  private static final LocalDate TAG = LocalDate.of(2026, 11, 12);
  private static final Instant ANGELEGT = Instant.parse("2026-11-12T08:00:00Z");
  private static final Instant GEAENDERT = Instant.parse("2026-11-13T09:15:00Z");
  private static final YearMonth NOVEMBER = YearMonth.of(2026, 11);

  /** Zwei Positionen in fester Reihenfolge — damit die gebundene Liste vorhersagbar bleibt. */
  private static final Set<Long> BEIDE_POSITIONEN =
      new LinkedHashSet<>(List.of(KONZEPTION, BERATUNG));

  @Mock private SpringDataZeiteintragRepository zeilen;

  @Captor private ArgumentCaptor<ZeiteintragEntity> gespeicherte;

  @Captor private ArgumentCaptor<LocalDate> vonTag;

  @Captor private ArgumentCaptor<LocalDate> bisTag;

  @InjectMocks private JpaZeiteintragRepository repository;

  private static Zeiteintrag zeiteintrag(final String von, final String bis) {
    return new Zeiteintrag(
        null, KONZEPTION, TAG, LocalTime.parse(von), LocalTime.parse(bis), ANGELEGT, GEAENDERT);
  }

  private static ZeiteintragEntity zeile(
      final long positionId, final String tag, final String von, final String bis) {
    return new ZeiteintragEntity(
        EINTRAG_ID,
        positionId,
        LocalDate.parse(tag),
        LocalTime.parse(von),
        LocalTime.parse(bis),
        ANGELEGT,
        GEAENDERT);
  }

  @Test
  void save_thenHandsEveryFieldToTheRow() {
    // Given
    when(zeilen.save(any(ZeiteintragEntity.class)))
        .thenReturn(zeile(KONZEPTION, "2026-11-12", "09:00", "10:45"));

    // When
    repository.save(zeiteintrag("09:00", "10:45"));

    // Then
    verify(zeilen).save(gespeicherte.capture());
    assertThat(gespeicherte.getValue())
        .extracting(
            ZeiteintragEntity::getAngebotPositionId,
            ZeiteintragEntity::getTag,
            ZeiteintragEntity::getVon,
            ZeiteintragEntity::getBis,
            ZeiteintragEntity::getCreatedAt,
            ZeiteintragEntity::getUpdatedAt)
        .containsExactly(
            KONZEPTION, TAG, LocalTime.of(9, 0), LocalTime.of(10, 45), ANGELEGT, GEAENDERT);
  }

  @Test
  void save_thenReturnsTheStoredEntryWithItsId() {
    // Given
    when(zeilen.save(any(ZeiteintragEntity.class)))
        .thenReturn(zeile(KONZEPTION, "2026-11-12", "09:00", "10:45"));

    // When
    final Zeiteintrag gesichert = repository.save(zeiteintrag("09:00", "10:45"));

    // Then
    assertThat(gesichert.id()).isEqualTo(EINTRAG_ID);
  }

  @Test
  void findById_givenAKnownId_thenTranslatesTheRowBack() {
    // Given
    when(zeilen.findById(EINTRAG_ID))
        .thenReturn(Optional.of(zeile(KONZEPTION, "2026-11-12", "09:00", "10:45")));

    // When
    final Optional<Zeiteintrag> gefunden = repository.findById(EINTRAG_ID);

    // Then
    assertThat(gefunden)
        .contains(
            new Zeiteintrag(
                EINTRAG_ID,
                KONZEPTION,
                TAG,
                LocalTime.of(9, 0),
                LocalTime.of(10, 45),
                ANGELEGT,
                GEAENDERT));
  }

  @Test
  void findById_givenAnUnknownId_thenEmpty() {
    // Given
    when(zeilen.findById(EINTRAG_ID)).thenReturn(Optional.empty());

    // When
    final Optional<Zeiteintrag> gefunden = repository.findById(EINTRAG_ID);

    // Then
    assertThat(gefunden).isEmpty();
  }

  @Test
  void findImZeitraum_thenPassesBothBoundsAndTranslatesEveryRow() {
    // Given
    when(zeilen.findImZeitraum(any(LocalDate.class), any(LocalDate.class)))
        .thenReturn(
            List.of(
                zeile(KONZEPTION, "2026-11-12", "09:00", "10:45"),
                zeile(BERATUNG, "2026-11-13", "13:00", "15:00")));

    // When
    final List<Zeiteintrag> gefunden =
        repository.findImZeitraum(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));

    // Then
    assertThat(gefunden)
        .extracting(Zeiteintrag::angebotPositionId, Zeiteintrag::tag)
        .containsExactly(
            tuple(KONZEPTION, LocalDate.of(2026, 11, 12)),
            tuple(BERATUNG, LocalDate.of(2026, 11, 13)));
  }

  @Test
  void findImZeitraum_thenAsksForExactlyTheRequestedRange() {
    // Given
    when(zeilen.findImZeitraum(any(LocalDate.class), any(LocalDate.class))).thenReturn(List.of());

    // When
    repository.findImZeitraum(LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 12));

    // Then — der einzelne Tag der Ueberschneidungspruefung ist ein Zeitraum von einem Tag.
    verify(zeilen).findImZeitraum(vonTag.capture(), bisTag.capture());
    assertThat(List.of(vonTag.getValue(), bisTag.getValue()))
        .containsExactly(LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 12));
  }

  @Test
  void delete_thenRemovesTheRow() {
    // When
    repository.delete(EINTRAG_ID);

    // Then
    verify(zeilen).deleteById(EINTRAG_ID);
  }

  @Test
  void angefallenJePosition_thenSumsTheMinutesBeforeConvertingToHours() {
    // Given — 1:45 und 2:00 an derselben Position ergeben 3,75 Stunden.
    when(zeilen.findByPositionen(List.of(KONZEPTION, BERATUNG)))
        .thenReturn(
            List.of(
                zeile(KONZEPTION, "2026-10-05", "09:00", "10:45"),
                zeile(KONZEPTION, "2026-11-12", "13:00", "15:00")));

    // When
    final Map<Long, BigDecimal> angefallen = repository.angefallenJePosition(BEIDE_POSITIONEN);

    // Then
    assertThat(angefallen.get(KONZEPTION)).isEqualByComparingTo("3.75");
  }

  @Test
  void angefallenJePosition_givenAPositionWithoutEntries_thenZero() {
    // Given
    when(zeilen.findByPositionen(List.of(KONZEPTION, BERATUNG)))
        .thenReturn(List.of(zeile(KONZEPTION, "2026-11-12", "09:00", "10:45")));

    // When
    final Map<Long, BigDecimal> angefallen = repository.angefallenJePosition(BEIDE_POSITIONEN);

    // Then — jede angefragte Position steht im Ergebnis; ohne Eintrag mit 0,00.
    assertThat(angefallen.get(BERATUNG)).isEqualByComparingTo("0.00");
  }

  @Test
  void stundenJePositionImMonat_thenAsksForTheFirstAndTheLastDayOfTheMonth() {
    // Given
    when(zeilen.findByPositionenImZeitraum(any(), any(LocalDate.class), any(LocalDate.class)))
        .thenReturn(List.of());

    // When
    repository.stundenJePositionImMonat(Set.of(KONZEPTION), NOVEMBER);

    // Then
    verify(zeilen).findByPositionenImZeitraum(any(), vonTag.capture(), bisTag.capture());
    assertThat(List.of(vonTag.getValue(), bisTag.getValue()))
        .containsExactly(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));
  }

  @Test
  void stundenJePositionImMonat_thenSumsTheRowsOfThatMonth() {
    // Given — das Beispiel aus #193: im November 12 Stunden an einer Position.
    when(zeilen.findByPositionenImZeitraum(
            List.of(KONZEPTION), LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)))
        .thenReturn(
            List.of(
                zeile(KONZEPTION, "2026-11-12", "09:00", "17:00"),
                zeile(KONZEPTION, "2026-11-13", "09:00", "13:00")));

    // When
    final Map<Long, BigDecimal> stunden =
        repository.stundenJePositionImMonat(Set.of(KONZEPTION), NOVEMBER);

    // Then
    assertThat(stunden.get(KONZEPTION)).isEqualByComparingTo("12.00");
  }

  @Test
  void alleAngefallenJePosition_thenSumsTheMinutesOfEveryRowInTheBestand() {
    // Given — 1:45 und 2:00 an derselben Position ergeben 3,75 Stunden, ohne Positionsmenge.
    when(zeilen.findAll())
        .thenReturn(
            List.of(
                zeile(KONZEPTION, "2026-10-05", "09:00", "10:45"),
                zeile(KONZEPTION, "2026-11-12", "13:00", "15:00"),
                zeile(BERATUNG, "2026-11-13", "09:00", "10:00")));

    // When
    final Map<Long, BigDecimal> alle = repository.alleAngefallenJePosition();

    // Then
    assertThat(alle.get(KONZEPTION)).isEqualByComparingTo("3.75");
  }

  @Test
  void alleAngefallenJePosition_givenAPositionWithoutEntries_thenItIsMissing() {
    // Given — anders als bei den Summen mit Positionsmenge wird hier nach nichts gefragt.
    when(zeilen.findAll()).thenReturn(List.of(zeile(KONZEPTION, "2026-11-12", "09:00", "10:45")));

    // When
    final Map<Long, BigDecimal> alle = repository.alleAngefallenJePosition();

    // Then
    assertThat(alle).containsOnlyKeys(Long.valueOf(KONZEPTION));
  }

  @Test
  void alleAngefallenJePosition_givenAnEmptyBestand_thenAnEmptyResult() {
    // Given
    when(zeilen.findAll()).thenReturn(List.of());

    // When
    final Map<Long, BigDecimal> alle = repository.alleAngefallenJePosition();

    // Then
    assertThat(alle).isEmpty();
  }

  @Test
  void alleStundenJePositionImMonat_thenAsksForTheFirstAndTheLastDayOfTheMonth() {
    // Given
    when(zeilen.findImZeitraum(any(LocalDate.class), any(LocalDate.class))).thenReturn(List.of());

    // When
    repository.alleStundenJePositionImMonat(NOVEMBER);

    // Then
    verify(zeilen).findImZeitraum(vonTag.capture(), bisTag.capture());
    assertThat(List.of(vonTag.getValue(), bisTag.getValue()))
        .containsExactly(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));
  }

  @Test
  void alleStundenJePositionImMonat_thenSumsTheRowsOfThatMonthAcrossPositions() {
    // Given
    when(zeilen.findImZeitraum(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)))
        .thenReturn(
            List.of(
                zeile(KONZEPTION, "2026-11-12", "09:00", "17:00"),
                zeile(KONZEPTION, "2026-11-13", "09:00", "13:00"),
                zeile(BERATUNG, "2026-11-13", "09:00", "10:45")));

    // When
    final Map<Long, BigDecimal> november = repository.alleStundenJePositionImMonat(NOVEMBER);

    // Then
    assertThat(november.get(KONZEPTION)).isEqualByComparingTo("12.00");
    assertThat(november.get(BERATUNG)).isEqualByComparingTo("1.75");
  }

  @Test
  void alleStundenJePositionImMonat_givenAMonthWithoutAnyEntry_thenAnEmptyResult() {
    // Given — keine Position wird vorbelegt, also steht auch keine mit 0,00 darin.
    when(zeilen.findImZeitraum(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)))
        .thenReturn(List.of());

    // When
    final Map<Long, BigDecimal> november = repository.alleStundenJePositionImMonat(NOVEMBER);

    // Then
    assertThat(november).isEmpty();
  }
}
