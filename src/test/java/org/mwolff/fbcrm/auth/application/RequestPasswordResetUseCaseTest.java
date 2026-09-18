package org.mwolff.fbcrm.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.auth.AuthProperties;
import org.mwolff.fbcrm.auth.domain.Account;
import org.mwolff.fbcrm.auth.domain.AccountRepository;
import org.mwolff.fbcrm.auth.domain.PasswordResetToken;
import org.mwolff.fbcrm.auth.domain.PasswordResetTokenRepository;
import org.mwolff.fbcrm.auth.domain.Role;
import org.mwolff.fbcrm.common.SecureTokens;
import org.mwolff.fbcrm.mail.MailProperties;
import org.mwolff.fbcrm.mail.application.EnqueueMailUseCase;

/**
 * Die Anforderung eines neuen Passworts (K7, E24, E25).
 *
 * <p>Der Kern steht in den beiden Faellen „bekannte Adresse" und „unbekannte Adresse": Nach aussen
 * sind sie nicht zu unterscheiden — der Anwendungsfall liefert in beiden Faellen nichts und wirft
 * in beiden Faellen nichts. Der Unterschied liegt ausschliesslich im Postausgangsfach, und genau
 * dort wird er geprueft (E25).
 */
@ExtendWith(MockitoExtension.class)
class RequestPasswordResetUseCaseTest {

  private static final String MAIL = "manne@example.org";
  private static final String FREMD = "fremd@example.org";
  private static final Instant JETZT = Instant.parse("2026-09-18T10:00:00Z");
  private static final Duration LAUFZEIT = Duration.ofHours(1);
  private static final long KONTO_ID = 11L;

  @Mock private AccountRepository accounts;
  @Mock private PasswordResetTokenRepository resetTokens;
  @Mock private EnqueueMailUseCase postausgang;

  @Captor private ArgumentCaptor<PasswordResetToken> gespeicherterToken;
  @Captor private ArgumentCaptor<String> rumpf;

  private RequestPasswordResetUseCase anforderung;

  private static AuthProperties schalter() {
    return new AuthProperties(
        "geheimnis-mit-mindestens-32-zeichen-laenge",
        Duration.ofDays(1),
        LAUFZEIT,
        "fbcrm_session",
        true,
        10,
        Duration.ofMinutes(15),
        List.of());
  }

  private static Account konto() {
    return new Account(KONTO_ID, MAIL, "Manne", "hash", Role.ADMIN, 3, JETZT, JETZT);
  }

  @BeforeEach
  void baueDenAnwendungsfall() {
    anforderung =
        new RequestPasswordResetUseCase(
            accounts,
            resetTokens,
            postausgang,
            new SecureTokens(),
            schalter(),
            new MailProperties(true, "no-reply@fbcrm.local", "https://crm.example.org"),
            Clock.fixed(JETZT, ZoneOffset.UTC));
  }

  private void bekannteAdresse() {
    when(accounts.findByEmail(MAIL)).thenReturn(Optional.of(konto()));
  }

  private PasswordResetToken ausgestellterToken() {
    verify(resetTokens).save(gespeicherterToken.capture());
    return gespeicherterToken.getValue();
  }

  private String versandterText() {
    verify(postausgang).enqueue(anyString(), anyString(), rumpf.capture());
    return rumpf.getValue();
  }

  @Test
  void request_givenAKnownAddress_thenIssuesATokenForThatAccount() {
    // Given
    bekannteAdresse();

    // When
    anforderung.request(MAIL);

    // Then
    assertThat(ausgestellterToken().accountId()).isEqualTo(KONTO_ID);
  }

  @Test
  void request_givenAKnownAddress_thenLetsTheTokenExpireAfterTheConfiguredLifetime() {
    // Given
    bekannteAdresse();

    // When
    anforderung.request(MAIL);

    // Then
    assertThat(ausgestellterToken().expiresAt()).isEqualTo(JETZT.plus(LAUFZEIT));
  }

  @Test
  void request_givenAKnownAddress_thenTheTokenIsNotUsedYet() {
    // Given
    bekannteAdresse();

    // When
    anforderung.request(MAIL);

    // Then
    assertThat(ausgestellterToken().usedAt()).isNull();
  }

  @Test
  void request_givenAKnownAddress_thenEnqueuesExactlyOneMessageToThatAddress() {
    // Given — E25: geprueft wird die Zeile im Postausgangsfach, nicht der SMTP-Verkehr.
    bekannteAdresse();

    // When
    anforderung.request(MAIL);

    // Then
    verify(postausgang, times(1)).enqueue(eq(MAIL), anyString(), anyString());
  }

  @Test
  void request_givenAKnownAddress_thenTheMailCarriesTheResetLink() {
    // Given
    bekannteAdresse();

    // When
    anforderung.request(MAIL);

    // Then
    assertThat(versandterText()).contains("https://crm.example.org/passwort-neu?token=");
  }

  @Test
  void request_givenAKnownAddress_thenTheMailCarriesTheRawTokenAndNeverItsHash() {
    // Given — in der Mail steht das Token, in der Datenbank sein Hash; nie umgekehrt.
    bekannteAdresse();

    // When
    anforderung.request(MAIL);

    // Then
    assertThat(versandterText()).doesNotContain(ausgestellterToken().tokenHash());
  }

  @Test
  void request_givenAKnownAddress_thenTheMailNamesTheLifetimeOfTheLink() {
    // Given — K7: „nur begrenzte Zeit" gehoert in die Nachricht, sonst raet der Empfaenger.
    bekannteAdresse();

    // When
    anforderung.request(MAIL);

    // Then
    assertThat(versandterText()).contains("60 Minuten");
  }

  @Test
  void request_givenAKnownAddress_thenTheMailGreetsTheAccountHolder() {
    // Given
    bekannteAdresse();

    // When
    anforderung.request(MAIL);

    // Then
    assertThat(versandterText()).contains("Manne");
  }

  @Test
  void request_givenAnUnknownAddress_thenIssuesNoTokenAtAll() {
    // Given
    when(accounts.findByEmail(FREMD)).thenReturn(Optional.empty());

    // When
    anforderung.request(FREMD);

    // Then
    verify(resetTokens, never()).save(any(PasswordResetToken.class));
  }

  @Test
  void request_givenAnUnknownAddress_thenEnqueuesNothing() {
    // Given — K7: der Unterschied darf sich ausschliesslich im Postausgangsfach zeigen.
    when(accounts.findByEmail(FREMD)).thenReturn(Optional.empty());

    // When
    anforderung.request(FREMD);

    // Then
    verifyNoInteractions(postausgang);
  }

  @Test
  void request_givenTwoCalls_thenIssuesTwoDifferentTokens() {
    // Given — jede Anforderung bekommt frisches Zufallsmaterial.
    bekannteAdresse();

    // When
    anforderung.request(MAIL);
    anforderung.request(MAIL);

    // Then
    verify(resetTokens, times(2)).save(gespeicherterToken.capture());
    assertThat(gespeicherterToken.getAllValues())
        .extracting(PasswordResetToken::tokenHash)
        .doesNotHaveDuplicates();
  }
}
