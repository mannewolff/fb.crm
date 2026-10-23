package org.mwolff.fbcrm.rechnung.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.angebot.application.AngebotNichtGefunden;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.angebot.domain.Angebotsposition;
import org.mwolff.fbcrm.angebot.domain.Angebotsstatus;
import org.mwolff.fbcrm.common.Bearbeitungskonflikt;
import org.mwolff.fbcrm.rechnung.domain.Rechnung;
import org.mwolff.fbcrm.rechnung.domain.RechnungRepository;
import org.mwolff.fbcrm.rechnung.domain.Rechnungsposition;

/**
 * Das Aendern eines Rechnungsentwurfs (#160, Kriterien 4, 5, 9, 10, 12).
 *
 * <p>Geaendert werden Rechnungsdatum, Leistungszeitraum und je Angebotsposition Text und Menge. Der
 * Einzelpreis ist an der Rechnung nicht aenderbar (Frage 5): Er bleibt der des Entwurfs, und eine
 * spaetere Preisaenderung am Angebot gilt erst fuer eine Position, die der Entwurf noch nicht trug
 * (Frage 15). Eine Menge 0 laesst die Position wegfallen (Kriterium 5); mehr abzurechnen als offen
 * ist bleibt erlaubt (Frage 4). Eine gestellte Rechnung wird abgewiesen (Kriterium 14).
 *
 * <p>Dazu der Schutz vor dem alten Stand (#411, Kriterium 1; Plan #415, E1, E14): Wer auf einem
 * Stand speichert, den es nicht mehr gibt, wird vor jeder fachlichen Pruefung abgewiesen, und ein
 * gelungenes Speichern traegt das handelnde Konto als Urheber.
 */
@ExtendWith(MockitoExtension.class)
class RechnungAendernUseCaseTest {

  private static final long ENTWURF = 2L;
  private static final Instant JETZT = Instant.parse("2026-10-05T09:00:00Z");
  private static final LocalDate NEUES_DATUM = LocalDate.of(2026, 10, 5);

  /** Der Stand, auf dem Manfred den Entwurf geoeffnet hat — der des frischen Entwurfs. */
  private static final long GELESEN = 0L;

  /** Das handelnde Konto. */
  private static final long MANFRED = 31L;

  /** Das Konto, das inzwischen geaendert hat. */
  private static final long ANNA = 30L;

  private static final Instant ANNAS_AENDERUNG = Instant.parse("2026-10-05T08:42:00Z");

  private final Clock uhr = Clock.fixed(JETZT, ZoneOffset.UTC);

  @Mock private RechnungRepository rechnungen;
  @Mock private AngebotRepository angebote;

  @Captor private ArgumentCaptor<Rechnung> geschrieben;

  private RechnungAendernUseCase useCase() {
    return new RechnungAendernUseCase(rechnungen, angebote, uhr);
  }

  private static RechnungDaten daten(final Abrechnungsangabe... angaben) {
    return new RechnungDaten(NEUES_DATUM, "Oktober 2026", List.of(angaben), GELESEN, MANFRED);
  }

  private static Abrechnungsangabe angabe(final long positionId, final String menge) {
    return new Abrechnungsangabe(positionId, "KI-Beratung am 15.09.2026", new BigDecimal(menge));
  }

  private void gegebenerEntwurf(final List<Rechnungsposition> positionen) {
    when(rechnungen.findById(ENTWURF))
        .thenReturn(Optional.of(Rechnungsdoppel.entwurf(ENTWURF, positionen)));
    when(angebote.findById(Rechnungsdoppel.ANGEBOT))
        .thenReturn(Optional.of(Rechnungsdoppel.angebot()));
  }

  private Rechnung geschriebeneRechnung() {
    verify(rechnungen).save(geschrieben.capture());
    return geschrieben.getValue();
  }

  @Test
  void aendere_withAnUnknownRechnung_thenNotFound() {
    // Given
    when(rechnungen.findById(ENTWURF)).thenReturn(Optional.empty());

    final RechnungAendernUseCase useCase = useCase();
    final RechnungDaten daten = daten();

    // When / Then
    assertThatThrownBy(() -> useCase.aendere(ENTWURF, daten))
        .isInstanceOf(RechnungNichtGefunden.class);
  }

