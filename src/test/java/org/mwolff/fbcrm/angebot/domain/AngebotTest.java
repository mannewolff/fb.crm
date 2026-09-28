package org.mwolff.fbcrm.angebot.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mwolff.fbcrm.angebot.application.AngebotNichtAenderbar;
import org.mwolff.fbcrm.common.Abrechnungsmodus;
import org.mwolff.fbcrm.common.Anschrift;
import org.mwolff.fbcrm.common.Einheit;

/**
 * Das Angebot als Fachobjekt: die Rechenregel aus Kriterium 5, der Anzeigestand aus Kriterium 18
 * und die Zustandsmaschine aus Kriterium 17.
 *
 * <p>Drei Zusagen tragen diese Klasse. Erstens: Die Summe ist die Summe der <b>gerundeten</b>
 * Positionsbetraege und nicht die gerundete Summe der ungerundeten — deshalb stehen unten zwei
 * Positionen, deren Einzelrundungen sich zu einem anderen Wert addieren als die Rundung ihrer
 * Summe. Zweitens: „abgelaufen" ist kein Zustand, sondern ein VERSENDETes Angebot mit verstrichener
 * Gueltigkeit (E4). Drittens: Annehmen und Ablehnen sind aus VERSENDET <b>und</b> aus ABGELOEST
 * erlaubt, jeweils unabhaengig vom Datum (F13, Kriterium 18).
 */
class AngebotTest {

  private static final LocalDate ANGEBOTSDATUM = LocalDate.of(2026, 9, 20);
  private static final LocalDate HEUTE = LocalDate.of(2026, 9, 27);
  private static final LocalDate GUELTIG_BIS = LocalDate.of(2026, 10, 20);
  private static final Instant ANGELEGT = Instant.parse("2026-09-20T08:00:00Z");
  private static final Instant JETZT = Instant.parse("2026-09-27T10:30:00Z");
  private static final String NUMMER = "A-2026-001";
  private static final String PDF_SCHLUESSEL = "angebot/7/6f1c9a.pdf";

  private static final Belegempfaenger EMPFAENGER =
      new Belegempfaenger(
          "Adler AG",
          new Anschrift("Hauptstrasse 1", "28195", "Bremen", "Deutschland"),
          "Frau Adler");

  private static final Belegabsender ABSENDER =
      new Belegabsender(
          "Manfred Wolff",
          new Anschrift("Am Deich 2", "28199", "Bremen", "Deutschland"),
          "manne@example.org",
          "0421 123456",
          "75/123/45678",
          "DE123456789",
          "IBAN DE00 1234");

  /** 2,5 × 1.000,01 € = 2.500,025 € und damit gerundet 2.500,03 €. */
  private static final Angebotsposition KONZEPTION =
      new Angebotsposition(
          "Konzeption",
          Abrechnungsmodus.AUFWAND,
          new BigDecimal("2.50"),
          Einheit.PERSONENTAG,
          new BigDecimal("1000.01"));

  /** 1,5 × 0,01 € = 0,015 € und damit gerundet 0,02 €. */
  private static final Angebotsposition KLEINKRAM =
      new Angebotsposition(
          "Kleinkram",
          Abrechnungsmodus.AUFWAND,
          new BigDecimal("1.50"),
          Einheit.STUNDE,
          new BigDecimal("0.01"));

  private static final Angebotsposition PAUSCHALE =
      new Angebotsposition(
          "Schulungstag",
          Abrechnungsmodus.FESTPREIS,
          BigDecimal.ONE,
          Einheit.PAUSCHAL,
          new BigDecimal("1200.00"));

  private static Angebot angebot(final Angebotszustand zustand, final LocalDate gueltigBis) {
    return angebot(zustand, gueltigBis, List.of(KONZEPTION));
  }

