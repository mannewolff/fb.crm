package org.mwolff.fbcrm.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mwolff.fbcrm.auth.infrastructure.LoginAttemptLimiter.Zaehlart.ADRESSE_STUNDE;
import static org.mwolff.fbcrm.auth.infrastructure.LoginAttemptLimiter.Zaehlart.ADRESSE_TAG;
import static org.mwolff.fbcrm.auth.infrastructure.LoginAttemptLimiter.Zaehlart.HERKUNFT;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mwolff.fbcrm.auth.AuthProperties;
import org.mwolff.fbcrm.auth.Konten;
import org.mwolff.fbcrm.auth.domain.Account;
import org.mwolff.fbcrm.auth.domain.AccountRepository;
import org.mwolff.fbcrm.auth.domain.LoginCode;
import org.mwolff.fbcrm.auth.domain.LoginCodeRepository;
import org.mwolff.fbcrm.auth.domain.SecretHasher;
import org.mwolff.fbcrm.auth.domain.SessionTokens;
import org.mwolff.fbcrm.auth.infrastructure.FailedLoginDelay;
import org.mwolff.fbcrm.auth.infrastructure.LoginAttemptLimiter;
import org.mwolff.fbcrm.common.NumericCodes;
import org.mwolff.fbcrm.mail.MailProperties;
import org.mwolff.fbcrm.mail.application.EnqueueMailUseCase;

/**
 * Anmelden (K5) und einen Code anfordern (E8, E13) — und vor allem, was dabei <b>nicht</b> nach
 * aussen dringt.
 *
 * <p>Die Mocks sind hier von Hand gesetzt statt ueber {@code @InjectMocks}: Der Anwendungsfall
 * rechnet seinen Dummy-Hash schon im Konstruktor, und der muss feststehen, bevor er entsteht.
 *
 * <p>Fuer die Anforderung des Codes laufen Ausstellung und Zaehlbremse <b>echt</b>; gemockt sind
 * nur ihre Raender — der Bestand der Codes, der Postausgang und der Hasher. Erst so laesst sich
 * zeigen, dass die Adresse des Kontos und die unbekannte Adresse gleich viel rechnen (E13) und dass
 * die sechste Anforderung in der Stunde fuer beide gleich abgewiesen wird (Kriterium 5).
 */
class LoginUseCaseTest {

  private static final String MAIL = "manne@example.org";
  private static final String FREMD = "fremd@example.org";
  private static final String PASSWORT = "richtiges-passwort";
  private static final String HASH = "argon2-hash-des-kontos";
  private static final String DUMMY_HASH = "argon2-hash-ohne-konto";
  private static final String IP = "203.0.113.7";
  private static final Instant JETZT = Instant.parse("2026-09-18T10:00:00Z");
  private static final Duration LAUFZEIT = Duration.ofDays(1);
  private static final long KONTO_ID = 42L;
  private static final int GENERATION = 3;
  private static final String TOKEN = "token.signatur";

  private final AccountRepository accounts = mock(AccountRepository.class);
  private final SecretHasher hasher = mock(SecretHasher.class);
  private final SessionTokens tokens = mock(SessionTokens.class);
  private final LoginAttemptLimiter limiter = mock(LoginAttemptLimiter.class);
  private final FailedLoginDelay delay = mock(FailedLoginDelay.class);
  private final LoginCodeRepository loginCodes = mock(LoginCodeRepository.class);
  private final EnqueueMailUseCase postausgang = mock(EnqueueMailUseCase.class);
  private final Clock uhr = Clock.fixed(JETZT, ZoneOffset.UTC);

  /**
   * Die echte Zaehlbremse der Anforderungen, bespitzelt, damit sich das Zaehlen nachweisen laesst.
   */
  private final LoginAttemptLimiter zaehler = spy(new LoginAttemptLimiter(schalter(), uhr));

  private LoginUseCase useCase;

  private static AuthProperties schalter() {
    return new AuthProperties(
        "geheimnis-mit-mindestens-32-zeichen-laenge",
        LAUFZEIT,
        "fbcrm_session",
        true,
        10,
        Duration.ofMinutes(15),
        5,
        Duration.ofHours(1),
        10,
        Duration.ofHours(24),
        Duration.ofMinutes(10),
        5,
        List.of());
  }

