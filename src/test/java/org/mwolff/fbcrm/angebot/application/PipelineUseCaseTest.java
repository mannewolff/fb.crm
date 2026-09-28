package org.mwolff.fbcrm.angebot.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.angebot.domain.Angebotsposition;
import org.mwolff.fbcrm.angebot.domain.Angebotszustand;
import org.mwolff.fbcrm.common.Abrechnungsmodus;
import org.mwolff.fbcrm.common.Anschrift;
import org.mwolff.fbcrm.common.Einheit;
import org.mwolff.fbcrm.firma.domain.Firma;
import org.mwolff.fbcrm.firma.domain.FirmaRepository;
import org.mwolff.fbcrm.vorgang.domain.Vorgang;
import org.mwolff.fbcrm.vorgang.domain.VorgangRepository;

/**
 * Die Pipeline: die offenen Angebote mit ihrer gewichteten Summe (Kriterien 23, 24, 26).
 *
 * <p>Zwei Aussagen sind hier der Gegenstand. Die <b>Auswahl</b>: Gezaehlt wird nur, was heute noch
 * auf eine Entscheidung wartet — jeder der sechs Ausschlussgruende hat seinen Fall, und die
 * Gueltigkeit wird an ihren beiden Grenzen geprueft. Und die <b>Rechnung</b>: Gewichtet wird je
 * Angebot und auf Cent gerundet, addiert wird erst danach (E20, Kriterium 5); ein Vorgang ohne
 * Einschaetzung zaehlt mit 50 % und traegt trotzdem keine Wahrscheinlichkeit (F2).
 *
 * <p>Die Auswahl steht im Anwendungsfall und nicht im Bestand: „abgelaufen" ist abgeleitet (E4),
 * und nur hier ist sie ohne Datenbank pruefbar. Der Bestand grenzt die Menge ein, er entscheidet
 * sie nicht — darum reicht dieser Test dem Anwendungsfall auch Angebote, die eine Einschraenkung
 * der Abfrage schon draussen gehalten haette.
 */
@ExtendWith(MockitoExtension.class)
class PipelineUseCaseTest {

  private static final Instant JETZT = Instant.parse("2026-09-27T08:00:00Z");
  private static final LocalDate HEUTE = LocalDate.of(2026, 9, 27);
  private static final LocalDate MORGEN = HEUTE.plusDays(1);
  private static final LocalDate GESTERN = HEUTE.minusDays(1);
  private static final LocalDate ENTSCHEIDUNG = LocalDate.of(2026, 10, 15);
  private static final LocalDate SPAETER = LocalDate.of(2026, 11, 30);

  private static final long VORGANG = 3L;
  private static final long ZWEITER_VORGANG = 4L;
  private static final long FIRMA = 7L;
  private static final long ZWEITE_FIRMA = 8L;

  @Mock private AngebotRepository angebote;
  @Mock private VorgangRepository vorgaenge;
  @Mock private FirmaRepository firmen;

  @Captor private ArgumentCaptor<Collection<Long>> gefragteVorgaenge;

  private PipelineUseCase useCase;

  @BeforeEach
  void baueDenAnwendungsfall() {
    useCase = new PipelineUseCase(angebote, vorgaenge, firmen, Clock.fixed(JETZT, ZoneOffset.UTC));
  }

  private static Angebotsposition pauschal(final String einzelpreis) {
    return new Angebotsposition(
        "Leistung",
        Abrechnungsmodus.FESTPREIS,
        BigDecimal.ONE,
        Einheit.PAUSCHAL,
        new BigDecimal(einzelpreis));
  }

  private static Angebot angebot(
      final long id,
      final long vorgangId,
      final Angebotszustand zustand,
      final LocalDate gueltigBis,
      final String einzelpreis) {
    return Angebotsdoppel.angebot(
        id,
        vorgangId,
        zustand,
        List.of(pauschal(einzelpreis)),
        Angebotsdoppel.ANGELEGT,
        gueltigBis);
  }

  private static Angebot versendet(final long id, final long vorgangId, final String einzelpreis) {
    return angebot(id, vorgangId, Angebotszustand.VERSENDET, MORGEN, einzelpreis);
  }

  private static Vorgang vorgang(
      final long id,
      final long firmaId,
      final Integer wahrscheinlichkeit,
      final LocalDate entscheidungErwartetAm,
      final boolean abgeschlossen) {
    return new Vorgang(
        Long.valueOf(id),
        10L + id,
        "Vorgang " + id,
        firmaId,
        null,
        wahrscheinlichkeit,
        entscheidungErwartetAm,
        abgeschlossen,
        Angebotsdoppel.ANGELEGT,
        Angebotsdoppel.ANGELEGT);
  }

