package org.mwolff.fbcrm.rechnung.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
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
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.common.Anschrift;
import org.mwolff.fbcrm.common.Einheit;
import org.mwolff.fbcrm.rechnung.domain.Belegabsender;
import org.mwolff.fbcrm.rechnung.domain.Belegempfaenger;
import org.mwolff.fbcrm.rechnung.domain.Rechnung;
import org.mwolff.fbcrm.rechnung.domain.Rechnungsposition;
import org.mwolff.fbcrm.rechnung.domain.Rechnungszustand;

/**
 * Die Uebersetzung zwischen Rechnung und Zeilen — in beide Richtungen.
 *
 * <p>Der Adapter steht hier gegen gemockte Spring-Data-Schnittstellen, damit die Abbildung selbst
 * geprueft ist und nicht nur ihr Zusammenspiel mit der Datenbank ({@code RechnungPersistenceIT}).
 * Gegenstand ist vor allem: Die Plaetze der Positionen entstehen lueckenlos ab 1 aus der
 * Reihenfolge der Liste (E24), die Positionszeilen werden ueber ihre Angebotsposition
 * fortgeschrieben statt ersetzt, und die beiden Kopien gehen ueber ihre Einzelspalten hin und
 * unveraendert zurueck.
 */
@ExtendWith(MockitoExtension.class)
class JpaRechnungRepositoryTest {

  private static final long RECHNUNG_ID = 11L;
  private static final long ANGEBOT_ID = 3L;
  private static final LocalDate RECHNUNGSDATUM = LocalDate.of(2026, 9, 30);
  private static final Instant ANGELEGT = Instant.parse("2026-09-30T08:00:00Z");
  private static final Instant GEAENDERT = Instant.parse("2026-10-01T09:15:00Z");
  private static final String ZEITRAUM = "September 2026";
  private static final BigDecimal NEUNZEHN = new BigDecimal("19.00");

  private static final Rechnungsposition BERATUNG =
      new Rechnungsposition(
          7L, "Beratung", new BigDecimal("3.00"), Einheit.STUNDE, new BigDecimal("120.00"));

  private static final Rechnungsposition KONZEPTION =
      new Rechnungsposition(
          8L, "Konzeption", new BigDecimal("2.50"), Einheit.PERSONENTAG, new BigDecimal("999.00"));

  private static final Belegempfaenger EMPFAENGER =
      new Belegempfaenger(
          "Adler AG", new Anschrift("Hauptstrasse 1", "28195", "Bremen", "Deutschland"), null);

  private static final Belegabsender ABSENDER =
      new Belegabsender(
          "Manfred Wolff",
          "Softwarearchitekt",
          new Anschrift("Am Deich 2", "28199", "Bremen", "Deutschland"),
          "post@example.org",
          "0421 123456",
          "75/123/45678",
          "DE123456789",
          "DE02 1203 0000 0000 2020 51",
          "https://example.org");

  @Mock private SpringDataRechnungRepository rechnungen;

  @Mock private SpringDataRechnungPositionRepository positionen;

  @Captor private ArgumentCaptor<RechnungEntity> gespeicherte;

  @Captor private ArgumentCaptor<List<RechnungPositionEntity>> gespeichertePositionen;

  @Captor private ArgumentCaptor<Collection<RechnungPositionEntity>> geloeschtePositionen;

  @InjectMocks private JpaRechnungRepository repository;

  private static Rechnung entwurf(final List<Rechnungsposition> positionen) {
    return new Rechnung(
        RECHNUNG_ID,
        ANGEBOT_ID,
        Rechnungszustand.ENTWURF,
        RECHNUNGSDATUM,
        ZEITRAUM,
        positionen,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        ANGELEGT,
        ANGELEGT);
  }

  private static Rechnung gestellt(final List<Rechnungsposition> positionen) {
    return entwurf(positionen)
        .gestellt("R26-0004", NEUNZEHN, 10, EMPFAENGER, ABSENDER, GEAENDERT)
        .mitDokument("rechnung/11/abc.pdf");
  }

