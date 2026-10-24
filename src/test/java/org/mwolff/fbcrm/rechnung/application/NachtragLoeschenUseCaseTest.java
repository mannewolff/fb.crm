package org.mwolff.fbcrm.rechnung.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.common.Bearbeitungskonflikt;
import org.mwolff.fbcrm.rechnung.domain.DokumentSpeicher;
import org.mwolff.fbcrm.rechnung.domain.DokumentSpeicherAusfall;
import org.mwolff.fbcrm.rechnung.domain.NachgetrageneRechnung;
import org.mwolff.fbcrm.rechnung.domain.NachgetrageneRechnungRepository;
import org.mwolff.fbcrm.rechnung.domain.Rechnungszustand;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Das Loeschen einer nachgetragenen Rechnung (#254, Kriterium 10; Plan #259, E14).
 *
 * <p>Gegenstand ist die Reihenfolge von Zeile und Objekt — das Objekt erst nach dem Commit, und
 * nur, wenn es eines gibt —, das Schlucken eines Speicherausfalls und dass kein Zustand das
 * Loeschen verhindert.
 *
 * <p>Loeschen heisst kennzeichnen (#411, Kriterium 6; Plan #415, E16, E17): Die Zeile bleibt mit
 * Zeitpunkt und Konto. Eine inzwischen geaenderte Rechnung wird nicht geloescht, eine schon
 * geloeschte nicht ein zweites Mal — und in beiden Faellen wird nichts geschrieben und kein Objekt
 * angeruehrt.
 */
@ExtendWith(MockitoExtension.class)
class NachtragLoeschenUseCaseTest {

  private static final long ID = 21L;
  private static final String SCHLUESSEL = "rechnung-nachtrag/21/8f3c.pdf";
  private static final Instant ANGELEGT = Instant.parse("2026-03-02T08:00:00Z");
  private static final Instant JETZT = Instant.parse("2026-10-05T09:00:00Z");

  /** Der Stand, auf dem Manfred die Rechnung geoeffnet hat. */
  private static final long GELESEN = 0L;

  /** Das handelnde Konto. */
  private static final long MANFRED = 31L;

  /** Das Konto, das inzwischen geaendert oder geloescht hat. */
  private static final long ANNA = 30L;

  private static final Instant ANNAS_SCHRITT = Instant.parse("2026-10-05T08:42:00Z");

  @Mock private NachgetrageneRechnungRepository nachgetragene;
  @Mock private DokumentSpeicher speicher;

  @Captor private ArgumentCaptor<NachgetrageneRechnung> geschrieben;

  private final ListAppender<ILoggingEvent> mitgeschrieben = new ListAppender<>();

  private NachtragLoeschenUseCase useCase;

  private ch.qos.logback.classic.Logger protokoll;

  private @Nullable Level vorherigeStufe;

  @BeforeEach
  void richteDenTestEin() {
    useCase =
        new NachtragLoeschenUseCase(nachgetragene, speicher, Clock.fixed(JETZT, ZoneOffset.UTC));
    protokoll =
        ((LoggerContext) LoggerFactory.getILoggerFactory())
            .getLogger(NachtragLoeschenUseCase.class.getName());
    vorherigeStufe = protokoll.getLevel();
    mitgeschrieben.setContext(protokoll.getLoggerContext());
    mitgeschrieben.start();
    protokoll.addAppender(mitgeschrieben);
    protokoll.setLevel(Level.TRACE);
  }

  /*
   * Stufe zuruecksetzen, Appender abhaengen und eine eroeffnete Synchronisation raeumen: Logger und
   * ThreadLocal gehoeren der ganzen JVM (Muster AngebotsanlageUseCaseTest).
   */
  @AfterEach
  void raeumeDenTestAuf() {
    protokoll.setLevel(vorherigeStufe);
    protokoll.detachAppender(mitgeschrieben);
    mitgeschrieben.stop();
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  private static NachgetrageneRechnung gespeichert(
      final Rechnungszustand zustand, final @Nullable String pdfSchluessel) {
    return new NachgetrageneRechnung(
        ID,
        4L,
        "RE-1",
        LocalDate.of(2026, 3, 1),
        new BigDecimal("1000.00"),
        new BigDecimal("1190.00"),
        zustand,
        pdfSchluessel,
        ANGELEGT,
        ANGELEGT,
        GELESEN,
        null);
  }

  private void rechnungGibtEs(final @Nullable String pdfSchluessel) {
    when(nachgetragene.findById(ID))
        .thenReturn(Optional.of(gespeichert(Rechnungszustand.GESTELLT, pdfSchluessel)));
  }

  @Test
  void loesche_withAPdf_thenMarksTheRowAndThenRemovesTheObject() {
    // Given
    rechnungGibtEs(SCHLUESSEL);

    // When
    useCase.loesche(ID, GELESEN, MANFRED);

    // Then — ohne laufende Transaktion sofort, aber erst nach der Zeile.
    final InOrder reihenfolge = inOrder(nachgetragene, speicher);
    reihenfolge.verify(nachgetragene).save(geschrieben.capture());
    reihenfolge.verify(speicher).loesche(SCHLUESSEL);
    assertThat(geschrieben.getValue())
        .satisfies(
            rechnung -> assertThat(rechnung.id()).isEqualTo(ID),
            rechnung -> assertThat(rechnung.geloeschtAm()).isEqualTo(JETZT),
            rechnung -> assertThat(rechnung.geloeschtVon()).isEqualTo(MANFRED),
            rechnung -> assertThat(rechnung.version()).isEqualTo(GELESEN),
            rechnung -> assertThat(rechnung.nummer()).isEqualTo("RE-1"));
    assertThat(mitgeschrieben.list).isEmpty();
  }

  @Test
  void loesche_withoutAPdf_thenNeverTouchesTheStorage() {
    // Given
    rechnungGibtEs(null);

    // When
    useCase.loesche(ID, GELESEN, MANFRED);

    // Then
    verify(nachgetragene).save(any());
    verifyNoInteractions(speicher);
  }

  @Test
  void loesche_withinATransaction_thenRemovesTheObjectOnlyAfterTheCommit() {
    // Given — E14: vor dem Commit geloescht, liesse ein gescheiterter Commit eine Zeile ohne
    // Objekt.
    TransactionSynchronizationManager.initSynchronization();
    rechnungGibtEs(SCHLUESSEL);

    // When
    useCase.loesche(ID, GELESEN, MANFRED);

    // Then
    verify(nachgetragene).save(any());
    verifyNoInteractions(speicher);

    // And — nach dem Commit ist das Objekt fort.
    TransactionSynchronizationManager.getSynchronizations()
        .forEach(TransactionSynchronization::afterCommit);
    verify(speicher).loesche(SCHLUESSEL);
  }

  @Test
  void loesche_whenRemovingTheObjectFails_thenCompletesAndLogsOnlyTheKey() {
    // Given — E14: die Zeile ist geloescht, ihr Original erreicht kein Leseweg mehr.
    rechnungGibtEs(SCHLUESSEL);
    doThrow(new DokumentSpeicherAusfall("Der Speicher antwortet nicht.", new IOException()))
        .when(speicher)
        .loesche(anyString());

    // When
    useCase.loesche(ID, GELESEN, MANFRED);

    // Then — der Fehlschlag steht im Protokoll statt in der Antwort, ohne die Rechnungsnummer.
    verify(nachgetragene).save(any());
    assertThat(mitgeschrieben.list).hasSize(1);
    assertThat(mitgeschrieben.list.getFirst().getLevel()).isEqualTo(Level.WARN);
    assertThat(mitgeschrieben.list.getFirst().getFormattedMessage())
        .contains(SCHLUESSEL)
        .doesNotContain("RE-1");
  }

  @Test
  void loesche_whenMarkingTheRowFails_thenNeverTouchesTheStorage() {
    // Given — das Objekt haengt am Erfolg der Zeile, nie umgekehrt.
    rechnungGibtEs(SCHLUESSEL);
    doThrow(new IllegalStateException("Die Zeile ist gesperrt.")).when(nachgetragene).save(any());

    // When / Then
    assertThatThrownBy(() -> useCase.loesche(ID, GELESEN, MANFRED))
        .isInstanceOf(IllegalStateException.class);
    verifyNoInteractions(speicher);
  }

  @Test
  void loesche_withAnUnknownRechnung_thenNachtragNichtGefunden() {
    // Given
    when(nachgetragene.findById(ID)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> useCase.loesche(ID, GELESEN, MANFRED))
        .isInstanceOf(NachtragNichtGefunden.class);
    verify(nachgetragene).findById(ID);
    verifyNoMoreInteractions(nachgetragene);
    verifyNoInteractions(speicher);
  }

  @ParameterizedTest
  @EnumSource(
      value = Rechnungszustand.class,
      names = {"GESTELLT", "BEZAHLT", "ABGESCHRIEBEN"})
  void loesche_inEveryZustand_thenMarked(final Rechnungszustand zustand) {
    // Given — fb.crm hat sie nicht erzeugt, also gibt es nichts festzuschreiben (Kriterium 10).
    when(nachgetragene.findById(ID)).thenReturn(Optional.of(gespeichert(zustand, SCHLUESSEL)));

    // When
    useCase.loesche(ID, GELESEN, MANFRED);

    // Then
    verify(nachgetragene).save(geschrieben.capture());
    assertThat(geschrieben.getValue().zustand()).isEqualTo(zustand);
    assertThat(geschrieben.getValue().geloeschtAm()).isEqualTo(JETZT);
    verify(speicher).loesche(SCHLUESSEL);
  }

  @Test
  void loesche_givenAnOutdatedStand_thenRejectsAndRemovesNothing() {
    // Given — #411, Kriterium 6: Anna hat die Rechnung inzwischen geaendert.
    when(nachgetragene.findById(ID))
        .thenReturn(
            Optional.of(
                new NachgetrageneRechnung(
                    ID,
                    4L,
                    "RE-1",
                    LocalDate.of(2026, 3, 1),
                    new BigDecimal("1000.00"),
                    new BigDecimal("1190.00"),
                    Rechnungszustand.GESTELLT,
                    SCHLUESSEL,
                    ANGELEGT,
                    ANNAS_SCHRITT,
                    1L,
                    ANNA)));

    // When / Then
    assertThatThrownBy(() -> useCase.loesche(ID, GELESEN, MANFRED))
        .asInstanceOf(InstanceOfAssertFactories.type(NachtragGeaendert.class))
        .satisfies(
            konflikt -> assertThat(konflikt.art()).isEqualTo(Bearbeitungskonflikt.Art.VERALTET),
            konflikt -> assertThat(konflikt.urheberKonto()).hasValue(ANNA),
            konflikt -> assertThat(konflikt.handelndesKonto()).isEqualTo(MANFRED),
            konflikt -> assertThat(konflikt.zeitpunkt()).hasValue(ANNAS_SCHRITT));
    verify(nachgetragene).findById(ID);
    verifyNoMoreInteractions(nachgetragene);
    verifyNoInteractions(speicher);
  }

  @Test
  void loesche_givenAnAlreadyDeletedRechnung_thenRejectsAsGeloeschtAndWritesNothing() {
    // Given — Anna hat die Rechnung inzwischen geloescht; der vorgelegte Stand ist der der Zeile.
    when(nachgetragene.findById(ID))
        .thenReturn(
            Optional.of(
                gespeichert(Rechnungszustand.GESTELLT, SCHLUESSEL).geloescht(ANNA, ANNAS_SCHRITT)));

    // When / Then — das Original wurde schon bei Annas Loeschung entfernt, nicht ein zweites Mal.
    assertThatThrownBy(() -> useCase.loesche(ID, GELESEN, MANFRED))
        .asInstanceOf(InstanceOfAssertFactories.type(NachtragGeloescht.class))
        .satisfies(
            konflikt -> assertThat(konflikt.art()).isEqualTo(Bearbeitungskonflikt.Art.GELOESCHT),
            konflikt -> assertThat(konflikt.urheberKonto()).hasValue(ANNA),
            konflikt -> assertThat(konflikt.handelndesKonto()).isEqualTo(MANFRED),
            konflikt -> assertThat(konflikt.zeitpunkt()).hasValue(ANNAS_SCHRITT));
    verify(nachgetragene).findById(ID);
    verifyNoMoreInteractions(nachgetragene);
    verifyNoInteractions(speicher);
  }
}