  private static Account konto() {
    return Konten.gespeichert(KONTO_ID, MAIL, HASH, GENERATION, JETZT);
  }

  /** Die echte Ausstellung; gemockt sind nur Bestand, Postausgang und Hasher. */
  private RequestLoginCodeUseCase ausstellung() {
    return new RequestLoginCodeUseCase(
        loginCodes,
        hasher,
        new NumericCodes(),
        postausgang,
        schalter(),
        new MailProperties(true, "no-reply@fbcrm.local", "https://crm.example.org"),
        uhr);
  }

  private LoginUseCase anwendungsfall(final LoginAttemptLimiter bremse) {
    return new LoginUseCase(
        accounts,
        hasher,
        tokens,
        bremse,
        delay,
        ausstellung(),
        new NumericCodes(),
        schalter(),
        uhr);
  }

  @BeforeEach
  void baueDenAnwendungsfall() {
    when(hasher.hash(anyString())).thenReturn(DUMMY_HASH);
    useCase = anwendungsfall(limiter);
  }

  /**
   * Der Anwendungsfall fuer die Anforderung des Codes: mit echter Zaehlbremse und ohne die Spuren
   * seines Konstruktors — der rechnet einmal den Dummy-Hash, und der zaehlt hier nicht mit.
   */
  private LoginUseCase anforderung() {
    when(loginCodes.save(any(LoginCode.class))).thenAnswer(aufruf -> aufruf.getArgument(0));
    final LoginUseCase anforderung = anwendungsfall(zaehler);
    clearInvocations(hasher);
    return anforderung;
  }

  private static void fordereFuenfmalAn(final LoginUseCase anforderung, final String email) {
    IntStream.range(0, 5).forEach(unbenutzt -> anforderung.codeAnfordern(email, IP));
  }

  private void kontoUnbekannt(final String email) {
    when(accounts.findByEmail(email)).thenReturn(Optional.empty());
  }

  private void erlaubeVersuch() {
    when(limiter.isAllowed(HERKUNFT, IP)).thenReturn(true);
    when(limiter.isAllowed(ADRESSE_STUNDE, MAIL)).thenReturn(true);
  }

  private void bremseHerkunft(final Duration rest) {
    when(limiter.isAllowed(HERKUNFT, IP)).thenReturn(false);
    when(limiter.restwartezeit(HERKUNFT, IP)).thenReturn(rest);
  }

  private void bremseAdresse(final Duration rest) {
    when(limiter.isAllowed(ADRESSE_STUNDE, MAIL)).thenReturn(false);
    when(limiter.restwartezeit(ADRESSE_STUNDE, MAIL)).thenReturn(rest);
  }

  private void kontoVorhanden() {
    when(accounts.findByEmail(MAIL)).thenReturn(Optional.of(konto()));
  }

  private void kontoUnbekannt() {
    when(accounts.findByEmail(MAIL)).thenReturn(Optional.empty());
  }

  private LoginUseCase nochEinAnwendungsfall() {
    return anwendungsfall(limiter);
  }

  @Test
  void construction_givenTheUseCase_thenHashesOneNonEmptyValueExactlyOnce() {
    // Given — der Anwendungsfall ist in baueDenAnwendungsfall() schon entstanden.
    final ArgumentCaptor<String> gehasht = ArgumentCaptor.forClass(String.class);

    // When / Then — ein Argon2-Durchlauf beim Hochfahren, nicht einer je Anmeldung. Welcher Wert
    // es ist, steht nirgends: Er ist bedeutungslos, gebraucht wird allein die Rechenzeit.
    verify(hasher).hash(gehasht.capture());
    assertThat(gehasht.getValue()).isNotBlank();
  }

  @Test
  void construction_givenASecondUseCase_thenItsDummyHashRestsOnItsOwnRandomValue() {
    // Given
    final ArgumentCaptor<String> gehasht = ArgumentCaptor.forClass(String.class);

    // When
    nochEinAnwendungsfall();

    // Then — zwei Instanzen, zwei verschiedene Werte. Eine Konstante im Quellcode waere hier
    // zweimal derselbe Wert und stuende als Passwort in einem oeffentlichen Repository.
    verify(hasher, times(2)).hash(gehasht.capture());
    assertThat(gehasht.getAllValues())
        .doesNotHaveDuplicates()
        .allSatisfy(wert -> assertThat(wert).isNotBlank());
  }