  private static Angebot angebot(
      final Angebotszustand zustand,
      final LocalDate gueltigBis,
      final List<Angebotsposition> positionen) {
    final boolean entwurf = zustand == Angebotszustand.ENTWURF;
    return new Angebot(
        7L,
        3L,
        entwurf ? null : NUMMER,
        zustand,
        ANGEBOTSDATUM,
        gueltigBis,
        "Neugestaltung der Website",
        "Zahlbar innerhalb von 14 Tagen ohne Abzug.",
        entwurf ? null : ANGELEGT,
        null,
        entwurf ? null : PDF_SCHLUESSEL,
        entwurf ? null : EMPFAENGER,
        entwurf ? null : ABSENDER,
        positionen,
        ANGELEGT,
        ANGELEGT);
  }

  private static Angebot entwurf() {
    return angebot(Angebotszustand.ENTWURF, GUELTIG_BIS);
  }

  @Test
  void summe_thenAddsTheAmountsRoundedPerPosition() {
    // Given — einzeln gerundet 2.500,03 € + 0,02 €; die Rundung der Summe ergaebe 2.500,04 €.
    final Angebot angebot =
        angebot(Angebotszustand.ENTWURF, GUELTIG_BIS, List.of(KONZEPTION, KLEINKRAM));

    // When
    final BigDecimal summe = angebot.summe();

    // Then
    assertThat(summe).isEqualTo(new BigDecimal("2500.05"));
  }

  @Test
  void summe_givenNoPosition_thenZeroWithTwoDecimals() {
    // Given — ein frischer Entwurf hat noch keine Position (Kriterium 3).
    final Angebot angebot = angebot(Angebotszustand.ENTWURF, GUELTIG_BIS, List.of());

    // When
    final BigDecimal summe = angebot.summe();

    // Then
    assertThat(summe).isEqualTo(new BigDecimal("0.00"));
  }

  @Test
  void stand_givenADraftWithLapsedValidity_thenDraft() {
    // Given — E27: der Entwurf traegt seine Gueltigkeit frei, und „abgelaufen" trifft ihn nicht.
    final Angebot angebot = angebot(Angebotszustand.ENTWURF, HEUTE.minusDays(1));

    // When / Then
    assertThat(angebot.stand(HEUTE)).isEqualTo(Angebotsstand.ENTWURF);
  }

  @Test
  void stand_givenValidityEndingToday_thenSent() {
    // Given — der letzte Tag der Gueltigkeit zaehlt noch mit.
    final Angebot angebot = angebot(Angebotszustand.VERSENDET, HEUTE);

    // When / Then
    assertThat(angebot.stand(HEUTE)).isEqualTo(Angebotsstand.VERSENDET);
  }

  @Test
  void stand_givenValidityEndedYesterday_thenLapsed() {
    // Given — E4: „abgelaufen" entsteht beim Lesen und steht in keiner Spalte.
    final Angebot angebot = angebot(Angebotszustand.VERSENDET, HEUTE.minusDays(1));

    // When / Then
    assertThat(angebot.stand(HEUTE)).isEqualTo(Angebotsstand.ABGELAUFEN);
  }

  @Test
  void stand_givenAnAcceptedOfferWithLapsedValidity_thenAccepted() {
    // Given — ein endgueltiger Zustand laeuft nicht mehr ab.
    final Angebot angebot = angebot(Angebotszustand.ANGENOMMEN, HEUTE.minusDays(1));

    // When / Then
    assertThat(angebot.stand(HEUTE)).isEqualTo(Angebotsstand.ANGENOMMEN);
  }

  @Test
  void stand_givenARejectedOfferWithLapsedValidity_thenRejected() {
    // Given
    final Angebot angebot = angebot(Angebotszustand.ABGELEHNT, HEUTE.minusDays(1));

    // When / Then
    assertThat(angebot.stand(HEUTE)).isEqualTo(Angebotsstand.ABGELEHNT);
  }

  @Test
  void stand_givenASupersededOfferWithLapsedValidity_thenSuperseded() {
    // Given — ein abgeloestes Angebot bleibt abgeloest; es ist nicht abgelaufen.
    final Angebot angebot = angebot(Angebotszustand.ABGELOEST, HEUTE.minusDays(1));

    // When / Then
    assertThat(angebot.stand(HEUTE)).isEqualTo(Angebotsstand.ABGELOEST);
  }

