package org.mwolff.fbcrm.rechnung.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
 */
@ExtendWith(MockitoExtension.class)
class NachtragLoeschenUseCaseTest {

  private static final long ID = 21L;
  private static final String SCHLUESSEL = "rechnung-nachtrag/21/8f3c.pdf";
  private static final Instant ANGELEGT = Instant.parse("2026-03-02T08:00:00Z");

  @Mock private NachgetrageneRechnungRepository nachgetragene;
  @Mock private DokumentSpeicher speicher;

  private final ListAppender<ILoggingEvent> mitgeschrieben = new ListAppender<>();

  private NachtragLoeschenUseCase useCase;

  private ch.qos.logback.classic.Logger protokoll;

  private @Nullable Level vorherigeStufe;

  @BeforeEach
  void richteDenTestEin() {
    useCase = new NachtragLoeschenUseCase(nachgetragene, speicher);
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
        ANGELEGT);
  }

  private void rechnungGibtEs(final @Nullable String pdfSchluessel) {
    when(nachgetragene.findById(ID))
        .thenReturn(Optional.of(gespeichert(Rechnungszustand.GESTELLT, pdfSchluessel)));
  }

  @Test
  void loesche_withAPdf_thenDeletesTheRowAndThenTheObject() {
    // Given
    rechnungGibtEs(SCHLUESSEL);

    // When
    useCase.loesche(ID);

    // Then — ohne laufende Transaktion sofort, aber erst nach der Zeile.
    final InOrder reihenfolge = inOrder(nachgetragene, speicher);
    reihenfolge.verify(nachgetragene).delete(ID);
    reihenfolge.verify(speicher).loesche(SCHLUESSEL);
    assertThat(mitgeschrieben.list).isEmpty();
  }

  @Test
  void loesche_withoutAPdf_thenNeverTouchesTheStorage() {
    // Given
    rechnungGibtEs(null);

    // When
    useCase.loesche(ID);

    // Then
    verify(nachgetragene).delete(ID);
    verifyNoInteractions(speicher);
  }

  @Test
  void loesche_withinATransaction_thenRemovesTheObjectOnlyAfterTheCommit() {
    // Given — E14: vor dem Commit geloescht, liesse ein gescheiterter Commit eine Zeile ohne
    // Objekt.
    TransactionSynchronizationManager.initSynchronization();
    rechnungGibtEs(SCHLUESSEL);

    // When
    useCase.loesche(ID);

    // Then
    verify(nachgetragene).delete(ID);
    verifyNoInteractions(speicher);

    // And — nach dem Commit ist das Objekt fort.
    TransactionSynchronizationManager.getSynchronizations()
        .forEach(TransactionSynchronization::afterCommit);
    verify(speicher).loesche(SCHLUESSEL);
  }

  @Test
  void loesche_whenRemovingTheObjectFails_thenCompletesAndLogsOnlyTheKey() {
    // Given — E14: die Zeile ist fort, ein Objekt ohne Zeile ist unerreichbar und schadet nicht.
    rechnungGibtEs(SCHLUESSEL);
    doThrow(new DokumentSpeicherAusfall("Der Speicher antwortet nicht.", new IOException()))
        .when(speicher)
        .loesche(anyString());

    // When
    useCase.loesche(ID);

    // Then — der Fehlschlag steht im Protokoll statt in der Antwort, ohne die Rechnungsnummer.
    verify(nachgetragene).delete(ID);
    assertThat(mitgeschrieben.list).hasSize(1);
    assertThat(mitgeschrieben.list.getFirst().getLevel()).isEqualTo(Level.WARN);
    assertThat(mitgeschrieben.list.getFirst().getFormattedMessage())
        .contains(SCHLUESSEL)
        .doesNotContain("RE-1");
  }

  @Test
  void loesche_whenDeletingTheRowFails_thenNeverTouchesTheStorage() {
    // Given — das Objekt haengt am Erfolg der Zeile, nie umgekehrt.
    rechnungGibtEs(SCHLUESSEL);
    doThrow(new IllegalStateException("Die Zeile ist gesperrt.")).when(nachgetragene).delete(ID);

    // When / Then
    assertThatThrownBy(() -> useCase.loesche(ID)).isInstanceOf(IllegalStateException.class);
    verifyNoInteractions(speicher);
  }

  @Test
  void loesche_withAnUnknownRechnung_thenNachtragNichtGefunden() {
    // Given
    when(nachgetragene.findById(ID)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> useCase.loesche(ID)).isInstanceOf(NachtragNichtGefunden.class);
    verify(nachgetragene, never()).delete(anyLong());
    verifyNoInteractions(speicher);
  }

  @ParameterizedTest
  @EnumSource(
      value = Rechnungszustand.class,
      names = {"GESTELLT", "BEZAHLT", "ABGESCHRIEBEN"})
  void loesche_inEveryZustand_thenDeleted(final Rechnungszustand zustand) {
    // Given — fb.crm hat sie nicht erzeugt, also gibt es nichts festzuschreiben (Kriterium 10).
    when(nachgetragene.findById(ID)).thenReturn(Optional.of(gespeichert(zustand, SCHLUESSEL)));

    // When
    useCase.loesche(ID);

    // Then
    verify(nachgetragene).delete(ID);
    verify(speicher).loesche(SCHLUESSEL);
  }
}