  @Test
  void login_givenTheRightCredentials_thenReturnsTheAccount() {
    // Given
    erlaubeVersuch();
    kontoVorhanden();
    when(hasher.matches(PASSWORT, HASH)).thenReturn(true);
    when(tokens.encode(KONTO_ID, GENERATION, JETZT, LAUFZEIT)).thenReturn(TOKEN);

    // When
    final LoginResult ergebnis = useCase.login(MAIL, PASSWORT, IP);

    // Then
    assertThat(ergebnis.account()).isEqualTo(konto());
  }

  @Test
  void login_givenTheRightCredentials_thenPutsTheFreshTokenIntoTheCookie() {
    // Given
    erlaubeVersuch();
    kontoVorhanden();
    when(hasher.matches(PASSWORT, HASH)).thenReturn(true);
    when(tokens.encode(KONTO_ID, GENERATION, JETZT, LAUFZEIT)).thenReturn(TOKEN);

    // When
    final LoginResult ergebnis = useCase.login(MAIL, PASSWORT, IP);

    // Then — Name, Laufzeit und Secure-Merkmal kommen aus den Schaltern (E4).
    assertThat(ergebnis.cookie())
        .isEqualTo(new SessionCookie("fbcrm_session", TOKEN, LAUFZEIT, true));
  }

  @Test
  void login_givenTheRightCredentials_thenClearsTheAttemptCounters() {
    // Given
    erlaubeVersuch();
    kontoVorhanden();
    when(hasher.matches(PASSWORT, HASH)).thenReturn(true);
    when(tokens.encode(anyLong(), anyLong(), any(), any())).thenReturn(TOKEN);

    // When
    useCase.login(MAIL, PASSWORT, IP);

    // Then — geloescht wird nur die Herkunft; die Adresszaehler bleiben stehen (E3).
    verify(limiter).recordSuccess(IP);
  }

  @Test
  void login_givenTheRightCredentials_thenDoesNotDelayTheAnswer() {
    // Given
    erlaubeVersuch();
    kontoVorhanden();
    when(hasher.matches(PASSWORT, HASH)).thenReturn(true);
    when(tokens.encode(anyLong(), anyLong(), any(), any())).thenReturn(TOKEN);

    // When
    useCase.login(MAIL, PASSWORT, IP);

    // Then
    verify(delay, never()).apply();
  }

  @Test
  void login_givenAWrongPassword_thenFails() {
    // Given
    erlaubeVersuch();
    kontoVorhanden();
    when(hasher.matches(PASSWORT, HASH)).thenReturn(false);

    // When / Then
    assertThatThrownBy(() -> useCase.login(MAIL, PASSWORT, IP)).isInstanceOf(LoginFailed.class);
  }

  @Test
  void login_givenAnUnknownAddress_thenFailsWithTheSameException() {
    // Given — K5: kein anderer Typ, keine andere Meldung als beim falschen Passwort.
    erlaubeVersuch();
    kontoUnbekannt();
    when(hasher.matches(PASSWORT, DUMMY_HASH)).thenReturn(false);

    // When / Then
    assertThatThrownBy(() -> useCase.login(MAIL, PASSWORT, IP)).isInstanceOf(LoginFailed.class);
  }

  @Test
  void login_givenAnUnknownAddress_thenStillSpendsTheTimeOfAHashCheck() {
    // Given — ohne den Dummy-Hash antwortete die Instanz auf unbekannte Adressen frueher (K5).
    erlaubeVersuch();
    kontoUnbekannt();
    when(hasher.matches(PASSWORT, DUMMY_HASH)).thenReturn(false);

    // When
    assertThatThrownBy(() -> useCase.login(MAIL, PASSWORT, IP)).isInstanceOf(LoginFailed.class);

    // Then
    verify(hasher).matches(PASSWORT, DUMMY_HASH);
  }