  @Test
  void offen_givenASentOfferStillValid_thenOpen() {
    // Given
    final Angebot angebot = angebot(Angebotszustand.VERSENDET, HEUTE);

    // When / Then
    assertThat(angebot.offen(HEUTE)).isTrue();
  }

  @Test
  void offen_givenASentOfferWithLapsedValidity_thenStillOpen() {
    // Given — Kriterium 18: das abgelaufene Angebot wartet weiter auf eine Reaktion.
    final Angebot angebot = angebot(Angebotszustand.VERSENDET, HEUTE.minusDays(1));

    // When / Then
    assertThat(angebot.offen(HEUTE)).isTrue();
  }

  @ParameterizedTest
  @EnumSource(
      value = Angebotszustand.class,
      names = {"ENTWURF", "ANGENOMMEN", "ABGELEHNT", "ABGELOEST"})
  void offen_givenAnyOtherState_thenNotOpen(final Angebotszustand zustand) {
    // Given
    final Angebot angebot = angebot(zustand, HEUTE);

    // When / Then
    assertThat(angebot.offen(HEUTE)).isFalse();
  }

  @Test
  void versendet_givenADraft_thenCarriesNumberCopiesAndPdf() {
    // When
    final Angebot versendet =
        entwurf().versendet(NUMMER, EMPFAENGER, ABSENDER, PDF_SCHLUESSEL, JETZT);

    // Then
    assertThat(versendet)
        .satisfies(
            a -> assertThat(a.zustand()).isEqualTo(Angebotszustand.VERSENDET),
            a -> assertThat(a.nummer()).isEqualTo(NUMMER),
            a -> assertThat(a.empfaenger()).isEqualTo(EMPFAENGER),
            a -> assertThat(a.absender()).isEqualTo(ABSENDER),
            a -> assertThat(a.pdfSchluessel()).isEqualTo(PDF_SCHLUESSEL),
            a -> assertThat(a.versendetAm()).isEqualTo(JETZT),
            a -> assertThat(a.reaktionAm()).isNull(),
            a -> assertThat(a.updatedAt()).isEqualTo(JETZT),
            a -> assertThat(a.createdAt()).isEqualTo(ANGELEGT),
            a -> assertThat(a.angebotDatum()).isEqualTo(ANGEBOTSDATUM),
            a -> assertThat(a.gueltigBis()).isEqualTo(GUELTIG_BIS),
            a -> assertThat(a.positionen()).containsExactly(KONZEPTION));
  }

  @ParameterizedTest
  @EnumSource(
      value = Angebotszustand.class,
      names = {"VERSENDET", "ANGENOMMEN", "ABGELEHNT", "ABGELOEST"})
  void versendet_givenAnAlreadyCommittedOffer_thenRejected(final Angebotszustand zustand) {
    // Given
    final Angebot angebot = angebot(zustand, GUELTIG_BIS);

    // When / Then
    assertThatThrownBy(() -> angebot.versendet(NUMMER, EMPFAENGER, ABSENDER, PDF_SCHLUESSEL, JETZT))
        .isInstanceOf(AngebotNichtAenderbar.class);
  }

  @Test
  void angenommen_givenASentOffer_thenRecordsTheReaction() {
    // Given
    final Angebot angebot = angebot(Angebotszustand.VERSENDET, GUELTIG_BIS);

    // When
    final Angebot angenommen = angebot.angenommen(JETZT);

    // Then
    assertThat(angenommen)
        .satisfies(
            a -> assertThat(a.zustand()).isEqualTo(Angebotszustand.ANGENOMMEN),
            a -> assertThat(a.reaktionAm()).isEqualTo(JETZT),
            a -> assertThat(a.updatedAt()).isEqualTo(JETZT),
            a -> assertThat(a.versendetAm()).isEqualTo(ANGELEGT),
            a -> assertThat(a.nummer()).isEqualTo(NUMMER),
            a -> assertThat(a.createdAt()).isEqualTo(ANGELEGT));
  }

