package org.mwolff.fbcrm.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
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
import org.mwolff.fbcrm.auth.Konten;
import org.mwolff.fbcrm.auth.domain.Account;
import org.mwolff.fbcrm.auth.domain.Role;

/**
 * Die Uebersetzung zwischen Konto und Zeile — in beide Richtungen.
 *
 * <p>Der Adapter steht hier gegen ein gemocktes Spring-Data-Repository, damit die Abbildung selbst
 * geprueft ist und nicht nur ihr Zusammenspiel mit der Datenbank ({@code JpaAccountRepositoryIT}).
 */
@ExtendWith(MockitoExtension.class)
class JpaAccountRepositoryTest {

  private static final Instant ANGELEGT = Instant.parse("2026-09-01T08:00:00Z");
  private static final Instant GEAENDERT = Instant.parse("2026-09-18T12:00:00Z");

  /** Das Konto, das zuletzt verwaltet hat (Plan #415, E19). */
  private static final long VERWALTER = 3L;

  /** Der Stand der Kontenverwaltung, den jede Zeile hier traegt. */
  private static final long VERSION = 5L;

  @Mock private SpringDataAccountRepository jpa;

  @Captor private ArgumentCaptor<AccountEntity> gespeicherte;

  @InjectMocks private JpaAccountRepository repository;

  private static AccountEntity zeile(final Long id) {
    return AccountEntity.aus(konto(id));
  }

  private static Account konto(final Long id) {
    return Konten.imStand(
        Konten.gespeichert(id, "manne@example.org", 2, ANGELEGT, GEAENDERT), VERSION, VERWALTER);
  }

  @Test
  void save_thenWritesEveryFieldOfTheAccountIntoTheRow() {
    // Given
    when(jpa.saveAndFlush(any(AccountEntity.class))).thenReturn(zeile(11L));

    // When
    repository.save(konto(null));

    // Then
    verify(jpa).saveAndFlush(gespeicherte.capture());
    assertThat(gespeicherte.getValue())
        .satisfies(
            zeile -> assertThat(zeile.getId()).isNull(),
            zeile -> assertThat(zeile.getEmail()).isEqualTo("manne@example.org"),
            zeile -> assertThat(zeile.getDisplayName()).isEqualTo("Manne"),
            zeile -> assertThat(zeile.getRole()).isEqualTo(Role.C_LEVEL),
            zeile -> assertThat(zeile.isAktiv()).isTrue(),
            zeile -> assertThat(zeile.getSessionGeneration()).isEqualTo(2),
            zeile -> assertThat(zeile.getCreatedAt()).isEqualTo(ANGELEGT),
            zeile -> assertThat(zeile.getUpdatedAt()).isEqualTo(GEAENDERT),
            zeile -> assertThat(zeile.getVersion()).isEqualTo(VERSION),
            zeile -> assertThat(zeile.getGeaendertVon()).isEqualTo(VERWALTER));
  }

  @Test
  void save_givenAnAccountWithoutUrheber_thenTheRowCarriesNone() {
    // Given — ein frisches Konto: Version 0, niemand hat es bisher verwaltet.
    final Account frisch = Konten.neu("anna@firma.de", ANGELEGT);
    when(jpa.saveAndFlush(any(AccountEntity.class))).thenReturn(AccountEntity.aus(frisch));

    // When
    final Account gesichert = repository.save(frisch);

    // Then
    verify(jpa).saveAndFlush(gespeicherte.capture());
    assertThat(gespeicherte.getValue().getVersion()).isZero();
    assertThat(gespeicherte.getValue().getGeaendertVon()).isNull();
    assertThat(gesichert.geaendertVon()).isNull();
  }

  @Test
  void save_thenReturnsTheAccountWithTheGeneratedId() {
    // Given
    when(jpa.saveAndFlush(any(AccountEntity.class))).thenReturn(zeile(11L));

    // When
    final Account gesichert = repository.save(konto(null));

    // Then
    assertThat(gesichert).isEqualTo(konto(11L));
  }

  @Test
  void findById_givenAKnownAccount_thenTranslatesTheRow() {
    // Given
    when(jpa.findById(11L)).thenReturn(Optional.of(zeile(11L)));

    // When
    final Optional<Account> gefunden = repository.findById(11L);

    // Then
    assertThat(gefunden).contains(konto(11L));
  }

  @Test
  void findById_givenAnUnknownId_thenEmpty() {
    // Given
    when(jpa.findById(11L)).thenReturn(Optional.empty());

    // When
    final Optional<Account> gefunden = repository.findById(11L);

    // Then
    assertThat(gefunden).isEmpty();
  }

