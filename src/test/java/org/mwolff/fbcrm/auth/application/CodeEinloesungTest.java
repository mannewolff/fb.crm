package org.mwolff.fbcrm.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mwolff.fbcrm.auth.infrastructure.LoginAttemptLimiter.Zaehlart.HERKUNFT;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mwolff.fbcrm.auth.AuthProperties;
import org.mwolff.fbcrm.auth.domain.LoginCode;
import org.mwolff.fbcrm.auth.domain.LoginCodeRepository;
import org.mwolff.fbcrm.auth.domain.SecretHasher;
import org.mwolff.fbcrm.auth.infrastructure.LoginAttemptLimiter;

/**
 * Das Einloesen eines Codes (Kriterium 4, E13, E27) — fuer Anmeldung und Einrichtung dieselbe
 * Regel.
 *
 * <p>Die Zaehlbremse laeuft <b>echt</b> und bespitzelt; gemockt sind nur der Bestand der Codes und
 * der Hasher. Die Mocks sind von Hand gesetzt statt ueber {@code @InjectMocks}: Der Baustein
 * rechnet seinen Wegwerf-Hash schon im Konstruktor, und der Hasher muss feststehen, bevor er
 * entsteht.
 */
class CodeEinloesungTest {

  private static final String MAIL = "manne@example.org";
  private static final String CODE = "042424";
  private static final String HASH = "argon2-hash-des-codes";
  private static final String DUMMY_HASH = "argon2-hash-ohne-code";
  private static final String IP = "203.0.113.7";
  private static final Instant JETZT = Instant.parse("2026-10-20T10:00:00Z");
  private static final Duration LAUFZEIT = Duration.ofMinutes(10);

  private final LoginCodeRepository loginCodes = mock(LoginCodeRepository.class);
  private final SecretHasher hasher = mock(SecretHasher.class);
  private final Clock uhr = Clock.fixed(JETZT, ZoneOffset.UTC);
  private final LoginAttemptLimiter zaehler = spy(new LoginAttemptLimiter(schalter(), uhr));

  private CodeEinloesung einloesung;

  static AuthProperties schalter() {
    return new AuthProperties(
        "geheimnis-mit-mindestens-32-zeichen-laenge",
        Duration.ofDays(30),
        "fbcrm_session",
        true,
        10,
        Duration.ofMinutes(15),
        5,
        Duration.ofHours(1),
        10,
        Duration.ofHours(24),
        LAUFZEIT,
        5,
        List.of());
  }

  @BeforeEach
  void baueDenBaustein() {
    when(hasher.hash(anyString())).thenReturn(DUMMY_HASH);
    einloesung = new CodeEinloesung(loginCodes, hasher, zaehler, schalter(), uhr);
  }

  /** Ein Code, ausgestellt vor der genannten Zeit, mit so vielen Fehleingaben. */
  private void codeLiegtVor(final Duration vor, final int fehleingaben) {
    final LoginCode zeile = LoginCode.ausgestellt(MAIL, HASH, JETZT.minus(vor), LAUFZEIT);
    when(loginCodes.findByEmail(MAIL))
        .thenReturn(
            Optional.of(
                new LoginCode(
                    7L,
                    zeile.email(),
                    zeile.codeHash(),
                    zeile.expiresAt(),
                    fehleingaben,
                    zeile.createdAt())));
  }

  private void codeLiegtVor(final int fehleingaben) {
    codeLiegtVor(Duration.ofMinutes(1), fehleingaben);
  }

  private LoginCode gespeicherteZeile() {
    final ArgumentCaptor<LoginCode> gespeichert = ArgumentCaptor.forClass(LoginCode.class);
    verify(loginCodes).save(gespeichert.capture());
    return gespeichert.getValue();
  }

  @Test
  void construction_thenHashesOneNonEmptyRandomValueExactlyOnce() {
    // Given — der Baustein ist in baueDenBaustein() schon entstanden.
    final ArgumentCaptor<String> gehasht = ArgumentCaptor.forClass(String.class);

    // When / Then — ein Argon2-Durchlauf beim Hochfahren, nicht einer je Eingabe.
    verify(hasher).hash(gehasht.capture());
    assertThat(gehasht.getValue()).isNotBlank();
  }

