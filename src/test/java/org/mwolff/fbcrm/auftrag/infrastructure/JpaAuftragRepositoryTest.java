package org.mwolff.fbcrm.auftrag.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.auftrag.domain.Auftrag;
import org.mwolff.fbcrm.auftrag.domain.Auftragsposition;
import org.mwolff.fbcrm.auftrag.domain.Auftragsstatus;
import org.mwolff.fbcrm.common.Abrechnungsmodus;
import org.mwolff.fbcrm.common.Einheit;

/**
 * Die Uebersetzung zwischen Auftrag und Zeilen — in beide Richtungen.
 *
 * <p>Der Adapter steht hier gegen gemockte Spring-Data-Repositories, damit die Abbildung selbst
 * geprueft ist und nicht nur ihr Zusammenspiel mit der Datenbank ({@code AuftragPersistenceIT}).
 * Drei Zusagen sind der Gegenstand: Die Plaetze der Positionen entstehen lueckenlos ab 1 aus der
 * Reihenfolge der Liste, „Stunden je Personentag" wandert in beide Richtungen mitsamt seinem Fehlen
 * beim Festpreis (E10), und geloescht wird erst die Position, dann die Zeile, die sie traegt.
 */
@ExtendWith(MockitoExtension.class)
class JpaAuftragRepositoryTest {

  private static final LocalDate AUFTRAGSDATUM = LocalDate.of(2026, 9, 22);
  private static final LocalDate LEISTUNG_AB = LocalDate.of(2026, 10, 1);
  private static final LocalDate LEISTUNG_BIS = LocalDate.of(2026, 12, 31);
  private static final Instant ANGELEGT = Instant.parse("2026-09-22T08:00:00Z");
  private static final Instant GEAENDERT = Instant.parse("2026-09-27T10:30:00Z");
  private static final String NUMMER = "AU-2026-001";
  private static final String BESTELLNUMMER = "BST-4711";

  private static final Auftragsposition KONZEPTION =
      new Auftragsposition(
          "Konzeption",
          Abrechnungsmodus.AUFWAND,
          new BigDecimal("2.50"),
          Einheit.PERSONENTAG,
          new BigDecimal("1000.01"),
          new BigDecimal("7.50"));

  private static final Auftragsposition SCHULUNG =
      new Auftragsposition(
          "Schulungstag",
          Abrechnungsmodus.FESTPREIS,
          new BigDecimal("1.00"),
          Einheit.PAUSCHAL,
          new BigDecimal("1200.00"),
          null);

  @Mock private SpringDataAuftragRepository auftraege;

  @Mock private SpringDataAuftragPositionRepository positionen;

  @Captor private ArgumentCaptor<AuftragEntity> gespeicherte;

  @Captor private ArgumentCaptor<List<AuftragPositionEntity>> gespeichertePositionen;

  @InjectMocks private JpaAuftragRepository repository;

  private static Auftrag auftrag(final List<Auftragsposition> positionen) {
    return new Auftrag(
        7L,
        3L,
        11L,
        NUMMER,
        Auftragsstatus.OFFEN,
        AUFTRAGSDATUM,
        BESTELLNUMMER,
        LEISTUNG_AB,
        LEISTUNG_BIS,
        positionen,
        ANGELEGT,
        GEAENDERT);
  }

  private static Auftrag ohneFreiwilligeAngaben() {
    return new Auftrag(
        7L,
        3L,
        11L,
        NUMMER,
        Auftragsstatus.ABGESCHLOSSEN,
        AUFTRAGSDATUM,
        null,
        null,
        null,
        List.of(SCHULUNG),
        ANGELEGT,
        GEAENDERT);
  }

  private static AuftragEntity zeile() {
    return zeile(7L);
  }

