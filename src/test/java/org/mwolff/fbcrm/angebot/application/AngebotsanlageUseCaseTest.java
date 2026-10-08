package org.mwolff.fbcrm.angebot.application;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.angebot.domain.Angebotsanlage;
import org.mwolff.fbcrm.angebot.domain.AngebotsanlageRepository;
import org.mwolff.fbcrm.angebot.domain.AnlageSpeicher;
import org.mwolff.fbcrm.angebot.domain.AnlageSpeicherAusfall;
import org.mwolff.fbcrm.angebot.domain.Vorschauart;
import org.mwolff.fbcrm.common.Feldfehler;
import org.mwolff.fbcrm.common.Uploadgrenze;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Der Anwendungsfall der Anlagen am Angebot: auflisten, hochladen, Inhalt lesen, loeschen (Issue
 * #148, Kriterien 1 bis 14).
 *
 * <p>Gegenstand ist alles, was der Anwendungsfall entscheidet und was keine der beiden Adapter
 * entscheiden darf: die Reihenfolge von Objekt und Zeile in beide Richtungen (Plan #150, E9), die
 * Erkennung der Vorschauart <b>am Inhalt</b> und die Unversehrtheit des Inhalts trotz dieser
 * Erkennung (E4), die drei Riegel gegen eine unbrauchbare Datei (E8), die Ordnung der Liste (E10)
 * und die Abgrenzung gegen die Anlage eines anderen Angebots.
 */
@ExtendWith(MockitoExtension.class)
class AngebotsanlageUseCaseTest {

  private static final long ANGEBOT = 11L;
  private static final long FREMDES_ANGEBOT = 12L;
  private static final long ANLAGE = 4L;
  private static final String SCHLUESSEL = "angebot/11/anlage/1a2b3c";
  private static final Instant ANGELEGT = Instant.parse("2026-09-30T08:00:00Z");
  private static final Instant JETZT = Instant.parse("2026-09-30T09:30:00Z");

  /** Ein PDF — laenger als die Signatur, damit das Zuruecklegen der ersten Bytes messbar ist. */
  private static final byte[] PDF_INHALT = "%PDF-1.7\nInhalt der Datei".getBytes(UTF_8);

  /** HTML mit der Endung eines PDFs: der Fall, den allein die Inhaltspruefung erkennt. */
  private static final byte[] HTML_INHALT = "<!DOCTYPE html><h1>Bericht</h1>".getBytes(UTF_8);

  @Mock private AngebotRepository angebote;
  @Mock private AngebotsanlageRepository anlagen;
  @Mock private AnlageSpeicher speicher;

  private final ListAppender<ILoggingEvent> mitgeschrieben = new ListAppender<>();

  private AngebotsanlageUseCase useCase;

  private ch.qos.logback.classic.Logger protokoll;

  private @Nullable Level vorherigeStufe;

  @BeforeEach
  void richteDenTestEin() {
    useCase =
        new AngebotsanlageUseCase(angebote, anlagen, speicher, Clock.fixed(JETZT, ZoneOffset.UTC));
    protokoll =
        ((LoggerContext) LoggerFactory.getILoggerFactory())
            .getLogger(AngebotsanlageUseCase.class.getName());
    vorherigeStufe = protokoll.getLevel();
    mitgeschrieben.setContext(protokoll.getLoggerContext());
    mitgeschrieben.start();
    protokoll.addAppender(mitgeschrieben);
    protokoll.setLevel(Level.TRACE);
  }

  /*
   * Die Stufe wird zurueckgesetzt, nicht nur der Appender abgehaengt: Ein Logger gehoert der ganzen
   * JVM, und ein zurueckgelassenes TRACE flutete jeden folgenden Test der Suite (Muster
   * OutboxDispatcherTest).
   *
   * <p>Danach die Synchronisation: Sie liegt in einem ThreadLocal und ueberlebte den Test, den sie
   * eroeffnet hat. Die Abfrage davor ist Aufraeumen und keine Logik um eine Behauptung: Nur ein
   * Test eroeffnet sie.
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

  private void angebotGibtEs() {
    when(angebote.findById(ANGEBOT)).thenReturn(Optional.of(Angebotsdoppel.angebot(ANGEBOT)));
  }

  private void angebotGibtEsNicht() {
    when(angebote.findById(ANGEBOT)).thenReturn(Optional.empty());
  }

  private static Angebotsanlage anlage(final long angebotId) {
    return new Angebotsanlage(
        Long.valueOf(ANLAGE), angebotId, "bericht.pdf", 24L, Vorschauart.PDF, SCHLUESSEL, ANGELEGT);
  }

  private void anlageGehoertZu(final long angebotId) {
    when(anlagen.findById(ANLAGE)).thenReturn(Optional.of(anlage(angebotId)));
  }

  private void speichernGibtZurueck() {
    when(anlagen.save(any())).thenAnswer(aufruf -> aufruf.getArgument(0));
  }

  private void ablegenGibtDenSchluessel() {
    when(speicher.ablegen(eq(ANGEBOT), any(), anyLong())).thenReturn(SCHLUESSEL);
  }

  /** Legt ab wie der Adapter: liest den Strom vollstaendig und haelt fest, was ankam. */
  private AtomicReference<byte[]> ablegenLiestDenStrom() {
    final AtomicReference<byte[]> abgelegt = new AtomicReference<>();
    when(speicher.ablegen(eq(ANGEBOT), any(), anyLong()))
        .thenAnswer(
            aufruf -> {
              abgelegt.set(((InputStream) aufruf.getArgument(1)).readAllBytes());
              return SCHLUESSEL;
            });
    return abgelegt;
  }

  private static InputStream strom(final byte[] inhalt) {
    return new ByteArrayInputStream(inhalt);
  }

  @Test
  void liste_thenReturnsTheAnlagenNewestFirst() {
    // Given — Kriterium 4: der Bestand sagt keine Reihenfolge zu (E10).
    angebotGibtEs();
    when(anlagen.findByAngebot(ANGEBOT))
        .thenReturn(
            List.of(
                new Angebotsanlage(1L, ANGEBOT, "alt.pdf", 9L, null, SCHLUESSEL, ANGELEGT),
                new Angebotsanlage(2L, ANGEBOT, "neu.pdf", 9L, null, SCHLUESSEL, JETZT)));

    // When
    final List<Angebotsanlage> liste = useCase.liste(ANGEBOT);

    // Then
    assertThat(liste).extracting(Angebotsanlage::dateiName).containsExactly("neu.pdf", "alt.pdf");
  }

  @Test
  void liste_whenTheAngebotIsUnknown_thenRejects() {
    // Given — E5 des Kommentar-Plans, hier gleich: eine unbekannte Kennung im Pfad ist 404.
    angebotGibtEsNicht();

    // When / Then
    assertThatThrownBy(() -> useCase.liste(ANGEBOT)).isInstanceOf(AngebotNichtGefunden.class);
    verifyNoInteractions(anlagen, speicher);
  }

  @Test
  void ladeHoch_thenStoresTheObjectBeforeTheRow() {
    // Given — E9: ein Abbruch dazwischen laesst hoechstens ein verwaistes Objekt liegen.
    angebotGibtEs();
    ablegenGibtDenSchluessel();
    speichernGibtZurueck();

    // When
    useCase.ladeHoch(ANGEBOT, "bericht.pdf", strom(PDF_INHALT), PDF_INHALT.length);

    // Then
    final InOrder reihenfolge = inOrder(speicher, anlagen);
    reihenfolge.verify(speicher).ablegen(eq(ANGEBOT), any(), anyLong());
    reihenfolge.verify(anlagen).save(any());
  }

  @Test
  void ladeHoch_thenStoresTheRowWithTheInstantOfTheClock() {
    // Given — E4, E9: Name, Groesse, Schluessel und Zeitpunkt stehen in der Zeile.
    angebotGibtEs();
    ablegenGibtDenSchluessel();
    speichernGibtZurueck();

    // When
    final Angebotsanlage geladen =
        useCase.ladeHoch(ANGEBOT, "C:\\Ablage\\bericht.pdf", strom(PDF_INHALT), PDF_INHALT.length);

    // Then — der Pfad des Browsers faellt weg, der Rest steht wie eingereicht in der Zeile.
    assertThat(geladen)
        .isEqualTo(
            new Angebotsanlage(
                null,
                ANGEBOT,
                "bericht.pdf",
                PDF_INHALT.length,
                Vorschauart.PDF,
                SCHLUESSEL,
                JETZT));
  }

  @Test
  void ladeHoch_whenStoringTheObjectFails_thenWritesNoRow() {
    // Given — E9: erst das Objekt, dann die Zeile; scheitert das Objekt, gibt es keine Anlage.
    angebotGibtEs();
    when(speicher.ablegen(eq(ANGEBOT), any(), anyLong()))
        .thenThrow(new AnlageSpeicherAusfall("Der Speicher antwortet nicht.", new IOException()));

    final InputStream quelle = strom(PDF_INHALT);

    // When / Then
    assertThatThrownBy(() -> useCase.ladeHoch(ANGEBOT, "bericht.pdf", quelle, PDF_INHALT.length))
        .isInstanceOf(AnlageSpeicherAusfall.class);
    verifyNoInteractions(anlagen);
  }

  @Test
  void ladeHoch_withHtmlContentNamedPdf_thenHasNoVorschauart() {
    // Given — Kriterium 15: erkannt wird am Inhalt, nicht am Namen.
    angebotGibtEs();
    ablegenGibtDenSchluessel();
    speichernGibtZurueck();

    // When
    final Angebotsanlage geladen =
        useCase.ladeHoch(ANGEBOT, "bericht.pdf", strom(HTML_INHALT), HTML_INHALT.length);

    // Then
    assertThat(geladen.vorschauArt()).isNull();
  }

  @Test
  void ladeHoch_withPdfContentNamedTxt_thenHasVorschauartPdf() {
    // Given — die Gegenprobe: der Name sagt das eine, der Inhalt das andere.
    angebotGibtEs();
    ablegenGibtDenSchluessel();
    speichernGibtZurueck();

    // When
    final Angebotsanlage geladen =
        useCase.ladeHoch(ANGEBOT, "notiz.txt", strom(PDF_INHALT), PDF_INHALT.length);

    // Then
    assertThat(geladen.vorschauArt()).isEqualTo(Vorschauart.PDF);
  }

  @Test
  void ladeHoch_thenStoresTheContentByteForByte() {
    // Given — die Erkennung liest den Anfang des Stroms; er muss trotzdem vollstaendig ankommen.
    angebotGibtEs();
    final AtomicReference<byte[]> abgelegt = ablegenLiestDenStrom();
    speichernGibtZurueck();

    // When
    useCase.ladeHoch(ANGEBOT, "bericht.pdf", strom(PDF_INHALT), PDF_INHALT.length);

    // Then
    assertThat(abgelegt.get()).isEqualTo(PDF_INHALT);
  }

  @Test
  void ladeHoch_withAContentShorterThanASignature_thenStoresItWithoutVorschauart() {
    // Given — eine halbe Signatur ist keine, und die wenigen Bytes muessen dennoch ankommen.
    angebotGibtEs();
    final AtomicReference<byte[]> abgelegt = ablegenLiestDenStrom();
    speichernGibtZurueck();
    final byte[] kurz = "%PD".getBytes(UTF_8);

    // When
    final Angebotsanlage geladen = useCase.ladeHoch(ANGEBOT, "kurz.bin", strom(kurz), kurz.length);

    // Then
    assertThat(geladen.vorschauArt()).isNull();
    assertThat(abgelegt.get()).isEqualTo(kurz);
  }

  @Test
  void ladeHoch_withASizeOfZero_thenRejectsAtTheFieldDatei() {
    // Given — Kriterium 5: eine leere Datei ist keine Anlage.
    angebotGibtEs();

    final InputStream quelle = strom(new byte[0]);

    // When / Then
    assertThatThrownBy(() -> useCase.ladeHoch(ANGEBOT, "leer.txt", quelle, 0L))
        .isInstanceOf(AnlageOhneInhalt.class)
        .extracting(fehler -> ((Feldfehler) fehler).felder())
        .isEqualTo(Map.of("datei", List.of(AnlageOhneInhalt.MELDUNG)));
    verifyNoInteractions(anlagen, speicher);
  }

  @Test
  void ladeHoch_withASizeAboveTheLimit_thenRejectsAtTheFieldDatei() {
    // Given — Kriterium 6: der letzte Riegel vor dem Objektspeicher (E8).
    angebotGibtEs();

    final InputStream quelle = strom(PDF_INHALT);

    // When / Then
    assertThatThrownBy(
            () -> useCase.ladeHoch(ANGEBOT, "gross.bin", quelle, Uploadgrenze.MAX_BYTE + 1L))
        .isInstanceOf(AnlageZuGross.class)
        .extracting(fehler -> ((Feldfehler) fehler).felder())
        .isEqualTo(Map.of("datei", List.of(Uploadgrenze.MELDUNG)));
    verifyNoInteractions(anlagen, speicher);
  }

  @Test
  void ladeHoch_withASizeExactlyAtTheLimit_thenAccepts() {
    // Given — die Grenze selbst geht durch; ohne diesen Fall waere sie um eins verschiebbar.
    angebotGibtEs();
    ablegenGibtDenSchluessel();
    speichernGibtZurueck();

    // When
    final Angebotsanlage geladen =
        useCase.ladeHoch(ANGEBOT, "gerade-noch.pdf", strom(PDF_INHALT), Uploadgrenze.MAX_BYTE);

    // Then
    assertThat(geladen.groesse()).isEqualTo(Uploadgrenze.MAX_BYTE);
  }

  @Test
  void ladeHoch_withANameThatIsBlankAfterCleaning_thenRejectsAtTheFieldDatei() {
    // Given — vom Namen bleibt nach der Saeuberung nichts uebrig (E8).
    angebotGibtEs();

    final InputStream quelle = strom(PDF_INHALT);

    // When / Then
    assertThatThrownBy(() -> useCase.ladeHoch(ANGEBOT, "  \t ", quelle, PDF_INHALT.length))
        .isInstanceOf(AnlageOhneNamen.class)
        .extracting(fehler -> ((Feldfehler) fehler).felder())
        .isEqualTo(Map.of("datei", List.of(AnlageOhneNamen.MELDUNG)));
    verifyNoInteractions(anlagen, speicher);
  }

  @Test
  void ladeHoch_whenTheAngebotIsUnknown_thenRejects() {
    // Given
    angebotGibtEsNicht();

    final InputStream quelle = strom(PDF_INHALT);

    // When / Then
    assertThatThrownBy(() -> useCase.ladeHoch(ANGEBOT, "bericht.pdf", quelle, PDF_INHALT.length))
        .isInstanceOf(AngebotNichtGefunden.class);
    verifyNoInteractions(anlagen, speicher);
  }

  @Test
  void ladeHoch_whenTheStreamFailsWhileReading_thenReportsItAndWritesNoRow() {
    // Given — der Port sagt zu, dass der Aufrufer den Strom schliesst; ein Lesefehler dabei darf
    // nicht als stiller Erfolg enden (CLAUDE-java.md §6.5).
    angebotGibtEs();

    final InputStream quelle = new KaputterStrom();

    // When / Then
    assertThatThrownBy(() -> useCase.ladeHoch(ANGEBOT, "bericht.pdf", quelle, 17L))
        .isInstanceOf(UncheckedIOException.class);
    verifyNoInteractions(anlagen, speicher);
  }

  @Test
  void liesInhalt_thenReturnsNameSizeVorschauartAndTheOpenStream() throws IOException {
    // Given — Kriterium 9, 15: was hinausgeht, steht in der Zeile, die Bytes im Speicher.
    angebotGibtEs();
    anlageGehoertZu(ANGEBOT);
    when(speicher.lesen(SCHLUESSEL)).thenReturn(Optional.of(strom(PDF_INHALT)));

    // When
    final Anlageninhalt inhalt = useCase.liesInhalt(ANGEBOT, ANLAGE);

    // Then
    assertThat(inhalt.dateiName()).isEqualTo("bericht.pdf");
    assertThat(inhalt.groesse()).isEqualTo(24L);
    assertThat(inhalt.vorschauArt()).isEqualTo(Vorschauart.PDF);
    assertThat(inhalt.inhalt().readAllBytes()).isEqualTo(PDF_INHALT);
  }

  @Test
  void liesInhalt_whenTheAnlageBelongsToAnotherAngebot_thenRejects() {
    // Given — beide Kennungen stehen im Pfad; die Anlage unter diesem Angebot gibt es nicht.
    angebotGibtEs();
    anlageGehoertZu(FREMDES_ANGEBOT);

    // When / Then
    assertThatThrownBy(() -> useCase.liesInhalt(ANGEBOT, ANLAGE))
        .isInstanceOf(AngebotsanlageNichtGefunden.class);
    verifyNoInteractions(speicher);
  }

  @Test
  void liesInhalt_whenTheAnlageIsUnknown_thenRejects() {
    // Given
    angebotGibtEs();
    when(anlagen.findById(ANLAGE)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> useCase.liesInhalt(ANGEBOT, ANLAGE))
        .isInstanceOf(AngebotsanlageNichtGefunden.class);
    verifyNoInteractions(speicher);
  }

  @Test
  void liesInhalt_whenTheStorageDoesNotKnowTheKey_thenRejects() {
    // Given — die Zeile steht, das Objekt fehlt: fuer den Abrufer ist die Anlage nicht vorhanden.
    angebotGibtEs();
    anlageGehoertZu(ANGEBOT);
    when(speicher.lesen(SCHLUESSEL)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> useCase.liesInhalt(ANGEBOT, ANLAGE))
        .isInstanceOf(AngebotsanlageNichtGefunden.class);
  }

  @Test
  void liesInhalt_whenTheAngebotIsUnknown_thenRejects() {
    // Given
    angebotGibtEsNicht();

    // When / Then
    assertThatThrownBy(() -> useCase.liesInhalt(ANGEBOT, ANLAGE))
        .isInstanceOf(AngebotNichtGefunden.class);
    verifyNoInteractions(anlagen, speicher);
  }

  @Test
  void loesche_withinATransaction_thenRemovesTheObjectOnlyAfterTheCommit() {
    // Given — E9: vor dem Commit geloescht, liesse ein gescheiterter Commit eine Zeile ohne Objekt.
    TransactionSynchronizationManager.initSynchronization();
    angebotGibtEs();
    anlageGehoertZu(ANGEBOT);

    // When
    useCase.loesche(ANGEBOT, ANLAGE);

    // Then
    verify(anlagen).deleteById(ANLAGE);
    verifyNoInteractions(speicher);

    // And — nach dem Commit ist das Objekt fort.
    TransactionSynchronizationManager.getSynchronizations()
        .forEach(TransactionSynchronization::afterCommit);
    verify(speicher).loeschen(SCHLUESSEL);
  }

  @Test
  void loesche_withoutATransaction_thenRemovesTheObjectRightAway() {
    // Given — ohne Transaktion gibt es keinen Commit, auf den zu warten waere.
    angebotGibtEs();
    anlageGehoertZu(ANGEBOT);

    // When
    useCase.loesche(ANGEBOT, ANLAGE);

    // Then
    verify(anlagen).deleteById(ANLAGE);
    verify(speicher).loeschen(SCHLUESSEL);
  }

  @Test
  void loesche_whenDeletingTheRowFails_thenNeverTouchesTheStorage() {
    // Given — der Fund aus dem Plan-Review: das Objekt haengt am Erfolg der Zeile, nie umgekehrt.
    angebotGibtEs();
    anlageGehoertZu(ANGEBOT);
    doThrow(new IllegalStateException("Die Zeile ist gesperrt.")).when(anlagen).deleteById(ANLAGE);

    // When / Then
    assertThatThrownBy(() -> useCase.loesche(ANGEBOT, ANLAGE))
        .isInstanceOf(IllegalStateException.class);
    verifyNoInteractions(speicher);
  }

  @Test
  void loesche_whenRemovingTheObjectFails_thenCompletesWithoutException() {
    // Given — E9: ein Objekt ohne Zeile ist unerreichbar und schadet nicht; die Zeile bleibt fort.
    angebotGibtEs();
    anlageGehoertZu(ANGEBOT);
    doThrow(new AnlageSpeicherAusfall("Der Speicher antwortet nicht.", new IOException()))
        .when(speicher)
        .loeschen(anyString());

    // When
    useCase.loesche(ANGEBOT, ANLAGE);

    // Then — die Zeile ist fort, und der Fehlschlag steht im Protokoll statt in der Antwort.
    verify(anlagen).deleteById(ANLAGE);
    assertThat(mitgeschrieben.list).hasSize(1);
    assertThat(mitgeschrieben.list.getFirst().getLevel()).isEqualTo(Level.WARN);
    assertThat(mitgeschrieben.list.getFirst().getFormattedMessage())
        .contains(SCHLUESSEL)
        .doesNotContain("bericht.pdf");
  }

  @Test
  void loesche_whenTheAnlageBelongsToAnotherAngebot_thenRejectsAndDeletesNothing() {
    // Given
    angebotGibtEs();
    anlageGehoertZu(FREMDES_ANGEBOT);

    // When / Then
    assertThatThrownBy(() -> useCase.loesche(ANGEBOT, ANLAGE))
        .isInstanceOf(AngebotsanlageNichtGefunden.class);
    verify(anlagen).findById(ANLAGE);
    verifyNoMoreInteractions(anlagen);
    verifyNoInteractions(speicher);
  }

  @Test
  void loesche_whenTheAnlageIsUnknown_thenRejects() {
    // Given
    angebotGibtEs();
    when(anlagen.findById(ANLAGE)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> useCase.loesche(ANGEBOT, ANLAGE))
        .isInstanceOf(AngebotsanlageNichtGefunden.class);
    verifyNoInteractions(speicher);
  }

  @Test
  void loesche_whenTheAngebotIsUnknown_thenRejects() {
    // Given
    angebotGibtEsNicht();

    // When / Then
    assertThatThrownBy(() -> useCase.loesche(ANGEBOT, ANLAGE))
        .isInstanceOf(AngebotNichtGefunden.class);
    verifyNoInteractions(anlagen, speicher);
  }

  /** Ein Datenstrom, der beim ersten Lesen scheitert — die Lage aus §6.5 des Java-Guides. */
  private static final class KaputterStrom extends InputStream {

    @Override
    public int read() throws IOException {
      throw new IOException("Die Leitung ist fort.");
    }

    /* readNBytes greift den Feld-Weg, nicht das einzelne Byte; beide muessen scheitern. */
    @Override
    public int read(final byte[] ziel, final int ab, final int laenge) throws IOException {
      throw new IOException("Die Leitung ist fort.");
    }
  }
}