  @Test
  void construction_givenASecondInstance_thenItsDummyHashRestsOnItsOwnRandomValue() {
    // Given
    final ArgumentCaptor<String> gehasht = ArgumentCaptor.forClass(String.class);

    // When
    new CodeEinloesung(loginCodes, hasher, zaehler, schalter(), uhr);

    // Then — zwei Instanzen, zwei Werte. Eine Konstante stuende als Code im Quellcode.
    verify(hasher, times(2)).hash(gehasht.capture());
    assertThat(gehasht.getAllValues()).doesNotHaveDuplicates();
  }

  @Test
  void loese_givenTheRightCode_thenSucceeds() {
    // Given
    codeLiegtVor(0);
    when(hasher.matches(CODE, HASH)).thenReturn(true);

    // When / Then
    assertThat(einloesung.loese(MAIL, CODE, IP)).isTrue();
  }

  @Test
  void loese_givenTheRightCode_thenUsesItUp() {
    // Given — Kriterium 4: nur einmal gueltig.
    codeLiegtVor(0);
    when(hasher.matches(CODE, HASH)).thenReturn(true);

    // When
    einloesung.loese(MAIL, CODE, IP);

    // Then
    verify(loginCodes).deleteByEmail(MAIL);
    verify(loginCodes, never()).save(any(LoginCode.class));
  }

  @Test
  void loese_givenTheRightCode_thenClearsTheCounterOfTheOrigin() {
    // Given — wer sich neunmal vertippt und beim zehnten Mal hereinkommt, soll nicht gesperrt sein.
    codeLiegtVor(0);
    when(hasher.matches(CODE, HASH)).thenReturn(true);

    // When
    einloesung.loese(MAIL, CODE, IP);

    // Then
    verify(zaehler).recordSuccess(IP);
  }

  @Test
  void loese_givenNoCodeForTheAddress_thenFailsAfterTheSameHashCheck() {
    // Given — E13: ohne den Durchlauf antwortete die Instanz auf unbekannte Adressen frueher.
    when(loginCodes.findByEmail(MAIL)).thenReturn(Optional.empty());

    // When
    final boolean eingeloest = einloesung.loese(MAIL, CODE, IP);

    // Then
    assertThat(eingeloest).isFalse();
    verify(hasher).matches(CODE, DUMMY_HASH);
  }

  @Test
  void loese_givenNoCodeAndADummyHashThatMatches_thenStillFails() {
    // Given — selbst wenn der Wegwerf-Hash je passte, entsteht daraus keine Einloesung.
    when(loginCodes.findByEmail(MAIL)).thenReturn(Optional.empty());
    when(hasher.matches(CODE, DUMMY_HASH)).thenReturn(true);

    // When / Then
    assertThat(einloesung.loese(MAIL, CODE, IP)).isFalse();
  }

  @Test
  void loese_givenNoCode_thenWritesNothing() {
    // Given
    when(loginCodes.findByEmail(MAIL)).thenReturn(Optional.empty());

    // When
    einloesung.loese(MAIL, CODE, IP);

    // Then
    verify(loginCodes, never()).save(any(LoginCode.class));
    verify(loginCodes, never()).deleteByEmail(anyString());
    verify(zaehler, never()).recordSuccess(anyString());
  }

  @Test
  void loese_givenAWrongCode_thenFailsAndCountsOneFailedAttempt() {
    // Given
    codeLiegtVor(2);
    when(hasher.matches(CODE, HASH)).thenReturn(false);

    // When
    final boolean eingeloest = einloesung.loese(MAIL, CODE, IP);

    // Then
    assertThat(eingeloest).isFalse();
    assertThat(gespeicherteZeile().failedAttempts()).isEqualTo(3);
    verify(loginCodes, never()).deleteByEmail(anyString());
  }

  @Test
  void loese_givenAWrongCode_thenLeavesTheCounterOfTheOriginStanding() {
    // Given
    codeLiegtVor(0);
    when(hasher.matches(CODE, HASH)).thenReturn(false);

    // When
    einloesung.loese(MAIL, CODE, IP);

    // Then
    verify(zaehler, never()).recordSuccess(anyString());
  }

  @Test
  void loese_givenTheFifthWrongCode_thenTheCodeExpires() {
    // Given — Kriterium 4: nach fuenf Fehleingaben verfaellt der Code.
    codeLiegtVor(4);
    when(hasher.matches(CODE, HASH)).thenReturn(false);

    // When
    final boolean eingeloest = einloesung.loese(MAIL, CODE, IP);

    // Then
    assertThat(eingeloest).isFalse();
    verify(loginCodes).deleteByEmail(MAIL);
    verify(loginCodes, never()).save(any(LoginCode.class));
  }