  private static AuftragEntity zeile(final long id) {
    return new AuftragEntity(
        Long.valueOf(id),
        3L,
        11L,
        NUMMER,
        Auftragsstatus.OFFEN,
        AUFTRAGSDATUM,
        BESTELLNUMMER,
        LEISTUNG_AB,
        LEISTUNG_BIS,
        ANGELEGT,
        GEAENDERT);
  }

  private static AuftragEntity zeileOhneFreiwilligeAngaben() {
    return new AuftragEntity(
        7L,
        3L,
        11L,
        NUMMER,
        Auftragsstatus.ABGESCHLOSSEN,
        AUFTRAGSDATUM,
        null,
        null,
        null,
        ANGELEGT,
        GEAENDERT);
  }

  private static AuftragPositionEntity positionszeile(
      final long auftragId, final short platz, final Auftragsposition position) {
    return new AuftragPositionEntity(
        null,
        auftragId,
        platz,
        position.bezeichnung(),
        position.abrechnungsmodus(),
        position.menge(),
        position.einheit(),
        position.einzelpreis(),
        position.stundenJePersonentag());
  }

  private void erwarteSchreibenDerZeile(final AuftragEntity zeile) {
    when(auftraege.save(any(AuftragEntity.class))).thenReturn(zeile);
    when(positionen.saveAll(any())).thenAnswer(aufruf -> aufruf.getArgument(0));
  }

  @Test
  void save_thenWritesEveryFieldOfTheOrderIntoTheRow() {
    // Given
    erwarteSchreibenDerZeile(zeile());

    // When
    repository.save(auftrag(List.of(KONZEPTION)));

    // Then
    verify(auftraege).save(gespeicherte.capture());
    assertThat(gespeicherte.getValue())
        .satisfies(
            zeile -> assertThat(zeile.getId()).isEqualTo(7L),
            zeile -> assertThat(zeile.getVorgangId()).isEqualTo(3L),
            zeile -> assertThat(zeile.getAngebotId()).isEqualTo(11L),
            zeile -> assertThat(zeile.getNummer()).isEqualTo(NUMMER),
            zeile -> assertThat(zeile.getStatus()).isEqualTo(Auftragsstatus.OFFEN),
            zeile -> assertThat(zeile.getAuftragDatum()).isEqualTo(AUFTRAGSDATUM),
            zeile -> assertThat(zeile.getKundenbestellnummer()).isEqualTo(BESTELLNUMMER),
            zeile -> assertThat(zeile.getLeistungAb()).isEqualTo(LEISTUNG_AB),
            zeile -> assertThat(zeile.getLeistungBis()).isEqualTo(LEISTUNG_BIS),
            zeile -> assertThat(zeile.getCreatedAt()).isEqualTo(ANGELEGT),
            zeile -> assertThat(zeile.getUpdatedAt()).isEqualTo(GEAENDERT));
  }

  @Test
  void save_givenAnOrderWithoutVoluntaryDetails_thenLeavesThoseColumnsEmpty() {
    // Given — Kundenbestellnummer und Leistungszeitraum sind freiwillig (Kriterium 3).
    erwarteSchreibenDerZeile(zeileOhneFreiwilligeAngaben());

    // When
    repository.save(ohneFreiwilligeAngaben());

    // Then
    verify(auftraege).save(gespeicherte.capture());
    assertThat(gespeicherte.getValue())
        .satisfies(
            zeile -> assertThat(zeile.getKundenbestellnummer()).isNull(),
            zeile -> assertThat(zeile.getLeistungAb()).isNull(),
            zeile -> assertThat(zeile.getLeistungBis()).isNull(),
            zeile -> assertThat(zeile.getStatus()).isEqualTo(Auftragsstatus.ABGESCHLOSSEN));
  }