  private static Vorgang offenerVorgang(final long id, final Integer wahrscheinlichkeit) {
    return vorgang(id, FIRMA, wahrscheinlichkeit, ENTSCHEIDUNG, false);
  }

  private static Firma firma(final long id, final String name) {
    return new Firma(
        Long.valueOf(id),
        name,
        new Anschrift("Am Wall 1", "28195", "Bremen", "Deutschland"),
        null,
        null,
        true,
        Angebotsdoppel.ANGELEGT,
        Angebotsdoppel.ANGELEGT);
  }

  private void imBestand(
      final List<Angebot> offen, final List<Vorgang> zu, final Firma... bekannte) {
    when(angebote.pipelinekandidaten(HEUTE)).thenReturn(offen);
    when(vorgaenge.findByIds(any())).thenReturn(zu);
    for (final Firma eine : bekannte) {
      when(firmen.findById(eine.requireId())).thenReturn(Optional.of(eine));
    }
  }

  private List<Long> kennungen(final Pipeline pipeline) {
    return pipeline.zeilen().stream().map(zeile -> Long.valueOf(zeile.angebotId())).toList();
  }

  @Test
  void pipeline_thenCarriesEveryFieldOfTheRow() {
    // Given — Kriterium 23: Vorgang, Firma, Summe, Wahrscheinlichkeit und beide Betraege.
    imBestand(
        List.of(versendet(11L, VORGANG, "1000.00")),
        List.of(offenerVorgang(VORGANG, Integer.valueOf(60))),
        firma(FIRMA, "Adler AG"));

    // When
    final Pipeline pipeline = useCase.pipeline();

    // Then
    assertThat(pipeline.zeilen())
        .containsExactly(
            new PipelineZeile(
                11L,
                "A-2026-011",
                VORGANG,
                13L,
                "Vorgang 3",
                "Adler AG",
                new BigDecimal("1000.00"),
                Integer.valueOf(60),
                new BigDecimal("600.00"),
                ENTSCHEIDUNG));
  }

  @Test
  void pipeline_thenAnswersWithBothTotals() {
    // Given — Kriterium 24: oben die ungewichtete und die gewichtete Summe.
    imBestand(
        List.of(versendet(11L, VORGANG, "1000.00"), versendet(12L, ZWEITER_VORGANG, "500.00")),
        List.of(
            offenerVorgang(VORGANG, Integer.valueOf(60)),
            offenerVorgang(ZWEITER_VORGANG, Integer.valueOf(20))),
        firma(FIRMA, "Adler AG"));

    // When
    final Pipeline pipeline = useCase.pipeline();

    // Then
    assertThat(pipeline.summe()).isEqualTo(new BigDecimal("1500.00"));
    assertThat(pipeline.gewichteteSumme()).isEqualTo(new BigDecimal("700.00"));
  }

  @Test
  void pipeline_givenAVorgangWithoutAnEstimate_thenCountsHalfAndLeavesTheRowUnestimated() {
    // Given — F2: der Ersatzwert 50 % gilt nur fuer die Gewichtung.
    imBestand(
        List.of(versendet(11L, VORGANG, "1000.00")),
        List.of(offenerVorgang(VORGANG, null)),
        firma(FIRMA, "Adler AG"));

    // When
    final Pipeline pipeline = useCase.pipeline();

    // Then
    assertThat(pipeline.zeilen())
        .singleElement()
        .satisfies(
            zeile -> assertThat(zeile.wahrscheinlichkeit()).isNull(),
            zeile -> assertThat(zeile.gewichteteSumme()).isEqualTo(new BigDecimal("500.00")));
    assertThat(pipeline.gewichteteSumme()).isEqualTo(new BigDecimal("500.00"));
  }

  @Test
  void pipeline_thenRoundsEveryOfferBeforeAddingThem() {
    // Given — E20: je Angebot 50,025 €; einzeln gerundet ergeben zwei Zeilen 100,06 €, die
    // Rundung der Gesamtsumme dagegen 100,05 € — und genau das schliesst Kriterium 5 aus.
    imBestand(
        List.of(versendet(11L, VORGANG, "100.05"), versendet(12L, ZWEITER_VORGANG, "100.05")),
        List.of(offenerVorgang(VORGANG, null), offenerVorgang(ZWEITER_VORGANG, null)),
        firma(FIRMA, "Adler AG"));

    // When
    final Pipeline pipeline = useCase.pipeline();

    // Then
    assertThat(pipeline.zeilen())
        .extracting(PipelineZeile::gewichteteSumme)
        .containsExactly(new BigDecimal("50.03"), new BigDecimal("50.03"));
    assertThat(pipeline.gewichteteSumme()).isEqualTo(new BigDecimal("100.06"));
    assertThat(pipeline.summe()).isEqualTo(new BigDecimal("200.10"));
  }

