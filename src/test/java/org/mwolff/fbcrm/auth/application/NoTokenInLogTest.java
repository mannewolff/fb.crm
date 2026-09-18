package org.mwolff.fbcrm.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
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
import org.mwolff.fbcrm.auth.domain.PasswordHasher;
import org.mwolff.fbcrm.auth.domain.PasswordResetToken;
import org.mwolff.fbcrm.auth.domain.PasswordResetTokenRepository;
import org.mwolff.fbcrm.auth.domain.Role;
import org.mwolff.fbcrm.common.SecureTokens;
import org.mwolff.fbcrm.mail.MailProperties;
import org.mwolff.fbcrm.mail.OutboxProperties;
import org.mwolff.fbcrm.mail.application.EnqueueMailUseCase;
import org.mwolff.fbcrm.mail.domain.MailGateway;
import org.mwolff.fbcrm.mail.domain.OutboxMessage;
import org.mwolff.fbcrm.mail.domain.OutboxRepository;
import org.mwolff.fbcrm.mail.infrastructure.OutboxDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailSendException;

/**
 * Der gesamte Reset-Weg schreibt weder Token noch Token-Hash ins Log (CLAUDE-security.md).
 *
 * <p>Ein Log-Archiv ueberlebt die Stunde, die ein Reset-Link gilt, bei weitem — und es wird von
 * mehr Menschen gelesen als die Datenbank. Steht der Link dort, ist er dort so lange gueltig, wie
 * er ueberhaupt gilt.
 *
 * <p>Geprueft wird der Weg an seiner gefaehrlichsten Stelle: beim <b>gescheiterten</b>
 * Zustellversuch. Nur dort schreibt die Anwendung ueberhaupt etwas ueber eine einzelne Nachricht,
 * und genau dort waere die Versuchung am groessten, „zur Fehlersuche" den Rumpf mitzugeben.
 */
@ExtendWith(MockitoExtension.class)
class NoTokenInLogTest {

  private static final String MAIL = "manne@example.org";
  private static final String LINK_ANFANG = "https://crm.example.org/passwort-neu?token=";
  private static final Instant JETZT = Instant.parse("2026-09-18T10:00:00Z");
  private static final long KONTO_ID = 11L;

  @Mock private AccountRepository accounts;
  @Mock private PasswordResetTokenRepository resetTokens;
  @Mock private PasswordHasher hasher;
  @Mock private OutboxRepository outbox;
  @Mock private MailGateway gateway;

  @Captor private ArgumentCaptor<PasswordResetToken> gespeicherterToken;
  @Captor private ArgumentCaptor<OutboxMessage> eingestellterAuftrag;

  private final SecureTokens tokens = new SecureTokens();
  private final ListAppender<ILoggingEvent> mitgeschrieben = new ListAppender<>();
  private final Clock uhr = Clock.fixed(JETZT, ZoneOffset.UTC);

  private ch.qos.logback.classic.Logger wurzel;
  private @org.jspecify.annotations.Nullable Level vorherigeStufe;
  private String token = "";
  private String hash = "";

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

  private static MailProperties mailSchalter() {
    return new MailProperties(true, "no-reply@fbcrm.local", "https://crm.example.org");
  }

  private static Account konto() {
    return new Account(KONTO_ID, MAIL, "Manne", "hash", Role.ADMIN, 0, JETZT, JETZT);
  }

  @BeforeEach
  void haengeDichAnDasWurzelProtokoll() {
    wurzel = ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger(Logger.ROOT_LOGGER_NAME);
    vorherigeStufe = wurzel.getLevel();
    mitgeschrieben.setContext(wurzel.getLoggerContext());
    mitgeschrieben.start();
    wurzel.addAppender(mitgeschrieben);
    wurzel.setLevel(Level.TRACE);
  }

