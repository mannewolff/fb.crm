package org.mwolff.fbcrm.angebot.infrastructure;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.Angebotsposition;
import org.mwolff.fbcrm.angebot.domain.Angebotszustand;
import org.mwolff.fbcrm.angebot.domain.Belegabsender;
import org.mwolff.fbcrm.angebot.domain.Belegempfaenger;
import org.mwolff.fbcrm.common.Abrechnungsmodus;
import org.mwolff.fbcrm.common.Anschrift;
import org.mwolff.fbcrm.common.Einheit;

/**
 * Die Uebersetzung zwischen Angebot und Zeilen — in beide Richtungen.
 *
 * <p>Der Adapter steht hier gegen gemockte Spring-Data-Repositories, damit die Abbildung selbst
 * geprueft ist und nicht nur ihr Zusammenspiel mit der Datenbank ({@code AngebotPersistenceIT}).
 * Zwei Zusagen sind hier der Gegenstand: Die Plaetze der Positionen entstehen lueckenlos ab 1 aus
 * der Reihenfolge der Liste (E24), und die beiden Anschriftskopien stehen im Entwurf vollstaendig
 * leer und ab dem Versenden vollstaendig da (R8).
 */
@ExtendWith(MockitoExtension.class)
class JpaAngebotRepositoryTest {

  private static final LocalDate ANGEBOTSDATUM = LocalDate.of(2026, 9, 20);
  private static final LocalDate GUELTIG_BIS = LocalDate.of(2026, 10, 20);
  private static final Instant ANGELEGT = Instant.parse("2026-09-20T08:00:00Z");
  private static final Instant GEAENDERT = Instant.parse("2026-09-27T10:30:00Z");
  private static final String NUMMER = "A-2026-001";
  private static final String PDF_SCHLUESSEL = "angebot/11/6f1c9a.pdf";
  private static final String BESCHREIBUNG = "Neugestaltung der Website";
  private static final String BEDINGUNGEN = "Zahlbar innerhalb von 14 Tagen ohne Abzug.";

  private static final Belegempfaenger EMPFAENGER =
      new Belegempfaenger(
          "Adler AG",
          new Anschrift("Hauptstrasse 1", "28195", "Bremen", "Deutschland"),
          "Frau Adler");

  private static final Belegabsender ABSENDER =
      new Belegabsender(
          "Manfred Wolff",
          new Anschrift("Am Deich 2", "28199", "Hansestadt", "Bundesrepublik"),
          "manne@example.org",
          "0421 123456",
          "75/123/45678",
          "DE123456789",
          "IBAN DE00 1234");

  private static final Angebotsposition KONZEPTION =
      new Angebotsposition(
          "Konzeption",
          Abrechnungsmodus.AUFWAND,
          new BigDecimal("2.50"),
          Einheit.PERSONENTAG,
          new BigDecimal("1000.01"));

  private static final Angebotsposition SCHULUNG =
      new Angebotsposition(
          "Schulungstag",
          Abrechnungsmodus.FESTPREIS,
          BigDecimal.ONE,
          Einheit.PAUSCHAL,
          new BigDecimal("1200.00"));

  @Mock private SpringDataAngebotRepository angebote;

  @Mock private SpringDataAngebotPositionRepository positionen;

  @Captor private ArgumentCaptor<AngebotEntity> gespeicherte;

  @Captor private ArgumentCaptor<List<AngebotPositionEntity>> gespeichertePositionen;

  @InjectMocks private JpaAngebotRepository repository;

  private static Angebot entwurf(final List<Angebotsposition> positionen) {
    return new Angebot(
        11L,
        3L,
        8L,
        null,
        Angebotszustand.ENTWURF,
        ANGEBOTSDATUM,
        GUELTIG_BIS,
        BESCHREIBUNG,
        BEDINGUNGEN,
        null,
        null,
        null,
        null,
        null,
        positionen,
        ANGELEGT,
        GEAENDERT);
  }