  @Test
  void aendere_aGestellteRechnung_thenItIsRejectedWithoutTouchingTheAngebot() {
    // Given
    when(rechnungen.findById(ENTWURF))
        .thenReturn(
            Optional.of(
                Rechnungsdoppel.gestellt(
                    ENTWURF, "R26-0001", List.of(Rechnungsdoppel.beratung("80.00")))));

    final RechnungAendernUseCase useCase = useCase();
    final RechnungDaten daten = daten(angabe(Rechnungsdoppel.BERATUNG_ID, "10.00"));

    // When / Then
    assertThatThrownBy(() -> useCase.aendere(ENTWURF, daten))
        .isInstanceOf(RechnungszustandPasstNicht.class);
    verify(rechnungen, never()).save(any());
  }

  @Test
  void aendere_whenTheAngebotIsMissing_thenItIsAContradictionInTheBestand() {
    // Given
    when(rechnungen.findById(ENTWURF))
        .thenReturn(
            Optional.of(
                Rechnungsdoppel.entwurf(ENTWURF, List.of(Rechnungsdoppel.beratung("80.00")))));
    when(angebote.findById(Rechnungsdoppel.ANGEBOT)).thenReturn(Optional.empty());

    final RechnungAendernUseCase useCase = useCase();
    final RechnungDaten daten = daten(angabe(Rechnungsdoppel.BERATUNG_ID, "10.00"));

    // When / Then
    assertThatThrownBy(() -> useCase.aendere(ENTWURF, daten))
        .isInstanceOf(AngebotNichtGefunden.class);
  }

  @Test
  void aendere_withANegativeMenge_thenItIsRejectedBeforeAnythingIsWritten() {
    // Given
    gegebenerEntwurf(List.of(Rechnungsdoppel.beratung("80.00")));

    final RechnungAendernUseCase useCase = useCase();
    final RechnungDaten daten = daten(angabe(Rechnungsdoppel.BERATUNG_ID, "-1.00"));

    // When / Then — die Maske liest die Meldung am Feld der Positionsliste.
    assertThatThrownBy(() -> useCase.aendere(ENTWURF, daten))
        .isInstanceOf(AbrechnungsangabenNichtWaehlbar.class)
        .asInstanceOf(InstanceOfAssertFactories.type(AbrechnungsangabenNichtWaehlbar.class))
        .extracting(AbrechnungsangabenNichtWaehlbar::felder)
        .isEqualTo(
            Map.of(
                AbrechnungsangabenNichtWaehlbar.FELD,
                List.of(AbrechnungsangabenNichtWaehlbar.MELDUNG)));
    verify(rechnungen, never()).save(any());
  }

  @Test
  void aendere_withAPositionOfAnotherAngebot_thenItIsRejected() {
    // Given
    gegebenerEntwurf(List.of(Rechnungsdoppel.beratung("80.00")));

    final RechnungAendernUseCase useCase = useCase();
    final RechnungDaten daten = daten(angabe(Rechnungsdoppel.FREMDE_POSITION, "5.00"));

    // When / Then
    assertThatThrownBy(() -> useCase.aendere(ENTWURF, daten))
        .isInstanceOf(AbrechnungsangabenNichtWaehlbar.class);
    verify(rechnungen, never()).save(any());
  }

  @Test
  void aendere_withMengeZero_thenThePositionFallsAway() {
    // Given — beide Positionen stehen im Entwurf, die Pauschale wird auf 0 gesetzt.
    gegebenerEntwurf(List.of(Rechnungsdoppel.beratung("80.00"), Rechnungsdoppel.pauschale("1")));

    // When
    useCase()
        .aendere(
            ENTWURF,
            daten(
                angabe(Rechnungsdoppel.BERATUNG_ID, "80.00"),
                angabe(Rechnungsdoppel.PAUSCHALE_ID, "0")));

    // Then
    assertThat(geschriebeneRechnung().positionen())
        .extracting(Rechnungsposition::angebotPositionId)
        .containsExactly(Rechnungsdoppel.BERATUNG_ID);
  }