  private static RechnungEntity entwurfszeile() {
    return new RechnungEntity(
        RECHNUNG_ID,
        ANGEBOT_ID,
        Rechnungszustand.ENTWURF,
        RECHNUNGSDATUM,
        ZEITRAUM,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        ANGELEGT,
        ANGELEGT);
  }

  private static RechnungPositionEntity positionszeile(
      final short platz, final Rechnungsposition position) {
    return positionszeile(RECHNUNG_ID, platz, position);
  }

  private static RechnungPositionEntity positionszeile(
      final long rechnungId, final short platz, final Rechnungsposition position) {
    return new RechnungPositionEntity(
        null,
        rechnungId,
        position.angebotPositionId(),
        platz,
        position.bezeichnung(),
        position.menge(),
        position.einheit(),
        position.einzelpreis());
  }

  /**
   * Die Rechnung wird geschrieben, und sie hat noch keine Positionszeile.
   *
   * <p>{@code saveAll} gibt die uebergebenen Zeilen zurueck: Der Adapter uebersetzt sie danach
   * zurueck, und genau diese Uebersetzung ist hier der Gegenstand.
   */
  private void erwarteSchreibenDerZeile(final RechnungEntity zeile) {
    erwarteSchreibenDerZeile(zeile, List.of());
  }

  /** Dasselbe, aber die Rechnung traegt die genannten Positionszeilen schon. */
  private void erwarteSchreibenDerZeile(
      final RechnungEntity zeile, final List<RechnungPositionEntity> vorhandene) {
    when(rechnungen.save(any(RechnungEntity.class))).thenReturn(zeile);
    when(positionen.findByRechnung(RECHNUNG_ID)).thenReturn(vorhandene);
    when(positionen.saveAll(any())).thenAnswer(aufruf -> aufruf.getArgument(0));
  }

  @Test
  void save_givenADraft_thenWritesEveryFieldIntoTheRow() {
    // Given
    erwarteSchreibenDerZeile(entwurfszeile());

    // When
    repository.save(entwurf(List.of(BERATUNG)));

    // Then
    verify(rechnungen).save(gespeicherte.capture());
    assertThat(gespeicherte.getValue())
        .satisfies(
            zeile -> assertThat(zeile.getId()).isEqualTo(RECHNUNG_ID),
            zeile -> assertThat(zeile.getAngebotId()).isEqualTo(ANGEBOT_ID),
            zeile -> assertThat(zeile.getZustand()).isEqualTo(Rechnungszustand.ENTWURF),
            zeile -> assertThat(zeile.getRechnungDatum()).isEqualTo(RECHNUNGSDATUM),
            zeile -> assertThat(zeile.getLeistungszeitraum()).isEqualTo(ZEITRAUM),
            zeile -> assertThat(zeile.getNummer()).isNull(),
            zeile -> assertThat(zeile.getSteuersatz()).isNull(),
            zeile -> assertThat(zeile.getZahlungszielTage()).isNull(),
            zeile -> assertThat(zeile.getGestelltAm()).isNull(),
            zeile -> assertThat(zeile.getPdfSchluessel()).isNull(),
            zeile -> assertThat(zeile.getEmpfaenger()).isNull(),
            zeile -> assertThat(zeile.getAbsender()).isNull(),
            zeile -> assertThat(zeile.getCreatedAt()).isEqualTo(ANGELEGT),
            zeile -> assertThat(zeile.getUpdatedAt()).isEqualTo(ANGELEGT));
  }