  private static Angebot versendet() {
    return new Angebot(
        11L,
        3L,
        8L,
        NUMMER,
        Angebotszustand.VERSENDET,
        ANGEBOTSDATUM,
        GUELTIG_BIS,
        BESCHREIBUNG,
        BEDINGUNGEN,
        GEAENDERT,
        null,
        PDF_SCHLUESSEL,
        EMPFAENGER,
        ABSENDER,
        List.of(KONZEPTION),
        ANGELEGT,
        GEAENDERT);
  }

  private static AngebotEntity entwurfszeile() {
    return new AngebotEntity(
        11L,
        3L,
        8L,
        null,
        Angebotszustand.ENTWURF,
        ANGEBOTSDATUM,
        GUELTIG_BIS,
        BESCHREIBUNG,
        BEDINGUNGEN,
        null,
        null,
        null,
        ANGELEGT,
        GEAENDERT);
  }

  private static AngebotEntity versandzeile() {
    final AngebotEntity zeile =
        new AngebotEntity(
            11L,
            3L,
            8L,
            NUMMER,
            Angebotszustand.VERSENDET,
            ANGEBOTSDATUM,
            GUELTIG_BIS,
            BESCHREIBUNG,
            BEDINGUNGEN,
            GEAENDERT,
            null,
            PDF_SCHLUESSEL,
            ANGELEGT,
            GEAENDERT);
    zeile.setzeEmpfaenger(EMPFAENGER);
    zeile.setzeAbsender(ABSENDER);
    return zeile;
  }

  private static Angebot angenommen() {
    return new Angebot(
        11L,
        3L,
        8L,
        NUMMER,
        Angebotszustand.ANGENOMMEN,
        ANGEBOTSDATUM,
        GUELTIG_BIS,
        BESCHREIBUNG,
        BEDINGUNGEN,
        ANGELEGT,
        GEAENDERT,
        PDF_SCHLUESSEL,
        EMPFAENGER,
        ABSENDER,
        List.of(KONZEPTION),
        ANGELEGT,
        GEAENDERT);
  }

  private static AngebotEntity reaktionszeile() {
    final AngebotEntity zeile =
        new AngebotEntity(
            11L,
            3L,
            8L,
            NUMMER,
            Angebotszustand.ANGENOMMEN,
            ANGEBOTSDATUM,
            GUELTIG_BIS,
            BESCHREIBUNG,
            BEDINGUNGEN,
            ANGELEGT,
            GEAENDERT,
            PDF_SCHLUESSEL,
            ANGELEGT,
            GEAENDERT);
    zeile.setzeEmpfaenger(EMPFAENGER);
    zeile.setzeAbsender(ABSENDER);
    return zeile;
  }

  private static AngebotPositionEntity positionszeile(
      final short platz, final Angebotsposition position) {
    return new AngebotPositionEntity(
        null,
        11L,
        platz,
        position.bezeichnung(),
        position.abrechnungsmodus(),
        position.menge(),
        position.einheit(),
        position.einzelpreis());
  }

  private void erwarteSchreibenDerZeile(final AngebotEntity zeile) {
    when(angebote.save(any(AngebotEntity.class))).thenReturn(zeile);
    when(positionen.saveAll(any())).thenAnswer(aufruf -> aufruf.getArgument(0));
  }