  @Test
  void save_thenNumbersThePositionsFromOneInTheOrderOfTheList() {
    // Given — die Reihenfolge der Liste ist die Reihenfolge im Bestand.
    erwarteSchreibenDerZeile(zeile());

    // When
    repository.save(auftrag(List.of(SCHULUNG, KONZEPTION)));

    // Then
    verify(positionen).saveAll(gespeichertePositionen.capture());
    assertThat(gespeichertePositionen.getValue())
        .extracting(
            AuftragPositionEntity::getPosition,
            AuftragPositionEntity::getBezeichnung,
            AuftragPositionEntity::getAuftragId)
        .containsExactly(tuple((short) 1, "Schulungstag", 7L), tuple((short) 2, "Konzeption", 7L));
  }

  @Test
  void save_thenWritesEveryFieldOfAPositionIntoItsRow() {
    // Given
    erwarteSchreibenDerZeile(zeile());

    // When
    repository.save(auftrag(List.of(KONZEPTION)));

    // Then
    verify(positionen).saveAll(gespeichertePositionen.capture());
    assertThat(gespeichertePositionen.getValue())
        .singleElement()
        .satisfies(
            zeile -> assertThat(zeile.getId()).isNull(),
            zeile -> assertThat(zeile.getBezeichnung()).isEqualTo("Konzeption"),
            zeile -> assertThat(zeile.getAbrechnungsmodus()).isEqualTo(Abrechnungsmodus.AUFWAND),
            zeile -> assertThat(zeile.getMenge()).isEqualTo(new BigDecimal("2.50")),
            zeile -> assertThat(zeile.getEinheit()).isEqualTo(Einheit.PERSONENTAG),
            zeile -> assertThat(zeile.getEinzelpreis()).isEqualTo(new BigDecimal("1000.01")),
            zeile -> assertThat(zeile.getStundenJePersonentag()).isEqualTo(new BigDecimal("7.50")));
  }

  @Test
  void save_givenAFixedPricePosition_thenLeavesTheHoursPerDayEmpty() {
    // Given — E10: beim Festpreis bleibt der Faktor leer, und die Datenbank haelt das fest.
    erwarteSchreibenDerZeile(zeile());

    // When
    repository.save(auftrag(List.of(SCHULUNG)));

    // Then
    verify(positionen).saveAll(gespeichertePositionen.capture());
    assertThat(gespeichertePositionen.getValue())
        .singleElement()
        .satisfies(zeile -> assertThat(zeile.getStundenJePersonentag()).isNull());
  }

  @Test
  void save_thenDeletesTheOldPositionsBeforeWritingTheNewOnes() {
    // Given — sonst stiesse die neue Reihenfolge auf die alten Plaetze (UNIQUE je Platz).
    erwarteSchreibenDerZeile(zeile());

    // When
    repository.save(auftrag(List.of(KONZEPTION)));

    // Then
    final InOrder reihenfolge = inOrder(positionen);
    reihenfolge.verify(positionen).loescheZuAuftrag(7L);
    reihenfolge.verify(positionen).saveAll(any());
  }

  @Test
  void save_givenAnOrderWithoutPositions_thenWritesNoPositionRow() {
    // Given
    erwarteSchreibenDerZeile(zeile());

    // When
    repository.save(auftrag(List.of()));

    // Then
    verify(positionen).saveAll(gespeichertePositionen.capture());
    assertThat(gespeichertePositionen.getValue()).isEmpty();
  }

  @Test
  void save_thenReturnsTheOrderWithTheGeneratedIdAndItsPositions() {
    // Given
    erwarteSchreibenDerZeile(zeile());

    // When
    final Auftrag gesichert = repository.save(auftrag(List.of(SCHULUNG, KONZEPTION)));

    // Then
    assertThat(gesichert)
        .satisfies(
            auftrag -> assertThat(auftrag.id()).isEqualTo(7L),
            auftrag -> assertThat(auftrag.positionen()).containsExactly(SCHULUNG, KONZEPTION));
  }

  @Test
  void findById_thenTranslatesTheRowAndItsPositionsBack() {
    // Given
    when(auftraege.findById(7L)).thenReturn(Optional.of(zeile()));
    when(positionen.findByAuftrag(7L))
        .thenReturn(List.of(positionszeile(7L, (short) 1, KONZEPTION)));

    // When
    final Optional<Auftrag> gefunden = repository.findById(7L);

    // Then
    assertThat(gefunden).contains(auftrag(List.of(KONZEPTION)));
  }