  @Test
  void aendere_thenTheEinzelpreisOfTheEntwurfStaysEvenWhenTheAngebotChangedIts() {
    // Given — der Entwurf traegt 100,00 €, das Angebot inzwischen 120,00 €.
    when(rechnungen.findById(ENTWURF))
        .thenReturn(
            Optional.of(
                Rechnungsdoppel.entwurf(
                    ENTWURF,
                    List.of(Rechnungsdoppel.beratung("80.00", new BigDecimal("100.00"))))));
    when(angebote.findById(Rechnungsdoppel.ANGEBOT)).thenReturn(Optional.of(umgepreistesAngebot()));

    // When
    useCase().aendere(ENTWURF, daten(angabe(Rechnungsdoppel.BERATUNG_ID, "90.00")));

    // Then
    assertThat(geschriebeneRechnung().positionen().getFirst().einzelpreis())
        .isEqualByComparingTo("100.00");
  }

  @Test
  void aendere_withAPositionTheEntwurfDidNotCarry_thenThePreisOfTheAngebotAppliesNow() {
    // Given — der Entwurf traegt nur die Beratung; die Pauschale kommt jetzt dazu.
    gegebenerEntwurf(List.of(Rechnungsdoppel.beratung("80.00")));

    // When
    useCase()
        .aendere(
            ENTWURF,
            daten(
                angabe(Rechnungsdoppel.BERATUNG_ID, "80.00"),
                angabe(Rechnungsdoppel.PAUSCHALE_ID, "1")));

    // Then
    assertThat(geschriebeneRechnung().positionen())
        .last()
        .satisfies(
            position ->
                assertThat(position.einzelpreis())
                    .isEqualByComparingTo(Rechnungsdoppel.PAUSCHALPREIS),
            position ->
                assertThat(position.einheit()).isEqualTo(Rechnungsdoppel.PAUSCHALE.einheit()));
  }

  @Test
  void aendere_withMoreThanIsOffen_thenItIsAllowed() {
    // Given — 200 von 160 angebotenen Stunden (Frage 4 aus #160).
    gegebenerEntwurf(List.of(Rechnungsdoppel.beratung("80.00")));

    // When
    useCase().aendere(ENTWURF, daten(angabe(Rechnungsdoppel.BERATUNG_ID, "200.00")));

    // Then
    assertThat(geschriebeneRechnung().positionen().getFirst().menge())
        .isEqualByComparingTo("200.00");
  }

  @Test
  void aendere_thenAnswersTheEntwurfAsTheBestandWroteIt() {
    // Given
    gegebenerEntwurf(List.of(Rechnungsdoppel.beratung("80.00")));
    final Rechnung gespeichert =
        Rechnungsdoppel.entwurf(ENTWURF, List.of(Rechnungsdoppel.beratung("90.00")));
    when(rechnungen.save(any())).thenReturn(gespeichert);

    // When
    final Rechnung geaendert =
        useCase().aendere(ENTWURF, daten(angabe(Rechnungsdoppel.BERATUNG_ID, "90.00")));

    // Then
    assertThat(geaendert).isSameAs(gespeichert);
  }

  @Test
  void aendere_thenDatumLeistungszeitraumAndTextAreTaken() {
    // Given
    gegebenerEntwurf(List.of(Rechnungsdoppel.beratung("80.00")));

    // When
    useCase().aendere(ENTWURF, daten(angabe(Rechnungsdoppel.BERATUNG_ID, "80.00")));

    // Then
    assertThat(geschriebeneRechnung())
        .satisfies(
            rechnung -> assertThat(rechnung.rechnungDatum()).isEqualTo(NEUES_DATUM),
            rechnung -> assertThat(rechnung.leistungszeitraum()).isEqualTo("Oktober 2026"),
            rechnung -> assertThat(rechnung.updatedAt()).isEqualTo(JETZT),
            rechnung -> assertThat(rechnung.geaendertVon()).isEqualTo(MANFRED),
            rechnung ->
                assertThat(rechnung.positionen().getFirst().bezeichnung())
                    .isEqualTo("KI-Beratung am 15.09.2026"));
  }