  @Test
  void save_thenWritesEveryFieldOfTheDraftIntoTheRow() {
    // Given
    erwarteSchreibenDerZeile(entwurfszeile());

    // When
    repository.save(entwurf(List.of(KONZEPTION)));

    // Then
    verify(angebote).save(gespeicherte.capture());
    assertThat(gespeicherte.getValue())
        .satisfies(
            zeile -> assertThat(zeile.getId()).isEqualTo(11L),
            zeile -> assertThat(zeile.getFirmaId()).isEqualTo(3L),
            zeile -> assertThat(zeile.getAnsprechpartnerId()).isEqualTo(8L),
            zeile -> assertThat(zeile.getNummer()).isNull(),
            zeile -> assertThat(zeile.getZustand()).isEqualTo(Angebotszustand.ENTWURF),
            zeile -> assertThat(zeile.getAngebotDatum()).isEqualTo(ANGEBOTSDATUM),
            zeile -> assertThat(zeile.getGueltigBis()).isEqualTo(GUELTIG_BIS),
            zeile -> assertThat(zeile.getLeistungsbeschreibung()).isEqualTo(BESCHREIBUNG),
            zeile -> assertThat(zeile.getZahlungsbedingungen()).isEqualTo(BEDINGUNGEN),
            zeile -> assertThat(zeile.getVersendetAm()).isNull(),
            zeile -> assertThat(zeile.getReaktionAm()).isNull(),
            zeile -> assertThat(zeile.getPdfSchluessel()).isNull(),
            zeile -> assertThat(zeile.getCreatedAt()).isEqualTo(ANGELEGT),
            zeile -> assertThat(zeile.getUpdatedAt()).isEqualTo(GEAENDERT));
  }

  @Test
  void save_givenADraft_thenLeavesEveryColumnOfBothCopiesEmpty() {
    // Given — E27: der Entwurf traegt keine Kopie, und die Datenbank haelt das fest.
    erwarteSchreibenDerZeile(entwurfszeile());

    // When
    repository.save(entwurf(List.of(KONZEPTION)));

    // Then
    verify(angebote).save(gespeicherte.capture());
    assertThat(gespeicherte.getValue())
        .satisfies(
            zeile -> assertThat(zeile.getEmpfaengerFirma()).isNull(),
            zeile -> assertThat(zeile.getEmpfaengerStrasse()).isNull(),
            zeile -> assertThat(zeile.getEmpfaengerPlz()).isNull(),
            zeile -> assertThat(zeile.getEmpfaengerOrt()).isNull(),
            zeile -> assertThat(zeile.getEmpfaengerLand()).isNull(),
            zeile -> assertThat(zeile.getEmpfaengerAnsprechpartner()).isNull(),
            zeile -> assertThat(zeile.getAbsenderName()).isNull(),
            zeile -> assertThat(zeile.getAbsenderStrasse()).isNull(),
            zeile -> assertThat(zeile.getAbsenderPlz()).isNull(),
            zeile -> assertThat(zeile.getAbsenderOrt()).isNull(),
            zeile -> assertThat(zeile.getAbsenderLand()).isNull(),
            zeile -> assertThat(zeile.getAbsenderEmail()).isNull(),
            zeile -> assertThat(zeile.getAbsenderTelefon()).isNull(),
            zeile -> assertThat(zeile.getAbsenderSteuernummer()).isNull(),
            zeile -> assertThat(zeile.getAbsenderUmsatzsteuerId()).isNull(),
            zeile -> assertThat(zeile.getAbsenderBankverbindung()).isNull());
  }

