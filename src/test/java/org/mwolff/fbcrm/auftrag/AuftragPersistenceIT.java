package org.mwolff.fbcrm.auftrag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.mwolff.fbcrm.auftrag.domain.Auftrag;
import org.mwolff.fbcrm.auftrag.domain.AuftragRepository;
import org.mwolff.fbcrm.auftrag.domain.Auftragsposition;
import org.mwolff.fbcrm.auftrag.domain.Auftragsstatus;
import org.mwolff.fbcrm.common.Abrechnungsmodus;
import org.mwolff.fbcrm.common.Einheit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Der Bestand des Auftrags gegen eine echte PostgreSQL-Instanz.
 *
 * <p>Gegenstand ist der Auftrag als <b>eine</b> Einheit mit seinen Positionen: Anlegen, Wiederlesen
 * in derselben Reihenfolge, Pflegen und echtes Loeschen (Kriterium 15). „Stunden je Personentag"
 * laeuft dabei in allen drei Formen durch, die E10 kennt — 7,50 und 8,00 an Aufwandspositionen,
 * leer am Festpreis.
 *
 * <p>Jeder Zug setzt seine Transaktionsgrenze selbst ueber {@link TransactionTemplate}: Der Adapter
 * loescht die alten Positionszeilen vor dem Schreiben der neuen und hat aus demselben Grund wie der
 * Nummernkreis keine eigene Grenze — sie gehoert dem Anwendungsfall.
 */
class AuftragPersistenceIT extends AbstractIntegrationTest {

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

  private static final Auftragsposition BETREUUNG =
      new Auftragsposition(
          "Betreuung",
          Abrechnungsmodus.AUFWAND,
          new BigDecimal("8.00"),
          Einheit.STUNDE,
          new BigDecimal("95.00"),
          new BigDecimal("8.00"));

  private static final Auftragsposition SCHULUNG =
      new Auftragsposition(
          "Schulungstag",
          Abrechnungsmodus.FESTPREIS,
          new BigDecimal("1.00"),
          Einheit.PAUSCHAL,
          new BigDecimal("1200.00"),
          null);

  private final AuftragRepository repository;
  private final TransactionTemplate transaktion;
  private final JdbcTemplate jdbc;

  private long vorgangId;
  private long angebotId;
  private long zweitesAngebotId;

  @Autowired
  AuftragPersistenceIT(
      final AuftragRepository repository,
      final TransactionTemplate transaktion,
      final JdbcTemplate jdbc) {
    this.repository = repository;
    this.transaktion = transaktion;
    this.jdbc = jdbc;
  }

  @BeforeEach
  void leereFachtabellenUndLegeVorgangUndAngeboteAn() {
    jdbc.execute(
        "TRUNCATE auftrag_position, auftrag, angebot_position, angebot, vorgang_eintrag, vorgang,"
            + " ansprechpartner, firma RESTART IDENTITY CASCADE");
    jdbc.update("INSERT INTO firma (name) VALUES ('Adler AG')");
    final Long firmaId =
        jdbc.queryForObject("SELECT id FROM firma WHERE name = 'Adler AG'", Long.class);
    jdbc.update(
        "INSERT INTO vorgang (nummer, titel, firma_id) VALUES (1, 'Website-Relaunch', ?)", firmaId);
    vorgangId =
        jdbc.queryForObject("SELECT id FROM vorgang WHERE nummer = 1", Long.class).longValue();
    angebotId = legeAngebotAn();
    zweitesAngebotId = legeAngebotAn();
  }

  private long legeAngebotAn() {
    jdbc.update(
        "INSERT INTO angebot (vorgang_id, zustand, angebot_datum, gueltig_bis)"
            + " VALUES (?, 'ENTWURF', DATE '2026-09-20', DATE '2026-10-20')",
        vorgangId);
    return jdbc.queryForObject("SELECT max(id) FROM angebot", Long.class).longValue();
  }

  private Auftrag auftrag(
      final long angebot, final String nummer, final List<Auftragsposition> positionen) {
    return new Auftrag(
        null,
        vorgangId,
        angebot,
        nummer,
        Auftragsstatus.OFFEN,
        AUFTRAGSDATUM,
        BESTELLNUMMER,
        LEISTUNG_AB,
        LEISTUNG_BIS,
        positionen,
        ANGELEGT,
        ANGELEGT);
  }

  private Auftrag geschrieben(final Auftrag auftrag) {
    return transaktion.execute(status -> repository.save(auftrag));
  }

  private Optional<Auftrag> gelesen(final long id) {
    return transaktion.execute(status -> repository.findById(id));
  }

  private Optional<Auftrag> gelesenZuAngebot(final long angebot) {
    return transaktion.execute(status -> repository.findByAngebot(angebot));
  }

