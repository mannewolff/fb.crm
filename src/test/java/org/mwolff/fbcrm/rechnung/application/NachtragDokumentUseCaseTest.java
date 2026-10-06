package org.mwolff.fbcrm.rechnung.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.common.Uploadgrenze;
import org.mwolff.fbcrm.rechnung.domain.DokumentSpeicher;
import org.mwolff.fbcrm.rechnung.domain.DokumentSpeicherAusfall;
import org.mwolff.fbcrm.rechnung.domain.NachgetrageneRechnung;
import org.mwolff.fbcrm.rechnung.domain.NachgetrageneRechnungRepository;
import org.mwolff.fbcrm.rechnung.domain.Rechnungszustand;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Das Original einer nachgetragenen Rechnung: ablegen, ersetzen, lesen, entfernen (#254, Kriterien
 * 2 und 10; Plan #259, E12–E15, E17).
 *
 * <p>Gegenstand sind die drei Riegel vor dem Speicher — leer, zu gross, kein PDF, erkannt allein am
 * Inhalt —, die Reihenfolge von Zeile und altem Objekt beim Ersetzen und Entfernen und der Umgang
 * mit einem ausgefallenen Speicher.
 */
@ExtendWith(MockitoExtension.class)
class NachtragDokumentUseCaseTest {

  private static final long ID = 21L;
  private static final String ALT = "rechnung-nachtrag/21/alt.pdf";
  private static final String NEU = "rechnung-nachtrag/21/neu.pdf";
  private static final Instant ANGELEGT = Instant.parse("2026-03-02T08:00:00Z");
  private static final Instant JETZT = Instant.parse("2026-10-06T12:00:00Z");
  private static final byte[] PDF =
      "%PDF-1.7\n1 0 obj\n<<>>\nendobj\n".getBytes(StandardCharsets.US_ASCII);

  /*
   * Eine HTML-Seite, wie sie als „rechnung.pdf" hochgeladen werden koennte. Der Teilname erreicht
   * den Anwendungsfall gar nicht: Er bekommt allein die Bytes, und an denen ist sie kein PDF.
   */
  private static final byte[] HTML_ALS_PDF =
      "<!DOCTYPE html><html><body><script>alert(1)</script></body></html>"
          .getBytes(StandardCharsets.UTF_8);

  @Mock private NachgetrageneRechnungRepository nachgetragene;
  @Mock private DokumentSpeicher speicher;

  private final ListAppender<ILoggingEvent> mitgeschrieben = new ListAppender<>();

  private NachtragDokumentUseCase useCase;

  private ch.qos.logback.classic.Logger protokoll;

  private @Nullable Level vorherigeStufe;

  @BeforeEach
  void richteDenTestEin() {
    useCase =
        new NachtragDokumentUseCase(nachgetragene, speicher, Clock.fixed(JETZT, ZoneOffset.UTC));
    protokoll =
        ((LoggerContext) LoggerFactory.getILoggerFactory())
            .getLogger(NachtragDokumentUseCase.class.getName());
    vorherigeStufe = protokoll.getLevel();
    mitgeschrieben.setContext(protokoll.getLoggerContext());
    mitgeschrieben.start();
    protokoll.addAppender(mitgeschrieben);
    protokoll.setLevel(Level.TRACE);
  }

  /*
   * Stufe zuruecksetzen, Appender abhaengen und eine eroeffnete Synchronisation raeumen: Logger und
   * ThreadLocal gehoeren der ganzen JVM (Muster NachtragLoeschenUseCaseTest).
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

  private static NachgetrageneRechnung gespeichert(final @Nullable String pdfSchluessel) {
    return new NachgetrageneRechnung(
        ID,
        4L,
        "RE-1",
        LocalDate.of(2026, 3, 1),
        new BigDecimal("1000.00"),
        new BigDecimal("1190.00"),
        Rechnungszustand.GESTELLT,
        pdfSchluessel,
        ANGELEGT,
        ANGELEGT);
  }

  private void rechnungGibtEs(final @Nullable String pdfSchluessel) {
    when(nachgetragene.findById(ID)).thenReturn(Optional.of(gespeichert(pdfSchluessel)));
  }

  private void speichernGibtZurueck() {
    when(nachgetragene.save(any())).thenAnswer(aufruf -> aufruf.getArgument(0));
  }

  private void assertAbgewiesenMit(final byte[] inhalt, final String meldung) {
    assertThatThrownBy(() -> useCase.lege(ID, inhalt))
        .isInstanceOfSatisfying(
            DokumentNichtAnnehmbar.class,
            fehler -> {
              assertThat(fehler.getMessage()).isEqualTo(meldung);
              assertThat(fehler.felder()).containsOnlyKeys(DokumentNichtAnnehmbar.FELD);
              assertThat(fehler.felder().get(DokumentNichtAnnehmbar.FELD))
                  .isEqualTo(List.of(meldung));
            });
    assertThat(DokumentNichtAnnehmbar.FELD).isEqualTo("datei");
    verifyNoInteractions(speicher);
    verify(nachgetragene, never()).save(any());
  }

  // --- lege ---------------------------------------------------------------------------------

  @Test
  void lege_withAPdf_thenStoresTheObjectAndTheKey() {
    // Given
    rechnungGibtEs(null);
    when(speicher.legeHochgeladenes(ID, PDF)).thenReturn(NEU);
    speichernGibtZurueck();

    // When
    final NachgetrageneRechnung ergebnis = useCase.lege(ID, PDF);

    // Then
    assertThat(ergebnis.pdfSchluessel()).isEqualTo(NEU);
    assertThat(ergebnis.updatedAt()).isEqualTo(JETZT);
    verify(speicher, never()).loesche(anyString());
  }

  @Test
  void lege_withHtmlNamedPdf_thenDokumentNichtAnnehmbarAtDatei() {
    // Given — E12: erkannt wird am Inhalt, nicht am Namen und nicht an der gemeldeten Art.
    rechnungGibtEs(null);

    // When / Then
    assertAbgewiesenMit(HTML_ALS_PDF, DokumentNichtAnnehmbar.MELDUNG_KEIN_PDF);
  }

  @Test
  void lege_withAnotherRecognisedType_thenDokumentNichtAnnehmbarAtDatei() {
    // Given — ein Bild hat eine Vorschauart, ist aber kein PDF.
    rechnungGibtEs(null);
    final byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

    // When / Then
    assertAbgewiesenMit(png, DokumentNichtAnnehmbar.MELDUNG_KEIN_PDF);
  }

  @Test
  void lege_withEmptyBytes_thenDokumentNichtAnnehmbarAtDatei() {
    // Given
    rechnungGibtEs(null);

    // When / Then
    assertAbgewiesenMit(new byte[0], DokumentNichtAnnehmbar.MELDUNG_LEER);
  }

  @Test
  void lege_aboveTheUploadgrenze_thenDokumentNichtAnnehmbarAtDatei() {
    // Given — ein echtes PDF, aber ein Byte zu gross: die Groesse sticht den Inhalt.
    rechnungGibtEs(null);
    final byte[] zuGross = Arrays.copyOf(PDF, Math.toIntExact(Uploadgrenze.MAX_BYTE + 1));

    // When / Then
    assertAbgewiesenMit(zuGross, Uploadgrenze.MELDUNG);
    assertThat(DokumentNichtAnnehmbar.MELDUNG_ZU_GROSS).isEqualTo(Uploadgrenze.MELDUNG);
  }

  @Test
  void lege_atTheUploadgrenze_thenAccepted() {
    // Given — die Grenze selbst ist erlaubt.
    rechnungGibtEs(null);
    final byte[] genau = Arrays.copyOf(PDF, Math.toIntExact(Uploadgrenze.MAX_BYTE));
    when(speicher.legeHochgeladenes(ID, genau)).thenReturn(NEU);
    speichernGibtZurueck();

    // When
    final NachgetrageneRechnung ergebnis = useCase.lege(ID, genau);

    // Then
    assertThat(ergebnis.pdfSchluessel()).isEqualTo(NEU);
  }

  @Test
  void lege_replacingAnOriginal_thenSavesTheNewKeyBeforeRemovingExactlyTheOldOne() {
    // Given — ohne laufende Transaktion geschieht das Entfernen sofort, aber nach dem Speichern.
    rechnungGibtEs(ALT);
    when(speicher.legeHochgeladenes(ID, PDF)).thenReturn(NEU);
    speichernGibtZurueck();

    // When
    useCase.lege(ID, PDF);

    // Then
    final ArgumentCaptor<NachgetrageneRechnung> gesichert =
        ArgumentCaptor.forClass(NachgetrageneRechnung.class);
    final InOrder reihenfolge = inOrder(speicher, nachgetragene);
    reihenfolge.verify(speicher).legeHochgeladenes(ID, PDF);
    reihenfolge.verify(nachgetragene).save(gesichert.capture());
    reihenfolge.verify(speicher).loesche(ALT);
    assertThat(gesichert.getValue().pdfSchluessel()).isEqualTo(NEU);
    verify(speicher, never()).loesche(NEU);
  }

  @Test
  void lege_replacingWithinATransaction_thenRemovesTheOldObjectOnlyAfterTheCommit() {
    // Given — E14: vor dem Commit geloescht, zeigte eine zurueckgerollte Zeile ins Leere.
    TransactionSynchronizationManager.initSynchronization();
    rechnungGibtEs(ALT);
    when(speicher.legeHochgeladenes(ID, PDF)).thenReturn(NEU);
    speichernGibtZurueck();

    // When
    useCase.lege(ID, PDF);

    // Then
    verify(speicher, never()).loesche(anyString());

    // And — nach dem Commit ist genau das alte Objekt fort.
    TransactionSynchronizationManager.getSynchronizations()
        .forEach(TransactionSynchronization::afterCommit);
    verify(speicher).loesche(ALT);
    verify(speicher, never()).loesche(NEU);
  }

  @Test
  void lege_whenRemovingTheOldObjectFails_thenCompletesAndLogsOnlyTheKey() {
    // Given — E14: die Zeile zeigt schon auf das neue Original, das alte ist unerreichbar.
    rechnungGibtEs(ALT);
    when(speicher.legeHochgeladenes(ID, PDF)).thenReturn(NEU);
    speichernGibtZurueck();
    doThrow(new DokumentSpeicherAusfall("Der Speicher antwortet nicht.", new IOException()))
        .when(speicher)
        .loesche(ALT);

    // When
    final NachgetrageneRechnung ergebnis = useCase.lege(ID, PDF);

    // Then
    assertThat(ergebnis.pdfSchluessel()).isEqualTo(NEU);
    assertThat(mitgeschrieben.list).hasSize(1);
    assertThat(mitgeschrieben.list.getFirst().getLevel()).isEqualTo(Level.WARN);
    assertThat(mitgeschrieben.list.getFirst().getFormattedMessage())
        .contains(ALT)
        .doesNotContain("RE-1");
  }

  @Test
  void lege_whenStoringFails_thenThrowsAndSavesNothing() {
    // Given — beim Ablegen ist noch nichts gespeichert; der Ausfall gehoert in die Antwort.
    rechnungGibtEs(ALT);
    final DokumentSpeicherAusfall ausfall =
        new DokumentSpeicherAusfall("Der Speicher antwortet nicht.", new IOException());
    when(speicher.legeHochgeladenes(ID, PDF)).thenThrow(ausfall);

    // When / Then
    assertThatThrownBy(() -> useCase.lege(ID, PDF)).isSameAs(ausfall);
    verify(nachgetragene, never()).save(any());
    verify(speicher, never()).loesche(anyString());
  }

  @Test
  void lege_withAnUnknownRechnung_thenNachtragNichtGefunden() {
    // Given
    when(nachgetragene.findById(ID)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> useCase.lege(ID, PDF)).isInstanceOf(NachtragNichtGefunden.class);
    verifyNoInteractions(speicher);
  }

  // --- lies ---------------------------------------------------------------------------------

  @Test
  void lies_withAnOriginal_thenReturnsItsBytes() {
    // Given
    rechnungGibtEs(ALT);
    when(speicher.lies(ALT)).thenReturn(PDF);

    // When / Then
    assertThat(useCase.lies(ID)).isEqualTo(PDF);
  }

  @Test
  void lies_withoutAnOriginal_thenNachtragNichtGefunden() {
    // Given — E17: hier fehlt die Sache selbst, also 404.
    rechnungGibtEs(null);

    // When / Then
    assertThatThrownBy(() -> useCase.lies(ID)).isInstanceOf(NachtragNichtGefunden.class);
    verifyNoInteractions(speicher);
  }

  @Test
  void lies_withAnUnknownRechnung_thenNachtragNichtGefunden() {
    // Given
    when(nachgetragene.findById(ID)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> useCase.lies(ID)).isInstanceOf(NachtragNichtGefunden.class);
    verifyNoInteractions(speicher);
  }

  // --- entferne -----------------------------------------------------------------------------

  @Test
  void entferne_withAnOriginal_thenClearsTheKeyBeforeRemovingTheObject() {
    // Given
    rechnungGibtEs(ALT);
    speichernGibtZurueck();

    // When
    final NachgetrageneRechnung ergebnis = useCase.entferne(ID);

    // Then
    final ArgumentCaptor<NachgetrageneRechnung> gesichert =
        ArgumentCaptor.forClass(NachgetrageneRechnung.class);
    final InOrder reihenfolge = inOrder(nachgetragene, speicher);
    reihenfolge.verify(nachgetragene).save(gesichert.capture());
    reihenfolge.verify(speicher).loesche(ALT);
    assertThat(gesichert.getValue().pdfSchluessel()).isNull();
    assertThat(ergebnis.pdfSchluessel()).isNull();
    assertThat(ergebnis.updatedAt()).isEqualTo(JETZT);
  }

  @Test
  void entferne_withinATransaction_thenRemovesTheObjectOnlyAfterTheCommit() {
    // Given
    TransactionSynchronizationManager.initSynchronization();
    rechnungGibtEs(ALT);
    speichernGibtZurueck();

    // When
    useCase.entferne(ID);

    // Then
    verify(speicher, never()).loesche(anyString());

    // And
    TransactionSynchronizationManager.getSynchronizations()
        .forEach(TransactionSynchronization::afterCommit);
    verify(speicher).loesche(ALT);
  }

  @Test
  void entferne_whenRemovingTheObjectFails_thenCompletesAndLogsTheKey() {
    // Given
    rechnungGibtEs(ALT);
    speichernGibtZurueck();
    doThrow(new DokumentSpeicherAusfall("Der Speicher antwortet nicht.", new IOException()))
        .when(speicher)
        .loesche(ALT);

    // When
    final NachgetrageneRechnung ergebnis = useCase.entferne(ID);

    // Then
    assertThat(ergebnis.pdfSchluessel()).isNull();
    assertThat(mitgeschrieben.list).hasSize(1);
    assertThat(mitgeschrieben.list.getFirst().getLevel()).isEqualTo(Level.WARN);
    assertThat(mitgeschrieben.list.getFirst().getFormattedMessage()).contains(ALT);
  }

  @Test
  void entferne_withoutAnOriginal_thenNachtragNichtGefunden() {
    // Given — E17
    rechnungGibtEs(null);

    // When / Then
    assertThatThrownBy(() -> useCase.entferne(ID)).isInstanceOf(NachtragNichtGefunden.class);
    verify(nachgetragene, never()).save(any());
    verifyNoInteractions(speicher);
  }

  @Test
  void entferne_withAnUnknownRechnung_thenNachtragNichtGefunden() {
    // Given
    when(nachgetragene.findById(ID)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> useCase.entferne(ID)).isInstanceOf(NachtragNichtGefunden.class);
    verifyNoInteractions(speicher);
  }
}
