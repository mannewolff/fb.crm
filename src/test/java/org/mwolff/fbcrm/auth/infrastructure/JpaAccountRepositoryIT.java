package org.mwolff.fbcrm.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.mwolff.fbcrm.auth.Konten;
import org.mwolff.fbcrm.auth.domain.Account;
import org.mwolff.fbcrm.auth.domain.Role;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Der Konto-Adapter gegen eine echte PostgreSQL-Instanz.
 *
 * <p>Die Sperre der aktiven C-Level-Zeilen (Plan #414, A6) ist nur hier pruefbar: Waehrend eine
 * Transaktion sie haelt, scheitert ein zweiter Zugriff mit {@code FOR UPDATE NOWAIT} auf dieselbe
 * Zeile sofort, statt zu warten — und auf eine Zeile, die nicht dazugehoert, nicht.
 */
class JpaAccountRepositoryIT extends AbstractIntegrationTest {

  private static final Instant ANGELEGT = Instant.parse("2026-09-01T08:00:00Z");
  private static final Duration GEDULD = Duration.ofSeconds(30);

  /** SQLState {@code lock_not_available}: Die Zeile haelt eine andere Transaktion. */
  private static final String SPERRE_BELEGT = "55P03";

  private static final String ZEILE_SOFORT_SPERREN =
      "SELECT id FROM account WHERE id = ? FOR UPDATE NOWAIT";

  private final JpaAccountRepository repository;
  private final JdbcTemplate jdbc;
  private final TransactionTemplate transaktion;

  @Autowired
  JpaAccountRepositoryIT(
      final JpaAccountRepository repository,
      final JdbcTemplate jdbc,
      final TransactionTemplate transaktion) {
    this.repository = repository;
    this.jdbc = jdbc;
    this.transaktion = transaktion;
  }

  private static Account neuesKonto(final String email) {
    return Konten.neu(email, ANGELEGT);
  }

  private Account speichere(final String email, final Role rolle, final boolean aktiv) {
    return repository.save(Konten.mit(null, email, rolle, aktiv, 0, ANGELEGT));
  }

  private Account speichereMitNamen(final String email, final String name) {
    return repository.save(neuesKonto(email).mitNamen(name, ANGELEGT));
  }

  /** Versucht, die Zeile ohne Warten zu sperren; liefert den Fehler oder {@code null}. */
  private @Nullable Throwable sperreSofort(final Account konto) {
    return catchThrowable(
        () -> jdbc.queryForList(ZEILE_SOFORT_SPERREN, Long.class, konto.requireId()));
  }

  /**
   * Haelt die Sperre aus {@code aktiveCLevelMitSperre()} in einer eigenen Transaktion, fuehrt
   * waehrenddessen {@code waehrend} aus und gibt die Sperre danach frei.
   */
  private <T> T waehrendDerSperre(final Supplier<T> waehrend) throws Exception {
    final CountDownLatch gesperrt = new CountDownLatch(1);
    final CountDownLatch freigeben = new CountDownLatch(1);
    try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
      final Future<List<Account>> halter =
          pool.submit(
              () ->
                  transaktion.execute(
                      status -> {
                        final List<Account> zeilen = repository.aktiveCLevelMitSperre();
                        gesperrt.countDown();
                        warte(freigeben);
                        return zeilen;
                      }));
      assertThat(gesperrt.await(GEDULD.toSeconds(), TimeUnit.SECONDS)).isTrue();
      try {
        return waehrend.get();
      } finally {
        freigeben.countDown();
        halter.get(GEDULD.toSeconds(), TimeUnit.SECONDS);
      }
    }
  }

  private static void warte(final CountDownLatch latch) {
    try {
      assertThat(latch.await(GEDULD.toSeconds(), TimeUnit.SECONDS)).isTrue();
    } catch (final InterruptedException unterbrochen) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(unterbrochen);
    }
  }

  @BeforeEach
  void leereFachtabellen() {
    jdbc.execute("TRUNCATE outbox_message, account RESTART IDENTITY CASCADE");
  }

  @Test
  void save_thenAssignsAnIdFromTheDatabase() {
    // When
    final Account gesichert = repository.save(neuesKonto("manne@example.org"));

    // Then
    assertThat(gesichert.id()).isNotNull();
  }

  @Test
  void findByEmail_afterSave_thenReturnsTheStoredAccount() {
    // Given
    final Account gesichert = repository.save(neuesKonto("manne@example.org"));

    // When
    final Optional<Account> gefunden = repository.findByEmail("manne@example.org");

    // Then
    assertThat(gefunden).contains(gesichert);
  }

  @Test
  void findByEmail_givenAnotherCase_thenStillFindsTheAccount() {
    // Given
    repository.save(neuesKonto("manne@example.org"));

    // When
    final Optional<Account> gefunden = repository.findByEmail("Manne@Example.ORG");

    // Then
    assertThat(gefunden).isPresent();
  }

  @Test
  void findById_afterSave_thenReturnsTheStoredAccount() {
    // Given
    final Account gesichert = repository.save(neuesKonto("manne@example.org"));

    // When
    final Optional<Account> gefunden = repository.findById(gesichert.requireId());

    // Then
    assertThat(gefunden).contains(gesichert);
  }

  @Test
  void findById_givenAnUnknownId_thenEmpty() {
    // When
    final Optional<Account> gefunden = repository.findById(4711L);

    // Then
    assertThat(gefunden).isEmpty();
  }

  @Test
  void existsAnyAccount_givenAnEmptyTable_thenFalse() {
    // When
    final boolean vorhanden = repository.existsAnyAccount();

    // Then
    assertThat(vorhanden).isFalse();
  }

  @Test
  void existsAnyAccount_givenOnlyADeactivatedReadOnlyAccount_thenTrue() {
    // Given — der Riegel der Einrichtung fragt nach irgendeinem Konto, nicht nach einer Rolle (A5).
    speichere("leser@firma.de", Role.READ_ONLY, false);

    // When
    final boolean vorhanden = repository.existsAnyAccount();

    // Then
    assertThat(vorhanden).isTrue();
  }

  @Test
  void save_givenADeactivatedAccount_thenStoresTheState() {
    // Given
    final Account gesichert = speichere("anna@firma.de", Role.EMPLOYEE, true);

    // When
    repository.save(gesichert.stillgelegt(Instant.parse("2026-09-18T12:00:00Z")));

    // Then
    assertThat(repository.findById(gesichert.requireId()))
        .hasValueSatisfying(konto -> assertThat(konto.aktiv()).isFalse());
  }

  @Test
  void save_givenASecondCLevelAccount_thenAccepted() {
    // Given
    speichere("chefin@firma.de", Role.C_LEVEL, true);

    // When
    final Account zweites = speichere("chef@firma.de", Role.C_LEVEL, true);

    // Then — der Index „genau ein Admin" ist mit V30 entfallen.
    assertThat(zweites.id()).isNotNull();
  }

  @Test
  void findAllOrderedByDisplayName_thenListsEveryAccountByName() {
    // Given
    speichereMitNamen("zora@firma.de", "Zora");
    speichereMitNamen("anna@firma.de", "Anna");
    speichereMitNamen("bert@firma.de", "Bert");

    // When
    final List<Account> liste = repository.findAllOrderedByDisplayName();

    // Then
    assertThat(liste).extracting(Account::displayName).containsExactly("Anna", "Bert", "Zora");
  }

  @Test
  void displayNamesByIds_thenMapsEachKnownIdToItsName() {
    // Given
    final Account anna = speichereMitNamen("anna@firma.de", "Anna");
    final Account bert = speichereMitNamen("bert@firma.de", "Bert");

    // When
    final Map<Long, String> namen =
        repository.displayNamesByIds(Set.of(anna.requireId(), bert.requireId(), 4711L));

    // Then
    assertThat(namen)
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(anna.requireId(), "Anna", bert.requireId(), "Bert"));
  }

  @Test
  void aktiveCLevelMitSperre_thenReturnsOnlyTheActiveCLevelAccounts() {
    // Given
    final Account chefin = speichere("chefin@firma.de", Role.C_LEVEL, true);
    final Account chef = speichere("chef@firma.de", Role.C_LEVEL, true);
    speichere("alt@firma.de", Role.C_LEVEL, false);
    speichere("anna@firma.de", Role.EMPLOYEE, true);
    speichere("leser@firma.de", Role.READ_ONLY, true);

    // When
    final List<Account> gesperrt =
        transaktion.execute(status -> repository.aktiveCLevelMitSperre());

    // Then
    assertThat(gesperrt).containsExactly(chefin, chef);
  }

  @Test
  void aktiveCLevelMitSperre_thenLocksTheReturnedRowsUntilTheTransactionEnds() throws Exception {
    // Given
    final Account chefin = speichere("chefin@firma.de", Role.C_LEVEL, true);

    // When — ein zweiter Zugriff auf dieselbe Zeile, waehrend die Sperre gehalten wird.
    final Optional<Throwable> fehler =
        waehrendDerSperre(() -> Optional.ofNullable(sperreSofort(chefin)));

    // Then — die Zeile ist gesperrt: NOWAIT scheitert sofort mit lock_not_available (A6).
    assertThat(fehler)
        .hasValueSatisfying(
            f ->
                assertThat(f.getCause())
                    .isInstanceOfSatisfying(
                        SQLException.class,
                        ursache -> assertThat(ursache.getSQLState()).isEqualTo(SPERRE_BELEGT)));
  }

  @Test
  void aktiveCLevelMitSperre_afterTheTransaction_thenTheRowIsFreeAgain() throws Exception {
    // Given
    final Account chefin = speichere("chefin@firma.de", Role.C_LEVEL, true);
    waehrendDerSperre(() -> Boolean.TRUE);

    // When
    final @Nullable Throwable fehler = sperreSofort(chefin);

    // Then
    assertThat(fehler).isNull();
  }

  @Test
  void aktiveCLevelMitSperre_thenLeavesOtherRowsUnlocked() throws Exception {
    // Given
    speichere("chefin@firma.de", Role.C_LEVEL, true);
    final Account anna = speichere("anna@firma.de", Role.EMPLOYEE, true);
    final Account alt = speichere("alt@firma.de", Role.C_LEVEL, false);

    // When
    final List<Optional<Throwable>> fehler =
        waehrendDerSperre(
            () ->
                List.of(
                    Optional.ofNullable(sperreSofort(anna)),
                    Optional.ofNullable(sperreSofort(alt))));

    // Then — gesperrt wird nur, was gezaehlt wird.
    assertThat(fehler).containsOnly(Optional.empty());
  }

  @Test
  void aktiveCLevelMitSperre_withoutATransaction_thenRefused() {
    // When / Then — eine Sperre ohne Transaktion endete mit dem Aufruf und hielte nichts fest.
    assertThatThrownBy(repository::aktiveCLevelMitSperre)
        .isInstanceOf(IllegalTransactionStateException.class);
  }

  @Test
  void save_givenASecondAccountWithTheSameEmail_thenRejectedByTheDatabase() {
    // Given
    repository.save(neuesKonto("manne@example.org"));

    final Account zweites = neuesKonto("manne@example.org");

    // When / Then — es greift der eindeutige Index auf lower(email).
    assertThatThrownBy(() -> repository.save(zweites))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void save_givenTheNextGeneration_thenStoresTheHigherSessionGeneration() {
    // Given
    final Account gesichert = repository.save(neuesKonto("manne@example.org"));

    // When
    repository.save(gesichert.mitNaechsterGeneration(Instant.parse("2026-09-18T12:00:00Z")));

    // Then
    assertThat(repository.findById(gesichert.requireId()))
        .hasValueSatisfying(konto -> assertThat(konto.sessionGeneration()).isEqualTo(1));
  }
}