  @Test
  void loese_givenTheFourthWrongCode_thenTheCodeStillStands() {
    // Given — die Grenze liegt bei fuenf, nicht bei vier.
    codeLiegtVor(3);
    when(hasher.matches(CODE, HASH)).thenReturn(false);

    // When
    einloesung.loese(MAIL, CODE, IP);

    // Then
    assertThat(gespeicherteZeile().failedAttempts()).isEqualTo(4);
    verify(loginCodes, never()).deleteByEmail(anyString());
  }

  @Test
  void loese_givenAnExpiredCode_thenFailsEvenIfItIsRight() {
    // Given — Kriterium 4: ein Code gilt 10 Minuten.
    codeLiegtVor(LAUFZEIT.plusSeconds(1), 0);
    when(hasher.matches(CODE, HASH)).thenReturn(true);

    // When / Then
    assertThat(einloesung.loese(MAIL, CODE, IP)).isFalse();
  }

  @Test
  void loese_givenACodeAtTheMomentOfItsExpiry_thenFails() {
    // Given — im Augenblick des Ablaufs gilt er nicht mehr.
    codeLiegtVor(LAUFZEIT, 0);
    when(hasher.matches(CODE, HASH)).thenReturn(true);

    // When / Then
    assertThat(einloesung.loese(MAIL, CODE, IP)).isFalse();
  }

  @Test
  void loese_givenAnExpiredCode_thenStillSpendsTheHashCheckAndDropsTheRow() {
    // Given — der Vergleich laeuft auch hier, damit die Laufzeit nichts verraet (E13).
    codeLiegtVor(LAUFZEIT.plusSeconds(1), 0);

    // When
    einloesung.loese(MAIL, CODE, IP);

    // Then
    verify(hasher).matches(CODE, HASH);
    verify(loginCodes).deleteByEmail(MAIL);
    verify(loginCodes, never()).save(any(LoginCode.class));
    verify(zaehler, never()).recordSuccess(anyString());
  }

  @Test
  void bremse_givenAnOpenOrigin_thenCountsTheInput() {
    // When
    einloesung.bremse(IP);

    // Then — Kriterium 5: Anforderungen und Eingaben zaehlen zusammen.
    verify(zaehler).record(HERKUNFT, IP);
  }

  @Test
  void bremse_givenTenInputsInTheWindow_thenRefusesTheEleventhWithItsWait() {
    // Given
    IntStream.range(0, 10).forEach(unbenutzt -> einloesung.bremse(IP));

    // When / Then
    assertThatExceptionOfType(TooManyLoginAttempts.class)
        .isThrownBy(() -> einloesung.bremse(IP))
        .satisfies(
            gebremst -> assertThat(gebremst.restwartezeit()).isEqualTo(Duration.ofMinutes(15)));
  }

  @Test
  void bremse_givenARefusal_thenCountsNothingMore() {
    // Given — sonst naennte die Meldung nach der Wartezeit eine falsche Zeit (E14).
    IntStream.range(0, 10).forEach(unbenutzt -> einloesung.bremse(IP));
    clearInvocations(zaehler);

    // When
    assertThatExceptionOfType(TooManyLoginAttempts.class).isThrownBy(() -> einloesung.bremse(IP));

    // Then
    verify(zaehler, never()).record(HERKUNFT, IP);
  }

  @Test
  void bremse_givenABrakeWhoseWindowRanOutMeanwhile_thenLetsTheInputThrough() {
    // Given — zwischen der Frage und der Restwartezeit ist der letzte Eintrag herausgefallen.
    final LoginAttemptLimiter bremse = mock(LoginAttemptLimiter.class);
    when(bremse.isAllowed(HERKUNFT, IP)).thenReturn(false);
    when(bremse.restwartezeit(HERKUNFT, IP)).thenReturn(Duration.ZERO);
    final CodeEinloesung mitBremse =
        new CodeEinloesung(loginCodes, hasher, bremse, schalter(), uhr);

    // When
    mitBremse.bremse(IP);

    // Then
    verify(bremse).record(HERKUNFT, IP);
  }
}