  @Test
  void login_givenAnUnknownAddressWhoseDummyHashMatches_thenStillFails() {
    // Given — selbst wenn der Dummy-Hash je passte, entsteht daraus keine Sitzung.
    erlaubeVersuch();
    kontoUnbekannt();
    when(hasher.matches(PASSWORT, DUMMY_HASH)).thenReturn(true);

    // When / Then
    assertThatThrownBy(() -> useCase.login(MAIL, PASSWORT, IP)).isInstanceOf(LoginFailed.class);
  }

  @Test
  void login_givenAWrongPassword_thenCountsTheFailure() {
    // Given
    erlaubeVersuch();
    kontoVorhanden();
    when(hasher.matches(PASSWORT, HASH)).thenReturn(false);

    // When
    assertThatThrownBy(() -> useCase.login(MAIL, PASSWORT, IP)).isInstanceOf(LoginFailed.class);

    // Then — bis zur Umstellung auf den Code zaehlt der Fehlversuch je Herkunft und je Adresse.
    verify(limiter).record(HERKUNFT, IP);
    verify(limiter).record(ADRESSE_STUNDE, MAIL);
  }

  @Test
  void login_givenAWrongPassword_thenDelaysTheAnswer() {
    // Given
    erlaubeVersuch();
    kontoVorhanden();
    when(hasher.matches(PASSWORT, HASH)).thenReturn(false);

    // When
    assertThatThrownBy(() -> useCase.login(MAIL, PASSWORT, IP)).isInstanceOf(LoginFailed.class);

    // Then
    verify(delay).apply();
  }

  @Test
  void login_givenTheRightCredentials_thenCountsNothing() {
    // Given
    erlaubeVersuch();
    kontoVorhanden();
    when(hasher.matches(PASSWORT, HASH)).thenReturn(true);
    when(tokens.encode(anyLong(), anyLong(), any(), any())).thenReturn(TOKEN);

    // When
    useCase.login(MAIL, PASSWORT, IP);

    // Then
    verify(limiter, never()).record(any(), anyString());
  }

  @Test
  void login_givenTheOriginIsBraked_thenRefusesWithItsRemainingWait() {
    // Given
    bremseHerkunft(Duration.ofMinutes(15));
    when(limiter.isAllowed(ADRESSE_STUNDE, MAIL)).thenReturn(true);

    // When / Then — E14: die Ausnahme traegt die Restwartezeit.
    assertThatThrownBy(() -> useCase.login(MAIL, PASSWORT, IP))
        .isInstanceOfSatisfying(
            TooManyLoginAttempts.class,
            gebremst -> assertThat(gebremst.restwartezeit()).isEqualTo(Duration.ofMinutes(15)));
  }

  @Test
  void login_givenTheAddressIsBraked_thenRefusesWithItsRemainingWait() {
    // Given
    when(limiter.isAllowed(HERKUNFT, IP)).thenReturn(true);
    bremseAdresse(Duration.ofMinutes(42));

    // When / Then
    assertThatThrownBy(() -> useCase.login(MAIL, PASSWORT, IP))
        .isInstanceOfSatisfying(
            TooManyLoginAttempts.class,
            gebremst -> assertThat(gebremst.restwartezeit()).isEqualTo(Duration.ofMinutes(42)));
  }

  @Test
  void login_givenBothAreBraked_thenNamesTheLongerWait() {
    // Given — wer nach der kuerzeren Zeit wiederkaeme, liefe in die zweite Bremse.
    bremseHerkunft(Duration.ofMinutes(15));
    bremseAdresse(Duration.ofMinutes(42));

    // When / Then
    assertThatThrownBy(() -> useCase.login(MAIL, PASSWORT, IP))
        .isInstanceOfSatisfying(
            TooManyLoginAttempts.class,
            gebremst -> assertThat(gebremst.restwartezeit()).isEqualTo(Duration.ofMinutes(42)));
  }

  @Test
  void login_givenBothAreBrakedTheOtherWayRound_thenStillNamesTheLongerWait() {
    // Given
    bremseHerkunft(Duration.ofMinutes(42));
    bremseAdresse(Duration.ofMinutes(15));

    // When / Then
    assertThatThrownBy(() -> useCase.login(MAIL, PASSWORT, IP))
        .isInstanceOfSatisfying(
            TooManyLoginAttempts.class,
            gebremst -> assertThat(gebremst.restwartezeit()).isEqualTo(Duration.ofMinutes(42)));
  }