  @Test
  void findByEmail_givenAKnownAddress_thenTranslatesTheRow() {
    // Given
    when(jpa.findByEmailIgnoreCase("manne@example.org")).thenReturn(Optional.of(zeile(11L)));

    // When
    final Optional<Account> gefunden = repository.findByEmail("manne@example.org");

    // Then
    assertThat(gefunden).contains(konto(11L));
  }

  @Test
  void findByEmail_givenAnUnknownAddress_thenEmpty() {
    // Given
    when(jpa.findByEmailIgnoreCase("fremd@example.org")).thenReturn(Optional.empty());

    // When
    final Optional<Account> gefunden = repository.findByEmail("fremd@example.org");

    // Then
    assertThat(gefunden).isEmpty();
  }

  @Test
  void save_givenADeactivatedAccount_thenTheRowIsNotActive() {
    // Given
    final Account stillgelegt =
        Konten.mit(null, "anna@firma.de", Role.EMPLOYEE, false, 0, ANGELEGT);
    when(jpa.saveAndFlush(any(AccountEntity.class))).thenReturn(AccountEntity.aus(stillgelegt));

    // When
    final Account gesichert = repository.save(stillgelegt);

    // Then — in beide Richtungen: die Zeile traegt den Zustand, und das Konto kommt so zurueck.
    verify(jpa).saveAndFlush(gespeicherte.capture());
    assertThat(gespeicherte.getValue().isAktiv()).isFalse();
    assertThat(gesichert.aktiv()).isFalse();
  }

  @Test
  void existsAnyAccount_givenARow_thenTrue() {
    // Given
    when(jpa.existsBy()).thenReturn(true);

    // When
    final boolean vorhanden = repository.existsAnyAccount();

    // Then
    assertThat(vorhanden).isTrue();
  }

  @Test
  void existsAnyAccount_givenNoRow_thenFalse() {
    // Given
    when(jpa.existsBy()).thenReturn(false);

    // When
    final boolean vorhanden = repository.existsAnyAccount();

    // Then
    assertThat(vorhanden).isFalse();
  }

  @Test
  void findAllOrderedByDisplayName_thenTranslatesEveryRowInTheGivenOrder() {
    // Given
    when(jpa.findAllByOrderByDisplayNameAscIdAsc()).thenReturn(List.of(zeile(12L), zeile(11L)));

    // When
    final List<Account> liste = repository.findAllOrderedByDisplayName();

    // Then
    assertThat(liste).containsExactly(konto(12L), konto(11L));
  }

  @Test
  void displayNamesByIds_thenMapsEachFoundIdToItsName() {
    // Given
    final AccountEntity anna = AccountEntity.aus(benannt(11L, "anna@firma.de", "Anna"));
    final AccountEntity bert = AccountEntity.aus(benannt(12L, "bert@firma.de", "Bert"));
    when(jpa.findAllById(Set.of(11L, 12L, 99L))).thenReturn(List.of(anna, bert));

    // When
    final Map<Long, String> namen = repository.displayNamesByIds(Set.of(11L, 12L, 99L));

    // Then — eine unbekannte Id fehlt in der Antwort, statt einen Platzhalter zu tragen.
    assertThat(namen).containsExactlyInAnyOrderEntriesOf(Map.of(11L, "Anna", 12L, "Bert"));
  }

  @Test
  void aktiveCLevelMitSperre_thenAsksForTheActiveCLevelRowsAndTranslatesThem() {
    // Given
    when(jpa.sperreAktiveMitRolle(Role.C_LEVEL)).thenReturn(List.of(zeile(11L)));

    // When
    final List<Account> gesperrt = repository.aktiveCLevelMitSperre();

    // Then
    assertThat(gesperrt).containsExactly(konto(11L));
  }

  @Test
  void findByIdMitSperre_givenAKnownAccount_thenLocksAndTranslatesTheRow() {
    // Given
    when(jpa.sperreKonto(11L)).thenReturn(Optional.of(zeile(11L)));

    // When
    final Optional<Account> gesperrt = repository.findByIdMitSperre(11L);

    // Then — Version und Urheber kommen mit, gegen sie prueft die Verwaltung (E19).
    assertThat(gesperrt).contains(konto(11L));
  }

  @Test
  void findByIdMitSperre_givenAnUnknownId_thenEmpty() {
    // Given
    when(jpa.sperreKonto(11L)).thenReturn(Optional.empty());

    // When
    final Optional<Account> gesperrt = repository.findByIdMitSperre(11L);

    // Then
    assertThat(gesperrt).isEmpty();
  }

  private static Account benannt(final Long id, final String email, final String name) {
    return Konten.benannt(konto(id).mitAdresse(email, GEAENDERT, VERWALTER), name);
  }
}