  @Test
  void angenommen_givenASupersededOffer_thenAllowed() {
    // Given — F13: nachverhandelt, dann nimmt der Kunde doch das erste Angebot.
    final Angebot angebot = angebot(Angebotszustand.ABGELOEST, GUELTIG_BIS);

    // When
    final Angebot angenommen = angebot.angenommen(JETZT);

    // Then
    assertThat(angenommen.zustand()).isEqualTo(Angebotszustand.ANGENOMMEN);
  }

  @Test
  void angenommen_givenALapsedOffer_thenAllowed() {
    // Given — Kriterium 18: die verstrichene Gueltigkeit hindert die Annahme nicht.
    final Angebot angebot = angebot(Angebotszustand.VERSENDET, HEUTE.minusDays(1));

    // When
    final Angebot angenommen = angebot.angenommen(JETZT);

    // Then
    assertThat(angenommen.zustand()).isEqualTo(Angebotszustand.ANGENOMMEN);
  }

  @ParameterizedTest
  @EnumSource(
      value = Angebotszustand.class,
      names = {"ENTWURF", "ANGENOMMEN", "ABGELEHNT"})
  void angenommen_givenADraftOrAFinalState_thenRejected(final Angebotszustand zustand) {
    // Given
    final Angebot angebot = angebot(zustand, GUELTIG_BIS);

    // When / Then
    assertThatThrownBy(() -> angebot.angenommen(JETZT)).isInstanceOf(AngebotNichtAenderbar.class);
  }

  @Test
  void abgelehnt_givenASentOffer_thenRecordsTheReaction() {
    // Given
    final Angebot angebot = angebot(Angebotszustand.VERSENDET, GUELTIG_BIS);

    // When
    final Angebot abgelehnt = angebot.abgelehnt(JETZT);

    // Then
    assertThat(abgelehnt)
        .satisfies(
            a -> assertThat(a.zustand()).isEqualTo(Angebotszustand.ABGELEHNT),
            a -> assertThat(a.reaktionAm()).isEqualTo(JETZT),
            a -> assertThat(a.updatedAt()).isEqualTo(JETZT));
  }

  @Test
  void abgelehnt_givenASupersededOffer_thenAllowed() {
    // Given
    final Angebot angebot = angebot(Angebotszustand.ABGELOEST, GUELTIG_BIS);

    // When
    final Angebot abgelehnt = angebot.abgelehnt(JETZT);

    // Then
    assertThat(abgelehnt.zustand()).isEqualTo(Angebotszustand.ABGELEHNT);
  }

  @Test
  void abgelehnt_givenALapsedOffer_thenAllowed() {
    // Given
    final Angebot angebot = angebot(Angebotszustand.VERSENDET, HEUTE.minusDays(1));

    // When
    final Angebot abgelehnt = angebot.abgelehnt(JETZT);

    // Then
    assertThat(abgelehnt.zustand()).isEqualTo(Angebotszustand.ABGELEHNT);
  }

  @ParameterizedTest
  @EnumSource(
      value = Angebotszustand.class,
      names = {"ENTWURF", "ANGENOMMEN", "ABGELEHNT"})
  void abgelehnt_givenADraftOrAFinalState_thenRejected(final Angebotszustand zustand) {
    // Given
    final Angebot angebot = angebot(zustand, GUELTIG_BIS);

    // When / Then
    assertThatThrownBy(() -> angebot.abgelehnt(JETZT)).isInstanceOf(AngebotNichtAenderbar.class);
  }

  @Test
  void abgeloest_givenASentOffer_thenSupersededWithoutAReaction() {
    // Given — die Abloesung ist ein Zug der Anwendung und keine Reaktion des Kunden.
    final Angebot angebot = angebot(Angebotszustand.VERSENDET, GUELTIG_BIS);

    // When
    final Angebot abgeloest = angebot.abgeloest(JETZT);

    // Then
    assertThat(abgeloest)
        .satisfies(
            a -> assertThat(a.zustand()).isEqualTo(Angebotszustand.ABGELOEST),
            a -> assertThat(a.reaktionAm()).isNull(),
            a -> assertThat(a.updatedAt()).isEqualTo(JETZT));
  }

