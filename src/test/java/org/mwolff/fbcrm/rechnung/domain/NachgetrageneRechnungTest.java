package org.mwolff.fbcrm.rechnung.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mwolff.fbcrm.rechnung.application.RechnungszustandPasstNicht;

/**
 * Die nachgetragene Rechnung als Fachobjekt: ihre beiden Invarianten und ihre vier Uebergaenge
 * (Plan #259; fachliche Quelle #254).
 *
 * <p>Brutto liegt nie unter Netto (E9, Kriterium 3), und einen Entwurf gibt es bei ihr nicht (E6,
 * Kriterium 8) — beides weist schon der Konstruktor ab, damit kein Weg an der Regel vorbeifuehrt.
 * Am Zustand nimmt sie teil wie jede gestellte Rechnung: Sie fragt dieselbe Kantenregel wie die
 * Rechnung ({@link Rechnungszustand#ausgangswechselErlaubt}, E4).
 *
 * <p>Unveraenderlich: Jeder Uebergang liefert eine neue Instanz, und der Zeitpunkt kommt von aussen
 * (CLAUDE-java.md §6.2).
 */
class NachgetrageneRechnungTest {

  private static final long ID = 21L;
  private static final long FIRMA_ID = 4L;
  private static final LocalDate RECHNUNGSDATUM = LocalDate.of(2026, 2, 15);
  private static final Instant ANGELEGT = Instant.parse("2026-10-06T08:00:00Z");
  private static final Instant SPAETER = Instant.parse("2026-10-07T09:15:00Z");
  private static final BigDecimal NETTO = new BigDecimal("1000.00");
  private static final BigDecimal BRUTTO = new BigDecimal("1190.00");
  private static final String SCHLUESSEL = "nachgetragen/21/original.pdf";

  private static NachgetrageneRechnung rechnung(final Rechnungszustand zustand) {
    return new NachgetrageneRechnung(
        ID,
        FIRMA_ID,
        "RE-9",
        RECHNUNGSDATUM,
        NETTO,
        BRUTTO,
        zustand,
        SCHLUESSEL,
        ANGELEGT,
        ANGELEGT);
  }

  private static NachgetrageneRechnung mitBetraegen(final String netto, final String brutto) {
    return new NachgetrageneRechnung(
        null,
        FIRMA_ID,
        "RE-9",
        RECHNUNGSDATUM,
        new BigDecimal(netto),
        new BigDecimal(brutto),
        Rechnungszustand.GESTELLT,
        null,
        ANGELEGT,
        ANGELEGT);
  }

