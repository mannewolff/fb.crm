package org.mwolff.fbcrm.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.auth.AuthProperties;
import org.mwolff.fbcrm.auth.SetupProperties;
import org.mwolff.fbcrm.auth.domain.Account;
import org.mwolff.fbcrm.auth.domain.AccountRepository;
import org.mwolff.fbcrm.auth.domain.PasswordHasher;
import org.mwolff.fbcrm.auth.domain.Role;
import org.mwolff.fbcrm.auth.domain.SessionTokens;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Die Einrichtung der frischen Instanz (E5, K3).
 *
 * <p>Zwei Faelle tragen dieses Paket. Der erste ist der <b>leere konfigurierte Schluessel</b>:
 * {@code docker-compose.yml} Z. 47 f. sagt „leer = deaktiviert" zu. Ein konstanter Vergleich gegen
 * den Leerstring nimmt aber genau die leere Eingabe an — die Zusage waere ins Gegenteil verkehrt.
 * Der zweite ist das <b>Wettrennen</b>: Kommt die Abweisung der Datenbank, muss sie aussehen wie
 * „Konto existiert bereits", sonst verriete der Unterschied, welcher der beiden Riegel griff.
 */
@ExtendWith(MockitoExtension.class)
class SetupAccountUseCaseTest {

  private static final String MAIL = "manne@example.org";
  private static final String NAME = "Manne";
  private static final String PASSWORT = "sicheres-passwort";
  private static final String HASH = "$argon2id$v=19$dummy";
  private static final String SCHLUESSEL = "einmal-schluessel-der-einrichtung";
  private static final String TOKEN = "nutzlast.signatur";
  private static final Instant JETZT = Instant.parse("2026-09-18T10:00:00Z");
  private static final long KONTO_ID = 1L;

  @Mock private AccountRepository accounts;
  @Mock private PasswordHasher hasher;
  @Mock private SessionTokens tokens;

  private static AuthProperties schalter() {
    return new AuthProperties(
        "geheimnis-mit-mindestens-32-zeichen-laenge",
        Duration.ofDays(1),
        "fbcrm_session",
        true,
        10,
        Duration.ofMinutes(15),
        List.of());
  }

  private SetupAccountUseCase einrichtung(final String konfigurierterSchluessel) {
    return new SetupAccountUseCase(
        accounts,
        hasher,
        tokens,
        schalter(),
        new SetupProperties(konfigurierterSchluessel),
        Clock.fixed(JETZT, ZoneOffset.UTC));
  }

  /** Der vollstaendig vorbereitete Erfolgsfall: kein Konto, Hash und Token liegen bereit. */
  private SetupAccountUseCase frischeInstanz() {
    when(accounts.existsAnyAdmin()).thenReturn(false);
    when(hasher.hash(PASSWORT)).thenReturn(HASH);
    when(accounts.save(any(Account.class)))
        .thenAnswer(
            aufruf -> {
              final Account uebergeben = aufruf.getArgument(0);
              return new Account(
                  KONTO_ID,
                  uebergeben.email(),
                  uebergeben.displayName(),
                  uebergeben.passwordHash(),
                  uebergeben.role(),
                  uebergeben.sessionGeneration(),
                  uebergeben.createdAt(),
                  uebergeben.updatedAt());
            });
    when(tokens.encode(KONTO_ID, 0L, JETZT, Duration.ofDays(1))).thenReturn(TOKEN);
    return einrichtung(SCHLUESSEL);
  }

  private static LoginResult richteEin(final SetupAccountUseCase einrichtung) {
    return einrichtung.initialize(MAIL, NAME, PASSWORT, SCHLUESSEL);
  }

  private Account gespeichertesKonto() {
    final ArgumentCaptor<Account> gespeichert = ArgumentCaptor.forClass(Account.class);
    verify(accounts).save(gespeichert.capture());
    return gespeichert.getValue();
  }

  @Test
  void initialize_givenTheRightKeyOnAFreshInstance_thenCreatesAnAdminAccount() {
    // Given
    final SetupAccountUseCase einrichtung = frischeInstanz();

    // When
    richteEin(einrichtung);

    // Then — die Rolle ist der ganze Zweck des Einmal-Schluessels.
    assertThat(gespeichertesKonto().role()).isEqualTo(Role.ADMIN);
  }