  @Test
  void save_givenAnIssuedRechnung_thenWritesTheFrozenFieldsAndBothCopies() {
    // Given
    erwarteSchreibenDerZeile(entwurfszeile());

    // When
    repository.save(gestellt(List.of(BERATUNG)));

    // Then — die Kopien gehen ueber ihre Einzelspalten und kommen unveraendert zurueck.
    verify(rechnungen).save(gespeicherte.capture());
    assertThat(gespeicherte.getValue())
        .satisfies(
            zeile -> assertThat(zeile.getZustand()).isEqualTo(Rechnungszustand.GESTELLT),
            zeile -> assertThat(zeile.getNummer()).isEqualTo("R26-0004"),
            zeile -> assertThat(zeile.getSteuersatz()).isEqualByComparingTo(NEUNZEHN),
            zeile -> assertThat(zeile.getZahlungszielTage()).isEqualTo(10),
            zeile -> assertThat(zeile.getGestelltAm()).isEqualTo(GEAENDERT),
            zeile -> assertThat(zeile.getPdfSchluessel()).isEqualTo("rechnung/11/abc.pdf"),
            zeile -> assertThat(zeile.getEmpfaenger()).isEqualTo(EMPFAENGER),
            zeile -> assertThat(zeile.getAbsender()).isEqualTo(ABSENDER));
  }

  @Test
  void save_thenNumbersThePositionsFromOneInTheOrderOfTheList() {
    // Given — E24: die Reihenfolge der Liste ist die Reihenfolge im Bestand.
    erwarteSchreibenDerZeile(entwurfszeile());

    // When
    repository.save(entwurf(List.of(KONZEPTION, BERATUNG)));

    // Then
    verify(positionen).saveAll(gespeichertePositionen.capture());
    assertThat(gespeichertePositionen.getValue())
        .extracting(
            RechnungPositionEntity::getPosition, RechnungPositionEntity::getAngebotPositionId)
        .containsExactly(tuple((short) 1, 8L), tuple((short) 2, 7L));
  }

  @Test
  void save_thenWritesEveryFieldOfAPositionRow() {
    // Given
    erwarteSchreibenDerZeile(entwurfszeile());

    // When
    repository.save(entwurf(List.of(BERATUNG)));

    // Then
    verify(positionen).saveAll(gespeichertePositionen.capture());
    assertThat(gespeichertePositionen.getValue())
        .singleElement()
        .satisfies(
            zeile -> assertThat(zeile.getRechnungId()).isEqualTo(RECHNUNG_ID),
            zeile -> assertThat(zeile.getAngebotPositionId()).isEqualTo(7L),
            zeile -> assertThat(zeile.getBezeichnung()).isEqualTo("Beratung"),
            zeile -> assertThat(zeile.getMenge()).isEqualByComparingTo("3.00"),
            zeile -> assertThat(zeile.getEinheit()).isEqualTo(Einheit.STUNDE),
            zeile -> assertThat(zeile.getEinzelpreis()).isEqualByComparingTo("120.00"));
  }

  @Test
  void save_givenAPositionOfAKnownAngebotsposition_thenCarriesItsRowForward() {
    // Given — die Zeile zur Angebotsposition 7 gibt es schon.
    final RechnungPositionEntity vorhanden = positionszeile((short) 2, BERATUNG);
    erwarteSchreibenDerZeile(entwurfszeile(), List.of(vorhanden));
    final Rechnungsposition weniger =
        new Rechnungsposition(
            7L, "Beratung gekuerzt", BigDecimal.ONE, Einheit.STUNDE, new BigDecimal("130.00"));

    // When
    repository.save(entwurf(List.of(weniger)));

    // Then — dieselbe Zeile, neuer Stand, neuer Platz; nichts wird geloescht.
    verify(positionen).saveAll(gespeichertePositionen.capture());
    assertThat(gespeichertePositionen.getValue())
        .singleElement()
        .satisfies(
            zeile -> assertThat(zeile).isSameAs(vorhanden),
            zeile -> assertThat(zeile.getPosition()).isEqualTo((short) 1),
            zeile -> assertThat(zeile.getBezeichnung()).isEqualTo("Beratung gekuerzt"),
            zeile -> assertThat(zeile.getMenge()).isEqualByComparingTo("1"),
            zeile -> assertThat(zeile.getEinzelpreis()).isEqualByComparingTo("130.00"));
    verify(positionen).deleteAll(geloeschtePositionen.capture());
    assertThat(geloeschtePositionen.getValue()).isEmpty();
  }

