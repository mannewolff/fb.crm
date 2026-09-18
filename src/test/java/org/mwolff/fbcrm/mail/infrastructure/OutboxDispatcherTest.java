package org.mwolff.fbcrm.mail.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Clock;
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
import org.mwolff.fbcrm.mail.MailProperties;
import org.mwolff.fbcrm.mail.OutboxProperties;
import org.mwolff.fbcrm.mail.domain.MailGateway;
import org.mwolff.fbcrm.mail.domain.OutboxMessage;
import org.mwolff.fbcrm.mail.domain.OutboxRepository;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailSendException;

/**
 * Der Hintergrundjob, der das Postausgangsfach leert (E7).
 *
 * <p>Der Fall, um dessentwillen dieser Job ueberhaupt einen Schalter hat, steht in {@code
 * dispatch_givenSendingIsOff_…}: {@code docker-compose.yml} liefert {@code
 * FBCRM_OUTBOX_ENABLED=true} zusammen mit {@code FBCRM_MAIL_ENABLED=false} und einem leeren
 * SMTP-Host. Ohne den Riegel liefe jeder Auftrag gegen diesen leeren Host und haette seine acht
 * Versuche verbrannt, bevor der Betreiber den Mailversand ueberhaupt eingerichtet hat.
 */
@ExtendWith(MockitoExtension.class)
class OutboxDispatcherTest {

  private static final Instant JETZT = Instant.parse("2026-09-18T10:00:00Z");
  private static final int MAX_VERSUCHE = 8;

  @Mock private OutboxRepository outbox;
  @Mock private MailGateway gateway;

  @Captor private ArgumentCaptor<OutboxMessage> gespeicherte;

  private final ListAppender<ILoggingEvent> mitgeschrieben = new ListAppender<>();

  private ch.qos.logback.classic.Logger protokoll;
  private @org.jspecify.annotations.Nullable Level vorherigeStufe;

  @BeforeEach
  void haengeDichAnDasProtokollDesJobs() {
    protokoll =
        ((LoggerContext) LoggerFactory.getILoggerFactory())
            .getLogger(OutboxDispatcher.class.getName());
    vorherigeStufe = protokoll.getLevel();
    mitgeschrieben.setContext(protokoll.getLoggerContext());
    mitgeschrieben.start();
    protokoll.addAppender(mitgeschrieben);
    protokoll.setLevel(Level.TRACE);
  }

  /*
   * Die Stufe wird zurueckgesetzt, nicht nur der Appender abgehaengt: Ein Logger gehoert der
   * ganzen JVM, und ein zurueckgelassenes TRACE flutete jeden folgenden Test der Suite.
   */
  @AfterEach
  void loeseDichWiederAb() {
    protokoll.setLevel(vorherigeStufe);
    protokoll.detachAppender(mitgeschrieben);
    mitgeschrieben.stop();
  }

  private List<Level> protokollierteStufen() {
    return mitgeschrieben.list.stream()
        .filter(eintrag -> eintrag.getLevel().isGreaterOrEqual(Level.WARN))
        .map(ILoggingEvent::getLevel)
        .toList();
  }

  private static OutboxMessage auftrag(final int versuche) {
    return new OutboxMessage(
        7L, "manne@example.org", "Betreff", "Rumpf", versuche, JETZT, null, JETZT);
  }

  private OutboxDispatcher dispatcher(final boolean mailAn) {
    return new OutboxDispatcher(
        outbox,
        gateway,
        new OutboxProperties(true, 5000L, MAX_VERSUCHE, 7),
        new MailProperties(mailAn, "no-reply@fbcrm.local", "https://crm.example.org"),
        Clock.fixed(JETZT, ZoneOffset.UTC));
  }

  private void faellig(final OutboxMessage... auftraege) {
    when(outbox.findDue(eq(JETZT), eq(MAX_VERSUCHE), anyInt())).thenReturn(List.of(auftraege));
  }

  private OutboxMessage fortgeschriebenerAuftrag() {
    verify(outbox).save(gespeicherte.capture());
    return gespeicherte.getValue();
  }

  @Test
  void dispatch_givenADueMessage_thenHandsItToTheGateway() {
    // Given
    faellig(auftrag(0));

    // When
    dispatcher(true).dispatch();

    // Then
    verify(gateway).send("manne@example.org", "Betreff", "Rumpf");
  }

  @Test
  void dispatch_givenADueMessage_thenMarksItAsDelivered() {
    // Given
    faellig(auftrag(0));

    // When
    dispatcher(true).dispatch();

    // Then
    assertThat(fortgeschriebenerAuftrag().sentAt()).isEqualTo(JETZT);
  }

  @Test
  void dispatch_givenADueMessage_thenNeverCountsAnAttemptAgainstIt() {
    // Given — eine gelungene Zustellung ist kein Fehlversuch.
    faellig(auftrag(0));

    // When
    dispatcher(true).dispatch();

    // Then
    assertThat(fortgeschriebenerAuftrag().attempts()).isZero();
  }

