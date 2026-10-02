package org.mwolff.fbcrm.angebot.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.Angebotsposition;
import org.mwolff.fbcrm.angebot.domain.Angebotsstatus;
import org.mwolff.fbcrm.common.Abrechnungsmodus;
import org.mwolff.fbcrm.common.Einheit;

/**
 * Die Uebersetzung zwischen Angebot und Zeilen — in beide Richtungen.
 *
 * <p>Der Adapter steht hier gegen gemockte Spring-Data-Repositories, damit die Abbildung selbst
 * geprueft ist und nicht nur ihr Zusammenspiel mit der Datenbank ({@code AngebotPersistenceIT}).
 * Gegenstand ist vor allem: Die Plaetze der Positionen entstehen lueckenlos ab 1 aus der
 * Reihenfolge der Liste (E24), und der Status geht unveraendert hin und zurueck.
 *
 * <p><b>Dazu das Fortschreiben der Positionszeilen</b> (Plan #169, E2): Eine eingereichte Position
 * mit bekannter Kennung uebernimmt den neuen Stand in ihrer Zeile, eine ohne Kennung wird
 * eingefuegt, und die Zeilen des Angebots, die die neue Liste nicht mehr nennt, werden geloescht.
 * Eine Kennung ausserhalb der Zeilen dieses Angebots ist hier ein Programmierfehler und wirft; die
 * fachliche Abweisung mit 422 liegt im Anwendungsfall und laeuft vorher.
 */
@ExtendWith(MockitoExtension.class)
class JpaAngebotRepositoryTest {

  private static final LocalDate ANGEBOTSDATUM = LocalDate.of(2026, 9, 20);
  private static final Instant ANGELEGT = Instant.parse("2026-09-20T08:00:00Z");
  private static final Instant GEAENDERT = Instant.parse("2026-09-27T10:30:00Z");
  private static final String BESCHREIBUNG = "Neugestaltung der Website";

  private static final Angebotsposition KONZEPTION =
      new Angebotsposition(
          null,
          "Konzeption",
          Abrechnungsmodus.AUFWAND,
          new BigDecimal("2.50"),
          Einheit.PERSONENTAG,
          new BigDecimal("1000.01"));

  private static final Angebotsposition SCHULUNG =
      new Angebotsposition(
          null,
          "Schulungstag",
          Abrechnungsmodus.FESTPREIS,
          BigDecimal.ONE,
          Einheit.PAUSCHAL,
          new BigDecimal("1200.00"));

  /** Dieselbe Position wie {@link #KONZEPTION}, umbenannt — fuer das Fortschreiben einer Zeile. */
  private static final Angebotsposition UMBENANNT =
      new Angebotsposition(
          null,
          "Entwurf",
          Abrechnungsmodus.AUFWAND,
          new BigDecimal("2.50"),
          Einheit.PERSONENTAG,
          new BigDecimal("1000.01"));

  @Mock private SpringDataAngebotRepository angebote;

  @Mock private SpringDataAngebotPositionRepository positionen;

  @Captor private ArgumentCaptor<AngebotEntity> gespeicherte;

  @Captor private ArgumentCaptor<List<AngebotPositionEntity>> gespeichertePositionen;

  @Captor private ArgumentCaptor<Collection<AngebotPositionEntity>> geloeschtePositionen;

  @InjectMocks private JpaAngebotRepository repository;

  private static Angebot angebot(final List<Angebotsposition> positionen) {
    return angebot(positionen, Angebotsstatus.BESTELLT);
  }

  private static Angebot angebot(
      final List<Angebotsposition> positionen, final Angebotsstatus status) {
    return new Angebot(
        11L,
        3L,
        8L,
        status.intern(),
        status,
        ANGEBOTSDATUM,
        BESCHREIBUNG,
        positionen,
        ANGELEGT,
        GEAENDERT);
  }