  @Test
  void save_givenAnOmittedPosition_thenItsRowIsDeleted() {
    // Given
    final RechnungPositionEntity beratung = positionszeile((short) 1, BERATUNG);
    final RechnungPositionEntity konzeption = positionszeile((short) 2, KONZEPTION);
    erwarteSchreibenDerZeile(entwurfszeile(), List.of(beratung, konzeption));

    // When — die Position zur Angebotsposition 8 fehlt in der neuen Liste.
    repository.save(entwurf(List.of(BERATUNG)));

    // Then
    verify(positionen).deleteAll(geloeschtePositionen.capture());
    assertThat(geloeschtePositionen.getValue()).containsExactly(konzeption);
  }

  @Test
  void save_givenADraftRow_thenReadsItBackWithoutCopies() {
    // Given
    erwarteSchreibenDerZeile(entwurfszeile());

    // When
    final Rechnung gelesen = repository.save(entwurf(List.of(BERATUNG)));

    // Then
    assertThat(gelesen)
        .satisfies(
            rechnung -> assertThat(rechnung.id()).isEqualTo(RECHNUNG_ID),
            rechnung -> assertThat(rechnung.angebotId()).isEqualTo(ANGEBOT_ID),
            rechnung -> assertThat(rechnung.zustand()).isEqualTo(Rechnungszustand.ENTWURF),
            rechnung -> assertThat(rechnung.rechnungDatum()).isEqualTo(RECHNUNGSDATUM),
            rechnung -> assertThat(rechnung.leistungszeitraum()).isEqualTo(ZEITRAUM),
            rechnung -> assertThat(rechnung.nummer()).isNull(),
            rechnung -> assertThat(rechnung.steuersatz()).isNull(),
            rechnung -> assertThat(rechnung.zahlungszielTage()).isNull(),
            rechnung -> assertThat(rechnung.gestelltAm()).isNull(),
            rechnung -> assertThat(rechnung.pdfSchluessel()).isNull(),
            rechnung -> assertThat(rechnung.empfaenger()).isNull(),
            rechnung -> assertThat(rechnung.absender()).isNull(),
            rechnung -> assertThat(rechnung.createdAt()).isEqualTo(ANGELEGT),
            rechnung -> assertThat(rechnung.updatedAt()).isEqualTo(ANGELEGT),
            rechnung -> assertThat(rechnung.positionen()).containsExactly(BERATUNG));
  }

  @Test
  void findById_givenAnIssuedRow_thenReadsBothCopiesBack() {
    // Given
    final RechnungEntity zeile =
        new RechnungEntity(
            RECHNUNG_ID,
            ANGEBOT_ID,
            Rechnungszustand.GESTELLT,
            RECHNUNGSDATUM,
            ZEITRAUM,
            "R26-0004",
            NEUNZEHN,
            10,
            GEAENDERT,
            "rechnung/11/abc.pdf",
            EMPFAENGER,
            ABSENDER,
            ANGELEGT,
            GEAENDERT);
    when(rechnungen.findById(RECHNUNG_ID)).thenReturn(Optional.of(zeile));
    when(positionen.findByRechnung(RECHNUNG_ID))
        .thenReturn(List.of(positionszeile((short) 1, BERATUNG)));

    // When
    final Optional<Rechnung> gelesen = repository.findById(RECHNUNG_ID);

    // Then
    assertThat(gelesen)
        .hasValueSatisfying(
            rechnung -> {
              assertThat(rechnung.zustand()).isEqualTo(Rechnungszustand.GESTELLT);
              assertThat(rechnung.nummer()).isEqualTo("R26-0004");
              assertThat(rechnung.steuersatz()).isEqualByComparingTo(NEUNZEHN);
              assertThat(rechnung.zahlungszielTage()).isEqualTo(10);
              assertThat(rechnung.gestelltAm()).isEqualTo(GEAENDERT);
              assertThat(rechnung.pdfSchluessel()).isEqualTo("rechnung/11/abc.pdf");
              assertThat(rechnung.empfaenger()).isEqualTo(EMPFAENGER);
              assertThat(rechnung.absender()).isEqualTo(ABSENDER);
              assertThat(rechnung.positionen()).containsExactly(BERATUNG);
            });
  }