  @Test
  void pipeline_givenAClosedVorgang_thenLeavesItsOfferOut() {
    // Given — Kriterium 23: am abgeschlossenen Vorgang zaehlt kein Angebot mehr.
    imBestand(
        List.of(versendet(11L, VORGANG, "1000.00")),
        List.of(vorgang(VORGANG, FIRMA, Integer.valueOf(60), ENTSCHEIDUNG, true)));

    // When / Then
    assertThat(useCase.pipeline()).isEqualTo(Pipeline.of(List.of()));
    verify(firmen, never()).findById(anyLong());
  }

  @ParameterizedTest
  @EnumSource(
      value = Angebotszustand.class,
      names = {"ENTWURF", "ANGENOMMEN", "ABGELEHNT", "ABGELOEST"})
  void pipeline_givenAnOfferThatAwaitsNoDecision_thenLeavesItOut(final Angebotszustand zustand) {
    // Given — Kriterium 23: offen ist allein das versendete Angebot.
    imBestand(
        List.of(angebot(11L, VORGANG, zustand, MORGEN, "1000.00")),
        List.of(offenerVorgang(VORGANG, Integer.valueOf(60))));

    // When / Then
    assertThat(useCase.pipeline().zeilen()).isEmpty();
  }

  @Test
  void pipeline_givenValidityEndsToday_thenTheOfferCounts() {
    // Given — E4: der letzte Tag der Gueltigkeit zaehlt noch mit.
    imBestand(
        List.of(angebot(11L, VORGANG, Angebotszustand.VERSENDET, HEUTE, "1000.00")),
        List.of(offenerVorgang(VORGANG, Integer.valueOf(60))),
        firma(FIRMA, "Adler AG"));

    // When / Then
    assertThat(kennungen(useCase.pipeline())).containsExactly(Long.valueOf(11L));
  }

  @Test
  void pipeline_givenValidityEndedYesterday_thenTheOfferIsLeftOut() {
    // Given — Kriterium 23: ein abgelaufenes Angebot zaehlt nicht mehr (E4).
    imBestand(
        List.of(angebot(11L, VORGANG, Angebotszustand.VERSENDET, GESTERN, "1000.00")),
        List.of(offenerVorgang(VORGANG, Integer.valueOf(60))));

    // When / Then
    assertThat(useCase.pipeline().zeilen()).isEmpty();
  }

  @Test
  void pipeline_givenNothingOpen_thenTwoZeroTotalsAndNoRow() {
    // Given — daran erkennt die Oberflaeche „keine offene Chance".
    when(angebote.pipelinekandidaten(HEUTE)).thenReturn(List.of());
    when(vorgaenge.findByIds(any())).thenReturn(List.of());

    // When
    final Pipeline pipeline = useCase.pipeline();

    // Then
    assertThat(pipeline.zeilen()).isEmpty();
    assertThat(pipeline.summe()).isEqualTo(new BigDecimal("0.00"));
    assertThat(pipeline.gewichteteSumme()).isEqualTo(new BigDecimal("0.00"));
  }

  @Test
  void pipeline_thenSortsByTheExpectedDecisionAndPutsUndatedRowsLast() {
    // Given — die Pipeline beantwortet „was kommt als naechstes".
    imBestand(
        List.of(
            versendet(11L, VORGANG, "1000.00"),
            versendet(12L, ZWEITER_VORGANG, "1000.00"),
            versendet(13L, 5L, "1000.00")),
        List.of(
            vorgang(VORGANG, FIRMA, Integer.valueOf(60), null, false),
            vorgang(ZWEITER_VORGANG, FIRMA, Integer.valueOf(60), SPAETER, false),
            vorgang(5L, FIRMA, Integer.valueOf(60), ENTSCHEIDUNG, false)),
        firma(FIRMA, "Adler AG"));

    // When / Then
    assertThat(kennungen(useCase.pipeline()))
        .containsExactly(Long.valueOf(13L), Long.valueOf(12L), Long.valueOf(11L));
  }

  @Test
  void pipeline_givenTheSameDate_thenTheBiggerChanceComesFirst() {
    // Given — bei gleichem Datum entscheidet die gewichtete Summe absteigend.
    imBestand(
        List.of(versendet(11L, VORGANG, "1000.00"), versendet(12L, ZWEITER_VORGANG, "1000.00")),
        List.of(
            offenerVorgang(VORGANG, Integer.valueOf(20)),
            offenerVorgang(ZWEITER_VORGANG, Integer.valueOf(80))),
        firma(FIRMA, "Adler AG"));

    // When / Then
    assertThat(kennungen(useCase.pipeline())).containsExactly(Long.valueOf(12L), Long.valueOf(11L));
  }

