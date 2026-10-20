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
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Anmelden per Code (Kriterium 2 bis 4) und einen Code anfordern (E8, E13) — und vor allem, was
 * dabei <b>nicht</b> nach aussen dringt.
 *
 * <p>Ausstellung, Einloesung und Zaehlbremse laufen <b>echt</b>; gemockt sind nur ihre Raender —
 * der Bestand der Konten und der Codes, der Postausgang, der Hasher und die Verzoegerung. Erst so
 * laesst sich zeigen, dass die Adresse des Kontos und die unbekannte Adresse gleich viel rechnen
 * (E13) und dass die sechste Anforderung in der Stunde fuer beide gleich abgewiesen wird (Kriterium
 * 5). Wie die Einloesung mit der Zeile des Codes umgeht, weist {@code CodeEinloesungTest} nach.
 */
class LoginUseCaseTest {

  private static final String MAIL = "manne@example.org";
  private static final String FREMD = "fremd@example.org";
  private static final String CODE = "042424";
  private static final String HASH = "argon2-hash-des-codes";
  private static final String DUMMY_HASH = "argon2-hash-ohne-konto";
  private static final String IP = "203.0.113.7";
  private static final Instant JETZT = Instant.parse("2026-09-18T10:00:00Z");
  private static final Duration LAUFZEIT = Duration.ofDays(1);
  private static final Duration CODE_LAUFZEIT = Duration.ofMinutes(10);
  private static final long KONTO_ID = 42L;
  private static final int GENERATION = 3;
  private static final String TOKEN = "token.signatur";

  private final AccountRepository accounts = mock(AccountRepository.class);
  private final SecretHasher hasher = mock(SecretHasher.class);
  private final SessionTokens tokens = mock(SessionTokens.class);
  private final FailedLoginDelay delay = mock(FailedLoginDelay.class);
  private final LoginCodeRepository loginCodes = mock(LoginCodeRepository.class);
  private final EnqueueMailUseCase postausgang = mock(EnqueueMailUseCase.class);
  private final Clock uhr = Clock.fixed(JETZT, ZoneOffset.UTC);