  @Test
  void findById_givenAnUnknownId_thenEmpty() {
    // Given
    when(rechnungen.findById(4711L)).thenReturn(Optional.empty());

    // When / Then
    assertThat(repository.findById(4711L)).isEmpty();
    verify(positionen, never()).findByRechnung(anyLong());
  }

  @Test
  void findByAngebot_thenAssignsThePositionsToTheirRechnung() {
    // Given — die Positionen aller Rechnungen kommen in EINER zweiten Abfrage.
    final RechnungEntity zweite =
        new RechnungEntity(
            12L,
            ANGEBOT_ID,
            Rechnungszustand.ENTWURF,
            RECHNUNGSDATUM,
            ZEITRAUM,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            ANGELEGT,
            ANGELEGT);
    when(rechnungen.findByAngebot(ANGEBOT_ID)).thenReturn(List.of(entwurfszeile(), zweite));
    when(positionen.findByRechnungen(List.of(RECHNUNG_ID, 12L)))
        .thenReturn(
            List.of(
                positionszeile(RECHNUNG_ID, (short) 1, BERATUNG),
                positionszeile(12L, (short) 1, KONZEPTION)));

    // When
    final List<Rechnung> gelesen = repository.findByAngebot(ANGEBOT_ID);

    // Then
    assertThat(gelesen)
        .extracting(Rechnung::id, Rechnung::positionen)
        .containsExactly(tuple(RECHNUNG_ID, List.of(BERATUNG)), tuple(12L, List.of(KONZEPTION)));
  }

  @Test
  void findByAngebot_givenNoRechnung_thenEmptyAndNoSecondQuery() {
    // Given — eine Abfrage mit leerer IN-Liste waere kein gueltiges SQL.
    when(rechnungen.findByAngebot(ANGEBOT_ID)).thenReturn(List.of());

    // When / Then
    assertThat(repository.findByAngebot(ANGEBOT_ID)).isEmpty();
    verify(positionen, never()).findByRechnungen(any());
  }

  @Test
  void findAlle_thenLoadsEveryRechnungWithItsPositions() {
    // Given
    when(rechnungen.findAll()).thenReturn(List.of(entwurfszeile()));
    when(positionen.findByRechnungen(List.of(RECHNUNG_ID)))
        .thenReturn(List.of(positionszeile(RECHNUNG_ID, (short) 1, BERATUNG)));

    // When / Then
    assertThat(repository.findAlle())
        .singleElement()
        .satisfies(rechnung -> assertThat(rechnung.positionen()).containsExactly(BERATUNG));
  }

  @Test
  void findAlle_givenARechnungWithoutPositions_thenItIsReadWithAnEmptyList() {
    // Given
    when(rechnungen.findAll()).thenReturn(List.of(entwurfszeile()));
    when(positionen.findByRechnungen(List.of(RECHNUNG_ID))).thenReturn(List.of());

    // When / Then
    assertThat(repository.findAlle())
        .singleElement()
        .satisfies(rechnung -> assertThat(rechnung.positionen()).isEmpty());
  }

  @Test
  void delete_thenRemovesThePositionRowsAndTheRechnung() {
    // Given
    final RechnungPositionEntity beratung = positionszeile((short) 1, BERATUNG);
    when(positionen.findByRechnung(RECHNUNG_ID)).thenReturn(List.of(beratung));

    // When
    repository.delete(RECHNUNG_ID);

    // Then
    verify(positionen).deleteAll(geloeschtePositionen.capture());
    assertThat(geloeschtePositionen.getValue()).containsExactly(beratung);
    verify(rechnungen).deleteById(RECHNUNG_ID);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void existiertNummer_thenPassesTheAnswerOn(final boolean vergeben) {
    // Given
    when(rechnungen.existiertNummer("R26-0004")).thenReturn(vergeben);

    // When / Then
    assertThat(repository.existiertNummer("R26-0004")).isEqualTo(vergeben);
  }
}
