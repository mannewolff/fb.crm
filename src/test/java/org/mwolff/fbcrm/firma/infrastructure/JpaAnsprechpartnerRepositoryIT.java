package org.mwolff.fbcrm.firma.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.mwolff.fbcrm.firma.domain.Anschrift;
import org.mwolff.fbcrm.firma.domain.Ansprechpartner;
import org.mwolff.fbcrm.firma.domain.Firma;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Der Ansprechpartner-Adapter gegen eine echte PostgreSQL-Instanz. */
class JpaAnsprechpartnerRepositoryIT extends AbstractIntegrationTest {

  private static final Instant ANGELEGT = Instant.parse("2026-09-01T08:00:00Z");
  private static final Instant GEAENDERT = Instant.parse("2026-09-18T12:00:00Z");

  private final JpaAnsprechpartnerRepository repository;
  private final JpaFirmaRepository firmen;
  private final JdbcTemplate jdbc;

  @Autowired
  JpaAnsprechpartnerRepositoryIT(
      final JpaAnsprechpartnerRepository repository,
      final JpaFirmaRepository firmen,
      final JdbcTemplate jdbc) {
    this.repository = repository;
    this.firmen = firmen;
    this.jdbc = jdbc;
  }

  @BeforeEach
  void leereFachtabellen() {
    jdbc.execute("TRUNCATE ansprechpartner, firma RESTART IDENTITY CASCADE");
  }

  private long neueFirma(final String name) {
    return firmen
        .save(
            new Firma(
                null,
                name,
                new Anschrift(null, null, null, null),
                null,
                null,
                true,
                ANGELEGT,
                ANGELEGT))
        .requireId();
  }

  private static Ansprechpartner neuerAnsprechpartner(
      final long firmaId, final String nachname, final boolean aktiv) {
    return new Ansprechpartner(
        null,
        firmaId,
        "Max",
        nachname,
        "Einkauf",
        "max@firma.de",
        "0421 123456",
        "0170 123456",
        aktiv,
        ANGELEGT,
        ANGELEGT);
  }

  @Test
  void save_thenAssignsAnIdFromTheDatabase() {
    // Given
    final long firmaId = neueFirma("Adler AG");

    // When
    final Ansprechpartner gesichert =
        repository.save(neuerAnsprechpartner(firmaId, "Mustermann", true));

    // Then
    assertThat(gesichert.id()).isNotNull();
  }

  @Test
  void findById_afterSave_thenReturnsEveryStoredValue() {
    // Given
    final long firmaId = neueFirma("Adler AG");
    final Ansprechpartner gesichert =
        repository.save(neuerAnsprechpartner(firmaId, "Mustermann", true));

    // When
    final Optional<Ansprechpartner> gefunden = repository.findById(gesichert.requireId());

    // Then
    assertThat(gefunden).contains(gesichert);
  }

  @Test
  void findById_afterSavingWithoutOptionalValues_thenReadsThemBackAsAbsent() {
    // Given
    final long firmaId = neueFirma("Adler AG");
    final Ansprechpartner gesichert =
        repository.save(
            new Ansprechpartner(
                null,
                firmaId,
                null,
                "Mustermann",
                null,
                null,
                null,
                null,
                false,
                ANGELEGT,
                ANGELEGT));

    // When
    final Optional<Ansprechpartner> gefunden = repository.findById(gesichert.requireId());

    // Then
    assertThat(gefunden)
        .hasValueSatisfying(
            partner -> {
              assertThat(partner.vorname()).isNull();
              assertThat(partner.rolle()).isNull();
              assertThat(partner.email()).isNull();
              assertThat(partner.telefonFestnetz()).isNull();
              assertThat(partner.telefonMobil()).isNull();
              assertThat(partner.aktiv()).isFalse();
            });
  }

  @Test
  void findById_givenAnUnknownId_thenEmpty() {
    // When
    final Optional<Ansprechpartner> gefunden = repository.findById(4711L);

    // Then
    assertThat(gefunden).isEmpty();
  }