  private static Angebotsposition mitKennung(
      final Angebotsposition position, final @Nullable Long id) {
    return new Angebotsposition(
        id,
        position.bezeichnung(),
        position.abrechnungsmodus(),
        position.menge(),
        position.einheit(),
        position.einzelpreis());
  }

  private static AngebotEntity zeile() {
    return zeile(11L);
  }

  private static AngebotEntity zeile(final long id) {
    return zeile(id, Angebotsstatus.BESTELLT);
  }

  private static AngebotEntity zeile(final long id, final Angebotsstatus status) {
    return new AngebotEntity(
        Long.valueOf(id),
        3L,
        8L,
        status.intern(),
        status,
        ANGEBOTSDATUM,
        BESCHREIBUNG,
        ANGELEGT,
        GEAENDERT);
  }

  private static AngebotPositionEntity positionszeile(
      final short platz, final Angebotsposition position) {
    return positionszeile(11L, platz, position);
  }

  private static AngebotPositionEntity positionszeile(
      final long angebotId, final short platz, final Angebotsposition position) {
    return new AngebotPositionEntity(
        position.id(),
        angebotId,
        platz,
        position.bezeichnung(),
        position.abrechnungsmodus(),
        position.menge(),
        position.einheit(),
        position.einzelpreis());
  }

  /**
   * Das Angebot wird geschrieben, und das Angebot hat noch keine Positionszeile.
   *
   * <p>{@code saveAll} gibt die uebergebenen Zeilen zurueck: Der Adapter uebersetzt sie danach
   * zurueck, und genau diese Uebersetzung ist hier der Gegenstand.
   */
  private void erwarteSchreibenDerZeile(final AngebotEntity zeile) {
    erwarteSchreibenDerZeile(zeile, List.of());
  }

  /** Dasselbe, aber das Angebot traegt die genannten Positionszeilen schon. */
  private void erwarteSchreibenDerZeile(
      final AngebotEntity zeile, final List<AngebotPositionEntity> vorhandene) {
    when(angebote.save(any(AngebotEntity.class))).thenReturn(zeile);
    when(positionen.findByAngebot(11L)).thenReturn(vorhandene);
    when(positionen.saveAll(any())).thenAnswer(aufruf -> aufruf.getArgument(0));
  }

  @Test
  void save_thenWritesEveryFieldIntoTheRow() {
    // Given
    erwarteSchreibenDerZeile(zeile());

    // When
    repository.save(angebot(List.of(KONZEPTION)));

    // Then
    verify(angebote).save(gespeicherte.capture());
    assertThat(gespeicherte.getValue())
        .satisfies(
            zeile -> assertThat(zeile.getId()).isEqualTo(11L),
            zeile -> assertThat(zeile.getFirmaId()).isEqualTo(3L),
            zeile -> assertThat(zeile.getAnsprechpartnerId()).isEqualTo(8L),
            zeile -> assertThat(zeile.isIntern()).isFalse(),
            zeile -> assertThat(zeile.getStatus()).isEqualTo(Angebotsstatus.BESTELLT),
            zeile -> assertThat(zeile.getAngebotDatum()).isEqualTo(ANGEBOTSDATUM),
            zeile -> assertThat(zeile.getBeschreibung()).isEqualTo(BESCHREIBUNG),
            zeile -> assertThat(zeile.getCreatedAt()).isEqualTo(ANGELEGT),
            zeile -> assertThat(zeile.getUpdatedAt()).isEqualTo(GEAENDERT));
  }

  @Test
  void save_thenNumbersThePositionsFromOneInTheOrderOfTheList() {
    // Given — E24: die Reihenfolge der Liste ist die Reihenfolge im Bestand.
    erwarteSchreibenDerZeile(zeile());

    // When
    repository.save(angebot(List.of(SCHULUNG, KONZEPTION)));

    // Then
    verify(positionen).saveAll(gespeichertePositionen.capture());
    assertThat(gespeichertePositionen.getValue())
        .extracting(
            AngebotPositionEntity::getPosition,
            AngebotPositionEntity::getBezeichnung,
            AngebotPositionEntity::getAngebotId)
        .containsExactly(
            tuple((short) 1, "Schulungstag", 11L), tuple((short) 2, "Konzeption", 11L));
  }

