package org.mwolff.fbcrm.rechnung.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.rechnung.domain.Briefbogen;
import org.mwolff.fbcrm.rechnung.domain.BriefbogenRepository;
import org.mwolff.fbcrm.rechnung.domain.Briefpapierblatt;
import org.mwolff.fbcrm.rechnung.domain.Briefpapierspeicher;
import org.mwolff.fbcrm.rechnung.domain.DokumentSpeicherAusfall;
import org.mwolff.fbcrm.rechnung.domain.Randmasse;
import org.mwolff.fbcrm.rechnung.domain.Zeilenstand;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Das Briefpapier hochladen, ersetzen, lesen und entfernen (fachliche Quelle #361, Kriterien 1, 2
 * und 16; Plan #363, E4, E5, E6, E15 und E17).
 *
 * <p>Gegenstand sind die drei Riegel vor dem Pruefer, die Regel, dass allein die gereinigten Bytes
 * des Pruefers in den Speicher gehen, die Reihenfolge von Ablage, Zeile und altem Objekt und die
 * Unberuehrtheit von Schaltern und Raendern. Was der Pruefer am geladenen Dokument abweist, steht
 * in {@code BriefpapierpruefungTest}; hier zeigt sich, dass jede dieser Abweisungen vor jeder
 * Ablage greift.
 *
 * <p>Dazu der Vergleich des vorgelegten Stands (#411, Kriterium 4; Plan #415, E1, E11, E14): Auf
 * einem alten Stand wird abgewiesen, vor den Riegeln und bevor etwas im Speicher landet.
 */
@ExtendWith(MockitoExtension.class)
class BriefpapierUseCaseTest {

  private static final String ALT = "briefpapier/alt.pdf";
  private static final String NEU = "briefpapier/neu.pdf";
  private static final Instant JETZT = Instant.parse("2026-10-19T09:30:00Z");

  private static final byte[] HOCHGELADEN =
      "%PDF-1.7\n1 0 obj\n<< /OpenAction 2 0 R >>\nendobj\n".getBytes(StandardCharsets.US_ASCII);

  private static final byte[] GEREINIGT =
      "%PDF-1.7\n1 0 obj\n<<>>\nendobj\n".getBytes(StandardCharsets.US_ASCII);

  /** Raender abseits der Vorgaben, damit ein Zuruecksetzen auffiele. */
  private static final Randmasse EINGESTELLT = new Randmasse(300, 150, 1_000, 250, 180, 220);

  private static final Briefpapierblatt BISHERIGES =
      new Briefpapierblatt(ALT, "alt.pdf", 12_345L, 2);

  /** Der Stand, den die Maske vorlegt und den die Zeile traegt. */
  private static final long STAND = 3L;

  /** Das handelnde Konto. */
  private static final long KONTO = 31L;

  /** Das Konto, das zuletzt geaendert hat. */
  private static final long ANNA = 30L;

  private static final Instant ANNAS_AENDERUNG = Instant.parse("2026-10-19T08:42:00Z");

  @Mock private BriefbogenRepository bestand;
  @Mock private Briefpapierspeicher speicher;
  @Mock private Briefpapierpruefer pruefer;

  private final ListAppender<ILoggingEvent> mitgeschrieben = new ListAppender<>();

  private BriefpapierUseCase useCase;

  private ch.qos.logback.classic.Logger protokoll;

  private @Nullable Level vorherigeStufe;

  @BeforeEach
  void richteDenTestEin() {
    useCase =
        new BriefpapierUseCase(bestand, speicher, pruefer, Clock.fixed(JETZT, ZoneOffset.UTC));
    // Nachsichtig: Nicht jeder Fall kommt bis zum Vergleich oder zum Schreiben.
    lenient().when(bestand.stand()).thenReturn(new Zeilenstand(STAND, ANNA, ANNAS_AENDERUNG));
    lenient()
        .when(bestand.speichere(any(), any(), anyLong(), anyLong()))
        .thenReturn(new Zeilenstand(STAND + 1, KONTO, JETZT));
    protokoll =
        ((LoggerContext) LoggerFactory.getILoggerFactory())
            .getLogger(BriefpapierUseCase.class.getName());
    vorherigeStufe = protokoll.getLevel();
    mitgeschrieben.setContext(protokoll.getLoggerContext());
    mitgeschrieben.start();
    protokoll.addAppender(mitgeschrieben);
    protokoll.setLevel(Level.TRACE);
  }

  /*
   * Stufe zuruecksetzen, Appender abhaengen und eine eroeffnete Synchronisation raeumen: Logger und
   * ThreadLocal gehoeren der ganzen JVM (Muster NachtragDokumentUseCaseTest).
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

  private void bogenTraegt(final @Nullable Briefpapierblatt blatt) {
    when(bestand.lies()).thenReturn(new Briefbogen(blatt, false, true, EINGESTELLT));
  }

  private void prueferNimmtAn(final byte[] eingang) {
    when(pruefer.pruefe(eingang)).thenReturn(new Briefpapierpruefer.Gereinigt(GEREINIGT, 2));
  }

  private Briefbogen gespeichert() {
    final ArgumentCaptor<Briefbogen> bogen = ArgumentCaptor.forClass(Briefbogen.class);
    verify(bestand).speichere(bogen.capture(), any(), anyLong(), anyLong());
    return bogen.getValue();
  }

  /** Die Abweisung nennt ihren eigenen Satz am Feld {@code datei}; Zeile und Speicher bleiben. */
  private void assertAbgewiesenMit(final byte[] inhalt, final String meldung) {
    assertThatThrownBy(() -> useCase.ladeHoch("briefpapier.pdf", inhalt, STAND, KONTO))
        .isInstanceOfSatisfying(
            BriefpapierNichtAnnehmbar.class,
            fehler -> {
              assertThat(fehler.getMessage()).isEqualTo(meldung);
              assertThat(fehler.felder()).containsOnlyKeys("datei");
              assertThat(fehler.felder().get("datei")).isEqualTo(List.of(meldung));
            });
    verifyNoInteractions(speicher);
    verify(bestand, never()).speichere(any(), any(), anyLong(), anyLong());
  }

  // --- ladeHoch: die sechs Abweisungen ------------------------------------------------------

  @Test
  void ladeHoch_givenQuerformat_thenUngeeignetesPdfAbgewiesen() {
    // Given — Szenario „Ungeeignetes PDF": ein Briefpapier ist schon hinterlegt. Die Abweisung
    // faellt, bevor die Zeile auch nur gelesen wird.
    when(pruefer.pruefe(HOCHGELADEN)).thenThrow(BriefpapierNichtAnnehmbar.falschesFormat());

    // When / Then — die Meldung nennt das Format.
    assertAbgewiesenMit(HOCHGELADEN, BriefpapierNichtAnnehmbar.MELDUNG_FORMAT);
    assertThat(BriefpapierNichtAnnehmbar.MELDUNG_FORMAT).contains("A4 hochkant");

    // And — das bisherige Briefpapier gilt weiter: Die Zeile ist nicht geschrieben (Schluessel,
    // Dateiname, Groesse und Seitenzahl unveraendert), und der Speicher hat nichts angenommen und
    // nichts verloren — beides prueft assertAbgewiesenMit.
    verify(bestand, never()).lies();
  }

  @Test
  void ladeHoch_givenLeer_thenAbgewiesen() {
    // When / Then — der Pruefer bekommt die Datei gar nicht erst.
    assertAbgewiesenMit(new byte[0], BriefpapierNichtAnnehmbar.MELDUNG_LEER);
    verifyNoInteractions(pruefer);
  }

  @Test
  void ladeHoch_givenZuGross_thenAbgewiesen() {
    // Given — ein PDF an der Signatur, aber ein Byte ueber 5 MB: die Groesse sticht den Inhalt.
    final byte[] zuGross =
        Arrays.copyOf(HOCHGELADEN, Math.toIntExact(Briefpapiergrenze.MAX_BYTE + 1));

    // When / Then
    assertAbgewiesenMit(zuGross, Briefpapiergrenze.MELDUNG);
    verifyNoInteractions(pruefer);
  }

  @Test
  void ladeHoch_givenGenauDieGrenze_thenAngenommen() {
    // Given — die Grenze selbst ist erlaubt.
    bogenTraegt(null);
    final byte[] genau = Arrays.copyOf(HOCHGELADEN, Math.toIntExact(Briefpapiergrenze.MAX_BYTE));
    prueferNimmtAn(genau);
    when(speicher.lege(any())).thenReturn(NEU);

    // When
    final Briefbogenstand stand = useCase.ladeHoch("briefpapier.pdf", genau, STAND, KONTO);

    // Then
    assertThat(stand.briefpapier()).isTrue();
  }

  @Test
  void ladeHoch_givenKeinPdf_thenAbgewiesen() {
    // Given — eine HTML-Seite, die „briefpapier.pdf" heisst: erkannt wird an den ersten Bytes.
    final byte[] html =
        "<!DOCTYPE html><html><body><script>alert(1)</script></body></html>"
            .getBytes(StandardCharsets.UTF_8);

    // When / Then
    assertAbgewiesenMit(html, BriefpapierNichtAnnehmbar.MELDUNG_KEIN_PDF);
    verifyNoInteractions(pruefer);
  }

  @Test
  void ladeHoch_givenEinAnderesErkanntesFormat_thenKeinPdf() {
    // Given — ein Bild hat eine Vorschauart, ist aber kein PDF.
    final byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

    // When / Then
    assertAbgewiesenMit(png, BriefpapierNichtAnnehmbar.MELDUNG_KEIN_PDF);
    verifyNoInteractions(pruefer);
  }

  @Test
  void ladeHoch_givenVerschluesselt_thenAbgewiesen() {
    // Given
    when(pruefer.pruefe(HOCHGELADEN)).thenThrow(BriefpapierNichtAnnehmbar.nichtLadbar());

    // When / Then
    assertAbgewiesenMit(HOCHGELADEN, BriefpapierNichtAnnehmbar.MELDUNG_NICHT_LADBAR);
  }

  @Test
  void ladeHoch_givenDreiSeiten_thenAbgewiesen() {
    // Given
    when(pruefer.pruefe(HOCHGELADEN)).thenThrow(BriefpapierNichtAnnehmbar.zuVieleSeiten());

    // When / Then
    assertAbgewiesenMit(HOCHGELADEN, BriefpapierNichtAnnehmbar.MELDUNG_ZU_VIELE_SEITEN);
  }

  @Test
  void ladeHoch_thenSechsVerschiedeneMeldungen() {
    // Then — jede Abweisung sagt ihren eigenen Grund.
    assertThat(
            List.of(
                BriefpapierNichtAnnehmbar.MELDUNG_LEER,
                BriefpapierNichtAnnehmbar.MELDUNG_ZU_GROSS,
                BriefpapierNichtAnnehmbar.MELDUNG_KEIN_PDF,
                BriefpapierNichtAnnehmbar.MELDUNG_NICHT_LADBAR,
                BriefpapierNichtAnnehmbar.MELDUNG_ZU_VIELE_SEITEN,
                BriefpapierNichtAnnehmbar.MELDUNG_FORMAT))
        .doesNotHaveDuplicates();
  }

  // --- ladeHoch: angenommen -----------------------------------------------------------------

  @Test
  void ladeHoch_thenNurGereinigteBytesAbgelegt() {
    // Given
    bogenTraegt(null);
    prueferNimmtAn(HOCHGELADEN);
    when(speicher.lege(any())).thenReturn(NEU);

    // When
    useCase.ladeHoch("briefpapier.pdf", HOCHGELADEN, STAND, KONTO);

    // Then — E4: in den Speicher geht, was der Pruefer zurueckgibt, nicht das Hochgeladene.
    final ArgumentCaptor<byte[]> abgelegt = ArgumentCaptor.forClass(byte[].class);
    verify(speicher).lege(abgelegt.capture());
    assertThat(abgelegt.getValue()).isEqualTo(GEREINIGT).isNotEqualTo(HOCHGELADEN);
  }

  @Test
  void ladeHoch_thenDieZeileTraegtSchluesselNameGroesseUndSeitenUndSonstNichtsNeues() {
    // Given
    bogenTraegt(null);
    prueferNimmtAn(HOCHGELADEN);
    when(speicher.lege(any())).thenReturn(NEU);

    // When
    final Briefbogenstand stand = useCase.ladeHoch("kopf.pdf", HOCHGELADEN, STAND, KONTO);

    // Then — die Groesse ist die des abgelegten Objekts; Schalter und Raender bleiben (E15).
    final Briefbogen bogen = gespeichert();
    assertThat(bogen.blatt()).isEqualTo(new Briefpapierblatt(NEU, "kopf.pdf", GEREINIGT.length, 2));
    assertThat(bogen.absenderzeile()).isFalse();
    assertThat(bogen.steuerBankZeile()).isTrue();
    assertThat(bogen.raender()).isEqualTo(EINGESTELLT);
    assertThat(stand)
        .isEqualTo(
            new Briefbogenstand(
                true, "kopf.pdf", (long) GEREINIGT.length, 2, false, true, EINGESTELLT, STAND + 1));
    verify(bestand).speichere(any(), eq(JETZT), eq(STAND), eq(KONTO));
  }

  @Test
  void ladeHoch_givenEinenPfadImNamen_thenBleibtAllein() {
    // Given — manche Browser schicken den ganzen Pfad; gesaeubert wie bei den Anlagen.
    bogenTraegt(null);
    prueferNimmtAn(HOCHGELADEN);
    when(speicher.lege(any())).thenReturn(NEU);

    // When
    final Briefbogenstand stand =
        useCase.ladeHoch("C:\\Briefe\\kopf\u0007.pdf", HOCHGELADEN, STAND, KONTO);

    // Then
    assertThat(stand.dateiname()).isEqualTo("kopf.pdf");
  }

  @Test
  void ladeHoch_givenKeinenBrauchbarenNamen_thenTraegtDenErsatznamen() {
    // Given — ohne Namen ist die Datei nicht abzuweisen; der Satz der Maske braucht trotzdem einen.
    bogenTraegt(null);
    prueferNimmtAn(HOCHGELADEN);
    when(speicher.lege(any())).thenReturn(NEU);

    // When
    final Briefbogenstand stand = useCase.ladeHoch("  ", HOCHGELADEN, STAND, KONTO);

    // Then
    assertThat(stand.dateiname()).isEqualTo(BriefpapierUseCase.ERSATZNAME);
    assertThat(BriefpapierUseCase.ERSATZNAME).isEqualTo("briefpapier.pdf");
  }

  @Test
  void ladeHoch_givenOhneBisherigesBriefpapier_thenWirdNichtsEntfernt() {
    // Given
    bogenTraegt(null);
    prueferNimmtAn(HOCHGELADEN);
    when(speicher.lege(any())).thenReturn(NEU);

    // When
    useCase.ladeHoch("kopf.pdf", HOCHGELADEN, STAND, KONTO);

    // Then — any() statt anyString(): anyString() passt nicht auf null.
    verify(speicher, never()).loesche(any());
  }

  @Test
  void ladeHoch_givenVorhandenesBriefpapier_thenAltesObjektErstNachDemCommit() {
    // Given — E6: vor dem Commit geloescht, zeigte eine zurueckgerollte Zeile ins Leere.
    TransactionSynchronizationManager.initSynchronization();
    bogenTraegt(BISHERIGES);
    prueferNimmtAn(HOCHGELADEN);
    when(speicher.lege(any())).thenReturn(NEU);

    // When
    useCase.ladeHoch("neu.pdf", HOCHGELADEN, STAND, KONTO);

    // Then — vor dem Commit ist das alte Objekt noch da.
    assertThat(gespeichert().blatt()).extracting(Briefpapierblatt::schluessel).isEqualTo(NEU);
    verify(speicher, never()).loesche(anyString());

    // And — nach dem Commit ist genau das alte Objekt fort.
    TransactionSynchronizationManager.getSynchronizations()
        .forEach(TransactionSynchronization::afterCommit);
    verify(speicher).loesche(ALT);
    verify(speicher, never()).loesche(NEU);
  }

  @Test
  void ladeHoch_givenVorhandenesBriefpapier_thenErstAblageDannZeileDannEntfernen() {
    // Given — ohne laufende Transaktion geschieht das Entfernen sofort, aber nach der Zeile.
    bogenTraegt(BISHERIGES);
    prueferNimmtAn(HOCHGELADEN);
    when(speicher.lege(any())).thenReturn(NEU);

    // When
    useCase.ladeHoch("neu.pdf", HOCHGELADEN, STAND, KONTO);

    // Then
    final InOrder reihenfolge = inOrder(pruefer, speicher, bestand);
    reihenfolge.verify(pruefer).pruefe(HOCHGELADEN);
    reihenfolge.verify(speicher).lege(any());
    reihenfolge.verify(bestand).speichere(any(), any(), anyLong(), anyLong());
    reihenfolge.verify(speicher).loesche(ALT);
  }

  @Test
  void ladeHoch_givenSpeicherausfallBeimAblegen_thenGeworfenUndZeileUnberuehrt() {
    // Given — beim Ablegen ist noch nichts geschrieben; der Ausfall gehoert in die Antwort.
    prueferNimmtAn(HOCHGELADEN);
    final DokumentSpeicherAusfall ausfall =
        new DokumentSpeicherAusfall("Der Speicher antwortet nicht.", new IOException());
    when(speicher.lege(any())).thenThrow(ausfall);

    // When / Then
    assertThatThrownBy(() -> useCase.ladeHoch("neu.pdf", HOCHGELADEN, STAND, KONTO))
        .isSameAs(ausfall);
    verify(bestand, never()).speichere(any(), any(), anyLong(), anyLong());
    verify(speicher, never()).loesche(anyString());
  }

  @Test
  void ladeHoch_givenSpeicherausfallBeimEntfernenDesAlten_thenNurProtokolliert() {
    // Given — die Zeile zeigt schon auf das neue Briefpapier, das alte ist unerreichbar.
    bogenTraegt(BISHERIGES);
    prueferNimmtAn(HOCHGELADEN);
    when(speicher.lege(any())).thenReturn(NEU);
    doThrow(new DokumentSpeicherAusfall("Der Speicher antwortet nicht.", new IOException()))
        .when(speicher)
        .loesche(ALT);

    // When
    final Briefbogenstand stand = useCase.ladeHoch("neu.pdf", HOCHGELADEN, STAND, KONTO);

    // Then
    assertThat(stand.dateiname()).isEqualTo("neu.pdf");
    assertThat(mitgeschrieben.list).hasSize(1);
    assertThat(mitgeschrieben.list.getFirst().getLevel()).isEqualTo(Level.WARN);
    assertThat(mitgeschrieben.list.getFirst().getFormattedMessage()).contains(ALT);
  }

  @Test
  void ladeHoch_givenEinenVeraltetenStand_thenAbgewiesenVorJedemRiegelUndJederAblage() {
    // When — #411, Kriterium 4: Anna hat den Briefbogen inzwischen geaendert (E11: gleich welche
    // Spalte).
    final BriefbogenGeaendert konflikt =
        catchThrowableOfType(
            BriefbogenGeaendert.class,
            () -> useCase.ladeHoch("neu.pdf", HOCHGELADEN, STAND - 1, KONTO));

    // Then — Urheber, Handelnder und Zeitpunkt fuer die Meldung.
    assertThat(konflikt).isNotNull();
    assertThat(konflikt.urheberKonto()).hasValue(ANNA);
    assertThat(konflikt.handelndesKonto()).isEqualTo(KONTO);
    assertThat(konflikt.zeitpunkt()).hasValue(ANNAS_AENDERUNG);

    // Then — weder geprueft noch abgelegt noch geschrieben.
    verifyNoInteractions(pruefer, speicher);
    verify(bestand, never()).speichere(any(), any(), anyLong(), anyLong());
  }

  @Test
  void ladeHoch_givenEinenVeraltetenStandUndEineLeereDatei_thenZuerstDerStand() {
    // When / Then — der Stand steht vor den Riegeln: Wer auf altem Stand arbeitet, soll davon
    // erfahren und nicht von einem Fehler an der Datei.
    assertThatThrownBy(() -> useCase.ladeHoch("leer.pdf", new byte[0], STAND - 1, KONTO))
        .isInstanceOf(BriefbogenGeaendert.class);
  }

  // --- lies ---------------------------------------------------------------------------------

  @Test
  void lies_givenHinterlegtesBriefpapier_thenSeineBytes() {
    // Given
    bogenTraegt(BISHERIGES);
    when(speicher.lies(ALT)).thenReturn(GEREINIGT);

    // When / Then
    assertThat(useCase.lies()).hasValue(GEREINIGT);
  }

  @Test
  void lies_givenOhneBriefpapier_thenLeer() {
    // Given — ohne Briefpapier bleibt die Rechnung, wie sie heute ist (Kriterium 11).
    bogenTraegt(null);

    // When / Then
    assertThat(useCase.lies()).isEmpty();
    verifyNoInteractions(speicher);
  }

  // --- entferne -----------------------------------------------------------------------------

  @Test
  void entferne_thenSchalterUndRaenderUnberuehrt() {
    // Given
    bogenTraegt(BISHERIGES);

    // When
    final Briefbogenstand stand = useCase.entferne(STAND, KONTO);

    // Then — E15, Kriterium 16: allein das Blatt faellt weg.
    final Briefbogen bogen = gespeichert();
    assertThat(bogen).isEqualTo(new Briefbogen(null, false, true, EINGESTELLT));
    assertThat(stand)
        .isEqualTo(
            new Briefbogenstand(false, null, null, null, false, true, EINGESTELLT, STAND + 1));
    verify(bestand).speichere(any(), eq(JETZT), eq(STAND), eq(KONTO));
  }

  @Test
  void entferne_thenZeileVorDemObjektUndDasObjektErstNachDemCommit() {
    // Given
    TransactionSynchronizationManager.initSynchronization();
    bogenTraegt(BISHERIGES);

    // When
    useCase.entferne(STAND, KONTO);

    // Then
    verify(bestand).speichere(any(), any(), anyLong(), anyLong());
    verify(speicher, never()).loesche(anyString());

    // And
    TransactionSynchronizationManager.getSynchronizations()
        .forEach(TransactionSynchronization::afterCommit);
    verify(speicher).loesche(ALT);
  }

  @Test
  void entferne_givenOhneBriefpapier_thenNichtGefunden() {
    // Given
    bogenTraegt(null);

    // When / Then
    assertThatThrownBy(() -> useCase.entferne(STAND, KONTO))
        .isInstanceOf(BriefpapierNichtGefunden.class);
    verify(bestand, never()).speichere(any(), any(), anyLong(), anyLong());
    verifyNoInteractions(speicher);
  }

  @Test
  void entferne_givenEinenVeraltetenStand_thenAbgewiesenUndNichtsGeschrieben() {
    // When / Then — #411, Kriterium 4.
    assertThatThrownBy(() -> useCase.entferne(STAND + 1, KONTO))
        .isInstanceOf(BriefbogenGeaendert.class);
    verify(bestand, never()).speichere(any(), any(), anyLong(), anyLong());
    verifyNoInteractions(speicher);
  }

  @Test
  void entferne_givenEinenVeraltetenStandOhneBriefpapier_thenZuerstDerStand() {
    // Given — ein anderer hat das Briefpapier inzwischen entfernt; davon soll die Meldung sagen,
    // wer und wann, und nicht nur, dass keines da ist.
    lenient().when(bestand.lies()).thenReturn(new Briefbogen(null, false, true, EINGESTELLT));

    // When / Then
    assertThatThrownBy(() -> useCase.entferne(STAND - 1, KONTO))
        .isInstanceOf(BriefbogenGeaendert.class);
  }

  @Test
  void entferne_givenSpeicherausfall_thenNurProtokolliert() {
    // Given
    bogenTraegt(BISHERIGES);
    doThrow(new DokumentSpeicherAusfall("Der Speicher antwortet nicht.", new IOException()))
        .when(speicher)
        .loesche(ALT);

    // When
    final Briefbogenstand stand = useCase.entferne(STAND, KONTO);

    // Then
    assertThat(stand.briefpapier()).isFalse();
    assertThat(mitgeschrieben.list).hasSize(1);
    assertThat(mitgeschrieben.list.getFirst().getLevel()).isEqualTo(Level.WARN);
    assertThat(mitgeschrieben.list.getFirst().getFormattedMessage()).contains(ALT);
  }
}