  @Test
  void save_givenAChangedAnsprechpartner_thenStoresTheNewValues() {
    // Given
    final long firmaId = neueFirma("Adler AG");
    final Ansprechpartner gesichert =
        repository.save(neuerAnsprechpartner(firmaId, "Mustermann", true));

    // When
    repository.save(
        gesichert.geaendert(
            "Erika", "Musterfrau", "Vertrieb", "erika@firma.de", null, null, GEAENDERT));

    // Then
    assertThat(repository.findById(gesichert.requireId()))
        .hasValueSatisfying(
            partner -> {
              assertThat(partner.nachname()).isEqualTo("Musterfrau");
              assertThat(partner.telefonFestnetz()).isNull();
              assertThat(partner.updatedAt()).isEqualTo(GEAENDERT);
            });
  }

  @Test
  void findByFirma_thenSortsByNachnameIgnoringCase() {
    // Given
    final long firmaId = neueFirma("Adler AG");
    repository.save(neuerAnsprechpartner(firmaId, "beckmann", true));
    repository.save(neuerAnsprechpartner(firmaId, "Adam", false));

    // When
    final List<Ansprechpartner> partner = repository.findByFirma(firmaId);

    // Then
    assertThat(partner).extracting(Ansprechpartner::nachname).containsExactly("Adam", "beckmann");
  }

  @Test
  void findByFirma_givenTwoWithTheSameNachname_thenKeepsTheOrderBetweenTwoCalls() {
    // Given
    final long firmaId = neueFirma("Adler AG");
    final Ansprechpartner erster = repository.save(neuerAnsprechpartner(firmaId, "Adam", true));
    final Ansprechpartner zweiter = repository.save(neuerAnsprechpartner(firmaId, "Adam", true));

    // When
    final List<Ansprechpartner> ersterAufruf = repository.findByFirma(firmaId);
    final List<Ansprechpartner> zweiterAufruf = repository.findByFirma(firmaId);

    // Then
    assertThat(List.of(ersterAufruf, zweiterAufruf))
        .allSatisfy(
            aufruf ->
                assertThat(aufruf)
                    .extracting(Ansprechpartner::id)
                    .containsExactly(erster.requireId(), zweiter.requireId()));
  }

  @Test
  void findByFirma_thenLeavesOutTheAnsprechpartnerOfAnotherFirma() {
    // Given
    final long adler = neueFirma("Adler AG");
    final long baum = neueFirma("Baum GmbH");
    repository.save(neuerAnsprechpartner(adler, "Mustermann", true));
    repository.save(neuerAnsprechpartner(baum, "Fremd", true));

    // When
    final List<Ansprechpartner> partner = repository.findByFirma(adler);

    // Then
    assertThat(partner).extracting(Ansprechpartner::nachname).containsExactly("Mustermann");
  }

  @Test
  void findByFirma_givenAFirmaWithoutAnsprechpartner_thenEmptyList() {
    // Given
    final long firmaId = neueFirma("Adler AG");

    // When
    final List<Ansprechpartner> partner = repository.findByFirma(firmaId);

    // Then
    assertThat(partner).isEmpty();
  }

  @Test
  void zaehleAktiveJeFirma_thenCountsOnlyTheActiveOnes() {
    // Given
    final long adler = neueFirma("Adler AG");
    final long baum = neueFirma("Baum GmbH");
    repository.save(neuerAnsprechpartner(adler, "Mustermann", true));
    repository.save(neuerAnsprechpartner(adler, "Musterfrau", true));
    repository.save(neuerAnsprechpartner(adler, "Stillgelegt", false));
    repository.save(neuerAnsprechpartner(baum, "Nurstillgelegt", false));

    // When
    final Map<Long, Long> anzahlen = repository.zaehleAktiveJeFirma(List.of(adler, baum));

    // Then
    assertThat(anzahlen).containsExactly(entry(adler, 2L), entry(baum, 0L));
  }

  @Test
  void zaehleAktiveJeFirma_givenNoIds_thenAnEmptyMap() {
    // Given
    neueFirma("Adler AG");

    // When — die leere Anfrage geht bis in die Datenbank; hier steht, dass Postgres sie annimmt.
    final Map<Long, Long> anzahlen = repository.zaehleAktiveJeFirma(List.of());

    // Then
    assertThat(anzahlen).isEmpty();
  }

  @Test
  void zaehleAktiveJeFirma_givenAnIdWithoutAnsprechpartner_thenReportsZero() {
    // Given
    final long firmaId = neueFirma("Adler AG");

    // When
    final Map<Long, Long> anzahlen = repository.zaehleAktiveJeFirma(List.of(firmaId));

    // Then
    assertThat(anzahlen).containsExactly(entry(firmaId, 0L));
  }
}