  @Test
  void login_givenABrakeWhoseWindowRanOutMeanwhile_thenLetsTheAttemptThrough() {
    // Given — zwischen der Frage und der Restwartezeit ist der letzte Eintrag herausgefallen.
    bremseHerkunft(Duration.ZERO);
    when(limiter.isAllowed(ADRESSE_STUNDE, MAIL)).thenReturn(true);
    kontoVorhanden();
    when(hasher.matches(PASSWORT, HASH)).thenReturn(true);
    when(tokens.encode(anyLong(), anyLong(), any(), any())).thenReturn(TOKEN);

    // When
    final LoginResult ergebnis = useCase.login(MAIL, PASSWORT, IP);

    // Then
    assertThat(ergebnis.account()).isEqualTo(konto());
  }

  @Test
  void login_givenTooManyFailuresInTheWindow_thenRefusesBeforeLookingUpTheAccount() {
    // Given
    bremseHerkunft(Duration.ofMinutes(15));
    bremseAdresse(Duration.ofMinutes(60));

    // When / Then
    assertThatThrownBy(() -> useCase.login(MAIL, PASSWORT, IP))
        .isInstanceOf(TooManyLoginAttempts.class);
  }

  @Test
  void login_givenTooManyFailuresInTheWindow_thenAsksTheRepositoryForNothing() {
    // Given — die Bremse soll die Last abwehren, nicht nur die Antwort aendern.
    bremseHerkunft(Duration.ofMinutes(15));
    bremseAdresse(Duration.ofMinutes(60));

    // When
    assertThatThrownBy(() -> useCase.login(MAIL, PASSWORT, IP))
        .isInstanceOf(TooManyLoginAttempts.class);

    // Then
    verify(accounts, never()).findByEmail(MAIL);
  }

  @Test
  @DisplayName("Szenario: Unbekannte Adresse")
  void szenarioUnbekannteAdresse_thenSameAnswerAndNoMail() {
    // Given — zu MAIL gibt es das Konto, zu FREMD keines.
    final LoginUseCase anforderung = anforderung();
    kontoVorhanden();
    kontoUnbekannt(FREMD);

    // When
    final Throwable fuerDasKonto = catchThrowable(() -> anforderung.codeAnfordern(MAIL, IP));
    final Throwable ohneKonto = catchThrowable(() -> anforderung.codeAnfordern(FREMD, IP));

    // Then — dieselbe Antwort: Beide enden ohne Ausnahme und ohne Rueckgabe (Kriterium 3).
    assertThat(ohneKonto).isEqualTo(fuerDasKonto).isNull();
    verify(postausgang, never()).enqueue(eq(FREMD), anyString(), anyString());
    verify(loginCodes, never()).deleteByEmail(FREMD);
    final ArgumentCaptor<LoginCode> zeilen = ArgumentCaptor.forClass(LoginCode.class);
    verify(loginCodes).save(zeilen.capture());
    assertThat(zeilen.getAllValues()).extracting(LoginCode::email).containsExactly(MAIL);
    verify(zaehler, times(2)).record(HERKUNFT, IP);
    verify(zaehler).record(ADRESSE_STUNDE, FREMD);
    verify(zaehler).record(ADRESSE_TAG, FREMD);
  }

  @Test
  void codeAnfordern_givenTheAddressOfTheAccount_thenSendsTheCodeWithTheNameOfTheAccount() {
    // Given
    final LoginUseCase anforderung = anforderung();
    kontoVorhanden();

    // When
    anforderung.codeAnfordern(MAIL, IP);

    // Then
    verify(postausgang).enqueue(eq(MAIL), anyString(), contains("Hallo Manne,"));
  }

