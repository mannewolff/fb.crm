package org.mwolff.fbcrm.auftrag.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.auftrag.domain.Auftrag;
import org.mwolff.fbcrm.auftrag.domain.AuftragRepository;
import org.mwolff.fbcrm.auftrag.domain.Auftragsposition;
import org.mwolff.fbcrm.auftrag.domain.Auftragsstatus;
import org.mwolff.fbcrm.common.Abrechnungsmodus;
import org.mwolff.fbcrm.common.Anschrift;
import org.mwolff.fbcrm.common.Einheit;
import org.mwolff.fbcrm.firma.domain.Firma;
import org.mwolff.fbcrm.firma.domain.FirmaRepository;
import org.mwolff.fbcrm.vorgang.domain.Vorgang;
import org.mwolff.fbcrm.vorgang.domain.VorgangRepository;

/**
 * Der Auftragsbestand: die nicht abgeschlossenen Auftraege offener Vorgaenge (Kriterien 12, 13).
 *
 * <p>Drei Aussagen sind hier der Gegenstand. Die <b>Auswahl</b>: Der Bestand beantwortet „was liegt
 * noch vor mir", und darum fehlt hier der Auftrag am abgeschlossenen Vorgang — er kommt nach dem
 * Wiedereroeffnen zurueck (Kriterium 12). Dass der <b>abgeschlossene Auftrag</b> fehlt (F7),
 * entscheidet die Abfrage und nicht dieser Anwendungsfall: Der Status steht in einer Spalte, und
 * ihn hier ein zweites Mal zu pruefen waere dieselbe Regel an zwei Orten. Nachgewiesen ist F7 darum
 * in {@code JpaAuftragRepositoryTest} und in {@code AuftragsbestandIT}. Die <b>Rechnung</b>: Der
 * abgerechnete Betrag ist bis Idee #7 ueberall 0,00 €, der offene Rest damit die Auftragssumme, und
 * die beiden Kennzahlen sind die Summen der jeweiligen Spalte (Kriterium 13). Und die
 * <b>Reihenfolge</b>: offener Rest absteigend, bei gleichem Rest die hoehere Kennung zuerst (Plan
 * E13) — ohne den zweiten Schluessel lieferten zwei Aufrufe verschiedene Listen.
 */
@ExtendWith(MockitoExtension.class)
class AuftragsbestandUseCaseTest {

  private static final Instant ANGELEGT = Instant.parse("2026-09-20T08:00:00Z");
  private static final BigDecimal NULL_EURO = new BigDecimal("0.00");

  private static final long VORGANG = 3L;
  private static final long ZWEITER_VORGANG = 4L;
  private static final long FIRMA = 7L;
  private static final long ZWEITE_FIRMA = 8L;

  @Mock private AuftragRepository auftraege;
  @Mock private VorgangRepository vorgaenge;
  @Mock private FirmaRepository firmen;

  @Captor private ArgumentCaptor<Collection<Long>> gefragteVorgaenge;

  @InjectMocks private AuftragsbestandUseCase useCase;

  private static Auftragsposition pauschal(final String einzelpreis) {
    return new Auftragsposition(
        "Leistung",
        Abrechnungsmodus.FESTPREIS,
        BigDecimal.ONE,
        Einheit.PAUSCHAL,
        new BigDecimal(einzelpreis),
        null);
  }

  private static Auftrag auftrag(
      final long id, final long vorgangId, final Auftragsstatus status, final String einzelpreis) {
    return new Auftrag(
        Long.valueOf(id),
        vorgangId,
        100L + id,
        "AU-2026-00" + id,
        status,
        LocalDate.of(2026, 9, 28),
        null,
        null,
        null,
        List.of(pauschal(einzelpreis)),
        ANGELEGT,
        ANGELEGT);
  }

  private static Auftrag offen(final long id, final long vorgangId, final String einzelpreis) {
    return auftrag(id, vorgangId, Auftragsstatus.OFFEN, einzelpreis);
  }

  private static Vorgang vorgang(final long id, final long firmaId, final boolean abgeschlossen) {
    return new Vorgang(
        Long.valueOf(id),
        10L + id,
        "Vorgang " + id,
        firmaId,
        null,
        null,
        null,
        abgeschlossen,
        ANGELEGT,
        ANGELEGT);
  }

  private static Firma firma(final long id, final String name) {
    return new Firma(
        Long.valueOf(id),
        name,
        new Anschrift("Am Wall 1", "28195", "Bremen", "Deutschland"),
        null,
        null,
        true,
        ANGELEGT,
        ANGELEGT);
  }

  private void imBestand(
      final List<Auftrag> kandidaten, final List<Vorgang> zu, final Firma... bekannte) {
    when(auftraege.bestandskandidaten()).thenReturn(kandidaten);
    when(vorgaenge.findByIds(any())).thenReturn(zu);
    for (final Firma eine : bekannte) {
      when(firmen.findById(eine.requireId())).thenReturn(Optional.of(eine));
    }
  }