  @ParameterizedTest
  @EnumSource(
      value = Angebotszustand.class,
      names = {"ENTWURF", "ANGENOMMEN", "ABGELEHNT", "ABGELOEST"})
  void abgeloest_givenAnyStateButSent_thenRejected(final Angebotszustand zustand) {
    // Given — abgeloest wird nur, was offen ist (Kriterium 17).
    final Angebot angebot = angebot(zustand, GUELTIG_BIS);

    // When / Then
    assertThatThrownBy(() -> angebot.abgeloest(JETZT)).isInstanceOf(AngebotNichtAenderbar.class);
  }

  @Test
  void entwurfGeaendert_givenADraft_thenCarriesTheNewValues() {
    // Given
    final LocalDate neueGueltigkeit = LocalDate.of(2026, 11, 30);

    // When
    final Angebot geaendert =
        entwurf()
            .entwurfGeaendert(
                neueGueltigkeit, "Betreuung", "Sofort zahlbar.", List.of(PAUSCHALE), JETZT);

    // Then
    assertThat(geaendert)
        .satisfies(
            a -> assertThat(a.zustand()).isEqualTo(Angebotszustand.ENTWURF),
            a -> assertThat(a.gueltigBis()).isEqualTo(neueGueltigkeit),
            a -> assertThat(a.leistungsbeschreibung()).isEqualTo("Betreuung"),
            a -> assertThat(a.zahlungsbedingungen()).isEqualTo("Sofort zahlbar."),
            a -> assertThat(a.positionen()).containsExactly(PAUSCHALE),
            a -> assertThat(a.updatedAt()).isEqualTo(JETZT),
            a -> assertThat(a.createdAt()).isEqualTo(ANGELEGT),
            a -> assertThat(a.angebotDatum()).isEqualTo(ANGEBOTSDATUM),
            a -> assertThat(a.nummer()).isNull());
  }

  @Test
  void entwurfGeaendert_givenEmptyTexts_thenKeepsThemAbsent() {
    // Given — E9: „nicht angegeben" ist null und nie der Leerstring.
    final Angebot geaendert = entwurf().entwurfGeaendert(GUELTIG_BIS, null, null, List.of(), JETZT);

    // Then
    assertThat(geaendert)
        .satisfies(
            a -> assertThat(a.leistungsbeschreibung()).isNull(),
            a -> assertThat(a.zahlungsbedingungen()).isNull(),
            a -> assertThat(a.positionen()).isEmpty());
  }

  @ParameterizedTest
  @EnumSource(
      value = Angebotszustand.class,
      names = {"VERSENDET", "ANGENOMMEN", "ABGELEHNT", "ABGELOEST"})
  void entwurfGeaendert_givenACommittedOffer_thenRejected(final Angebotszustand zustand) {
    // Given — Kriterium 17: ein festgeschriebenes Dokument aendert sich nicht mehr.
    final Angebot angebot = angebot(zustand, GUELTIG_BIS);

    // When / Then
    assertThatThrownBy(
            () ->
                angebot.entwurfGeaendert(
                    GUELTIG_BIS, "Betreuung", "Sofort zahlbar.", List.of(PAUSCHALE), JETZT))
        .isInstanceOf(AngebotNichtAenderbar.class);
  }

  @Test
  void positionen_thenAreACopyAndNotTheHandedInList() {
    // Given — der Record ist unveraenderlich, auch wenn der Aufrufer seine Liste behaelt.
    final List<Angebotsposition> uebergeben = new ArrayList<>(List.of(KONZEPTION));
    final Angebot angebot = angebot(Angebotszustand.ENTWURF, GUELTIG_BIS, uebergeben);

    // When
    uebergeben.clear();

    // Then
    assertThat(angebot.positionen()).containsExactly(KONZEPTION);
  }
}