  private List<Auftrag> gelesenZuVorgang(final long vorgang) {
    return transaktion.execute(status -> repository.findByVorgang(vorgang));
  }

  @Test
  void save_givenANewOrder_thenReadsBackEveryFieldAndThePositionsInOrder() {
    // Given — drei Positionen: 7,50 nach Aufwand, 8,00 nach Aufwand, eine zum Festpreis ohne Wert.
    final Auftrag gespeichert =
        geschrieben(auftrag(angebotId, NUMMER, List.of(KONZEPTION, BETREUUNG, SCHULUNG)));

    // When
    final Optional<Auftrag> gelesen = gelesen(gespeichert.requireId());

    // Then
    assertThat(gelesen)
        .hasValueSatisfying(
            auftrag -> {
              assertThat(auftrag.id()).isEqualTo(gespeichert.requireId());
              assertThat(auftrag.vorgangId()).isEqualTo(vorgangId);
              assertThat(auftrag.angebotId()).isEqualTo(angebotId);
              assertThat(auftrag.nummer()).isEqualTo(NUMMER);
              assertThat(auftrag.status()).isEqualTo(Auftragsstatus.OFFEN);
              assertThat(auftrag.auftragDatum()).isEqualTo(AUFTRAGSDATUM);
              assertThat(auftrag.kundenbestellnummer()).isEqualTo(BESTELLNUMMER);
              assertThat(auftrag.leistungAb()).isEqualTo(LEISTUNG_AB);
              assertThat(auftrag.leistungBis()).isEqualTo(LEISTUNG_BIS);
              assertThat(auftrag.positionen()).containsExactly(KONZEPTION, BETREUUNG, SCHULUNG);
            });
  }

  @Test
  void save_thenStoresThePlacesFromOneUpwards() {
    // Given
    final Auftrag gespeichert =
        geschrieben(auftrag(angebotId, NUMMER, List.of(SCHULUNG, KONZEPTION)));

    // When
    final List<Short> plaetze =
        jdbc.queryForList(
            "SELECT position FROM auftrag_position WHERE auftrag_id = ? ORDER BY position",
            Short.class,
            Long.valueOf(gespeichert.requireId()));

    // Then
    assertThat(plaetze).containsExactly((short) 1, (short) 2);
  }

  @Test
  void save_givenAnOrderWithoutVoluntaryDetails_thenReadsThemBackEmpty() {
    // Given — Kundenbestellnummer und Leistungszeitraum sind freiwillig (Kriterium 3).
    final Auftrag ohne =
        new Auftrag(
            null,
            vorgangId,
            angebotId,
            NUMMER,
            Auftragsstatus.OFFEN,
            AUFTRAGSDATUM,
            null,
            null,
            null,
            List.of(SCHULUNG),
            ANGELEGT,
            ANGELEGT);
    final Auftrag gespeichert = geschrieben(ohne);

    // Then
    assertThat(gelesen(gespeichert.requireId()))
        .hasValueSatisfying(
            auftrag -> {
              assertThat(auftrag.kundenbestellnummer()).isNull();
              assertThat(auftrag.leistungAb()).isNull();
              assertThat(auftrag.leistungBis()).isNull();
            });
  }

  @Test
  void save_givenAMaintainedOrder_thenReadsBackTheFourChangedDetails() {
    // Given — Kriterium 7: der eine Schreibweg pflegt alle vier Angaben.
    final Auftrag gespeichert = geschrieben(auftrag(angebotId, NUMMER, List.of(KONZEPTION)));

    // When
    geschrieben(
        gespeichert.gepflegt(
            LocalDate.of(2026, 9, 25),
            "BST-0815",
            null,
            null,
            Auftragsstatus.ABGESCHLOSSEN,
            GEAENDERT));

    // Then
    assertThat(gelesen(gespeichert.requireId()))
        .hasValueSatisfying(
            auftrag -> {
              assertThat(auftrag.auftragDatum()).isEqualTo(LocalDate.of(2026, 9, 25));
              assertThat(auftrag.kundenbestellnummer()).isEqualTo("BST-0815");
              assertThat(auftrag.leistungAb()).isNull();
              assertThat(auftrag.leistungBis()).isNull();
              assertThat(auftrag.status()).isEqualTo(Auftragsstatus.ABGESCHLOSSEN);
              assertThat(auftrag.positionen()).containsExactly(KONZEPTION);
            });
  }