  private List<Long> kennungen(final Auftragsbestand bestand) {
    return bestand.zeilen().stream().map(zeile -> Long.valueOf(zeile.auftragId())).toList();
  }

  @Test
  void bestand_thenCarriesEveryFieldOfTheRow() {
    // Given — Kriterium 12: Vorgang, Firma, Nummer, Status und die drei Betraege.
    imBestand(
        List.of(offen(1L, VORGANG, "1000.00")),
        List.of(vorgang(VORGANG, FIRMA, false)),
        firma(FIRMA, "Adler AG"));

    // When
    final Auftragsbestand bestand = useCase.bestand();

    // Then
    assertThat(bestand.zeilen())
        .containsExactly(
            new AuftragsbestandZeile(
                VORGANG,
                13L,
                "Vorgang 3",
                "Adler AG",
                1L,
                "AU-2026-001",
                Auftragsstatus.OFFEN,
                new BigDecimal("1000.00"),
                NULL_EURO,
                new BigDecimal("1000.00")));
  }

  @ParameterizedTest
  @EnumSource(
      value = Auftragsstatus.class,
      names = {"OFFEN", "IN_ARBEIT"})
  void bestand_givenAnUnfinishedOrder_thenItCounts(final Auftragsstatus status) {
    // Given — F7: erst der Abschluss nimmt den Auftrag aus dem Bestand.
    imBestand(
        List.of(auftrag(1L, VORGANG, status, "1000.00")),
        List.of(vorgang(VORGANG, FIRMA, false)),
        firma(FIRMA, "Adler AG"));

    // When / Then
    assertThat(kennungen(useCase.bestand())).containsExactly(Long.valueOf(1L));
  }

  @Test
  void bestand_givenAClosedVorgang_thenLeavesItsOrderOut() {
    // Given — Kriterium 12: nur die Auftraege offener Vorgaenge zaehlen.
    imBestand(List.of(offen(1L, VORGANG, "1000.00")), List.of(vorgang(VORGANG, FIRMA, true)));

    // When / Then
    assertThat(useCase.bestand().zeilen()).isEmpty();
    verify(firmen, never()).findById(anyLong());
  }

  @Test
  void bestand_givenAReopenedVorgang_thenItsOrderIsBackAgain() {
    // Given — Kriterium 12: das Wiedereroeffnen bringt den Auftrag zurueck, ohne ihn zu aendern.
    imBestand(
        List.of(offen(1L, VORGANG, "1000.00")),
        List.of(vorgang(VORGANG, FIRMA, false)),
        firma(FIRMA, "Adler AG"));

    // When / Then
    assertThat(kennungen(useCase.bestand())).containsExactly(Long.valueOf(1L));
  }

  @Test
  void bestand_thenCarriesNothingBilledAndTheFullSumAsOpenRest() {
    // Given — Kriterium 13, Plan E12: bis Idee #7 ist nichts abgerechnet.
    imBestand(
        List.of(offen(1L, VORGANG, "1000.00"), offen(2L, ZWEITER_VORGANG, "500.00")),
        List.of(vorgang(VORGANG, FIRMA, false), vorgang(ZWEITER_VORGANG, FIRMA, false)),
        firma(FIRMA, "Adler AG"));

    // When
    final Auftragsbestand bestand = useCase.bestand();

    // Then
    assertThat(bestand.zeilen())
        .allSatisfy(
            zeile -> {
              assertThat(zeile.abgerechnet()).isEqualTo(NULL_EURO);
              assertThat(zeile.offenerRest()).isEqualTo(zeile.auftragssumme());
            });
  }

  @Test
  void bestand_thenAnswersWithBothTotalsAndTheyMatchUntilTheFirstInvoice() {
    // Given — Kriterium 13: „Beauftragt" und „Noch offen" ueber den Zeilen.
    imBestand(
        List.of(offen(1L, VORGANG, "1000.00"), offen(2L, ZWEITER_VORGANG, "500.00")),
        List.of(vorgang(VORGANG, FIRMA, false), vorgang(ZWEITER_VORGANG, FIRMA, false)),
        firma(FIRMA, "Adler AG"));

    // When
    final Auftragsbestand bestand = useCase.bestand();

    // Then
    assertThat(bestand.beauftragt()).isEqualTo(new BigDecimal("1500.00"));
    assertThat(bestand.nochOffen()).isEqualTo(bestand.beauftragt());
  }

  @Test
  void bestand_thenSortsByTheOpenRestDescending() {
    // Given — Kriterium 12: der groesste offene Rest oben.
    imBestand(
        List.of(offen(1L, VORGANG, "500.00"), offen(2L, ZWEITER_VORGANG, "1000.00")),
        List.of(vorgang(VORGANG, FIRMA, false), vorgang(ZWEITER_VORGANG, FIRMA, false)),
        firma(FIRMA, "Adler AG"));

    // When / Then
    assertThat(kennungen(useCase.bestand())).containsExactly(Long.valueOf(2L), Long.valueOf(1L));
  }