  @Test
  void save_thenWritesEveryFieldOfAPositionIntoItsRow() {
    // Given
    erwarteSchreibenDerZeile(zeile());

    // When
    repository.save(angebot(List.of(KONZEPTION)));

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
            zeile -> assertThat(zeile.getEinzelpreis()).isEqualTo(new BigDecimal("1000.01")));
  }

  @Test
  void save_givenAPositionWithAKnownId_thenCarriesItsRowOnInsteadOfReplacingIt() {
    // Given — Plan #169, E2: die Zeile bleibt dieselbe und uebernimmt den neuen Stand.
    final AngebotPositionEntity vorhandene = positionszeile((short) 1, mitKennung(KONZEPTION, 71L));
    erwarteSchreibenDerZeile(zeile(), List.of(vorhandene));

    // When — dieselbe Position, umbenannt und auf den zweiten Platz gestellt.
    repository.save(angebot(List.of(SCHULUNG, mitKennung(UMBENANNT, 71L))));

    // Then
    verify(positionen).saveAll(gespeichertePositionen.capture());
    assertThat(gespeichertePositionen.getValue())
        .extracting(
            AngebotPositionEntity::getId,
            AngebotPositionEntity::getPosition,
            AngebotPositionEntity::getBezeichnung)
        .containsExactly(tuple(null, (short) 1, "Schulungstag"), tuple(71L, (short) 2, "Entwurf"));
    // Die vorhandene Zeile selbst ist fortgeschrieben und nicht durch eine neue ersetzt.
    assertThat(vorhandene.getPosition()).isEqualTo((short) 2);
    assertThat(vorhandene.getBezeichnung()).isEqualTo("Entwurf");
  }

  @Test
  void save_thenDeletesOnlyTheRowsTheNewListNoLongerNames() {
    // Given — die Positionen sind Teil des Angebots: Was die neue Liste nicht nennt, faellt weg.
    final AngebotPositionEntity bleibt = positionszeile((short) 1, mitKennung(KONZEPTION, 71L));
    final AngebotPositionEntity faelltWeg = positionszeile((short) 2, mitKennung(SCHULUNG, 72L));
    erwarteSchreibenDerZeile(zeile(), List.of(bleibt, faelltWeg));

    // When
    repository.save(angebot(List.of(mitKennung(KONZEPTION, 71L))));

    // Then
    verify(positionen).deleteAll(geloeschtePositionen.capture());
    assertThat(geloeschtePositionen.getValue()).containsExactly(faelltWeg);
  }

  @Test
  void save_givenAnIdOutsideTheRowsOfTheAngebot_thenThrowsAndWritesNoPositionRow() {
    // Given — nach der Pruefung im Anwendungsfall ist das ein Programmierfehler, kein
    // Eingabefehler.
    when(angebote.save(any(AngebotEntity.class))).thenReturn(zeile());
    when(positionen.findByAngebot(11L))
        .thenReturn(List.of(positionszeile((short) 1, mitKennung(KONZEPTION, 71L))));

    final Angebot fremdeKennung = angebot(List.of(mitKennung(SCHULUNG, 4711L)));

    // When / Then
    assertThatThrownBy(() -> repository.save(fremdeKennung))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("4711");
    verify(positionen, never()).saveAll(any());
    verify(positionen, never()).deleteAll(any());
  }

  @Test
  void save_givenNoPositions_thenWritesNoPositionRowAndDeletesTheOldOnes() {
    // Given
    final AngebotPositionEntity vorhandene = positionszeile((short) 1, mitKennung(KONZEPTION, 71L));
    erwarteSchreibenDerZeile(zeile(), List.of(vorhandene));

    // When
    repository.save(angebot(List.of()));

    // Then
    verify(positionen).saveAll(gespeichertePositionen.capture());
    assertThat(gespeichertePositionen.getValue()).isEmpty();
    verify(positionen).deleteAll(geloeschtePositionen.capture());
    assertThat(geloeschtePositionen.getValue()).containsExactly(vorhandene);
  }

  @Test
  void save_thenReturnsTheOfferWithTheGeneratedIdAndItsPositions() {
    // Given
    erwarteSchreibenDerZeile(zeile());

    // When
    final Angebot gesichert = repository.save(angebot(List.of(SCHULUNG, KONZEPTION)));

    // Then
    assertThat(gesichert)
        .satisfies(
            angebot -> assertThat(angebot.id()).isEqualTo(11L),
            angebot -> assertThat(angebot.positionen()).containsExactly(SCHULUNG, KONZEPTION));
  }

  @Test
  void findById_thenTranslatesTheRowAndItsPositions() {
    // Given
    when(angebote.findById(11L)).thenReturn(Optional.of(zeile()));
    when(positionen.findByAngebot(11L))
        .thenReturn(List.of(positionszeile((short) 1, mitKennung(KONZEPTION, 71L))));

    // When
    final Optional<Angebot> gefunden = repository.findById(11L);

    // Then — die Kennung der Zeile steht danach an der Position (Plan #169, E2).
    assertThat(gefunden).contains(angebot(List.of(mitKennung(KONZEPTION, 71L))));
  }

  @Test
  void save_givenAnInternalAngebot_thenWritesTheFlagAndTheInternalStatus() {
    // Given — Issue #226: die Art geht als eigene Spalte in die Zeile.
    erwarteSchreibenDerZeile(zeile(11L, Angebotsstatus.LAEUFT));

    // When
    repository.save(angebot(List.of(KONZEPTION), Angebotsstatus.LAEUFT));

    // Then
    verify(angebote).save(gespeicherte.capture());
    assertThat(gespeicherte.getValue())
        .satisfies(
            zeile -> assertThat(zeile.isIntern()).isTrue(),
            zeile -> assertThat(zeile.getStatus()).isEqualTo(Angebotsstatus.LAEUFT));
  }

  @Test
  void findById_givenAnInternalRow_thenTranslatesTheFlagAndTheStatusBack() {
    // Given
    when(angebote.findById(11L)).thenReturn(Optional.of(zeile(11L, Angebotsstatus.LAEUFT)));
    when(positionen.findByAngebot(11L)).thenReturn(List.of());

    // When
    final Optional<Angebot> gefunden = repository.findById(11L);

    // Then
    assertThat(gefunden)
        .hasValueSatisfying(
            angebot -> {
              assertThat(angebot.intern()).isTrue();
              assertThat(angebot.status()).isEqualTo(Angebotsstatus.LAEUFT);
            });
  }

  @Test
  void findById_givenARowOfACustomerOffer_thenTheFlagStaysUnset() {
    // Given
    when(angebote.findById(11L)).thenReturn(Optional.of(zeile()));
    when(positionen.findByAngebot(11L)).thenReturn(List.of());

    // When
    final Optional<Angebot> gefunden = repository.findById(11L);

    // Then
    assertThat(gefunden).hasValueSatisfying(angebot -> assertThat(angebot.intern()).isFalse());
  }

  @Test
  void findById_thenKeepsTheOrderOfThePositionsAsRead() {
    // Given — gelesen wird nach Platz, und genau diese Folge steht danach am Angebot (E24).
    when(angebote.findById(11L)).thenReturn(Optional.of(zeile()));
    when(positionen.findByAngebot(11L))
        .thenReturn(
            List.of(positionszeile((short) 1, SCHULUNG), positionszeile((short) 2, KONZEPTION)));

    // When
    final Optional<Angebot> gefunden = repository.findById(11L);

    // Then
    assertThat(gefunden)
        .hasValueSatisfying(
            angebot -> assertThat(angebot.positionen()).containsExactly(SCHULUNG, KONZEPTION));
  }

  @Test
  void findById_givenAnUnknownId_thenEmpty() {
    // Given
    when(angebote.findById(11L)).thenReturn(Optional.empty());

    // When
    final Optional<Angebot> gefunden = repository.findById(11L);

    // Then
    assertThat(gefunden).isEmpty();
    verify(positionen, never()).findByAngebot(anyLong());
  }

  @Test
  void findByFirma_thenLoadsThePositionsOfEveryOfferInOneQuery() {
    // Given — E20: ein findByAngebot je Zeile waere die bekannte Abfrage-Lawine.
    when(angebote.findByFirma(3L)).thenReturn(List.of(zeile(11L), zeile(12L)));
    when(positionen.findByAngebote(List.of(11L, 12L)))
        .thenReturn(
            List.of(
                positionszeile(11L, (short) 1, SCHULUNG),
                positionszeile(11L, (short) 2, KONZEPTION),
                positionszeile(12L, (short) 1, KONZEPTION)));

    // When
    final List<Angebot> gefunden = repository.findByFirma(3L);

    // Then
    assertThat(gefunden)
        .extracting(Angebot::id, Angebot::positionen)
        .containsExactly(
            tuple(11L, List.of(SCHULUNG, KONZEPTION)), tuple(12L, List.of(KONZEPTION)));
    verify(positionen, never()).findByAngebot(anyLong());
  }

  @Test
  void findByFirma_givenAnOfferWithoutPositions_thenTranslatesItWithAnEmptyList() {
    // Given — ein frisch angelegtes Angebot hat noch keine Position.
    when(angebote.findByFirma(3L)).thenReturn(List.of(zeile(11L)));
    when(positionen.findByAngebote(List.of(11L))).thenReturn(List.of());

    // When
    final List<Angebot> gefunden = repository.findByFirma(3L);

    // Then
    assertThat(gefunden).singleElement().satisfies(a -> assertThat(a.positionen()).isEmpty());
  }

  @Test
  void findByFirma_givenAFirmaWithoutOffers_thenAsksNothingMore() {
    // Given — eine Abfrage mit leerer IN-Liste waere kein gueltiges SQL.
    when(angebote.findByFirma(3L)).thenReturn(List.of());

    // When
    final List<Angebot> gefunden = repository.findByFirma(3L);

    // Then
    assertThat(gefunden).isEmpty();
    verify(positionen, never()).findByAngebote(any());
  }

  @Test
  void findAlle_withoutAStatus_thenReadsEveryRowWithItsPositionsInOneQuery() {
    // Given
    when(angebote.findAll()).thenReturn(List.of(zeile(11L), zeile(12L)));
    when(positionen.findByAngebote(List.of(11L, 12L)))
        .thenReturn(
            List.of(
                positionszeile(11L, (short) 1, KONZEPTION),
                positionszeile(12L, (short) 1, SCHULUNG)));

    // When
    final List<Angebot> gefunden = repository.findAlle(Optional.empty());

    // Then
    assertThat(gefunden)
        .extracting(Angebot::id, Angebot::positionen)
        .containsExactly(tuple(11L, List.of(KONZEPTION)), tuple(12L, List.of(SCHULUNG)));
    verify(angebote, never()).findByStatus(any());
    verify(positionen, never()).findByAngebot(anyLong());
  }

  @Test
  void findAlle_withAStatus_thenAsksOnlyForThatStatus() {
    // Given
    when(angebote.findByStatus(Angebotsstatus.BESTELLT)).thenReturn(List.of(zeile(11L)));
    when(positionen.findByAngebote(List.of(11L))).thenReturn(List.of());

    // When
    final List<Angebot> gefunden = repository.findAlle(Optional.of(Angebotsstatus.BESTELLT));

    // Then
    assertThat(gefunden).extracting(Angebot::id).containsExactly(11L);
    verify(angebote, never()).findAll();
  }
}