  @Test
  void save_givenAMaintainedOrder_thenLeavesNoOrphanedPositionRow() {
    // Given — geschrieben wird als Ganzes: die alten Positionszeilen fallen weg.
    final Auftrag gespeichert =
        geschrieben(auftrag(angebotId, NUMMER, List.of(KONZEPTION, SCHULUNG)));

    // When
    geschrieben(
        gespeichert.gepflegt(
            AUFTRAGSDATUM, BESTELLNUMMER, null, null, Auftragsstatus.IN_ARBEIT, GEAENDERT));

    // Then
    assertThat(jdbc.queryForObject("SELECT count(*) FROM auftrag_position", Integer.class))
        .isEqualTo(2);
  }

  @Test
  void findByAngebot_thenFindsTheOrderThatGrewOutOfIt() {
    // Given
    final Auftrag gespeichert = geschrieben(auftrag(angebotId, NUMMER, List.of(KONZEPTION)));

    // When
    final Optional<Auftrag> gefunden = gelesenZuAngebot(angebotId);

    // Then
    assertThat(gefunden)
        .hasValueSatisfying(
            auftrag -> {
              assertThat(auftrag.id()).isEqualTo(gespeichert.requireId());
              assertThat(auftrag.positionen()).containsExactly(KONZEPTION);
            });
  }

  @Test
  void findByAngebot_givenAnOfferWithoutAnOrder_thenEmpty() {
    // When / Then
    assertThat(gelesenZuAngebot(zweitesAngebotId)).isEmpty();
  }

  @Test
  void findByVorgang_thenDeliversEveryOrderOfTheVorgangWithItsPositions() {
    // Given — zwei Auftraege am selben Vorgang, jeder aus einem eigenen Angebot (F9).
    geschrieben(auftrag(angebotId, NUMMER, List.of(KONZEPTION, BETREUUNG)));
    geschrieben(auftrag(zweitesAngebotId, "AU-2026-002", List.of(SCHULUNG)));

    // When
    final List<Auftrag> gefunden = gelesenZuVorgang(vorgangId);

    // Then
    assertThat(gefunden)
        .extracting(Auftrag::nummer, Auftrag::positionen)
        .containsExactlyInAnyOrder(
            tuple(NUMMER, List.of(KONZEPTION, BETREUUNG)), tuple("AU-2026-002", List.of(SCHULUNG)));
  }

  @Test
  void findByVorgang_givenAVorgangWithoutOrders_thenEmpty() {
    // When / Then
    assertThat(gelesenZuVorgang(vorgangId)).isEmpty();
  }

  @Test
  void loesche_thenRemovesThePositionsAndTheRow() {
    // Given — Kriterium 15: geloescht wird echt, ohne Statusspur.
    final Auftrag gespeichert =
        geschrieben(auftrag(angebotId, NUMMER, List.of(KONZEPTION, SCHULUNG)));

    // When
    transaktion.execute(
        status -> {
          repository.loesche(gespeichert.requireId());
          return null;
        });

    // Then
    assertThat(gelesen(gespeichert.requireId())).isEmpty();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM auftrag_position", Integer.class))
        .isZero();
  }

  @Test
  void save_givenAnExpensePositionWithoutHoursPerDay_thenRejectedByTheDatabase() {
    // Given — E10: die Datenbank haelt die Regel auch dann, wenn ein Aufrufer sie umgeht.
    final Auftragsposition ohneFaktor =
        new Auftragsposition(
            "Konzeption",
            Abrechnungsmodus.AUFWAND,
            new BigDecimal("1.00"),
            Einheit.PERSONENTAG,
            new BigDecimal("95.00"),
            null);

    // When / Then
    assertThatThrownBy(() -> geschrieben(auftrag(angebotId, NUMMER, List.of(ohneFaktor))))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void save_givenAServicePeriodEndingBeforeItStarts_thenRejectedByTheDatabase() {
    // Given — E21: derselbe Riegel wie am Klassen-Constraint der Anfrage.
    final Auftrag widerspruch =
        new Auftrag(
            null,
            vorgangId,
            angebotId,
            NUMMER,
            Auftragsstatus.OFFEN,
            AUFTRAGSDATUM,
            BESTELLNUMMER,
            LEISTUNG_BIS,
            LEISTUNG_AB,
            List.of(KONZEPTION),
            ANGELEGT,
            ANGELEGT);

    // When / Then
    assertThatThrownBy(() -> geschrieben(widerspruch))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void save_givenASecondOrderForTheSameOffer_thenRejectedByTheDatabase() {
    // Given — F9: hoechstens ein Auftrag je Angebot.
    geschrieben(auftrag(angebotId, NUMMER, List.of(KONZEPTION)));

    // When / Then
    assertThatThrownBy(() -> geschrieben(auftrag(angebotId, "AU-2026-002", List.of(KONZEPTION))))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void findById_givenAnUnknownId_thenEmpty() {
    // When / Then
    assertThat(gelesen(4711L)).isEmpty();
  }
}