  @Test
  void findById_givenAFixedPricePosition_thenTranslatesTheEmptyHoursPerDayBack() {
    // Given — E10: das Fehlen des Faktors ist eine Aussage und keine Luecke.
    when(auftraege.findById(7L)).thenReturn(Optional.of(zeileOhneFreiwilligeAngaben()));
    when(positionen.findByAuftrag(7L)).thenReturn(List.of(positionszeile(7L, (short) 1, SCHULUNG)));

    // When
    final Optional<Auftrag> gefunden = repository.findById(7L);

    // Then
    assertThat(gefunden).contains(ohneFreiwilligeAngaben());
  }

  @Test
  void findById_thenKeepsTheOrderOfThePositionsAsRead() {
    // Given — gelesen wird nach Platz, und genau diese Folge steht danach am Auftrag.
    when(auftraege.findById(7L)).thenReturn(Optional.of(zeile()));
    when(positionen.findByAuftrag(7L))
        .thenReturn(
            List.of(
                positionszeile(7L, (short) 1, SCHULUNG),
                positionszeile(7L, (short) 2, KONZEPTION)));

    // When
    final Optional<Auftrag> gefunden = repository.findById(7L);

    // Then
    assertThat(gefunden)
        .hasValueSatisfying(
            auftrag -> assertThat(auftrag.positionen()).containsExactly(SCHULUNG, KONZEPTION));
  }

  @Test
  void findById_givenAnUnknownId_thenEmpty() {
    // Given
    when(auftraege.findById(7L)).thenReturn(Optional.empty());

    // When
    final Optional<Auftrag> gefunden = repository.findById(7L);

    // Then
    assertThat(gefunden).isEmpty();
    verify(positionen, never()).findByAuftrag(anyLong());
  }

  @Test
  void findByAngebot_thenTranslatesTheRowAndItsPositionsBack() {
    // Given — F9: hoechstens ein Auftrag je Angebot, deshalb ein Optional.
    when(auftraege.findByAngebot(11L)).thenReturn(Optional.of(zeile()));
    when(positionen.findByAuftrag(7L))
        .thenReturn(List.of(positionszeile(7L, (short) 1, KONZEPTION)));

    // When
    final Optional<Auftrag> gefunden = repository.findByAngebot(11L);

    // Then
    assertThat(gefunden).contains(auftrag(List.of(KONZEPTION)));
  }

  @Test
  void findByAngebot_givenAnOfferWithoutAnOrder_thenEmpty() {
    // Given
    when(auftraege.findByAngebot(11L)).thenReturn(Optional.empty());

    // When
    final Optional<Auftrag> gefunden = repository.findByAngebot(11L);

    // Then
    assertThat(gefunden).isEmpty();
    verify(positionen, never()).findByAuftrag(anyLong());
  }

  @Test
  void findByVorgang_thenLoadsThePositionsOfEveryOrderInOneQuery() {
    // Given — je Zeile nachzuladen waere die bekannte Abfrage-Lawine.
    when(auftraege.findByVorgang(3L)).thenReturn(List.of(zeile(7L), zeile(8L)));
    when(positionen.findByAuftraege(List.of(7L, 8L)))
        .thenReturn(
            List.of(
                positionszeile(7L, (short) 1, SCHULUNG),
                positionszeile(7L, (short) 2, KONZEPTION),
                positionszeile(8L, (short) 1, KONZEPTION)));

    // When
    final List<Auftrag> gefunden = repository.findByVorgang(3L);

    // Then
    assertThat(gefunden)
        .extracting(Auftrag::id, Auftrag::positionen)
        .containsExactly(tuple(7L, List.of(SCHULUNG, KONZEPTION)), tuple(8L, List.of(KONZEPTION)));
    verify(positionen, never()).findByAuftrag(anyLong());
  }

