package org.mwolff.fbcrm.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.auth.domain.Account;
import org.mwolff.fbcrm.auth.domain.AccountRepository;
import org.mwolff.fbcrm.auth.domain.PasswordHasher;
import org.mwolff.fbcrm.auth.domain.PasswordResetToken;
import org.mwolff.fbcrm.auth.domain.PasswordResetTokenRepository;
import org.mwolff.fbcrm.auth.domain.Role;
import org.mwolff.fbcrm.common.SecureTokens;

/**
 * Das Einloesen des Reset-Links (K7, E24).
 *
 * <p>Drei Aussagen tragen diesen Anwendungsfall. Erstens: Nachgeschlagen wird ueber den <b>Hash</b>
 * des vorgelegten Tokens — in der Datenbank liegt nie das Token selbst. Zweitens: Das Einloesen
 * zaehlt die Sitzungs-Generation hoch, wodurch jedes vor dem Reset ausgestellte Cookie ungueltig
 * wird. Drittens: Unbekannt, abgelaufen und bereits benutzt fuehren zur <b>selben</b> Abweisung —
 * ein Unterschied verriete, ob es den Link je gab.
 */
@ExtendWith(MockitoExtension.class)
class ConfirmPasswordResetUseCaseTest {

  private static final String TOKEN = "roh-token-aus-der-mail";
  private static final String NEUES_PASSWORT = "neues-sicheres-passwort";
  private static final String NEUER_HASH = "$argon2id$v=19$neu";
  private static final Instant AUSGESTELLT = Instant.parse("2026-09-18T10:00:00Z");
  private static final Instant JETZT = Instant.parse("2026-09-18T10:30:00Z");
  private static final Instant ABLAUF = Instant.parse("2026-09-18T11:00:00Z");
  private static final long KONTO_ID = 11L;

  @Mock private AccountRepository accounts;
  @Mock private PasswordResetTokenRepository resetTokens;
  @Mock private PasswordHasher hasher;

  @Captor private ArgumentCaptor<Account> gespeichertesKonto;
  @Captor private ArgumentCaptor<PasswordResetToken> gespeicherterToken;

  private final SecureTokens tokens = new SecureTokens();

  private ConfirmPasswordResetUseCase einloesung;

  private static Account konto() {
    return new Account(
        KONTO_ID,
        "manne@example.org",
        "Manne",
        "alter-hash",
        Role.ADMIN,
        3,
        AUSGESTELLT,
        AUSGESTELLT);
  }

  private PasswordResetToken token(final Instant benutztAm, final Instant ablauf) {
    return new PasswordResetToken(7L, KONTO_ID, tokens.hash(TOKEN), ablauf, benutztAm, AUSGESTELLT);
  }

  @BeforeEach
  void baueDenAnwendungsfall() {
    einloesung =
        new ConfirmPasswordResetUseCase(
            accounts, resetTokens, hasher, tokens, Clock.fixed(JETZT, ZoneOffset.UTC));
  }

  private void gueltigerToken() {
    when(resetTokens.findByTokenHash(tokens.hash(TOKEN)))
        .thenReturn(Optional.of(token(null, ABLAUF)));
  }

  private void vollstaendigerErfolgsfall() {
    gueltigerToken();
    when(accounts.findById(KONTO_ID)).thenReturn(Optional.of(konto()));
    when(hasher.hash(NEUES_PASSWORT)).thenReturn(NEUER_HASH);
  }

  private Account fortgeschriebenesKonto() {
    verify(accounts).save(gespeichertesKonto.capture());
    return gespeichertesKonto.getValue();
  }

  @Test
  void confirm_givenAValidToken_thenLooksUpTheHashAndNeverTheRawToken() {
    // Given
    vollstaendigerErfolgsfall();

    // When
    einloesung.confirm(TOKEN, NEUES_PASSWORT);

    // Then
    verify(resetTokens).findByTokenHash(tokens.hash(TOKEN));
  }

  @Test
  void confirm_givenAValidToken_thenStoresOnlyTheHashOfTheNewPassword() {
    // Given
    vollstaendigerErfolgsfall();

    // When
    einloesung.confirm(TOKEN, NEUES_PASSWORT);

    // Then
    assertThat(fortgeschriebenesKonto().passwordHash()).isEqualTo(NEUER_HASH);
  }

  @Test
  void confirm_givenAValidToken_thenRaisesTheSessionGeneration() {
    // Given — K7: bestehende Anmeldungen auf anderen Geraeten enden.
    vollstaendigerErfolgsfall();

    // When
    einloesung.confirm(TOKEN, NEUES_PASSWORT);

    // Then
    assertThat(fortgeschriebenesKonto().sessionGeneration()).isEqualTo(4);
  }

  @Test
  void confirm_givenAValidToken_thenStampsTheAccountWithTheInjectedClock() {
    // Given
    vollstaendigerErfolgsfall();

    // When
    einloesung.confirm(TOKEN, NEUES_PASSWORT);

    // Then
    assertThat(fortgeschriebenesKonto().updatedAt()).isEqualTo(JETZT);
  }

  @Test
  void confirm_givenAValidToken_thenMarksTheTokenAsUsed() {
    // Given
    vollstaendigerErfolgsfall();

    // When
    einloesung.confirm(TOKEN, NEUES_PASSWORT);

    // Then
    verify(resetTokens).save(gespeicherterToken.capture());
    assertThat(gespeicherterToken.getValue().usedAt()).isEqualTo(JETZT);
  }

