package org.mwolff.fbcrm.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
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
import java.io.Serial;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.auth.AuthProperties;
import org.mwolff.fbcrm.auth.domain.LoginCode;
import org.mwolff.fbcrm.auth.domain.LoginCodeRepository;
import org.mwolff.fbcrm.auth.domain.SecretHasher;
import org.mwolff.fbcrm.common.NumericCodes;
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
 * Die Anforderung eines Codes schreibt ihn ins Protokoll genau dann, wenn der Mailversand
 * ausgeschaltet ist — und seinen Hash nie (Kriterium 7, CLAUDE-security.md).
 *
 * <p>Ein Log-Archiv ueberlebt die zehn Minuten, die ein Code gilt, bei weitem — und es wird von
 * mehr Menschen gelesen als die Datenbank. Mit eingeschaltetem Versand ist das Postfach der einzige
 * Weg zum Code; stuende er auch im Protokoll, waere es ein zweiter. Mit ausgeschaltetem Versand ist
 * das Protokoll dagegen der einzige Weg, und dort muss er stehen.
 *
 * <p>Mitgeschrieben wird am <b>Wurzel-Logger</b>, nicht an der Klasse: So faellt auch eine Zeile
 * auf, die ein anderer Baustein unterwegs schreibt. Den eingeschalteten Versand prueft der Test an
 * seiner gefaehrlichsten Stelle, dem <b>gescheiterten</b> Zustellversuch: Nur dort schreibt die
 * Anwendung ueberhaupt etwas ueber eine einzelne Nachricht, und genau dort waere die Versuchung am
 * groessten, „zur Fehlersuche" den Rumpf mitzugeben.
 */
@ExtendWith(MockitoExtension.class)
class NoTokenInLogTest {

  private static final String MAIL = "manne@example.org";
  private static final String NAME = "Manne";
  private static final String CODE = "042424";
  private static final String HASH = "$argon2id$v=19$m=19456,t=2,p=1$salz$hash-des-codes";
  private static final Instant JETZT = Instant.parse("2026-10-20T10:00:00Z");

  @Mock private LoginCodeRepository loginCodes;
  @Mock private SecretHasher hasher;
  @Mock private OutboxRepository outbox;
  @Mock private MailGateway gateway;

  @Captor private ArgumentCaptor<OutboxMessage> eingestellterAuftrag;

  private final ListAppender<ILoggingEvent> mitgeschrieben = new ListAppender<>();
  private final Clock uhr = Clock.fixed(JETZT, ZoneOffset.UTC);

  private ch.qos.logback.classic.Logger wurzel;
  private @org.jspecify.annotations.Nullable Level vorherigeStufe;

  private static AuthProperties schalter() {
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
        Duration.ofMinutes(10),
        5,
        List.of());
  }

  private static MailProperties mailSchalter(final boolean versand) {
    return new MailProperties(versand, "no-reply@fbcrm.local", "https://crm.example.org");
  }

  /** Eine Zufallsquelle, die immer {@link #CODE} zieht. */
  private static NumericCodes festerCode() {
    return new NumericCodes(
        new SecureRandom() {
          @Serial private static final long serialVersionUID = 1L;

          @Override
          public int nextInt(final int schranke) {
            return Integer.parseInt(CODE);
          }
        });
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

  /** Fordert einen Code an — ohne Transaktion, die Protokollzeile entsteht darum sofort. */
  private void fordereAn(final boolean versand) {
    when(hasher.hash(CODE)).thenReturn(HASH);
    when(loginCodes.save(any(LoginCode.class))).then(returnsFirstArg());
    new RequestLoginCodeUseCase(
            loginCodes,
            hasher,
            festerCode(),
            new EnqueueMailUseCase(outbox, uhr),
            schalter(),
            mailSchalter(versand),
            uhr)
        .ausstellen(MAIL, NAME);
  }

  /** Der Zustellversuch scheitert — nur dann protokolliert die Anwendung etwas ueber die Mail. */
  private void stelleErfolglosZu() {
    verify(outbox).save(eingestellterAuftrag.capture());
    final OutboxMessage auftrag = eingestellterAuftrag.getValue();
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
            outbox, gateway, new OutboxProperties(true, 5000L, 8, 7), mailSchalter(true), uhr)
        .dispatch();
  }

  private void fordereAnMitVersand() {
    fordereAn(true);
    stelleErfolglosZu();
  }

  private String protokoll() {
    return mitgeschrieben.list.stream()
        .map(ILoggingEvent::getFormattedMessage)
        .reduce("", (links, rechts) -> links + '\n' + rechts);
  }

  @Test
  void anforderung_withMailEnabled_thenNeverWritesTheCodeIntoTheLog() {
    // When
    fordereAnMitVersand();

    // Then — der Code steckte im Rumpf des Auftrags, den der Zustellversuch in der Hand hatte.
    assertThat(eingestellterAuftrag.getValue().body()).contains(CODE);
    assertThat(protokoll()).doesNotContain(CODE);
  }

  @Test
  void anforderung_withMailEnabled_thenNeverWritesTheCodeHashIntoTheLog() {
    // When — auch der Hash gehoert nicht ins Log: Sechs Ziffern sind aus ihm schnell erraten.
    fordereAnMitVersand();

    // Then
    assertThat(protokoll()).doesNotContain(HASH);
  }

  @Test
  void anforderung_withMailEnabled_givenAFailedDelivery_thenStillReportsThatSomethingWentWrong() {
    // When — Schweigen waere die falsche Loesung: Der Betreiber muss den Fehlschlag sehen.
    fordereAnMitVersand();

    // Then
    assertThat(protokoll()).contains("Zustellauftrag 7");
  }

  @Test
  void anforderung_withMailDisabled_thenWritesTheCodeIntoTheLog() {
    // When — ohne Versand ist das Protokoll der einzige Weg zum Code.
    fordereAn(false);

    // Then
    assertThat(protokoll()).contains(CODE);
  }

  @Test
  void anforderung_withMailDisabled_thenNeverWritesTheCodeHashIntoTheLog() {
    // When
    fordereAn(false);

    // Then
    assertThat(protokoll()).doesNotContain(HASH);
  }
}