  @Test
  void konstruktor_givenBruttoBelowNetto_thenRejected() {
    // When / Then — E9: die Paarregel ist eine Invariante des Datensatzes.
    assertThatThrownBy(() -> mitBetraegen("100.00", "99.99"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Brutto");
  }

  @Test
  void konstruktor_givenBruttoEqualToNetto_thenAccepted() {
    // When / Then — Kriterium 3: Brutto gleich Netto ist zulaessig, auch in anderer Skalierung.
    assertThat(mitBetraegen("100.00", "100.0").brutto()).isEqualByComparingTo("100.00");
  }

  @Test
  void konstruktor_givenBruttoAboveNetto_thenKeepsBothAsEntered() {
    // When
    final NachgetrageneRechnung rechnung = mitBetraegen("100.00", "100.01");

    // Then — Kriterium 3: fb.crm rechnet nicht nach.
    assertThat(rechnung.netto()).isEqualByComparingTo("100.00");
    assertThat(rechnung.brutto()).isEqualByComparingTo("100.01");
  }

  @Test
  void konstruktor_givenEntwurf_thenRejected() {
    // When / Then — E6: einen Entwurf gibt es bei ihr nicht.
    assertThatThrownBy(() -> rechnung(Rechnungszustand.ENTWURF))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Entwurf");
  }

  @ParameterizedTest
  @EnumSource(
      value = Rechnungszustand.class,
      names = {"GESTELLT", "BEZAHLT", "ABGESCHRIEBEN"})
  void konstruktor_givenAGestellterZustand_thenAccepted(final Rechnungszustand zustand) {
    assertThat(rechnung(zustand).zustand()).isEqualTo(zustand);
  }

  @Test
  void requireId_givenAStoredRechnung_thenItsId() {
    assertThat(rechnung(Rechnungszustand.GESTELLT).requireId()).isEqualTo(ID);
  }

  @ParameterizedTest
  @EnumSource(
      value = Rechnungszustand.class,
      names = {"GESTELLT", "BEZAHLT", "ABGESCHRIEBEN"})
  void geaendert_inEveryState_thenTakesEveryNewValueAndKeepsTheRest(
      final Rechnungszustand zustand) {
    // Given — Kriterium 10: aenderbar in jedem Zustand, auch Kunde und Nummer.
    final NachgetrageneRechnung rechnung = rechnung(zustand);
    final LocalDate neuesDatum = LocalDate.of(2026, 3, 1);

    // When
    final NachgetrageneRechnung geaendert =
        rechnung.geaendert(
            5L, "RE-10", neuesDatum, new BigDecimal("50.00"), new BigDecimal("59.50"), SPAETER);

    // Then
    assertThat(geaendert)
        .satisfies(
            neu -> assertThat(neu.id()).isEqualTo(ID),
            neu -> assertThat(neu.firmaId()).isEqualTo(5L),
            neu -> assertThat(neu.nummer()).isEqualTo("RE-10"),
            neu -> assertThat(neu.rechnungDatum()).isEqualTo(neuesDatum),
            neu -> assertThat(neu.netto()).isEqualByComparingTo("50.00"),
            neu -> assertThat(neu.brutto()).isEqualByComparingTo("59.50"),
            neu -> assertThat(neu.zustand()).isEqualTo(zustand),
            neu -> assertThat(neu.pdfSchluessel()).isEqualTo(SCHLUESSEL),
            neu -> assertThat(neu.createdAt()).isEqualTo(ANGELEGT),
            neu -> assertThat(neu.updatedAt()).isEqualTo(SPAETER));
  }

  @Test
  void geaendert_givenBruttoBelowNetto_thenRejected() {
    // Given
    final NachgetrageneRechnung rechnung = rechnung(Rechnungszustand.GESTELLT);

    // When / Then — die Invariante gilt auch fuer den neuen Stand.
    assertThatThrownBy(
            () ->
                rechnung.geaendert(
                    FIRMA_ID,
                    "RE-9",
                    RECHNUNGSDATUM,
                    new BigDecimal("50.00"),
                    new BigDecimal("49.99"),
                    SPAETER))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void mitZustand_fromGestelltToBezahlt_thenBezahltAndKeepsEverythingElse() {
    // When
    final NachgetrageneRechnung bezahlt =
        rechnung(Rechnungszustand.GESTELLT).mitZustand(Rechnungszustand.BEZAHLT, SPAETER);

    // Then — nur der Zustand und der Zeitpunkt der letzten Aenderung ruecken vor.
    assertThat(bezahlt)
        .isEqualTo(
            new NachgetrageneRechnung(
                ID,
                FIRMA_ID,
                "RE-9",
                RECHNUNGSDATUM,
                NETTO,
                BRUTTO,
                Rechnungszustand.BEZAHLT,
                SCHLUESSEL,
                ANGELEGT,
                SPAETER));
  }

  @Test
  void mitZustand_fromBezahltToGestellt_thenGestellt() {
    assertThat(
            rechnung(Rechnungszustand.BEZAHLT)
                .mitZustand(Rechnungszustand.GESTELLT, SPAETER)
                .zustand())
        .isEqualTo(Rechnungszustand.GESTELLT);
  }

  @Test
  void mitZustand_fromGestelltToAbgeschrieben_thenAbgeschrieben() {
    assertThat(
            rechnung(Rechnungszustand.GESTELLT)
                .mitZustand(Rechnungszustand.ABGESCHRIEBEN, SPAETER)
                .zustand())
        .isEqualTo(Rechnungszustand.ABGESCHRIEBEN);
  }

  @Test
  void mitZustand_fromAbgeschriebenToGestellt_thenGestellt() {
    assertThat(
            rechnung(Rechnungszustand.ABGESCHRIEBEN)
                .mitZustand(Rechnungszustand.GESTELLT, SPAETER)
                .zustand())
        .isEqualTo(Rechnungszustand.GESTELLT);
  }

  @Test
  void mitZustand_fromBezahltToAbgeschrieben_thenRejected() {
    // Given — der direkte Weg zwischen den Ausgaengen fuehrt ueber „gestellt".
    final NachgetrageneRechnung rechnung = rechnung(Rechnungszustand.BEZAHLT);

    // When / Then
    assertThatThrownBy(() -> rechnung.mitZustand(Rechnungszustand.ABGESCHRIEBEN, SPAETER))
        .isInstanceOf(RechnungszustandPasstNicht.class);
  }

  @Test
  void mitZustand_fromAbgeschriebenToBezahlt_thenRejected() {
    // Given
    final NachgetrageneRechnung rechnung = rechnung(Rechnungszustand.ABGESCHRIEBEN);

    // When / Then
    assertThatThrownBy(() -> rechnung.mitZustand(Rechnungszustand.BEZAHLT, SPAETER))
        .isInstanceOf(RechnungszustandPasstNicht.class);
  }

  @ParameterizedTest
  @EnumSource(
      value = Rechnungszustand.class,
      names = {"GESTELLT", "BEZAHLT", "ABGESCHRIEBEN"})
  void mitZustand_givenTheSameState_thenRejected(final Rechnungszustand zustand) {
    // Given — Stillstand ist keine Kante.
    final NachgetrageneRechnung rechnung = rechnung(zustand);

    // When / Then
    assertThatThrownBy(() -> rechnung.mitZustand(zustand, SPAETER))
        .isInstanceOf(RechnungszustandPasstNicht.class);
  }

  @Test
  void mitZustand_towardsEntwurf_thenRejected() {
    // Given
    final NachgetrageneRechnung rechnung = rechnung(Rechnungszustand.GESTELLT);

    // When / Then
    assertThatThrownBy(() -> rechnung.mitZustand(Rechnungszustand.ENTWURF, SPAETER))
        .isInstanceOf(RechnungszustandPasstNicht.class);
  }

  @Test
  void mitDokument_thenCarriesTheNewKeyAndTheTime() {
    // Given
    final NachgetrageneRechnung ohne = rechnung(Rechnungszustand.BEZAHLT).ohneDokument(ANGELEGT);

    // When
    final NachgetrageneRechnung mit = ohne.mitDokument("nachgetragen/21/neu.pdf", SPAETER);

    // Then
    assertThat(mit)
        .isEqualTo(
            new NachgetrageneRechnung(
                ID,
                FIRMA_ID,
                "RE-9",
                RECHNUNGSDATUM,
                NETTO,
                BRUTTO,
                Rechnungszustand.BEZAHLT,
                "nachgetragen/21/neu.pdf",
                ANGELEGT,
                SPAETER));
  }

  @Test
  void ohneDokument_thenDropsTheKeyAndKeepsEverythingElse() {
    // When
    final NachgetrageneRechnung ohne =
        rechnung(Rechnungszustand.ABGESCHRIEBEN).ohneDokument(SPAETER);

    // Then
    assertThat(ohne)
        .isEqualTo(
            new NachgetrageneRechnung(
                ID,
                FIRMA_ID,
                "RE-9",
                RECHNUNGSDATUM,
                NETTO,
                BRUTTO,
                Rechnungszustand.ABGESCHRIEBEN,
                null,
                ANGELEGT,
                SPAETER));
  }
}