  /** Die echte Zaehlbremse, bespitzelt, damit sich das Zaehlen nachweisen laesst. */
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
        CODE_LAUFZEIT,
        5,
        List.of());
  }

  private static Account konto() {
    return Konten.gespeichert(KONTO_ID, MAIL, GENERATION, JETZT);
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

  /** Der Anwendungsfall; die Einloesung des Codes liest die Zeit von der genannten Uhr. */
  private LoginUseCase anwendungsfall(final Clock einloeseUhr) {
    return new LoginUseCase(
        accounts,
        hasher,
        tokens,
        zaehler,
        delay,
        ausstellung(),
        new CodeEinloesung(loginCodes, hasher, zaehler, schalter(), einloeseUhr),
        new NumericCodes(),
        schalter(),
        uhr);
  }

  @BeforeEach
  void baueDenAnwendungsfall() {
    when(hasher.hash(anyString())).thenReturn(DUMMY_HASH);
    when(loginCodes.save(any(LoginCode.class))).thenAnswer(aufruf -> aufruf.getArgument(0));
    useCase = anwendungsfall(uhr);
    clearInvocations(hasher);
  }

  private static void fordereFuenfmalAn(final LoginUseCase anforderung, final String email) {
    IntStream.range(0, 5).forEach(unbenutzt -> anforderung.codeAnfordern(email, IP));
  }

  private void kontoUnbekannt(final String email) {
    when(accounts.findByEmail(email)).thenReturn(Optional.empty());
  }

  private void kontoVorhanden() {
    when(accounts.findByEmail(MAIL)).thenReturn(Optional.of(konto()));
  }

  private void kontoUnbekannt() {
    kontoUnbekannt(MAIL);
  }

  /** Zu MAIL liegt ein frischer Code, dessen Hash HASH ist. */
  private void codeLiegtVor() {
    when(loginCodes.findByEmail(MAIL))
        .thenReturn(Optional.of(LoginCode.ausgestellt(MAIL, HASH, JETZT, CODE_LAUFZEIT)));
  }

  private void codeStimmt() {
    codeLiegtVor();
    when(hasher.matches(CODE, HASH)).thenReturn(true);
  }

  private void codeFalsch() {
    codeLiegtVor();
    when(hasher.matches(CODE, HASH)).thenReturn(false);
  }

  @Test
  void login_givenTheRightCode_thenReturnsTheAccount() {
    // Given
    kontoVorhanden();
    codeStimmt();
    when(tokens.encode(KONTO_ID, GENERATION, JETZT, LAUFZEIT)).thenReturn(TOKEN);

    // When
    final LoginResult ergebnis = useCase.login(MAIL, CODE, IP);

    // Then
    assertThat(ergebnis.account()).isEqualTo(konto());
  }

  @Test
  void login_givenTheRightCode_thenPutsTheFreshTokenIntoTheCookie() {
    // Given
    kontoVorhanden();
    codeStimmt();
    when(tokens.encode(KONTO_ID, GENERATION, JETZT, LAUFZEIT)).thenReturn(TOKEN);

    // When
    final LoginResult ergebnis = useCase.login(MAIL, CODE, IP);

    // Then — Name, Laufzeit und Secure-Merkmal kommen aus den Schaltern (E4).
    assertThat(ergebnis.cookie())
        .isEqualTo(new SessionCookie("fbcrm_session", TOKEN, LAUFZEIT, true));
  }

  @Test
  void login_givenTheRightCode_thenUsesItUp() {
    // Given — Kriterium 4: der Code gilt nur einmal.
    kontoVorhanden();
    codeStimmt();
    when(tokens.encode(anyLong(), anyLong(), any(), any())).thenReturn(TOKEN);

    // When
    useCase.login(MAIL, CODE, IP);

    // Then
    verify(loginCodes).deleteByEmail(MAIL);
  }

  @Test
  void login_givenTheRightCode_thenDoesNotDelayTheAnswer() {
    // Given
    kontoVorhanden();
    codeStimmt();
    when(tokens.encode(anyLong(), anyLong(), any(), any())).thenReturn(TOKEN);

    // When
    useCase.login(MAIL, CODE, IP);

    // Then
    verify(delay, never()).apply();
  }

  @Test
  void login_givenTheRightCode_thenClearsTheCounterOfTheOriginButNotThoseOfTheAddress() {
    // Given
    kontoVorhanden();
    codeStimmt();
    when(tokens.encode(anyLong(), anyLong(), any(), any())).thenReturn(TOKEN);

    // When
    useCase.login(MAIL, CODE, IP);

    // Then — geloescht wird nur die Herkunft; die Adresszaehler bleiben stehen (E3).
    verify(zaehler).recordSuccess(IP);
    verify(zaehler, never()).record(eq(ADRESSE_STUNDE), anyString());
  }

  @Test
  void login_givenAWrongCode_thenFails() {
    // Given
    kontoVorhanden();
    codeFalsch();

    // When / Then
    assertThatThrownBy(() -> useCase.login(MAIL, CODE, IP)).isInstanceOf(LoginFailed.class);
  }

  @Test
  void login_givenAWrongCode_thenDelaysTheAnswer() {
    // Given
    kontoVorhanden();
    codeFalsch();

    // When
    assertThatThrownBy(() -> useCase.login(MAIL, CODE, IP)).isInstanceOf(LoginFailed.class);

    // Then
    verify(delay).apply();
  }

  @Test
  void login_givenAWrongCode_thenNeitherLooksUpTheAccountNorIssuesASession() {
    // Given
    kontoVorhanden();
    codeFalsch();

    // When
    assertThatThrownBy(() -> useCase.login(MAIL, CODE, IP)).isInstanceOf(LoginFailed.class);

    // Then
    verify(accounts, never()).findByEmail(anyString());
    verify(tokens, never()).encode(anyLong(), anyLong(), any(), any());
  }

  @Test
  void login_givenAnUnknownAddress_thenFailsWithTheSameMessageAfterTheSameHashCheck() {
    // Given — Kriterium 3: kein anderer Typ, keine andere Meldung als beim falschen Code.
    kontoVorhanden();
    codeFalsch();
    final Throwable falscherCode = catchThrowable(() -> useCase.login(MAIL, CODE, IP));
    when(loginCodes.findByEmail(FREMD)).thenReturn(Optional.empty());

    // When
    final Throwable unbekannt = catchThrowable(() -> useCase.login(FREMD, CODE, IP));

    // Then — E13: auch hier ein Argon2-Vergleich, gegen den Wegwerf-Hash.
    assertThat(unbekannt).isInstanceOf(LoginFailed.class).hasMessage(falscherCode.getMessage());
    verify(hasher).matches(CODE, DUMMY_HASH);
    verify(delay, times(2)).apply();
  }

  @Test
  void login_givenARightCodeWithoutAnAccount_thenFails() {
    // Given — etwa der Code einer laufenden Einrichtung: Er meldet niemanden an.
    kontoUnbekannt();
    codeStimmt();

    // When / Then
    assertThatThrownBy(() -> useCase.login(MAIL, CODE, IP)).isInstanceOf(LoginFailed.class);
    verify(delay).apply();
  }

  @Test
  void login_thenCountsTheInputAtTheOriginBeforeTheCodeIsLookedAt() {
    // Given — Kriterium 5: Eingaben zaehlen je Herkunft, die gescheiterten wie die gelungenen.
    kontoVorhanden();
    codeFalsch();
    final InOrder reihenfolge = inOrder(zaehler, loginCodes);

    // When
    assertThatThrownBy(() -> useCase.login(MAIL, CODE, IP)).isInstanceOf(LoginFailed.class);

    // Then
    reihenfolge.verify(zaehler).record(HERKUNFT, IP);
    reihenfolge.verify(loginCodes).findByEmail(MAIL);
  }

  @Test
  void login_givenTenFailedInputsFromTheOrigin_thenRefusesWithTheWaitBeforeLookingAtTheCode() {
    // Given — die Bremse greift vor der Pruefung, sonst waere sie keine.
    kontoVorhanden();
    codeFalsch();
    IntStream.range(0, 10)
        .forEach(
            unbenutzt ->
                assertThatThrownBy(() -> useCase.login(MAIL, CODE, IP))
                    .isInstanceOf(LoginFailed.class));
    clearInvocations(loginCodes, hasher);

    // When / Then
    assertThatThrownBy(() -> useCase.login(MAIL, CODE, IP))
        .isInstanceOfSatisfying(
            TooManyLoginAttempts.class,
            gebremst -> assertThat(gebremst.restwartezeit()).isEqualTo(Duration.ofMinutes(15)));
    verifyNoInteractions(loginCodes, hasher);
  }

  @Test
  @DisplayName("Szenario: Abgelaufener Code")
  void szenarioAbgelaufenerCode_thenRejectedWithTheHintToRequestANewOne() {
    // Given — ein Code, angefordert vor mehr als login-code-ttl: Die Uhr der Einloesung ist um mehr
    // als die Laufzeit vorgestellt.
    kontoVorhanden();
    useCase.codeAnfordern(MAIL, IP);
    final ArgumentCaptor<LoginCode> ausgestellt = ArgumentCaptor.forClass(LoginCode.class);
    verify(loginCodes).save(ausgestellt.capture());
    final ArgumentCaptor<String> mailtext = ArgumentCaptor.forClass(String.class);
    verify(postausgang).enqueue(eq(MAIL), anyString(), mailtext.capture());
    final String code =
        mailtext
            .getValue()
            .lines()
            .filter(zeile -> zeile.matches("\\d{6}"))
            .findFirst()
            .orElseThrow();
    when(loginCodes.findByEmail(MAIL)).thenReturn(Optional.of(ausgestellt.getValue()));
    when(hasher.matches(code, ausgestellt.getValue().codeHash())).thenReturn(true);
    final LoginUseCase spaeter = anwendungsfall(Clock.offset(uhr, CODE_LAUFZEIT.plusSeconds(1)));

    // When
    final Throwable abgewiesen = catchThrowable(() -> spaeter.login(MAIL, code, IP));

    // Then — abgewiesen, und die Meldung sagt, was zu tun ist (Kriterium 4).
    assertThat(abgewiesen).isInstanceOf(LoginFailed.class);
    assertThat(
            AnnotatedElementUtils.findMergedAnnotation(abgewiesen.getClass(), ResponseStatus.class))
        .extracting(ResponseStatus::reason)
        .asString()
        .contains("neuen Code anfordern");
  }

  @Test
  @DisplayName("Szenario: Unbekannte Adresse")
  void szenarioUnbekannteAdresse_thenSameAnswerAndNoMail() {
    // Given — zu MAIL gibt es das Konto, zu FREMD keines.
    final LoginUseCase anforderung = useCase;
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
    final LoginUseCase anforderung = useCase;
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
    final LoginUseCase anforderung = useCase;
    when(accounts.findByEmail("Manne@Example.org")).thenReturn(Optional.of(konto()));

    // When
    anforderung.codeAnfordern("Manne@Example.org", IP);

    // Then
    verify(postausgang).enqueue(eq(MAIL), anyString(), anyString());
  }

  @Test
  void codeAnfordern_givenTheAddressOfTheAccount_thenHashesExactlyOnce() {
    // Given
    final LoginUseCase anforderung = useCase;
    kontoVorhanden();

    // When
    anforderung.codeAnfordern(MAIL, IP);

    // Then — E13: ein Argon2-Durchlauf, der des ausgestellten Codes.
    verify(hasher, times(1)).hash(anyString());
  }

  @Test
  void codeAnfordern_givenAnUnknownAddress_thenHashesExactlyOnceAGeneratedCode() {
    // Given
    final LoginUseCase anforderung = useCase;
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
    final LoginUseCase anforderung = useCase;
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
    final LoginUseCase anforderung = useCase;
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
    final LoginUseCase anforderung = useCase;
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
    final LoginUseCase anforderung = useCase;
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
