package org.mwolff.fbcrm.rechnung.application;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.angebot.application.AngebotNichtGefunden;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.angebot.domain.Angebotsstatus;
import org.mwolff.fbcrm.common.Anschrift;
import org.mwolff.fbcrm.eigeneangaben.domain.EigeneAngaben;
import org.mwolff.fbcrm.eigeneangaben.domain.EigeneAngabenRepository;
import org.mwolff.fbcrm.firma.application.FirmaNichtGefunden;
import org.mwolff.fbcrm.firma.domain.Firma;
import org.mwolff.fbcrm.firma.domain.FirmaRepository;
import org.mwolff.fbcrm.rechnung.domain.Belegabsender;
import org.mwolff.fbcrm.rechnung.domain.Belegempfaenger;
import org.mwolff.fbcrm.rechnung.domain.DokumentSpeicher;
import org.mwolff.fbcrm.rechnung.domain.Nummernkreis;
import org.mwolff.fbcrm.rechnung.domain.Nummernmuster;
import org.mwolff.fbcrm.rechnung.domain.Rechnung;
import org.mwolff.fbcrm.rechnung.domain.RechnungRepository;
import org.mwolff.fbcrm.rechnung.domain.Rechnungseinstellungen;
import org.mwolff.fbcrm.rechnung.domain.RechnungseinstellungenRepository;
import org.mwolff.fbcrm.rechnung.domain.Rechnungszustand;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Das Stellen einer Rechnung: Nummer, Festschreibung, Dokument (#160, Kriterien 5, 13 bis 16, 18
 * und 27; Plan #169, E7).
 *
 * <p>Gegenstand ist vor allem die <b>Reihenfolge</b> der Schritte, denn an ihr haengt die
 * Lueckenlosigkeit des Nummernkreises: Erst wird die gestellte Rechnung geschrieben, dann entsteht
 * ihr Dokument. Ein Druck, der vorher liefe und scheiterte, verbrauchte eine Nummer, die nirgends
 * steht. Umgekehrt wird gar nicht gedruckt, wenn die Nummer schon vergeben ist — das Objekt im
 * Speicher waere eine Waise.
 *
 * <p>Daneben die Abweisungen, die alle <b>vor</b> dem Zug aus dem Nummernkreis liegen, und der
 * Sprung des Angebots auf „abgerechnet", der nur mit gestellten Rechnungen rechnet: Ein Entwurf
 * kann noch geloescht werden, und dann waere das Angebot zu frueh abgerechnet (Manne, 2026-09-30).
 */
@ExtendWith(MockitoExtension.class)
class RechnungStellenUseCaseTest {

  private static final long ENTWURF = 2L;
  private static final Instant JETZT = Instant.parse("2026-09-30T10:00:00Z");
  private static final BigDecimal STEUERSATZ = new BigDecimal("19.00");
  private static final int ZAHLUNGSZIEL = 14;
  private static final String MUSTER_MIT_JAHR = "{NNNN}-{JJJJ}";
  private static final String MUSTER_OHNE_JAHR = "R-{NNN}";
  private static final String NUMMER = "0001-2026";
  private static final byte[] PDF = "%PDF-1.7 Rechnung".getBytes(UTF_8);
  private static final String SCHLUESSEL = "rechnung/2/beleg.pdf";

  private static final Anschrift LEER = new Anschrift(null, null, null, null);

  private static final EigeneAngaben OHNE_ANGABEN =
      new EigeneAngaben(null, null, LEER, null, null, null, null, null, null);

  @Mock private RechnungRepository rechnungen;
  @Mock private AngebotRepository angebote;
  @Mock private FirmaRepository firmen;
  @Mock private EigeneAngabenRepository eigeneAngaben;
  @Mock private RechnungseinstellungenRepository einstellungen;
  @Mock private Nummernkreis nummernkreis;
  @Mock private Belegdrucker drucker;
  @Mock private DokumentSpeicher dokumente;

  @Captor private ArgumentCaptor<Rechnung> festgeschrieben;
  @Captor private ArgumentCaptor<Angebot> fortgeschrieben;

  private RechnungStellenUseCase useCase;

  @BeforeEach
  void baueDenAnwendungsfall() {
    useCase =
        new RechnungStellenUseCase(
            rechnungen,
            angebote,
            firmen,
            eigeneAngaben,
            einstellungen,
            nummernkreis,
            drucker,
            dokumente,
            Clock.fixed(JETZT, ZoneOffset.UTC));
  }

  /** Die beiden Lagen, in denen genau eine der zwei Steuerangaben steht (Kriterium 13). */
  private static Stream<EigeneAngaben> eineVonBeidenSteuerangaben() {
    return Stream.of(mitSteuerangaben("75/123/45678", null), mitSteuerangaben(null, "DE123456789"));
  }

  private static EigeneAngaben mitSteuerangaben(
      final String steuernummer, final String umsatzsteuerId) {
    final EigeneAngaben voll = Rechnungsdoppel.eigeneAngaben();
    return new EigeneAngaben(
        voll.name(),
        voll.berufsbezeichnung(),
        voll.anschrift(),
        voll.email(),
        voll.telefon(),
        voll.webadresse(),
        steuernummer,
        umsatzsteuerId,
        voll.bankverbindung());
  }

  private static EigeneAngaben ohneBankverbindung() {
    final EigeneAngaben voll = Rechnungsdoppel.eigeneAngaben();
    return new EigeneAngaben(
        voll.name(),
        voll.berufsbezeichnung(),
        voll.anschrift(),
        voll.email(),
        voll.telefon(),
        voll.webadresse(),
        voll.steuernummer(),
        voll.umsatzsteuerId(),
        null);
  }

  /** Das bestellte Angebot mit nur der Beratung: 160 Stunden zu 100,00 €. */
  private static Angebot angebotMitBeratung() {
    return Rechnungsdoppel.angebot(Angebotsstatus.BESTELLT, List.of(Rechnungsdoppel.BERATUNG));
  }

  private static Rechnung entwurfUeber(final String menge) {
    return Rechnungsdoppel.entwurf(ENTWURF, List.of(Rechnungsdoppel.beratung(menge)));
  }

  private static Rechnung gestelltUeber(final String menge) {
    return Rechnungsdoppel.gestellt(ENTWURF, NUMMER, List.of(Rechnungsdoppel.beratung(menge)));
  }

  private void entwurfLiegt(final Rechnung entwurf) {
    when(rechnungen.findByIdMitSperre(ENTWURF)).thenReturn(Optional.of(entwurf));
  }

  private void angabenSind(final Firma firma, final EigeneAngaben eigene) {
    when(angebote.findById(Rechnungsdoppel.ANGEBOT)).thenReturn(Optional.of(angebotMitBeratung()));
    when(firmen.findById(Rechnungsdoppel.FIRMA)).thenReturn(Optional.of(firma));
    when(eigeneAngaben.lies()).thenReturn(eigene);
  }

  private void nummerWirdGezogen(final String muster, final int laufend) {
    when(einstellungen.lies())
        .thenReturn(
            new Rechnungseinstellungen(new Nummernmuster(muster), STEUERSATZ, ZAHLUNGSZIEL));
    when(nummernkreis.ziehe(anyInt())).thenReturn(laufend);
  }

  private void schreibenUndDruckenGelingen() {
    when(rechnungen.saveAndFlush(any())).thenAnswer(aufruf -> aufruf.getArgument(0));
    when(rechnungen.save(any())).thenAnswer(aufruf -> aufruf.getArgument(0));
    when(drucker.drucke(any())).thenReturn(PDF);
    when(dokumente.lege(ENTWURF, PDF)).thenReturn(SCHLUESSEL);
  }

  /**
   * Der ganze Weg: ein stellbarer Entwurf, vollstaendige Angaben, freie Nummer.
   *
   * @param entwurf der Entwurf, der gestellt wird
   * @param danach die Rechnungen, die das Angebot nach dem Festschreiben traegt
   */
  private Rechnung derWegGelingt(final Rechnung entwurf, final List<Rechnung> danach) {
    entwurfLiegt(entwurf);
    angabenSind(Rechnungsdoppel.firma(), Rechnungsdoppel.eigeneAngaben());
    nummerWirdGezogen(MUSTER_MIT_JAHR, 1);
    schreibenUndDruckenGelingen();
    when(rechnungen.findByAngebot(Rechnungsdoppel.ANGEBOT)).thenReturn(danach);
    return useCase.stelle(ENTWURF);
  }

  @Test
  void stelle_withAnUnknownRechnung_thenNotFound() {
    // Given
    when(rechnungen.findByIdMitSperre(ENTWURF)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> useCase.stelle(ENTWURF)).isInstanceOf(RechnungNichtGefunden.class);
    verifyNoInteractions(nummernkreis, drucker, dokumente);
  }

  @Test
  void stelle_atAnAlreadyGestellteRechnung_thenConflict() {
    // Given — eine zweite Nummer auf derselben Rechnung risse ein Loch in den Nummernkreis.
    entwurfLiegt(gestelltUeber("80.00"));

    // When / Then
    assertThatThrownBy(() -> useCase.stelle(ENTWURF))
        .isInstanceOf(RechnungszustandPasstNicht.class);
    verifyNoInteractions(nummernkreis, drucker, dokumente);
  }

  @Test
  void stelle_withoutAPosition_thenUnprocessable() {
    // Given — ein Entwurf ueber nichts ist keine Rechnung (Kriterium 5).
    entwurfLiegt(Rechnungsdoppel.entwurf(ENTWURF, List.of()));

    // When / Then
    assertThatThrownBy(() -> useCase.stelle(ENTWURF)).isInstanceOf(RechnungOhnePosition.class);
    verifyNoInteractions(nummernkreis, drucker, dokumente);
  }

  @Test
  void stelle_whenTheAngebotIsMissing_thenItIsAContradictionInTheBestand() {
    // Given — Angebote werden nie geloescht.
    entwurfLiegt(entwurfUeber("80.00"));
    when(angebote.findById(Rechnungsdoppel.ANGEBOT)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> useCase.stelle(ENTWURF)).isInstanceOf(AngebotNichtGefunden.class);
  }

  @Test
  void stelle_whenTheFirmaIsMissing_thenItIsAContradictionInTheBestand() {
    // Given — Firmen werden nie geloescht, nur stillgelegt.
    entwurfLiegt(entwurfUeber("80.00"));
    when(angebote.findById(Rechnungsdoppel.ANGEBOT)).thenReturn(Optional.of(angebotMitBeratung()));
    when(firmen.findById(Rechnungsdoppel.FIRMA)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> useCase.stelle(ENTWURF)).isInstanceOf(FirmaNichtGefunden.class);
  }

  @Test
  void stelle_withoutTheBankverbindung_thenTheFieldIsNamed() {
    // Given — ohne Bankverbindung kann der Kunde nicht zahlen (Kriterium 13).
    entwurfLiegt(entwurfUeber("80.00"));
    angabenSind(Rechnungsdoppel.firma(), ohneBankverbindung());

    // When
    final PflichtangabenFehlen fehler =
        catchThrowableOfType(PflichtangabenFehlen.class, () -> useCase.stelle(ENTWURF));

    // Then
    assertThat(fehler.felder()).containsOnlyKeys("bankverbindung");
    verifyNoInteractions(nummernkreis, drucker, dokumente);
  }

  @Test
  void stelle_withoutSteuernummerAndUmsatzsteuerId_thenTheSteuernummerIsNamed() {
    // Given — eine von beiden muss auf dem Beleg stehen; welche, bleibt frei.
    entwurfLiegt(entwurfUeber("80.00"));
    angabenSind(Rechnungsdoppel.firma(), mitSteuerangaben(null, null));

    // When
    final PflichtangabenFehlen fehler =
        catchThrowableOfType(PflichtangabenFehlen.class, () -> useCase.stelle(ENTWURF));

    // Then
    assertThat(fehler.felder()).containsOnlyKeys("steuernummer");
  }

  @ParameterizedTest
  @MethodSource("eineVonBeidenSteuerangaben")
  void stelle_withOnlyOneOfTheSteuerangaben_thenTheRechnungIsIssued(final EigeneAngaben eigene) {
    // Given
    entwurfLiegt(entwurfUeber("80.00"));
    angabenSind(Rechnungsdoppel.firma(), eigene);
    nummerWirdGezogen(MUSTER_MIT_JAHR, 1);
    schreibenUndDruckenGelingen();
    when(rechnungen.findByAngebot(Rechnungsdoppel.ANGEBOT))
        .thenReturn(List.of(gestelltUeber("80.00")));

    // When / Then
    assertThat(useCase.stelle(ENTWURF).nummer()).isEqualTo(NUMMER);
  }

  @Test
  void stelle_withoutAnyPflichtangabe_thenEveryMissingFieldIsNamedAtOnce() {
    // Given — alles fehlt: die eigenen Angaben und die Angaben der Firma.
    entwurfLiegt(entwurfUeber("80.00"));
    angabenSind(
        new Firma(
            Long.valueOf(Rechnungsdoppel.FIRMA),
            " ",
            LEER,
            null,
            null,
            true,
            Rechnungsdoppel.ANGELEGT,
            Rechnungsdoppel.ANGELEGT),
        OHNE_ANGABEN);

    // When
    final PflichtangabenFehlen fehler =
        catchThrowableOfType(PflichtangabenFehlen.class, () -> useCase.stelle(ENTWURF));

    // Then — eine Antwort nennt alle Felder, nicht nur das erste.
    assertThat(fehler.felder())
        .containsOnlyKeys(
            "name",
            "strasse",
            "plz",
            "ort",
            "bankverbindung",
            "steuernummer",
            "firma.name",
            "firma.strasse",
            "firma.plz",
            "firma.ort");
  }

  @Test
  void stelle_thenTheNummerIsDrawnFromTheZaehlerjahrOfTheRechnungDatum() {
    // Given — Kriterium 16: das Jahr des Rechnungsdatums zaehlt, nicht das von heute.
    derWegGelingt(entwurfUeber("80.00"), List.of(gestelltUeber("80.00")));

    // Then
    verify(nummernkreis).ziehe(LocalDate.of(2026, 9, 30).getYear());
  }

  @Test
  void stelle_givenAMusterWithoutAJahr_thenOneContinuousKreisCounts() {
    // Given — ohne Jahres-Platzhalter laeuft ein einziger Kreis unter OHNE_JAHR.
    entwurfLiegt(entwurfUeber("80.00"));
    angabenSind(Rechnungsdoppel.firma(), Rechnungsdoppel.eigeneAngaben());
    nummerWirdGezogen(MUSTER_OHNE_JAHR, 3);
    schreibenUndDruckenGelingen();
    when(rechnungen.findByAngebot(Rechnungsdoppel.ANGEBOT))
        .thenReturn(List.of(gestelltUeber("80.00")));

    // When
    final Rechnung gestellt = useCase.stelle(ENTWURF);

    // Then
    verify(nummernkreis).ziehe(Nummernmuster.OHNE_JAHR);
    assertThat(gestellt.nummer()).isEqualTo("R-003");
  }

  @Test
  void stelle_whenTheNummerIsAlreadyTaken_thenConflictBeforeAnyDocument() {
    // Given — ein zurueckgesetzter Zaehler trifft eine Nummer, die schon steht (Kriterium 18).
    entwurfLiegt(entwurfUeber("80.00"));
    angabenSind(Rechnungsdoppel.firma(), Rechnungsdoppel.eigeneAngaben());
    nummerWirdGezogen(MUSTER_MIT_JAHR, 1);
    when(rechnungen.existiertNummer(NUMMER)).thenReturn(true);

    // When / Then — kein Dokument, keine Waise im Speicher.
    assertThatThrownBy(() -> useCase.stelle(ENTWURF))
        .isInstanceOf(RechnungsnummerSchonVergeben.class)
        .hasMessageContaining(NUMMER);
    verifyNoInteractions(drucker, dokumente);
    verify(rechnungen, never()).saveAndFlush(any());
  }

  @Test
  void stelle_whenWritingViolatesTheUniqueNummer_thenConflict() {
    // Given — zwischen Frage und Antwort kann eine zweite Sitzung dieselbe Nummer schreiben.
    entwurfLiegt(entwurfUeber("80.00"));
    angabenSind(Rechnungsdoppel.firma(), Rechnungsdoppel.eigeneAngaben());
    nummerWirdGezogen(MUSTER_MIT_JAHR, 1);
    when(rechnungen.saveAndFlush(any()))
        .thenThrow(new DataIntegrityViolationException("rechnung_nummer_key"));

    // When / Then
    assertThatThrownBy(() -> useCase.stelle(ENTWURF))
        .isInstanceOf(RechnungsnummerSchonVergeben.class)
        .hasMessageContaining(NUMMER);
    verifyNoInteractions(drucker, dokumente);
  }

  @Test
  void stelle_thenNothingIsPrintedBeforeTheGestellteRechnungIsWritten() {
    // Given / When
    derWegGelingt(entwurfUeber("80.00"), List.of(gestelltUeber("80.00")));

    // Then — scheitert der Druck, ist keine Nummer verbraucht.
    final InOrder reihenfolge = inOrder(rechnungen, drucker, dokumente);
    reihenfolge.verify(rechnungen).saveAndFlush(any());
    reihenfolge.verify(drucker).drucke(any());
    reihenfolge.verify(dokumente).lege(anyLong(), any());
    reihenfolge.verify(rechnungen).save(any());
  }

  @Test
  void stelle_thenTheRechnungCarriesTheEinstellungenAndBothCopies() {
    // Given / When
    derWegGelingt(entwurfUeber("80.00"), List.of(gestelltUeber("80.00")));

    // Then — Kriterium 14: Satz, Ziel und die beiden Kopien stehen ab jetzt an der Rechnung.
    verify(rechnungen).saveAndFlush(festgeschrieben.capture());
    assertThat(festgeschrieben.getValue())
        .satisfies(
            rechnung -> assertThat(rechnung.zustand()).isEqualTo(Rechnungszustand.GESTELLT),
            rechnung -> assertThat(rechnung.nummer()).isEqualTo(NUMMER),
            rechnung -> assertThat(rechnung.steuersatz()).isEqualByComparingTo(STEUERSATZ),
            rechnung -> assertThat(rechnung.zahlungszielTage()).isEqualTo(ZAHLUNGSZIEL),
            rechnung -> assertThat(rechnung.gestelltAm()).isEqualTo(JETZT),
            rechnung ->
                assertThat(rechnung.empfaenger())
                    .isEqualTo(
                        new Belegempfaenger(
                            Rechnungsdoppel.FIRMENNAME, Rechnungsdoppel.firma().anschrift(), null)),
            rechnung ->
                assertThat(rechnung.absender())
                    .isEqualTo(
                        new Belegabsender(
                            "Manfred Wolff",
                            "Softwarearchitekt",
                            Rechnungsdoppel.eigeneAngaben().anschrift(),
                            "post@example.org",
                            "0421 123456",
                            "75/123/45678",
                            "DE123456789",
                            "DE02 1203 0000 0000 2020 51",
                            "https://example.org")));
  }

  @Test
  void stelle_thenTheRechnungCarriesTheKeyOfItsDocument() {
    // Given / When
    final Rechnung gestellt = derWegGelingt(entwurfUeber("80.00"), List.of(gestelltUeber("80.00")));

    // Then
    assertThat(gestellt.pdfSchluessel()).isEqualTo(SCHLUESSEL);
  }

  @Test
  void stelle_whenNothingIsOpenInGestellteRechnungen_thenTheAngebotIsAbgerechnet() {
    // Given — die gestellten Rechnungen decken die angebotenen 160 Stunden (Kriterium 27).
    derWegGelingt(entwurfUeber("160.00"), List.of(gestelltUeber("160.00")));

    // Then
    verify(angebote).save(fortgeschrieben.capture());
    assertThat(fortgeschrieben.getValue().status()).isEqualTo(Angebotsstatus.ABGERECHNET);
  }

  @Test
  void stelle_whenOnlyAnEntwurfCarriesTheRest_thenTheAngebotKeepsItsStatus() {
    // Given — 80 gestellt, 80 im zweiten Entwurf: Entwuerfe zaehlen fuer den Sprung nicht mit.
    derWegGelingt(
        entwurfUeber("80.00"),
        List.of(
            gestelltUeber("80.00"),
            Rechnungsdoppel.entwurf(9L, List.of(Rechnungsdoppel.beratung("80.00")))));

    // Then
    verify(angebote, never()).save(any());
  }
}
