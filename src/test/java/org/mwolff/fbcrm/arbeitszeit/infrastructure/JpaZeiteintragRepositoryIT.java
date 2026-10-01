package org.mwolff.fbcrm.arbeitszeit.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.mwolff.fbcrm.arbeitszeit.domain.Zeiteintrag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Der Arbeitszeit-Adapter gegen eine echte PostgreSQL-Instanz.
 *
 * <p>Gegenstand ist, was nur mit Datenbank geprueft werden kann: der Zeitraum mit beiden
 * eingeschlossenen Grenzen und die beiden Summen ueber echte Zeilen — einmal ueber alle Monate,
 * einmal ueber genau einen.
 */
class JpaZeiteintragRepositoryIT extends AbstractIntegrationTest {

  private static final Instant ANGELEGT = Instant.parse("2026-11-12T08:00:00Z");

  private final JpaZeiteintragRepository repository;
  private final JdbcTemplate jdbc;

  private long konzeption;
  private long beratung;

  @Autowired
  JpaZeiteintragRepositoryIT(final JpaZeiteintragRepository repository, final JdbcTemplate jdbc) {
    this.repository = repository;
    this.jdbc = jdbc;
  }

  @BeforeEach
  void leereFachtabellenUndLegeZweiPositionenAn() {
    jdbc.execute(
        "TRUNCATE arbeitszeit, rechnung_position, rechnung, angebot_position, angebot,"
            + " ansprechpartner, firma RESTART IDENTITY CASCADE");
    jdbc.update("INSERT INTO firma (name) VALUES ('IT Bildungshaus')");
    final long firmaId =
        jdbc.queryForObject("SELECT id FROM firma LIMIT 1", Long.class).longValue();
    jdbc.update(
        "INSERT INTO angebot (firma_id, status, angebot_datum)"
            + " VALUES (?, 'BESTELLT', DATE '2026-10-01')",
        Long.valueOf(firmaId));
    final long angebotId =
        jdbc.queryForObject("SELECT id FROM angebot LIMIT 1", Long.class).longValue();
    konzeption = position(angebotId, 1, "Konzeption");
    beratung = position(angebotId, 2, "Beratung");
  }

  private long position(final long angebotId, final int platz, final String bezeichnung) {
    jdbc.update(
        "INSERT INTO angebot_position"
            + " (angebot_id, position, bezeichnung, abrechnungsmodus, menge, einheit, einzelpreis)"
            + " VALUES (?, ?, ?, 'AUFWAND', 20, 'STUNDE', 120.00)",
        Long.valueOf(angebotId),
        Integer.valueOf(platz),
        bezeichnung);
    return jdbc.queryForObject(
            "SELECT id FROM angebot_position WHERE bezeichnung = ?", Long.class, bezeichnung)
        .longValue();
  }

  private Zeiteintrag gesichert(
      final long positionId, final String tag, final String von, final String bis) {
    return repository.save(
        new Zeiteintrag(
            null,
            positionId,
            LocalDate.parse(tag),
            LocalTime.parse(von),
            LocalTime.parse(bis),
            ANGELEGT,
            ANGELEGT));
  }

  @Test
  void save_thenAssignsAnIdFromTheDatabase() {
    // When
    final Zeiteintrag angelegt = gesichert(konzeption, "2026-11-12", "09:00", "10:45");

    // Then
    assertThat(angelegt.id()).isNotNull();
  }

  @Test
  void findById_afterSave_thenReturnsEveryStoredValue() {
    // Given
    final Zeiteintrag angelegt = gesichert(konzeption, "2026-11-12", "09:00", "10:45");

    // When
    final Optional<Zeiteintrag> gefunden = repository.findById(angelegt.requireId());

    // Then
    assertThat(gefunden).contains(angelegt);
  }

  @Test
  void delete_thenTheRowIsGone() {
    // Given
    final Zeiteintrag angelegt = gesichert(konzeption, "2026-11-12", "09:00", "10:45");

    // When
    repository.delete(angelegt.requireId());

    // Then
    assertThat(repository.findById(angelegt.requireId())).isEmpty();
  }