  @Test
  void codeAnfordern_givenTheAddressInAnotherSpelling_thenSendsToTheStoredAddressOfTheAccount() {
    // Given — gefunden wird ohne Ruecksicht auf die Schreibweise; die Mail geht an die Adresse,
    // die das Konto traegt, nicht an die eingegebene.
    final LoginUseCase anforderung = anforderung();
    when(accounts.findByEmail("Manne@Example.org")).thenReturn(Optional.of(konto()));

    // When
    anforderung.codeAnfordern("Manne@Example.org", IP);

    // Then
    verify(postausgang).enqueue(eq(MAIL), anyString(), anyString());
  }

  @Test
  void codeAnfordern_givenTheAddressOfTheAccount_thenHashesExactlyOnce() {
    // Given
    final LoginUseCase anforderung = anforderung();
    kontoVorhanden();

    // When
    anforderung.codeAnfordern(MAIL, IP);

    // Then — E13: ein Argon2-Durchlauf, der des ausgestellten Codes.
    verify(hasher, times(1)).hash(anyString());
  }

  @Test
  void codeAnfordern_givenAnUnknownAddress_thenHashesExactlyOnceAGeneratedCode() {
    // Given
    final LoginUseCase anforderung = anforderung();
    kontoUnbekannt();
    final ArgumentCaptor<String> gehasht = ArgumentCaptor.forClass(String.class);

    // When
    anforderung.codeAnfordern(MAIL, IP);

    // Then — E13: derselbe Rechenaufwand wie fuer das Konto, gegen einen verworfenen Code.
    verify(hasher, times(1)).hash(gehasht.capture());
    assertThat(gehasht.getValue()).matches("\\d{6}");
  }

  @Test
  void codeAnfordern_thenCountsBeforeItLooksUpTheAccount() {
    // Given — andersherum waere der sechste Versuch die Auskunft ueber das Konto (E8).
    final LoginUseCase anforderung = anforderung();
    kontoUnbekannt();
    final InOrder reihenfolge = inOrder(zaehler, accounts);

    // When
    anforderung.codeAnfordern(MAIL, IP);

    // Then
    reihenfolge.verify(zaehler).record(HERKUNFT, IP);
    reihenfolge.verify(zaehler).record(ADRESSE_STUNDE, MAIL);
    reihenfolge.verify(zaehler).record(ADRESSE_TAG, MAIL);
    reihenfolge.verify(accounts).findByEmail(MAIL);
  }

  @Test
  void codeAnfordern_givenFiveRequestsForAnUnknownAddress_thenRefusesTheSixthWithTheWait() {
    // Given — Kriterium 5: auch fuer Adressen ohne Konto.
    final LoginUseCase anforderung = anforderung();
    kontoUnbekannt();
    fordereFuenfmalAn(anforderung, MAIL);

    // When / Then
    assertThatThrownBy(() -> anforderung.codeAnfordern(MAIL, IP))
        .isInstanceOfSatisfying(
            TooManyLoginAttempts.class,
            gebremst -> assertThat(gebremst.restwartezeit()).isEqualTo(Duration.ofHours(1)));
  }

  @Test
  void codeAnfordern_givenFiveRequestsForTheAccount_thenRefusesTheSixthWithTheSameWait() {
    // Given — dieselbe Abweisung wie fuer die unbekannte Adresse, sonst verriete sie das Konto.
    final LoginUseCase anforderung = anforderung();
    kontoVorhanden();
    fordereFuenfmalAn(anforderung, MAIL);

    // When / Then
    assertThatThrownBy(() -> anforderung.codeAnfordern(MAIL, IP))
        .isInstanceOfSatisfying(
            TooManyLoginAttempts.class,
            gebremst -> assertThat(gebremst.restwartezeit()).isEqualTo(Duration.ofHours(1)));
  }

  @Test
  void codeAnfordern_givenTheLimitReached_thenNeitherLooksUpNorHashesNorIssues() {
    // Given — eine abgewiesene Anforderung laesst den bisherigen Code gueltig (E12).
    final LoginUseCase anforderung = anforderung();
    kontoVorhanden();
    fordereFuenfmalAn(anforderung, MAIL);
    clearInvocations(accounts, hasher, loginCodes, postausgang);

    // When
    assertThatThrownBy(() -> anforderung.codeAnfordern(MAIL, IP))
        .isInstanceOf(TooManyLoginAttempts.class);

    // Then
    verifyNoInteractions(accounts, hasher, loginCodes, postausgang);
  }
}
