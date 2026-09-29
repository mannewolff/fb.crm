package org.mwolff.fbcrm.angebot;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.angebot.domain.Angebotsposition;
import org.mwolff.fbcrm.angebot.domain.Angebotsstatus;
import org.mwolff.fbcrm.common.Abrechnungsmodus;
import org.mwolff.fbcrm.common.Einheit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Der Bestand des Angebots gegen eine echte PostgreSQL-Instanz.
 *
 * <p>Gegenstand ist das Angebot als <b>eine</b> Einheit mit seinen Positionen: Anlegen, Aendern mit
 * umgestellter Reihenfolge und Lesen in genau dieser Reihenfolge (E24), dazu der Status als Text in
 * seiner Spalte.
 *
 * <p>Jeder Zug setzt seine Transaktionsgrenze selbst ueber {@link TransactionTemplate}: Der Adapter
 * loescht die alten Positionszeilen vor dem Schreiben der neuen und hat keine eigene Grenze — sie
 * gehoert dem Anwendungsfall.
 */
class AngebotPersistenceIT extends AbstractIntegrationTest {

  private static final LocalDate ANGEBOTSDATUM = LocalDate.of(2026, 9, 20);
  private static final Instant ANGELEGT = Instant.parse("2026-09-20T08:00:00Z");
  private static final String BESCHREIBUNG = "Neugestaltung der Website";
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

  private long firmaId;
  private Long ansprechpartnerId;

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
  void leereFachtabellenUndLegeFirmaUndAnsprechpartnerAn() {
    jdbc.execute(
        "TRUNCATE angebot_position, angebot, ansprechpartner, firma RESTART IDENTITY CASCADE");
    jdbc.update("INSERT INTO firma (name) VALUES ('Adler AG')");
    firmaId =
        jdbc.queryForObject("SELECT id FROM firma WHERE name = 'Adler AG'", Long.class).longValue();
    jdbc.update(
        "INSERT INTO ansprechpartner (firma_id, nachname) VALUES (?, 'Adler')",
        Long.valueOf(firmaId));
    ansprechpartnerId = jdbc.queryForObject("SELECT id FROM ansprechpartner", Long.class);
  }

  private Angebot angebot(final List<Angebotsposition> positionen) {
    return new Angebot(
        null,
        firmaId,
        ansprechpartnerId,
        Angebotsstatus.ANGELEGT,
        ANGEBOTSDATUM,
        BESCHREIBUNG,
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
  void save_givenANewAngebot_thenReadsBackEveryFieldAndThePositionsInOrder() {
    // Given
    final Angebot gespeichert = geschrieben(angebot(List.of(KONZEPTION, SCHULUNG)));

    // When
    final Optional<Angebot> gelesen = gelesen(gespeichert.requireId());

    // Then
    assertThat(gelesen)
        .hasValueSatisfying(
            angebot -> {
              assertThat(angebot.id()).isEqualTo(gespeichert.requireId());
              assertThat(angebot.firmaId()).isEqualTo(firmaId);
              assertThat(angebot.ansprechpartnerId()).isEqualTo(ansprechpartnerId);
              assertThat(angebot.status()).isEqualTo(Angebotsstatus.ANGELEGT);
              assertThat(angebot.angebotDatum()).isEqualTo(ANGEBOTSDATUM);
              assertThat(angebot.beschreibung()).isEqualTo(BESCHREIBUNG);
              assertThat(angebot.createdAt()).isEqualTo(ANGELEGT);
              assertThat(angebot.updatedAt()).isEqualTo(ANGELEGT);
              assertThat(angebot.positionen()).containsExactly(KONZEPTION, SCHULUNG);
            });
  }

  @Test
  void save_thenStoresThePlacesFromOneUpwards() {
    // Given
    final Angebot gespeichert = geschrieben(angebot(List.of(SCHULUNG, KONZEPTION)));

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
  void save_givenAChangedAngebot_thenReplacesThePositionsWithTheNewOrder() {
    // Given
    final Angebot gespeichert = geschrieben(angebot(List.of(KONZEPTION, SCHULUNG)));

    // When — dieselben Positionen, umgestellt, und eine dritte dazu.
    geschrieben(
        gespeichert.geaendert(
            ANGEBOTSDATUM,
            ansprechpartnerId,
            BESCHREIBUNG,
            List.of(SCHULUNG, BETREUUNG, KONZEPTION),
            ANGELEGT));

    // Then
    assertThat(gelesen(gespeichert.requireId()))
        .hasValueSatisfying(
            angebot ->
                assertThat(angebot.positionen()).containsExactly(SCHULUNG, BETREUUNG, KONZEPTION));
  }

  @Test
  void save_givenAChangedAngebot_thenLeavesNoOrphanedPositionRow() {
    // Given
    final Angebot gespeichert = geschrieben(angebot(List.of(KONZEPTION, SCHULUNG)));

    // When
    geschrieben(
        gespeichert.geaendert(
            ANGEBOTSDATUM, ansprechpartnerId, BESCHREIBUNG, List.of(BETREUUNG), ANGELEGT));

    // Then
    assertThat(jdbc.queryForObject("SELECT count(*) FROM angebot_position", Integer.class))
        .isEqualTo(1);
  }

  @Test
  void save_givenAStatusChange_thenStoresTheStatusAsText() {
    // Given
    final Angebot gespeichert = geschrieben(angebot(List.of(KONZEPTION)));

    // When
    geschrieben(gespeichert.statusWeiter(ANGELEGT).statusWeiter(ANGELEGT));

    // Then — als Text und nicht als Ordnungszahl, damit der CHECK der Migration ihn kennt.
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM angebot WHERE id = ?",
                String.class,
                Long.valueOf(gespeichert.requireId())))
        .isEqualTo("BESTELLT");
    assertThat(gelesen(gespeichert.requireId()))
        .hasValueSatisfying(
            angebot -> assertThat(angebot.status()).isEqualTo(Angebotsstatus.BESTELLT));
  }

  @Test
  void save_givenNoContactAndNoText_thenReadsBackBothAbsent() {
    // Given — Ansprechpartner und Beschreibung sind optional.
    final Angebot gespeichert =
        geschrieben(angebot(List.of()).geaendert(ANGEBOTSDATUM, null, null, List.of(), ANGELEGT));

    // Then
    assertThat(gelesen(gespeichert.requireId()))
        .hasValueSatisfying(
            angebot -> {
              assertThat(angebot.ansprechpartnerId()).isNull();
              assertThat(angebot.beschreibung()).isNull();
              assertThat(angebot.positionen()).isEmpty();
            });
  }

  @Test
  void save_givenAPositionWithABlankLabel_thenAccepted() {
    // Given — die fehlende Bezeichnung weist der Eingang der Maske ab, nicht die Datenbank.
    final Angebotsposition ohneBezeichnung =
        new Angebotsposition(
            "   ",
            Abrechnungsmodus.AUFWAND,
            new BigDecimal("1.00"),
            Einheit.STUNDE,
            new BigDecimal("0.00"));
    final Angebot gespeichert = geschrieben(angebot(List.of(ohneBezeichnung)));

    // Then
    assertThat(gelesen(gespeichert.requireId()))
        .hasValueSatisfying(
            angebot -> assertThat(angebot.positionen()).containsExactly(ohneBezeichnung));
  }

  @Test
  void findById_givenAnUnknownId_thenEmpty() {
    // When / Then
    assertThat(gelesen(4711L)).isEmpty();
  }
}
