package org.mwolff.fbcrm.angebot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.mwolff.fbcrm.angebot.domain.Abrechnungsmodus;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.angebot.domain.Angebotsposition;
import org.mwolff.fbcrm.angebot.domain.Angebotszustand;
import org.mwolff.fbcrm.angebot.domain.Belegabsender;
import org.mwolff.fbcrm.angebot.domain.Belegempfaenger;
import org.mwolff.fbcrm.angebot.domain.Einheit;
import org.mwolff.fbcrm.common.Anschrift;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Der Bestand des Angebots gegen eine echte PostgreSQL-Instanz.
 *
 * <p>Gegenstand ist das Angebot als <b>eine</b> Einheit mit seinen Positionen: Anlegen,
 * Fortschreiben mit umgestellter Reihenfolge und Lesen in genau dieser Reihenfolge (E24). Daneben
 * die Grenze zwischen Entwurf und festgeschriebenem Dokument, die allein die Datenbank haelt — ein
 * halbfertiger Entwurf geht durch, ein widerspruechliches Dokument nicht (E27).
 *
 * <p>Jeder Zug setzt seine Transaktionsgrenze selbst ueber {@link TransactionTemplate}: Der Adapter
 * loescht die alten Positionszeilen vor dem Schreiben der neuen und hat aus demselben Grund wie der
 * Nummernkreis keine eigene Grenze — sie gehoert dem Anwendungsfall.
 */
class AngebotPersistenceIT extends AbstractIntegrationTest {