  @Test
  void initialize_givenTheRightKeyOnAFreshInstance_thenStoresOnlyTheHashOfThePassword() {
    // Given
    final SetupAccountUseCase einrichtung = frischeInstanz();

    // When
    richteEin(einrichtung);

    // Then
    assertThat(gespeichertesKonto().passwordHash()).isEqualTo(HASH);
  }

  @Test
  void initialize_givenTheRightKeyOnAFreshInstance_thenKeepsTheEnteredAddressAndName() {
    // Given
    final SetupAccountUseCase einrichtung = frischeInstanz();

    // When
    richteEin(einrichtung);

    // Then
    assertThat(gespeichertesKonto())
        .extracting(Account::email, Account::displayName)
        .containsExactly(MAIL, NAME);
  }

  @Test
  void initialize_givenTheRightKeyOnAFreshInstance_thenStampsTheAccountWithTheInjectedClock() {
    // Given
    final SetupAccountUseCase einrichtung = frischeInstanz();

    // When
    richteEin(einrichtung);

    // Then
    assertThat(gespeichertesKonto())
        .extracting(Account::createdAt, Account::updatedAt)
        .containsExactly(JETZT, JETZT);
  }

  @Test
  void initialize_givenTheRightKeyOnAFreshInstance_thenStartsAtSessionGenerationZero() {
    // Given
    final SetupAccountUseCase einrichtung = frischeInstanz();

    // When
    richteEin(einrichtung);

    // Then
    assertThat(gespeichertesKonto().sessionGeneration()).isZero();
  }

  @Test
  void initialize_givenTheRightKeyOnAFreshInstance_thenAnswersWithTheSavedAccount() {
    // Given
    final SetupAccountUseCase einrichtung = frischeInstanz();

    // When
    final LoginResult ergebnis = richteEin(einrichtung);

    // Then
    assertThat(ergebnis.account().requireId()).isEqualTo(KONTO_ID);
  }

  @Test
  void initialize_givenTheRightKeyOnAFreshInstance_thenSignsInTheOperatorImmediately() {
    // Given — K3: einrichten und anmelden sind ein Schritt, nicht zwei.
    final SetupAccountUseCase einrichtung = frischeInstanz();

    // When
    final LoginResult ergebnis = richteEin(einrichtung);

    // Then
    assertThat(ergebnis.cookie())
        .isEqualTo(new SessionCookie("fbcrm_session", TOKEN, Duration.ofDays(1), true));
  }

  @Test
  void initialize_givenAWrongKey_thenRefuses() {
    // Given
    final SetupAccountUseCase einrichtung = einrichtung(SCHLUESSEL);

    // When / Then
    assertThatExceptionOfType(SetupRefused.class)
        .isThrownBy(() -> einrichtung.initialize(MAIL, NAME, PASSWORT, "falscher-schluessel"));
  }

  @Test
  void initialize_givenAWrongKey_thenNeverTouchesTheAccounts() {
    // Given — ohne gueltigen Schluessel wird nicht einmal nachgesehen, ob es ein Konto gibt.
    final SetupAccountUseCase einrichtung = einrichtung(SCHLUESSEL);

    // When
    assertThatExceptionOfType(SetupRefused.class)
        .isThrownBy(() -> einrichtung.initialize(MAIL, NAME, PASSWORT, "falscher-schluessel"));

    // Then
    verify(accounts, never()).save(any(Account.class));
  }

  @Test
  void initialize_givenAKeyThatIsOnlyAPrefix_thenRefuses() {
    // Given — der Vergleich ist auf Gleichheit, nicht auf Anfang.
    final SetupAccountUseCase einrichtung = einrichtung(SCHLUESSEL);

    // When / Then
    assertThatExceptionOfType(SetupRefused.class)
        .isThrownBy(() -> einrichtung.initialize(MAIL, NAME, PASSWORT, "einmal-schluessel"));
  }

  @Test
  void initialize_givenAnEmptyConfiguredKey_thenRefusesAnEmptyInputAsWell() {
    // Given — E5: „leer = deaktiviert" aus docker-compose.yml Z. 47 f. Ein konstanter Vergleich
    // gegen den Leerstring naehme genau diese Eingabe an.
    final SetupAccountUseCase einrichtung = einrichtung("");

    // When / Then
    assertThatExceptionOfType(SetupRefused.class)
        .isThrownBy(() -> einrichtung.initialize(MAIL, NAME, PASSWORT, ""));
  }

  @Test
  void initialize_givenAnEmptyConfiguredKey_thenRefusesAnyInput() {
    // Given
    final SetupAccountUseCase einrichtung = einrichtung("");

    // When / Then
    assertThatExceptionOfType(SetupRefused.class)
        .isThrownBy(() -> einrichtung.initialize(MAIL, NAME, PASSWORT, "irgendwas"));
  }