  @Test
  void save_givenACommittedOffer_thenWritesEveryColumnOfBothCopies() {
    // Given — R8: die Kopien stehen am Dokument und nicht als Verweis.
    erwarteSchreibenDerZeile(versandzeile());

    // When
    repository.save(versendet());

    // Then
    verify(angebote).save(gespeicherte.capture());
    assertThat(gespeicherte.getValue())
        .satisfies(
            zeile -> assertThat(zeile.getNummer()).isEqualTo(NUMMER),
            zeile -> assertThat(zeile.getVersendetAm()).isEqualTo(GEAENDERT),
            zeile -> assertThat(zeile.getPdfSchluessel()).isEqualTo(PDF_SCHLUESSEL),
            zeile -> assertThat(zeile.getEmpfaengerFirma()).isEqualTo("Adler AG"),
            zeile -> assertThat(zeile.getEmpfaengerStrasse()).isEqualTo("Hauptstrasse 1"),
            zeile -> assertThat(zeile.getEmpfaengerPlz()).isEqualTo("28195"),
            zeile -> assertThat(zeile.getEmpfaengerOrt()).isEqualTo("Bremen"),
            zeile -> assertThat(zeile.getEmpfaengerLand()).isEqualTo("Deutschland"),
            zeile -> assertThat(zeile.getEmpfaengerAnsprechpartner()).isEqualTo("Frau Adler"),
            zeile -> assertThat(zeile.getAbsenderName()).isEqualTo("Manfred Wolff"),
            zeile -> assertThat(zeile.getAbsenderStrasse()).isEqualTo("Am Deich 2"),
            zeile -> assertThat(zeile.getAbsenderPlz()).isEqualTo("28199"),
            zeile -> assertThat(zeile.getAbsenderOrt()).isEqualTo("Hansestadt"),
            zeile -> assertThat(zeile.getAbsenderLand()).isEqualTo("Bundesrepublik"),
            zeile -> assertThat(zeile.getAbsenderEmail()).isEqualTo("manne@example.org"),
            zeile -> assertThat(zeile.getAbsenderTelefon()).isEqualTo("0421 123456"),
            zeile -> assertThat(zeile.getAbsenderSteuernummer()).isEqualTo("75/123/45678"),
            zeile -> assertThat(zeile.getAbsenderUmsatzsteuerId()).isEqualTo("DE123456789"),
            zeile -> assertThat(zeile.getAbsenderBankverbindung()).isEqualTo("IBAN DE00 1234"));
  }