  @Test
  void aendere_givenTheCurrentStand_thenKeepsTheStandForTheRepository() {
    // Given — #411, Kriterium 8: auf dem aktuellen Stand merkt man vom Schutz nichts.
    when(rechnungen.findById(ENTWURF))
        .thenReturn(
            Optional.of(
                Rechnungsdoppel.aufStand(
                    Rechnungsdoppel.entwurf(ENTWURF, List.of(Rechnungsdoppel.beratung("80.00"))),
                    2L,
                    ANNA,
                    ANNAS_AENDERUNG)));
    when(angebote.findById(Rechnungsdoppel.ANGEBOT))
        .thenReturn(Optional.of(Rechnungsdoppel.angebot()));
    final RechnungDaten daten =
        new RechnungDaten(
            NEUES_DATUM,
            "Oktober 2026",
            List.of(angabe(Rechnungsdoppel.BERATUNG_ID, "60.00")),
            2L,
            MANFRED);

    // When
    useCase().aendere(ENTWURF, daten);

    // Then
    assertThat(geschriebeneRechnung())
        .satisfies(
            rechnung -> assertThat(rechnung.version()).isEqualTo(2L),
            rechnung -> assertThat(rechnung.geaendertVon()).isEqualTo(MANFRED));
  }

  @Test
  void aendere_givenAnOutdatedStand_thenRejectsWithTheConflictBeforeAnyCheck() {
    // Given — Anna hat den Entwurf inzwischen gestellt; der Konflikt kommt vor dem Zustand.
    when(rechnungen.findById(ENTWURF))
        .thenReturn(
            Optional.of(
                Rechnungsdoppel.aufStand(
                    Rechnungsdoppel.gestellt(
                        ENTWURF, "R26-0001", List.of(Rechnungsdoppel.beratung("80.00"))),
                    1L,
                    ANNA,
                    ANNAS_AENDERUNG)));
    final RechnungAendernUseCase useCase = useCase();
    final RechnungDaten daten = daten(angabe(Rechnungsdoppel.BERATUNG_ID, "10.00"));

    // When / Then
    assertThatThrownBy(() -> useCase.aendere(ENTWURF, daten))
        .asInstanceOf(InstanceOfAssertFactories.type(RechnungGeaendert.class))
        .satisfies(
            konflikt -> assertThat(konflikt.art()).isEqualTo(Bearbeitungskonflikt.Art.VERALTET),
            konflikt -> assertThat(konflikt.urheberKonto()).hasValue(ANNA),
            konflikt -> assertThat(konflikt.handelndesKonto()).isEqualTo(MANFRED),
            konflikt -> assertThat(konflikt.zeitpunkt()).hasValue(ANNAS_AENDERUNG));
    verify(rechnungen).findById(ENTWURF);
    verifyNoMoreInteractions(rechnungen);
    verifyNoInteractions(angebote);
  }

  @Test
  void aendere_givenADeletedEntwurf_thenRejectsAsGeloeschtAndWritesNothing() {
    // Given — #411, Kriterium 6: Anna hat den Entwurf inzwischen geloescht.
    when(rechnungen.findById(ENTWURF))
        .thenReturn(Optional.of(Rechnungsdoppel.geloescht(ENTWURF, ANNA, ANNAS_AENDERUNG)));
    final RechnungAendernUseCase useCase = useCase();
    final RechnungDaten daten = daten(angabe(Rechnungsdoppel.BERATUNG_ID, "10.00"));

    // When / Then — der vorgelegte Stand ist der der Zeile; abgewiesen wird wegen der Loeschung.
    assertThatThrownBy(() -> useCase.aendere(ENTWURF, daten))
        .asInstanceOf(InstanceOfAssertFactories.type(RechnungGeloescht.class))
        .satisfies(
            konflikt -> assertThat(konflikt.art()).isEqualTo(Bearbeitungskonflikt.Art.GELOESCHT),
            konflikt -> assertThat(konflikt.urheberKonto()).hasValue(ANNA),
            konflikt -> assertThat(konflikt.handelndesKonto()).isEqualTo(MANFRED),
            konflikt -> assertThat(konflikt.zeitpunkt()).hasValue(ANNAS_AENDERUNG));
    verify(rechnungen).findById(ENTWURF);
    verifyNoMoreInteractions(rechnungen);
    verifyNoInteractions(angebote);
  }

  private static Angebot umgepreistesAngebot() {
    return Rechnungsdoppel.angebot(
        Angebotsstatus.BESTELLT,
        List.of(
            new Angebotsposition(
                Long.valueOf(Rechnungsdoppel.BERATUNG_ID),
                "Beratung",
                Rechnungsdoppel.BERATUNG.abrechnungsmodus(),
                new BigDecimal("160.00"),
                Rechnungsdoppel.BERATUNG.einheit(),
                new BigDecimal("120.00"),
                Year.of(2026))));
  }
}
