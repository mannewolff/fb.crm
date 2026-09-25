package org.mwolff.fbcrm.firma.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.mwolff.fbcrm.firma.domain.Anschrift;
import org.mwolff.fbcrm.firma.domain.Firma;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Der Firma-Adapter gegen eine echte PostgreSQL-Instanz. */
class JpaFirmaRepositoryIT extends AbstractIntegrationTest {

  private static final Instant ANGELEGT = Instant.parse("2026-09-01T08:00:00Z");
  private static final Instant GEAENDERT = Instant.parse("2026-09-18T12:00:00Z");

  private final JpaFirmaRepository repository;
  private final JdbcTemplate jdbc;

  @Autowired
  JpaFirmaRepositoryIT(final JpaFirmaRepository repository, final JdbcTemplate jdbc) {
    this.repository = repository;
    this.jdbc = jdbc;
  }

  @BeforeEach
  void leereFachtabellen() {
    jdbc.execute("TRUNCATE ansprechpartner, firma RESTART IDENTITY CASCADE");
  }

  private static Firma neueFirma(final String name) {
    return new Firma(
        null,
        name,
        new Anschrift("Am Wall 1", "28195", "Bremen", "Deutschland"),
        "75/123/45678",
        "DE123456789",
        true,
        ANGELEGT,
        ANGELEGT);
  }

  private static Firma neueStillgelegteFirma(final String name) {
    return new Firma(
        null, name, new Anschrift(null, null, null, null), null, null, false, ANGELEGT, ANGELEGT);
  }

  private List<String> namenDerUebersicht(final String suche, final boolean auchStillgelegte) {
    return repository.uebersicht(suche, auchStillgelegte).stream().map(Firma::name).toList();
  }

  @Test
  void save_thenAssignsAnIdFromTheDatabase() {
    // When
    final Firma gesichert = repository.save(neueFirma("Adler AG"));

    // Then
    assertThat(gesichert.id()).isNotNull();
  }

  @Test
  void findById_afterSave_thenReturnsEveryStoredValue() {
    // Given
    final Firma gesichert = repository.save(neueFirma("Adler AG"));

    // When
    final Optional<Firma> gefunden = repository.findById(gesichert.requireId());

    // Then
    assertThat(gefunden).contains(gesichert);
  }

  @Test
  void findById_afterSavingAFirmaWithoutOptionalValues_thenReadsThemBackAsAbsent() {
    // Given
    final Firma gesichert = repository.save(neueStillgelegteFirma("Adler AG"));

    // When
    final Optional<Firma> gefunden = repository.findById(gesichert.requireId());

    // Then
    assertThat(gefunden)
        .hasValueSatisfying(
            firma -> {
              assertThat(firma.anschrift()).isEqualTo(new Anschrift(null, null, null, null));
              assertThat(firma.steuernummer()).isNull();
              assertThat(firma.umsatzsteuerId()).isNull();
              assertThat(firma.aktiv()).isFalse();
            });
  }

  @Test
  void findById_givenAnUnknownId_thenEmpty() {
    // When
    final Optional<Firma> gefunden = repository.findById(4711L);

    // Then
    assertThat(gefunden).isEmpty();
  }

  @Test
  void save_givenAChangedFirma_thenStoresTheNewValues() {
    // Given
    final Firma gesichert = repository.save(neueFirma("Adler AG"));

    // When
    repository.save(
        gesichert.geaendert(
            "Adler GmbH",
            new Anschrift("Moenckebergstrasse 2", "20095", "Hamburg", "Deutschland"),
            "75/999/00000",
            "DE999999999",
            GEAENDERT));

    // Then
    assertThat(repository.findById(gesichert.requireId()))
        .hasValueSatisfying(
            firma -> {
              assertThat(firma.name()).isEqualTo("Adler GmbH");
              assertThat(firma.anschrift().ort()).isEqualTo("Hamburg");
              assertThat(firma.updatedAt()).isEqualTo(GEAENDERT);
            });
  }

  @Test
  void uebersicht_thenSortsByNameIgnoringCase() {
    // Given
    repository.save(neueFirma("beta ag"));
    repository.save(neueFirma("Alpha AG"));

    // When
    final List<String> namen = namenDerUebersicht("", false);

    // Then
    assertThat(namen).containsExactly("Alpha AG", "beta ag");
  }

  @Test
  void uebersicht_givenTwoFirmenWithTheSameName_thenKeepsTheOrderBetweenTwoCalls() {
    // Given
    final Firma erste = repository.save(neueFirma("Adler AG"));
    final Firma zweite = repository.save(neueFirma("Adler AG"));

    // When
    final List<Firma> ersterAufruf = repository.uebersicht("", false);
    final List<Firma> zweiterAufruf = repository.uebersicht("", false);

    // Then
    assertThat(List.of(ersterAufruf, zweiterAufruf))
        .allSatisfy(
            aufruf ->
                assertThat(aufruf)
                    .extracting(Firma::id)
                    .containsExactly(erste.requireId(), zweite.requireId()));
  }

  @Test
  void uebersicht_givenASearchTextInAnotherCase_thenStillFindsTheFirma() {
    // Given
    repository.save(neueFirma("Adler AG"));
    repository.save(neueFirma("Baum GmbH"));

    // When
    final List<String> namen = namenDerUebersicht("ADLER", false);

    // Then
    assertThat(namen).containsExactly("Adler AG");
  }

  @Test
  void uebersicht_givenAPercentSignInTheSearchText_thenMatchesOnlyNamesContainingIt() {
    // Given
    repository.save(neueFirma("Prozent % GmbH"));
    repository.save(neueFirma("Adler AG"));

    // When
    final List<String> namen = namenDerUebersicht("%", false);

    // Then
    assertThat(namen).containsExactly("Prozent % GmbH");
  }

  @Test
  void uebersicht_givenAnUnderscoreInTheSearchText_thenMatchesOnlyNamesContainingIt() {
    // Given
    repository.save(neueFirma("Unter_strich AG"));
    repository.save(neueFirma("Adler AG"));

    // When
    final List<String> namen = namenDerUebersicht("_", false);

    // Then
    assertThat(namen).containsExactly("Unter_strich AG");
  }

  @Test
  void uebersicht_givenTheSwitchIsOff_thenLeavesOutRetiredFirmen() {
    // Given
    repository.save(neueFirma("Adler AG"));
    repository.save(neueStillgelegteFirma("Baum GmbH"));

    // When
    final List<String> namen = namenDerUebersicht("", false);

    // Then
    assertThat(namen).containsExactly("Adler AG");
  }

  @Test
  void uebersicht_givenTheSwitchIsOn_thenIncludesRetiredFirmen() {
    // Given
    repository.save(neueFirma("Adler AG"));
    repository.save(neueStillgelegteFirma("Baum GmbH"));

    // When
    final List<String> namen = namenDerUebersicht("", true);

    // Then
    assertThat(namen).containsExactly("Adler AG", "Baum GmbH");
  }

  @Test
  void zaehleAlle_thenCountsRetiredFirmenAsWell() {
    // Given
    repository.save(neueFirma("Adler AG"));
    repository.save(neueStillgelegteFirma("Baum GmbH"));

    // When
    final long anzahl = repository.zaehleAlle();

    // Then
    assertThat(anzahl).isEqualTo(2L);
  }

  @Test
  void zaehleAlle_givenAnEmptyTable_thenZero() {
    // When
    final long anzahl = repository.zaehleAlle();

    // Then
    assertThat(anzahl).isZero();
  }
}