  @Test
  void dispatch_givenNothingDue_thenTouchesNeitherGatewayNorRepository() {
    // Given
    faellig();

    // When
    dispatcher(true).dispatch();

    // Then
    verifyNoInteractions(gateway);
    verify(outbox, never()).save(any(OutboxMessage.class));
  }

  @Test
  void dispatch_givenAFailingDelivery_thenCountsTheAttempt() {
    // Given
    faellig(auftrag(2));
    doThrow(new MailSendException("SMTP nicht erreichbar"))
        .when(gateway)
        .send(anyString(), anyString(), anyString());

    // When
    dispatcher(true).dispatch();

    // Then
    assertThat(fortgeschriebenerAuftrag().attempts()).isEqualTo(3);
  }

  @Test
  void dispatch_givenAFailingDelivery_thenSchedulesAnotherAttempt() {
    // Given
    faellig(auftrag(2));
    doThrow(new MailSendException("SMTP nicht erreichbar"))
        .when(gateway)
        .send(anyString(), anyString(), anyString());

    // When
    dispatcher(true).dispatch();

    // Then — 2^2 Basisintervalle zu je 5 Sekunden.
    assertThat(fortgeschriebenerAuftrag().nextAttemptAt()).isEqualTo(JETZT.plusSeconds(20));
  }

  @Test
  void dispatch_givenAFailingDelivery_thenLeavesTheMessageUndelivered() {
    // Given
    faellig(auftrag(2));
    doThrow(new MailSendException("SMTP nicht erreichbar"))
        .when(gateway)
        .send(anyString(), anyString(), anyString());

    // When
    dispatcher(true).dispatch();

    // Then
    assertThat(fortgeschriebenerAuftrag().sentAt()).isNull();
  }

  @Test
  void dispatch_givenAMessageThatHasUsedUpItsAttempts_thenNeverTriesAgain() {
    // Given — dieselbe Grenze wie in der Abfrage, hier als Riegel im Ablauf.
    faellig(auftrag(MAX_VERSUCHE));

    // When
    dispatcher(true).dispatch();

    // Then
    verifyNoInteractions(gateway);
  }

  @Test
  void dispatch_givenAMessageThatHasUsedUpItsAttempts_thenWritesNothing() {
    // Given
    faellig(auftrag(MAX_VERSUCHE));

    // When
    dispatcher(true).dispatch();

    // Then
    verify(outbox, never()).save(any(OutboxMessage.class));
  }

  @Test
  void dispatch_givenAFailureOnTheLastAllowedAttempt_thenStoresTheExhaustedMessage() {
    // Given — der achte Versuch scheitert; danach wird der Auftrag nicht mehr aufgegriffen.
    faellig(auftrag(MAX_VERSUCHE - 1));
    doThrow(new MailSendException("SMTP nicht erreichbar"))
        .when(gateway)
        .send(anyString(), anyString(), anyString());

    // When
    dispatcher(true).dispatch();

    // Then
    assertThat(fortgeschriebenerAuftrag().isExhausted(MAX_VERSUCHE)).isTrue();
  }

  @Test
  void dispatch_givenAFailingDelivery_thenWarnsWithoutRaisingAlarm() {
    // Given — ein einzelner Fehlversuch ist normaler Betrieb: Der naechste Versuch kommt.
    faellig(auftrag(2));
    doThrow(new MailSendException("SMTP nicht erreichbar"))
        .when(gateway)
        .send(anyString(), anyString(), anyString());

    // When
    dispatcher(true).dispatch();

    // Then
    assertThat(protokollierteStufen()).containsExactly(Level.WARN);
  }

  @Test
  void dispatch_givenAFailureOnTheLastAllowedAttempt_thenReportsAnError() {
    // Given — ein aufgegebener Auftrag ist kein normaler Betrieb mehr: Niemand versucht es
    // wieder, und der Betreiber muss das in seinem Protokoll finden.
    faellig(auftrag(MAX_VERSUCHE - 1));
    doThrow(new MailSendException("SMTP nicht erreichbar"))
        .when(gateway)
        .send(anyString(), anyString(), anyString());

    // When
    dispatcher(true).dispatch();

    // Then
    assertThat(protokollierteStufen()).containsExactly(Level.ERROR);
  }

  @Test
  void dispatch_givenSendingIsOff_thenLeavesTheOutboxCompletelyAlone() {
    // Given — FBCRM_MAIL_ENABLED=false bei FBCRM_OUTBOX_ENABLED=true.
    // When
    dispatcher(false).dispatch();

    // Then
    verifyNoInteractions(outbox, gateway);
  }

  @Test
  void dispatch_givenSeveralDueMessages_thenDeliversEveryOneOfThem() {
    // Given
    final OutboxMessage zweiter =
        new OutboxMessage(8L, "zweite@example.org", "B", "R", 0, JETZT, null, JETZT);
    faellig(auftrag(0), zweiter);

    // When
    dispatcher(true).dispatch();

    // Then
    verify(gateway).send("manne@example.org", "Betreff", "Rumpf");
    verify(gateway).send("zweite@example.org", "B", "R");
  }
}
