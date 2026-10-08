package org.mwolff.fbcrm.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
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
 * Was von einem Reset-Token in die Datenbank geht — und was nicht (E24, CLAUDE-security.md).
 *
 * <p>Der Test nimmt den Weg des Angreifers: Er liest das Token aus dem Link, der hinausgeht, und
 * vergleicht es mit dem, was gespeichert werden soll. Beide duerfen sich nicht gleichen; wer die
 * Tabelle liest, darf daraus keinen benutzbaren Link bauen koennen.
 */
@ExtendWith(MockitoExtension.class)
class PasswordResetTokenStorageTest {

  private static final String MAIL = "manne@example.org";
  private static final String LINK_ANFANG = "https://crm.example.org/passwort-neu?token=";
  private static final Instant JETZT = Instant.parse("2026-09-18T10:00:00Z");
  private static final long KONTO_ID = 11L;

  @Mock private AccountRepository accounts;
  @Mock private PasswordResetTokenRepository resetTokens;
  @Mock private EnqueueMailUseCase postausgang;

  @Captor private ArgumentCaptor<PasswordResetToken> gespeicherterToken;
  @Captor private ArgumentCaptor<String> rumpf;

  private final SecureTokens tokens = new SecureTokens();

  private RequestPasswordResetUseCase anforderung;

  private static AuthProperties schalter() {
    return new AuthProperties(
        "geheimnis-mit-mindestens-32-zeichen-laenge",
        Duration.ofDays(1),
        Duration.ofHours(1),
        "fbcrm_session",
        true,
        10,
        Duration.ofMinutes(15),
        List.of());
  }

  @BeforeEach
  void fordereEinNeuesPasswortAn() {
    anforderung =
        new RequestPasswordResetUseCase(
            accounts,
            resetTokens,
            postausgang,
            tokens,
            schalter(),
            new MailProperties(true, "no-reply@fbcrm.local", "https://crm.example.org"),
            Clock.fixed(JETZT, ZoneOffset.UTC));
    when(accounts.findByEmail(MAIL))
        .thenReturn(
            Optional.of(new Account(KONTO_ID, MAIL, "Manne", "hash", Role.ADMIN, 0, JETZT, JETZT)));
    anforderung.request(MAIL);
  }

  /** Das Token, wie es im Link der Mail steht. */
  private String tokenAusDerMail() {
    verify(postausgang).enqueue(anyString(), anyString(), rumpf.capture());
    final String text = rumpf.getValue();
    final int anfang = text.indexOf(LINK_ANFANG) + LINK_ANFANG.length();
    final int ende = text.indexOf('\n', anfang);
    return text.substring(anfang, ende);
  }

  private String gespeicherterHash() {
    verify(resetTokens).save(gespeicherterToken.capture());
    return gespeicherterToken.getValue().tokenHash();
  }

  @Test
  void request_thenStoresTheHashAndNeverTheTokenItself() {
    // When / Then
    assertThat(gespeicherterHash()).isNotEqualTo(tokenAusDerMail());
  }

  @Test
  void request_thenTheStoredValueIsTheSha256OfTheToken() {
    // When / Then — nachgeschlagen wird spaeter ueber genau diesen Hash.
    assertThat(gespeicherterHash()).isEqualTo(tokens.hash(tokenAusDerMail()));
  }

  @Test
  void request_thenTheStoredValueFitsTheColumnOfTheBaselineSchema() {
    // When / Then — token_hash ist varchar(64) in V1__baseline.sql.
    assertThat(gespeicherterHash()).hasSizeLessThanOrEqualTo(64);
  }

  @Test
  void request_thenTheTokenInTheMailIsNotGuessable() {
    // When / Then — 256 Bit als Base64url ohne Auffuellzeichen sind 43 Zeichen.
    assertThat(tokenAusDerMail()).hasSize(43);
  }
}