  /*
   * Die Stufe wird zurueckgesetzt, nicht nur der Appender abgehaengt: Der Wurzel-Logger gehoert
   * der ganzen JVM, und ein zurueckgelassenes TRACE flutete jeden folgenden Test der Suite.
   */
  @AfterEach
  void loeseDichWiederAb() {
    wurzel.setLevel(vorherigeStufe);
    wurzel.detachAppender(mitgeschrieben);
    mitgeschrieben.stop();
  }

  /** Schritt 1: ein neues Passwort anfordern. Danach stehen {@code token} und {@code hash} fest. */
  private OutboxMessage fordereAn() {
    when(accounts.findByEmail(MAIL)).thenReturn(Optional.of(konto()));
    new RequestPasswordResetUseCase(
            accounts,
            resetTokens,
            new EnqueueMailUseCase(outbox, uhr),
            tokens,
            schalter(),
            mailSchalter(),
            uhr)
        .request(MAIL);

    verify(resetTokens).save(gespeicherterToken.capture());
    verify(outbox).save(eingestellterAuftrag.capture());
    final OutboxMessage auftrag = eingestellterAuftrag.getValue();
    final int anfang = auftrag.body().indexOf(LINK_ANFANG) + LINK_ANFANG.length();
    token = auftrag.body().substring(anfang, auftrag.body().indexOf('\n', anfang));
    hash = gespeicherterToken.getValue().tokenHash();
    return auftrag;
  }

  /**
   * Schritt 2: der Zustellversuch scheitert — der einzige Weg, auf dem etwas protokolliert wird.
   */
  private void stelleErfolglosZu(final OutboxMessage auftrag) {
    when(outbox.findDue(eq(JETZT), anyInt(), anyInt()))
        .thenReturn(
            List.of(
                new OutboxMessage(
                    7L,
                    auftrag.recipient(),
                    auftrag.subject(),
                    auftrag.body(),
                    0,
                    JETZT,
                    null,
                    JETZT)));
    doThrow(new MailSendException("SMTP nicht erreichbar"))
        .when(gateway)
        .send(anyString(), anyString(), anyString());
    new OutboxDispatcher(
            outbox, gateway, new OutboxProperties(true, 5000L, 8, 7), mailSchalter(), uhr)
        .dispatch();
  }

  /** Schritt 3: den Link einloesen. */
  private void loeseEin() {
    when(resetTokens.findByTokenHash(hash)).thenReturn(Optional.of(gespeicherterToken.getValue()));
    when(accounts.findById(KONTO_ID)).thenReturn(Optional.of(konto()));
    new ConfirmPasswordResetUseCase(accounts, resetTokens, hasher, tokens, uhr)
        .confirm(token, "neues-sicheres-passwort");
  }

  private void gesamterResetWeg() {
    stelleErfolglosZu(fordereAn());
    loeseEin();
  }

  private String protokoll() {
    return mitgeschrieben.list.stream()
        .map(ILoggingEvent::getFormattedMessage)
        .reduce("", (links, rechts) -> links + '\n' + rechts);
  }

  @Test
  void resetPath_thenNeverWritesTheTokenIntoTheLog() {
    // When
    gesamterResetWeg();

    // Then
    assertThat(protokoll()).doesNotContain(token);
  }

  @Test
  void resetPath_thenNeverWritesTheTokenHashIntoTheLog() {
    // When — auch der Hash gehoert nicht ins Log: er ist der Schluessel der Tabelle.
    gesamterResetWeg();

    // Then
    assertThat(protokoll()).doesNotContain(hash);
  }

  @Test
  void resetPath_thenNeverWritesTheResetLinkIntoTheLog() {
    // When — der Rumpf traegt den Link; er darf auch nicht als Ganzes hineingeraten.
    gesamterResetWeg();

    // Then
    assertThat(protokoll()).doesNotContain(LINK_ANFANG);
  }

  @Test
  void resetPath_givenAFailedDelivery_thenStillReportsThatSomethingWentWrong() {
    // When — Schweigen waere die falsche Loesung: Der Betreiber muss den Fehlschlag sehen.
    gesamterResetWeg();

    // Then
    assertThat(protokoll()).contains("Zustellauftrag 7");
  }
}