  @Test
  void pipeline_givenTheSameDateAndChance_thenTheHigherIdComesFirst() {
    // Given — der Tiebreak, damit zwei Aufrufe dieselbe Liste liefern.
    imBestand(
        List.of(versendet(11L, VORGANG, "1000.00"), versendet(12L, ZWEITER_VORGANG, "1000.00")),
        List.of(
            offenerVorgang(VORGANG, Integer.valueOf(60)),
            offenerVorgang(ZWEITER_VORGANG, Integer.valueOf(60))),
        firma(FIRMA, "Adler AG"));

    // When / Then
    assertThat(kennungen(useCase.pipeline())).containsExactly(Long.valueOf(12L), Long.valueOf(11L));
  }

  @Test
  void pipeline_thenAsksForEveryVorgangInOneQuery() {
    // Given — ein findById je Zeile waere die bekannte Abfrage-Lawine.
    imBestand(
        List.of(versendet(11L, VORGANG, "1000.00"), versendet(12L, VORGANG, "1000.00")),
        List.of(offenerVorgang(VORGANG, Integer.valueOf(60))),
        firma(FIRMA, "Adler AG"));

    // When
    useCase.pipeline();

    // Then — jede Kennung genau einmal, auch wenn zwei Angebote am selben Vorgang haengen.
    verify(vorgaenge).findByIds(gefragteVorgaenge.capture());
    assertThat(gefragteVorgaenge.getValue()).containsExactly(Long.valueOf(VORGANG));
  }

  @Test
  void pipeline_givenTwoOffersOfTheSameFirma_thenAsksForItsNameOnce() {
    // Given — gemerkt statt je Zeile geholt, wie in der Vorgangsliste.
    imBestand(
        List.of(versendet(11L, VORGANG, "1000.00"), versendet(12L, ZWEITER_VORGANG, "1000.00")),
        List.of(
            offenerVorgang(VORGANG, Integer.valueOf(60)),
            offenerVorgang(ZWEITER_VORGANG, Integer.valueOf(60))),
        firma(FIRMA, "Adler AG"));

    // When
    useCase.pipeline();

    // Then
    verify(firmen).findById(FIRMA);
  }

  @Test
  void pipeline_givenTwoFirmen_thenEveryRowCarriesItsOwnName() {
    // Given — der gemerkte Name darf nicht auf die fremde Zeile durchschlagen.
    imBestand(
        List.of(versendet(11L, VORGANG, "1000.00"), versendet(12L, ZWEITER_VORGANG, "1000.00")),
        List.of(
            vorgang(VORGANG, FIRMA, Integer.valueOf(60), ENTSCHEIDUNG, false),
            vorgang(ZWEITER_VORGANG, ZWEITE_FIRMA, Integer.valueOf(60), SPAETER, false)),
        firma(FIRMA, "Adler AG"),
        firma(ZWEITE_FIRMA, "Biber GmbH"));

    // When / Then
    assertThat(useCase.pipeline().zeilen())
        .extracting(PipelineZeile::firma)
        .containsExactly("Adler AG", "Biber GmbH");
  }

  @Test
  void pipeline_whenTheVorgangOfAnOfferIsMissing_thenFailsLoudly() {
    // Given — der Fremdschluessel angebot.vorgang_id schliesst das aus; traete es ein, waere der
    // Bestand kaputt, und dann ist ein lauter Fehler die richtige Antwort.
    when(angebote.pipelinekandidaten(HEUTE))
        .thenReturn(List.of(versendet(11L, VORGANG, "1000.00")));
    when(vorgaenge.findByIds(any())).thenReturn(List.of());

    // When / Then
    assertThatThrownBy(() -> useCase.pipeline())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(String.valueOf(VORGANG));
  }

  @Test
  void pipeline_whenTheFirmaOfAVorgangIsMissing_thenFailsLoudly() {
    // Given — derselbe Grund fuer den Fremdschluessel vorgang.firma_id.
    when(angebote.pipelinekandidaten(HEUTE))
        .thenReturn(List.of(versendet(11L, VORGANG, "1000.00")));
    when(vorgaenge.findByIds(any()))
        .thenReturn(List.of(offenerVorgang(VORGANG, Integer.valueOf(60))));
    when(firmen.findById(FIRMA)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> useCase.pipeline())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(String.valueOf(FIRMA));
  }
}