  @Test
  void findImZeitraum_thenIncludesBothBoundsAndNothingBeyond() {
    // Given
    gesichert(konzeption, "2026-10-31", "09:00", "10:00");
    gesichert(konzeption, "2026-11-01", "09:00", "10:00");
    gesichert(beratung, "2026-11-30", "09:00", "10:00");
    gesichert(konzeption, "2026-12-01", "09:00", "10:00");

    // When
    final List<Zeiteintrag> gefunden =
        repository.findImZeitraum(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));

    // Then
    assertThat(gefunden)
        .extracting(Zeiteintrag::tag)
        .containsExactlyInAnyOrder(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));
  }

  @Test
  void findImZeitraum_givenTheSameDayAsBothBounds_thenReturnsThatDayOnly() {
    // Given — so fragt die Ueberschneidungspruefung (A8).
    gesichert(konzeption, "2026-11-12", "09:00", "10:00");
    gesichert(beratung, "2026-11-13", "09:00", "10:00");

    // When
    final List<Zeiteintrag> gefunden =
        repository.findImZeitraum(LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 12));

    // Then
    assertThat(gefunden).extracting(Zeiteintrag::tag).containsExactly(LocalDate.of(2026, 11, 12));
  }

  @Test
  void angefallenJePosition_thenSumsEveryMonthAndKeepsPositionsWithoutTime() {
    // Given — das Beispiel aus #193: im Oktober 10, im November 12 Stunden an einer Position.
    gesichert(konzeption, "2026-10-05", "09:00", "17:00");
    gesichert(konzeption, "2026-10-06", "09:00", "11:00");
    gesichert(konzeption, "2026-11-12", "09:00", "17:00");
    gesichert(konzeption, "2026-11-13", "09:00", "13:00");

    // When
    final Map<Long, BigDecimal> angefallen =
        repository.angefallenJePosition(Set.of(konzeption, beratung));

    // Then
    assertThat(angefallen)
        .hasSize(2)
        .containsEntry(konzeption, new BigDecimal("22.00"))
        .containsEntry(beratung, new BigDecimal("0.00"));
  }

  @Test
  void angefallenJePosition_givenNoPositionAtAll_thenAnEmptyResult() {
    // Given — eine leere Anfrage ist keine Abfrage ohne Bedingung.
    gesichert(konzeption, "2026-11-12", "09:00", "17:00");

    // When
    final Map<Long, BigDecimal> angefallen = repository.angefallenJePosition(Set.of());

    // Then
    assertThat(angefallen).isEmpty();
  }

  @Test
  void stundenJePositionImMonat_thenCountsOnlyThatMonth() {
    // Given — dieselben Eintraege wie oben.
    gesichert(konzeption, "2026-10-05", "09:00", "17:00");
    gesichert(konzeption, "2026-10-06", "09:00", "11:00");
    gesichert(konzeption, "2026-11-12", "09:00", "17:00");
    gesichert(konzeption, "2026-11-13", "09:00", "13:00");

    // When
    final Map<Long, BigDecimal> november =
        repository.stundenJePositionImMonat(Set.of(konzeption), YearMonth.of(2026, 11));

    // Then
    assertThat(november).containsEntry(konzeption, new BigDecimal("12.00"));
  }

  @Test
  void stundenJePositionImMonat_thenIncludesTheFirstAndTheLastDayOfTheMonth() {
    // Given — die Monatsgrenzen zaehlen mit.
    gesichert(konzeption, "2026-11-01", "09:00", "10:00");
    gesichert(konzeption, "2026-11-30", "09:00", "10:45");
    gesichert(konzeption, "2026-10-31", "09:00", "17:00");
    gesichert(konzeption, "2026-12-01", "09:00", "17:00");

    // When
    final Map<Long, BigDecimal> november =
        repository.stundenJePositionImMonat(Set.of(konzeption), YearMonth.of(2026, 11));

    // Then
    assertThat(november).containsEntry(konzeption, new BigDecimal("2.75"));
  }

  @Test
  void stundenJePositionImMonat_givenAMonthWithoutAnyEntry_thenZero() {
    // Given
    gesichert(konzeption, "2026-11-12", "09:00", "17:00");

    // When
    final Map<Long, BigDecimal> dezember =
        repository.stundenJePositionImMonat(Set.of(konzeption), YearMonth.of(2026, 12));

    // Then
    assertThat(dezember).containsEntry(konzeption, new BigDecimal("0.00"));
  }
}
