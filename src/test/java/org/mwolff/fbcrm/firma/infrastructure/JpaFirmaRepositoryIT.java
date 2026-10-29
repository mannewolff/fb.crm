package org.mwolff.fbcrm.firma.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.mwolff.fbcrm.common.Anschrift;
import org.mwolff.fbcrm.firma.domain.Firma;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Der Firma-Adapter gegen eine echte PostgreSQL-Instanz.
 *
 * <p>Hier steht auch der Nachweis, dass der Zaehler an der Zeile das Rennen zwischen Lesen und
 * Schreiben faengt (Plan #415, E1): Von zwei Transaktionen auf demselben Stand scheitert die
 * zweite, und die erste bleibt.
 */
class JpaFirmaRepositoryIT extends AbstractIntegrationTest {

  private static final Instant ANGELEGT = Instant.parse("2026-09-01T08:00:00Z");
  private static final Instant GEAENDERT = Instant.parse("2026-09-18T12:00:00Z");

  private final JpaFirmaRepository repository;
  private final JdbcTemplate jdbc;
  private final PlatformTransactionManager transaktionen;

  @Autowired
  JpaFirmaRepositoryIT(
      final JpaFirmaRepository repository,
      final JdbcTemplate jdbc,
      final PlatformTransactionManager transaktionen) {
    this.repository = repository;
    this.jdbc = jdbc;
    this.transaktionen = transaktionen;
  }

  @BeforeEach
  void leereFachtabellen() {
    jdbc.execute("TRUNCATE ansprechpartner, firma RESTART IDENTITY CASCADE");
  }

  private static Firma neueFirma(final String name) {
    return new Firma(
        null,
        name,
        new Anschrift("Am Wall 1", "28195", "Bremen", "DE"),
        "75/123/45678",
        "DE123456789",
        true,
        ANGELEGT,
        ANGELEGT,
        0L,
        null);
  }

  private static Firma neueStillgelegteFirma(final String name) {
    return new Firma(
        null,
        name,
        new Anschrift(null, null, null, "DE"),
        null,
        null,
        false,
        ANGELEGT,
        ANGELEGT,
        0L,
        null);
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
              assertThat(firma.anschrift()).isEqualTo(new Anschrift(null, null, null, "DE"));
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
            new Anschrift("Moenckebergstrasse 2", "20095", "Hamburg", "DE"),
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
  void save_givenAChangedFirma_thenRaisesTheVersion() {
    // Given — Plan #415, E1: jedes Schreiben zaehlt den Stand weiter.
    final Firma gesichert = repository.save(neueFirma("Adler AG"));

    // When
    repository.save(gesichert.stillgelegt(GEAENDERT));

    // Then
    assertThat(repository.findById(gesichert.requireId()))
        .hasValueSatisfying(firma -> assertThat(firma.version()).isEqualTo(1L));
  }

  @Test
  void save_givenTwoTransactionsOnTheSameRow_thenTheSecondFailsAndTheFirstStays() {
    // Given — E1: Der Vergleich im Anwendungsfall faengt den Regelfall; der Zaehler an der Zeile
    // faengt das Rennen dazwischen. Beide Transaktionen lesen Stand 0, die erste schreibt und
    // schliesst ab, die zweite schreibt danach auf dem alten Stand.
    final long id = repository.save(neueFirma("Adler AG")).requireId();
    final TransactionTemplate zweite = new TransactionTemplate(transaktionen);
    final TransactionTemplate erste = new TransactionTemplate(transaktionen);
    erste.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

    // When / Then
    assertThatThrownBy(
            () ->
                zweite.executeWithoutResult(
                    aussen -> {
                      final Firma gelesen = repository.findById(id).orElseThrow();
                      erste.executeWithoutResult(
                          innen ->
                              repository.save(
                                  umbenannt(repository.findById(id).orElseThrow(), "Erste")));
                      repository.save(umbenannt(gelesen, "Zweite"));
                    }))
        .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    assertThat(repository.findById(id))
        .hasValueSatisfying(
            firma -> {
              assertThat(firma.name()).isEqualTo("Erste");
              assertThat(firma.version()).isEqualTo(1L);
            });
  }

  private static Firma umbenannt(final Firma firma, final String name) {
    return firma.geaendert(
        name, firma.anschrift(), firma.steuernummer(), firma.umsatzsteuerId(), GEAENDERT);
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