  @Test
  void initialize_givenAnEmptyConfiguredKey_thenNeverTouchesTheAccounts() {
    // Given
    final SetupAccountUseCase einrichtung = einrichtung("");

    // When
    assertThatExceptionOfType(SetupRefused.class)
        .isThrownBy(() -> einrichtung.initialize(MAIL, NAME, PASSWORT, ""));

    // Then
    verify(accounts, never()).save(any(Account.class));
  }

  @Test
  void initialize_givenAnInstanceThatAlreadyHasAnAdmin_thenRefuses() {
    // Given — K3: der Einmal-Schluessel wirkt genau einmal.
    when(accounts.existsAnyAdmin()).thenReturn(true);
    final SetupAccountUseCase einrichtung = einrichtung(SCHLUESSEL);

    // When / Then
    assertThatExceptionOfType(SetupRefused.class).isThrownBy(() -> richteEin(einrichtung));
  }

  @Test
  void initialize_givenAnInstanceThatAlreadyHasAnAdmin_thenStoresNothing() {
    // Given
    when(accounts.existsAnyAdmin()).thenReturn(true);
    final SetupAccountUseCase einrichtung = einrichtung(SCHLUESSEL);

    // When
    assertThatExceptionOfType(SetupRefused.class).isThrownBy(() -> richteEin(einrichtung));

    // Then
    verify(accounts, never()).save(any(Account.class));
  }

  @Test
  void initialize_givenALostRaceAgainstTheUniqueIndex_thenRefusesLikeAnExistingAccount() {
    // Given — zwei gleichzeitige Aufrufe mit verschiedenen Adressen kommen beide durch
    // existsAnyAdmin; was sie trennt, ist account_single_admin auf Ebene der Datenbank.
    when(accounts.existsAnyAdmin()).thenReturn(false);
    when(hasher.hash(PASSWORT)).thenReturn(HASH);
    when(accounts.save(any(Account.class)))
        .thenThrow(new DataIntegrityViolationException("account_single_admin"));
    final SetupAccountUseCase einrichtung = einrichtung(SCHLUESSEL);

    // When / Then
    assertThatExceptionOfType(SetupRefused.class).isThrownBy(() -> richteEin(einrichtung));
  }

  @Test
  void initialize_givenALostRaceAgainstTheUniqueIndex_thenNeverIssuesASession() {
    // Given
    when(accounts.existsAnyAdmin()).thenReturn(false);
    when(hasher.hash(PASSWORT)).thenReturn(HASH);
    when(accounts.save(any(Account.class)))
        .thenThrow(new DataIntegrityViolationException("account_single_admin"));
    final SetupAccountUseCase einrichtung = einrichtung(SCHLUESSEL);

    // When
    assertThatExceptionOfType(SetupRefused.class).isThrownBy(() -> richteEin(einrichtung));

    // Then
    verify(tokens, never()).encode(anyLong(), anyLong(), any(Instant.class), any(Duration.class));
  }

  @Test
  void initialize_givenALostRaceAgainstTheUniqueIndex_thenNeverCarriesTheDatabaseTextOutwards() {
    // Given — der Text der Datenbank nennt Tabelle und Index; er gehoert nicht in die Antwort.
    when(accounts.existsAnyAdmin()).thenReturn(false);
    when(hasher.hash(PASSWORT)).thenReturn(HASH);
    when(accounts.save(any(Account.class)))
        .thenThrow(new DataIntegrityViolationException("account_single_admin"));
    final SetupAccountUseCase einrichtung = einrichtung(SCHLUESSEL);

    // When / Then
    assertThatExceptionOfType(SetupRefused.class)
        .isThrownBy(() -> richteEin(einrichtung))
        .withMessage(null);
  }

  @Test
  void isInitialized_givenAnInstanceWithAnAdmin_thenIsTrue() {
    // Given
    when(accounts.existsAnyAdmin()).thenReturn(true);

    // When / Then
    assertThat(einrichtung(SCHLUESSEL).isInitialized()).isTrue();
  }

  @Test
  void isInitialized_givenAFreshInstance_thenIsFalse() {
    // Given
    when(accounts.existsAnyAdmin()).thenReturn(false);

    // When / Then
    assertThat(einrichtung(SCHLUESSEL).isInitialized()).isFalse();
  }
}