  @Test
  void findByVorgang_givenAnOrderWithoutPositions_thenTranslatesItWithAnEmptyList() {
    // Given
    when(auftraege.findByVorgang(3L)).thenReturn(List.of(zeile(7L)));
    when(positionen.findByAuftraege(List.of(7L))).thenReturn(List.of());

    // When
    final List<Auftrag> gefunden = repository.findByVorgang(3L);

    // Then
    assertThat(gefunden).singleElement().satisfies(a -> assertThat(a.positionen()).isEmpty());
  }

  @Test
  void findByVorgang_givenAVorgangWithoutOrders_thenAsksNothingMore() {
    // Given — eine Abfrage mit leerer IN-Liste waere kein gueltiges SQL.
    when(auftraege.findByVorgang(3L)).thenReturn(List.of());

    // When
    final List<Auftrag> gefunden = repository.findByVorgang(3L);

    // Then
    assertThat(gefunden).isEmpty();
    verify(positionen, never()).findByAuftraege(any());
  }

  @Test
  void loesche_thenRemovesThePositionsBeforeTheRowThatCarriesThem() {
    // Given — der Fremdschluessel traegt kein ON DELETE (Kriterium 15).

    // When
    repository.loesche(7L);

    // Then
    final InOrder reihenfolge = inOrder(positionen, auftraege);
    reihenfolge.verify(positionen).loescheZuAuftrag(7L);
    reihenfolge.verify(auftraege).deleteById(7L);
  }

  @Test
  void vorgaengeMitAuftrag_thenAnswersWithASetOfTheKeys() {
    // Given — die Auskunft, an der der Vorgang seine Phase ableitet (Kriterium 10). Die Abfrage
    // liefert eine Liste, der Port sagt eine Menge zu.
    when(auftraege.vorgaengeMitAuftrag(List.of(3L, 4L))).thenReturn(List.of(3L, 3L));

    // When
    final Set<Long> gefunden = repository.vorgaengeMitAuftrag(List.of(3L, 4L));

    // Then
    assertThat(gefunden).containsExactly(Long.valueOf(3L));
  }

  @Test
  void bestandskandidaten_thenPassesTheQueryThroughAndCarriesThePositionsAlong() {
    // Given — Kriterium 12: die nicht abgeschlossenen Auftraege samt ihren Positionen.
    when(auftraege.bestandskandidaten(Auftragsstatus.ABGESCHLOSSEN))
        .thenReturn(List.of(zeile(7L), zeile(8L)));
    when(positionen.findByAuftraege(List.of(7L, 8L)))
        .thenReturn(
            List.of(
                positionszeile(7L, (short) 1, SCHULUNG),
                positionszeile(7L, (short) 2, KONZEPTION),
                positionszeile(8L, (short) 1, KONZEPTION)));

    // When
    final List<Auftrag> gefunden = repository.bestandskandidaten();

    // Then — in EINER zweiten Abfrage, nicht je Zeile: sonst die bekannte Abfrage-Lawine.
    assertThat(gefunden)
        .extracting(Auftrag::id, Auftrag::positionen)
        .containsExactly(tuple(7L, List.of(SCHULUNG, KONZEPTION)), tuple(8L, List.of(KONZEPTION)));
    verify(positionen, never()).findByAuftrag(anyLong());
  }

  @Test
  void bestandskandidaten_givenNoUnfinishedOrder_thenAsksNothingMore() {
    // Given — eine Abfrage mit leerer IN-Liste waere kein gueltiges SQL.
    when(auftraege.bestandskandidaten(Auftragsstatus.ABGESCHLOSSEN)).thenReturn(List.of());

    // When
    final List<Auftrag> gefunden = repository.bestandskandidaten();

    // Then
    assertThat(gefunden).isEmpty();
    verify(positionen, never()).findByAuftraege(any());
  }
}