  @Test
  void confirm_givenAnUnknownToken_thenRefusesTheLink() {
    // Given
    when(resetTokens.findByTokenHash(tokens.hash(TOKEN))).thenReturn(Optional.empty());

    // When / Then
    assertThatExceptionOfType(PasswordResetLinkInvalid.class)
        .isThrownBy(() -> einloesung.confirm(TOKEN, NEUES_PASSWORT));
  }

  @Test
  void confirm_givenAnUnknownToken_thenChangesNoPassword() {
    // Given
    when(resetTokens.findByTokenHash(tokens.hash(TOKEN))).thenReturn(Optional.empty());

    // When
    assertThatExceptionOfType(PasswordResetLinkInvalid.class)
        .isThrownBy(() -> einloesung.confirm(TOKEN, NEUES_PASSWORT));

    // Then
    verify(accounts, never()).save(any(Account.class));
  }

  @Test
  void confirm_givenAnAlreadyUsedToken_thenRefusesTheLink() {
    // Given — K7: der Link gilt genau einmal.
    when(resetTokens.findByTokenHash(tokens.hash(TOKEN)))
        .thenReturn(Optional.of(token(AUSGESTELLT, ABLAUF)));

    // When / Then
    assertThatExceptionOfType(PasswordResetLinkInvalid.class)
        .isThrownBy(() -> einloesung.confirm(TOKEN, NEUES_PASSWORT));
  }

  @Test
  void confirm_givenAnExpiredToken_thenRefusesTheLink() {
    // Given — feste Uhr: der Ablauf liegt eine Minute vor „jetzt".
    when(resetTokens.findByTokenHash(tokens.hash(TOKEN)))
        .thenReturn(Optional.of(token(null, JETZT.minusSeconds(60))));

    // When / Then
    assertThatExceptionOfType(PasswordResetLinkInvalid.class)
        .isThrownBy(() -> einloesung.confirm(TOKEN, NEUES_PASSWORT));
  }

  @Test
  void confirm_givenATokenWhoseAccountIsGone_thenRefusesTheLink() {
    // Given — der Fremdschluessel raeumt mit, aber der Anwendungsfall verlaesst sich nicht darauf.
    gueltigerToken();
    when(accounts.findById(KONTO_ID)).thenReturn(Optional.empty());

    // When / Then
    assertThatExceptionOfType(PasswordResetLinkInvalid.class)
        .isThrownBy(() -> einloesung.confirm(TOKEN, NEUES_PASSWORT));
  }

  @Test
  void confirm_givenAnInvalidToken_thenCarriesNoMessageOutwards() {
    // Given — unbekannt, abgelaufen und benutzt sehen von aussen gleich aus.
    when(resetTokens.findByTokenHash(tokens.hash(TOKEN))).thenReturn(Optional.empty());

    // When / Then
    assertThatExceptionOfType(PasswordResetLinkInvalid.class)
        .isThrownBy(() -> einloesung.confirm(TOKEN, NEUES_PASSWORT))
        .withMessage(null);
  }

  @Test
  void ensureRedeemable_givenAValidToken_thenPasses() {
    // Given
    gueltigerToken();

    // When / Then — kein Wurf heisst: die Seite darf ein Formular zeigen.
    einloesung.ensureRedeemable(TOKEN);
  }

  @Test
  void ensureRedeemable_givenAValidToken_thenChangesNothingAtAll() {
    // Given — E24: die Voranfrage ist nebenwirkungsfrei, der Token bleibt einloesbar.
    gueltigerToken();

    // When
    einloesung.ensureRedeemable(TOKEN);

    // Then
    verify(resetTokens, never()).save(any(PasswordResetToken.class));
    verify(accounts, never()).save(any(Account.class));
  }

  @Test
  void ensureRedeemable_givenAnAlreadyUsedToken_thenRefusesTheLink() {
    // Given — K7: ein verbrauchter Link zeigt eine Meldung statt eines Formulars.
    when(resetTokens.findByTokenHash(tokens.hash(TOKEN)))
        .thenReturn(Optional.of(token(AUSGESTELLT, ABLAUF)));

    // When / Then
    assertThatExceptionOfType(PasswordResetLinkInvalid.class)
        .isThrownBy(() -> einloesung.ensureRedeemable(TOKEN));
  }

  @Test
  void ensureRedeemable_givenAnExpiredToken_thenRefusesTheLink() {
    // Given
    when(resetTokens.findByTokenHash(tokens.hash(TOKEN)))
        .thenReturn(Optional.of(token(null, JETZT.minusSeconds(60))));

    // When / Then
    assertThatExceptionOfType(PasswordResetLinkInvalid.class)
        .isThrownBy(() -> einloesung.ensureRedeemable(TOKEN));
  }

  @Test
  void ensureRedeemable_givenAnUnknownToken_thenRefusesTheLink() {
    // Given
    when(resetTokens.findByTokenHash(tokens.hash(TOKEN))).thenReturn(Optional.empty());

    // When / Then
    assertThatExceptionOfType(PasswordResetLinkInvalid.class)
        .isThrownBy(() -> einloesung.ensureRedeemable(TOKEN));
  }
}