  @Test
  void save_thenNumbersThePositionsFromOneInTheOrderOfTheList() {
    // Given — E24: die Reihenfolge der Liste ist die Reihenfolge im Bestand.
    erwarteSchreibenDerZeile(entwurfszeile());

    // When
    repository.save(entwurf(List.of(SCHULUNG, KONZEPTION)));

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
    erwarteSchreibenDerZeile(entwurfszeile());

    // When
    repository.save(entwurf(List.of(KONZEPTION)));

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
  void save_thenDeletesTheOldPositionsBeforeWritingTheNewOnes() {
    // Given — sonst stiesse die neue Reihenfolge auf die alten Plaetze (UNIQUE je Platz).
    erwarteSchreibenDerZeile(entwurfszeile());

    // When
    repository.save(entwurf(List.of(KONZEPTION)));

    // Then
    final InOrder reihenfolge = inOrder(positionen);
    reihenfolge.verify(positionen).loescheZuAngebot(11L);
    reihenfolge.verify(positionen).saveAll(any());
  }

  @Test
  void save_givenADraftWithoutPositions_thenWritesNoPositionRow() {
    // Given
    erwarteSchreibenDerZeile(entwurfszeile());

    // When
    repository.save(entwurf(List.of()));

    // Then
    verify(positionen).saveAll(gespeichertePositionen.capture());
    assertThat(gespeichertePositionen.getValue()).isEmpty();
  }

  @Test
  void save_thenReturnsTheOfferWithTheGeneratedIdAndItsPositions() {
    // Given
    erwarteSchreibenDerZeile(entwurfszeile());

    // When
    final Angebot gesichert = repository.save(entwurf(List.of(SCHULUNG, KONZEPTION)));

    // Then
    assertThat(gesichert)
        .satisfies(
            angebot -> assertThat(angebot.id()).isEqualTo(11L),
            angebot -> assertThat(angebot.positionen()).containsExactly(SCHULUNG, KONZEPTION));
  }

  @Test
  void findById_givenADraft_thenTranslatesTheRowWithoutCopies() {
    // Given
    when(angebote.findById(11L)).thenReturn(Optional.of(entwurfszeile()));
    when(positionen.findByAngebot(11L)).thenReturn(List.of(positionszeile((short) 1, KONZEPTION)));

    // When
    final Optional<Angebot> gefunden = repository.findById(11L);

    // Then
    assertThat(gefunden).contains(entwurf(List.of(KONZEPTION)));
  }

  @Test
  void findById_givenACommittedOffer_thenTranslatesBothCopies() {
    // Given
    when(angebote.findById(11L)).thenReturn(Optional.of(versandzeile()));
    when(positionen.findByAngebot(11L)).thenReturn(List.of(positionszeile((short) 1, KONZEPTION)));

    // When
    final Optional<Angebot> gefunden = repository.findById(11L);

    // Then
    assertThat(gefunden).contains(versendet());
  }

  @Test
  void findById_thenKeepsTheOrderOfThePositionsAsRead() {
    // Given — gelesen wird nach Platz, und genau diese Folge steht danach am Angebot (E24).
    when(angebote.findById(11L)).thenReturn(Optional.of(entwurfszeile()));
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
  void save_givenAnAcceptedOffer_thenWritesTheMomentOfTheReaction() {
    // Given — der Zeitpunkt der Reaktion ist die einzige Angabe, die ein festgeschriebenes
    // Dokument noch bekommt (Kriterium 17).
    erwarteSchreibenDerZeile(reaktionszeile());

    // When
    repository.save(angenommen());

    // Then
    verify(angebote).save(gespeicherte.capture());
    assertThat(gespeicherte.getValue())
        .satisfies(
            zeile -> assertThat(zeile.getZustand()).isEqualTo(Angebotszustand.ANGENOMMEN),
            zeile -> assertThat(zeile.getReaktionAm()).isEqualTo(GEAENDERT));
  }

  @Test
  void findById_givenAnAcceptedOffer_thenTranslatesTheMomentOfTheReaction() {
    // Given
    when(angebote.findById(11L)).thenReturn(Optional.of(reaktionszeile()));
    when(positionen.findByAngebot(11L)).thenReturn(List.of(positionszeile((short) 1, KONZEPTION)));

    // When
    final Optional<Angebot> gefunden = repository.findById(11L);

    // Then
    assertThat(gefunden).contains(angenommen());
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

  private static AngebotEntity entwurfszeile(final long id) {
    return new AngebotEntity(
        Long.valueOf(id),
        3L,
        8L,
        null,
        Angebotszustand.ENTWURF,
        ANGEBOTSDATUM,
        GUELTIG_BIS,
        BESCHREIBUNG,
        BEDINGUNGEN,
        null,
        null,
        null,
        ANGELEGT,
        GEAENDERT);
  }

  private static AngebotPositionEntity positionszeile(
      final long angebotId, final short platz, final Angebotsposition position) {
    return new AngebotPositionEntity(
        null,
        angebotId,
        platz,
        position.bezeichnung(),
        position.abrechnungsmodus(),
        position.menge(),
        position.einheit(),
        position.einzelpreis());
  }

  @Test
  void findByFirma_thenLoadsThePositionsOfEveryOfferInOneQuery() {
    // Given — E20: ein findByAngebot je Zeile waere die bekannte Abfrage-Lawine.
    when(angebote.findByFirma(3L)).thenReturn(List.of(entwurfszeile(11L), entwurfszeile(12L)));
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
    // Given — ein frisch angelegter Entwurf hat noch keine Position.
    when(angebote.findByFirma(3L)).thenReturn(List.of(entwurfszeile(11L)));
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
  void loesche_thenRemovesThePositionsBeforeTheRowThatCarriesThem() {
    // Given — der Fremdschluessel traegt kein ON DELETE (Kriterium 7, E19).

    // When
    repository.loesche(11L);

    // Then
    final InOrder reihenfolge = inOrder(positionen, angebote);
    reihenfolge.verify(positionen).loescheZuAngebot(11L);
    reihenfolge.verify(angebote).deleteById(11L);
  }
}