  private static final LocalDate ANGEBOTSDATUM = LocalDate.of(2026, 9, 20);
  private static final LocalDate GUELTIG_BIS = LocalDate.of(2026, 10, 20);
  private static final Instant ANGELEGT = Instant.parse("2026-09-20T08:00:00Z");
  private static final Instant VERSANDZEITPUNKT = Instant.parse("2026-09-21T09:00:00Z");
  private static final String NUMMER = "A-2026-001";
  private static final String PDF_SCHLUESSEL = "angebot/1/6f1c9a.pdf";
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
          new BigDecimal("1.00"),
          Einheit.PAUSCHAL,
          new BigDecimal("1200.00"));

  private static final Angebotsposition BETREUUNG =
      new Angebotsposition(
          "Betreuung",
          Abrechnungsmodus.AUFWAND,
          new BigDecimal("8.00"),
          Einheit.STUNDE,
          new BigDecimal("95.00"));

  private final AngebotRepository repository;
  private final TransactionTemplate transaktion;
  private final JdbcTemplate jdbc;

  private long vorgangId;

  @Autowired
  AngebotPersistenceIT(
      final AngebotRepository repository,
      final TransactionTemplate transaktion,
      final JdbcTemplate jdbc) {
    this.repository = repository;
    this.transaktion = transaktion;
    this.jdbc = jdbc;
  }

  @BeforeEach
  void leereFachtabellenUndLegeEinenVorgangAn() {
    jdbc.execute(
        "TRUNCATE angebot_position, angebot, vorgang_eintrag, vorgang, ansprechpartner, firma"
            + " RESTART IDENTITY CASCADE");
    jdbc.update("INSERT INTO firma (name) VALUES ('Adler AG')");
    final Long firmaId =
        jdbc.queryForObject("SELECT id FROM firma WHERE name = 'Adler AG'", Long.class);
    jdbc.update(
        "INSERT INTO vorgang (nummer, titel, firma_id) VALUES (1, 'Website-Relaunch', ?)", firmaId);
    vorgangId =
        jdbc.queryForObject("SELECT id FROM vorgang WHERE nummer = 1", Long.class).longValue();
  }

  private Angebot entwurf(final LocalDate gueltigBis, final List<Angebotsposition> positionen) {
    return new Angebot(
        null,
        vorgangId,
        null,
        Angebotszustand.ENTWURF,
        ANGEBOTSDATUM,
        gueltigBis,
        BESCHREIBUNG,
        BEDINGUNGEN,
        null,
        null,
        null,
        null,
        null,
        positionen,
        ANGELEGT,
        ANGELEGT);
  }

  private Angebot geschrieben(final Angebot angebot) {
    return transaktion.execute(status -> repository.save(angebot));
  }

  private Optional<Angebot> gelesen(final long id) {
    return transaktion.execute(status -> repository.findById(id));
  }

  @Test
  void save_givenANewDraft_thenReadsBackEveryFieldAndThePositionsInOrder() {
    // Given
    final Angebot gespeichert = geschrieben(entwurf(GUELTIG_BIS, List.of(KONZEPTION, SCHULUNG)));

    // When
    final Optional<Angebot> gelesen = gelesen(gespeichert.requireId());

    // Then
    assertThat(gelesen)
        .hasValueSatisfying(
            angebot -> {
              assertThat(angebot.id()).isEqualTo(gespeichert.requireId());
              assertThat(angebot.vorgangId()).isEqualTo(vorgangId);
              assertThat(angebot.nummer()).isNull();
              assertThat(angebot.zustand()).isEqualTo(Angebotszustand.ENTWURF);
              assertThat(angebot.angebotDatum()).isEqualTo(ANGEBOTSDATUM);
              assertThat(angebot.gueltigBis()).isEqualTo(GUELTIG_BIS);
              assertThat(angebot.leistungsbeschreibung()).isEqualTo(BESCHREIBUNG);
              assertThat(angebot.zahlungsbedingungen()).isEqualTo(BEDINGUNGEN);
              assertThat(angebot.versendetAm()).isNull();
              assertThat(angebot.reaktionAm()).isNull();
              assertThat(angebot.pdfSchluessel()).isNull();
              assertThat(angebot.empfaenger()).isNull();
              assertThat(angebot.absender()).isNull();
              assertThat(angebot.positionen()).containsExactly(KONZEPTION, SCHULUNG);
            });
  }

  @Test
  void save_thenStoresThePlacesFromOneUpwards() {
    // Given
    final Angebot gespeichert = geschrieben(entwurf(GUELTIG_BIS, List.of(SCHULUNG, KONZEPTION)));

    // When
    final List<Short> plaetze =
        jdbc.queryForList(
            "SELECT position FROM angebot_position WHERE angebot_id = ? ORDER BY position",
            Short.class,
            Long.valueOf(gespeichert.requireId()));

    // Then
    assertThat(plaetze).containsExactly((short) 1, (short) 2);
  }

  @Test
  void save_givenAChangedDraft_thenReplacesThePositionsWithTheNewOrder() {
    // Given
    final Angebot gespeichert = geschrieben(entwurf(GUELTIG_BIS, List.of(KONZEPTION, SCHULUNG)));

    // When — dieselben Positionen, umgestellt, und eine dritte dazu.
    geschrieben(
        gespeichert.entwurfGeaendert(
            GUELTIG_BIS,
            BESCHREIBUNG,
            BEDINGUNGEN,
            List.of(SCHULUNG, BETREUUNG, KONZEPTION),
            ANGELEGT));

    // Then
    assertThat(gelesen(gespeichert.requireId()))
        .hasValueSatisfying(
            angebot ->
                assertThat(angebot.positionen()).containsExactly(SCHULUNG, BETREUUNG, KONZEPTION));
  }

  @Test
  void save_givenAChangedDraft_thenLeavesNoOrphanedPositionRow() {
    // Given
    final Angebot gespeichert = geschrieben(entwurf(GUELTIG_BIS, List.of(KONZEPTION, SCHULUNG)));

    // When
    geschrieben(
        gespeichert.entwurfGeaendert(
            GUELTIG_BIS, BESCHREIBUNG, BEDINGUNGEN, List.of(BETREUUNG), ANGELEGT));

    // Then
    assertThat(jdbc.queryForObject("SELECT count(*) FROM angebot_position", Integer.class))
        .isEqualTo(1);
  }

  @Test
  void save_givenADraftWithValidityBeforeTheOfferDate_thenAccepted() {
    // Given — E27: der Entwurf traegt seine Gueltigkeit frei.
    final Angebot gespeichert =
        geschrieben(entwurf(ANGEBOTSDATUM.minusDays(1), List.of(KONZEPTION)));

    // Then
    assertThat(gelesen(gespeichert.requireId()))
        .hasValueSatisfying(
            angebot -> assertThat(angebot.gueltigBis()).isEqualTo(ANGEBOTSDATUM.minusDays(1)));
  }

  @Test
  void save_givenAPositionWithABlankLabel_thenAccepted() {
    // Given — E27: die fehlende Bezeichnung meldet die Versandpruefung, nicht die Datenbank.
    final Angebotsposition ohneBezeichnung =
        new Angebotsposition(
            "   ",
            Abrechnungsmodus.AUFWAND,
            new BigDecimal("1.00"),
            Einheit.STUNDE,
            new BigDecimal("0.00"));
    final Angebot gespeichert = geschrieben(entwurf(GUELTIG_BIS, List.of(ohneBezeichnung)));

    // Then
    assertThat(gelesen(gespeichert.requireId()))
        .hasValueSatisfying(
            angebot -> assertThat(angebot.positionen()).containsExactly(ohneBezeichnung));
  }

  @Test
  void save_givenACommittedOffer_thenReadsBackBothAddressCopies() {
    // Given — R8: die Kopien stehen am Dokument.
    final Angebot entwurf = geschrieben(entwurf(GUELTIG_BIS, List.of(KONZEPTION)));

    // When
    final Angebot versendet =
        geschrieben(
            entwurf.versendet(NUMMER, EMPFAENGER, ABSENDER, PDF_SCHLUESSEL, VERSANDZEITPUNKT));

    // Then
    assertThat(gelesen(versendet.requireId()))
        .hasValueSatisfying(
            angebot -> {
              assertThat(angebot.zustand()).isEqualTo(Angebotszustand.VERSENDET);
              assertThat(angebot.nummer()).isEqualTo(NUMMER);
              assertThat(angebot.versendetAm()).isEqualTo(VERSANDZEITPUNKT);
              assertThat(angebot.pdfSchluessel()).isEqualTo(PDF_SCHLUESSEL);
              assertThat(angebot.empfaenger()).isEqualTo(EMPFAENGER);
              assertThat(angebot.absender()).isEqualTo(ABSENDER);
            });
  }

  @Test
  void save_givenADraftCarryingANumber_thenRejectedByTheDatabase() {
    // Given — die Datenbank haelt die Grenze auch dann, wenn ein Aufrufer sie umgeht.
    final Angebot widerspruch =
        new Angebot(
            null,
            vorgangId,
            NUMMER,
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
            List.of(KONZEPTION),
            ANGELEGT,
            ANGELEGT);

    // When / Then
    assertThatThrownBy(() -> geschrieben(widerspruch))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void save_givenACommittedOfferWithoutANumber_thenRejectedByTheDatabase() {
    // Given
    final Angebot widerspruch =
        new Angebot(
            null,
            vorgangId,
            null,
            Angebotszustand.VERSENDET,
            ANGEBOTSDATUM,
            GUELTIG_BIS,
            BESCHREIBUNG,
            BEDINGUNGEN,
            VERSANDZEITPUNKT,
            null,
            PDF_SCHLUESSEL,
            EMPFAENGER,
            ABSENDER,
            List.of(KONZEPTION),
            ANGELEGT,
            ANGELEGT);

    // When / Then
    assertThatThrownBy(() -> geschrieben(widerspruch))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void save_givenACommittedOfferWithValidityBeforeTheOfferDate_thenRejectedByTheDatabase() {
    // Given
    final Angebot widerspruch =
        new Angebot(
            null,
            vorgangId,
            NUMMER,
            Angebotszustand.VERSENDET,
            ANGEBOTSDATUM,
            ANGEBOTSDATUM.minusDays(1),
            BESCHREIBUNG,
            BEDINGUNGEN,
            VERSANDZEITPUNKT,
            null,
            PDF_SCHLUESSEL,
            EMPFAENGER,
            ABSENDER,
            List.of(KONZEPTION),
            ANGELEGT,
            ANGELEGT);

    // When / Then
    assertThatThrownBy(() -> geschrieben(widerspruch))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void findById_givenAnUnknownId_thenEmpty() {
    // When / Then
    assertThat(gelesen(4711L)).isEmpty();
  }
}