  @Test
  void bestand_givenTheSameOpenRest_thenTheHigherIdComesFirstAndStays() {
    // Given — Plan E13: ohne den zweiten Schluessel lieferten zwei Aufrufe verschiedene Listen.
    imBestand(
        List.of(offen(1L, VORGANG, "1000.00"), offen(2L, ZWEITER_VORGANG, "1000.00")),
        List.of(vorgang(VORGANG, FIRMA, false), vorgang(ZWEITER_VORGANG, FIRMA, false)),
        firma(FIRMA, "Adler AG"));

    // When / Then
    assertThat(kennungen(useCase.bestand())).containsExactly(Long.valueOf(2L), Long.valueOf(1L));
    assertThat(kennungen(useCase.bestand())).containsExactly(Long.valueOf(2L), Long.valueOf(1L));
  }

  @Test
  void bestand_givenNothingLeftToDo_thenTwoZeroTotalsAndNoRow() {
    // Given — daran erkennt die Oberflaeche „kein Auftragsbestand".
    when(auftraege.bestandskandidaten()).thenReturn(List.of());
    when(vorgaenge.findByIds(any())).thenReturn(List.of());

    // When
    final Auftragsbestand bestand = useCase.bestand();

    // Then
    assertThat(bestand.zeilen()).isEmpty();
    assertThat(bestand.beauftragt()).isEqualTo(NULL_EURO);
    assertThat(bestand.nochOffen()).isEqualTo(NULL_EURO);
  }

  @Test
  void bestand_thenAsksForEveryVorgangInOneQuery() {
    // Given — ein findById je Zeile waere die bekannte Abfrage-Lawine.
    imBestand(
        List.of(offen(1L, VORGANG, "1000.00"), offen(2L, VORGANG, "500.00")),
        List.of(vorgang(VORGANG, FIRMA, false)),
        firma(FIRMA, "Adler AG"));

    // When
    useCase.bestand();

    // Then — jede Kennung genau einmal, auch bei zwei Auftraegen am selben Vorgang.
    verify(vorgaenge).findByIds(gefragteVorgaenge.capture());
    assertThat(gefragteVorgaenge.getValue()).containsExactly(Long.valueOf(VORGANG));
  }

  @Test
  void bestand_givenTwoOrdersOfTheSameFirma_thenAsksForItsNameOnce() {
    // Given — gemerkt statt je Zeile geholt, Muster VorgangZeilen.
    imBestand(
        List.of(offen(1L, VORGANG, "1000.00"), offen(2L, ZWEITER_VORGANG, "500.00")),
        List.of(vorgang(VORGANG, FIRMA, false), vorgang(ZWEITER_VORGANG, FIRMA, false)),
        firma(FIRMA, "Adler AG"));

    // When
    useCase.bestand();

    // Then
    verify(firmen).findById(FIRMA);
  }

  @Test
  void bestand_givenTwoFirmen_thenEveryRowCarriesItsOwnName() {
    // Given — der gemerkte Name darf nicht auf die fremde Zeile durchschlagen.
    imBestand(
        List.of(offen(2L, VORGANG, "1000.00"), offen(1L, ZWEITER_VORGANG, "500.00")),
        List.of(vorgang(VORGANG, FIRMA, false), vorgang(ZWEITER_VORGANG, ZWEITE_FIRMA, false)),
        firma(FIRMA, "Adler AG"),
        firma(ZWEITE_FIRMA, "Biber GmbH"));

    // When / Then
    assertThat(useCase.bestand().zeilen())
        .extracting(AuftragsbestandZeile::firma)
        .containsExactly("Adler AG", "Biber GmbH");
  }

  @Test
  void bestand_whenTheVorgangOfAnOrderIsMissing_thenFailsLoudly() {
    // Given — der Fremdschluessel auftrag.vorgang_id schliesst das aus; traete es ein, waere der
    // Bestand kaputt, und dann ist ein lauter Fehler die richtige Antwort.
    when(auftraege.bestandskandidaten()).thenReturn(List.of(offen(1L, VORGANG, "1000.00")));
    when(vorgaenge.findByIds(any())).thenReturn(List.of());

    // When / Then
    assertThatThrownBy(() -> useCase.bestand())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(String.valueOf(VORGANG));
  }

  @Test
  void bestand_whenTheFirmaOfAVorgangIsMissing_thenFailsLoudly() {
    // Given — derselbe Grund fuer den Fremdschluessel vorgang.firma_id.
    when(auftraege.bestandskandidaten()).thenReturn(List.of(offen(1L, VORGANG, "1000.00")));
    when(vorgaenge.findByIds(any())).thenReturn(List.of(vorgang(VORGANG, FIRMA, false)));
    when(firmen.findById(FIRMA)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> useCase.bestand())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(String.valueOf(FIRMA));
  }
}
