package org.mwolff.fbcrm.angebot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Prueft das Schema des Angebots nach {@code V20__angebot_intern.sql} gegen eine echte
 * PostgreSQL-Instanz.
 *
 * <p>Gegenstand sind die Zusagen, die allein die Datenbank haelt: die sieben Status als CHECK, die
 * Bindung von {@code intern} an den Status ({@code angebot_art_status}, Issue #226), die
 * Wertebereiche der Positionen und die Reihenfolge als Schluessel. Gearbeitet wird mit {@link
 * JdbcTemplate} und direkten Anweisungen — der Weg ueber die Entities kaeme an einigen dieser
 * Faelle gar nicht vorbei.
 *
 * <p><b>Seit {@code V15__angebot_position_kennung.sql} ist die Reihenfolge aufgeschoben
 * eindeutig</b> (Plan #169, E2). Drei Zusagen gehoeren zusammen: {@code pg_constraint} meldet den
 * Constraint als aufgeschoben, zwei Plaetze lassen sich in einer Transaktion tauschen, und zweimal
 * derselbe Platz scheitert weiterhin — nur jetzt beim Commit und nicht nach der Anweisung. Ohne die
 * letzte Zusage waere das Aufschieben ein Verzicht auf die Regel und nicht eine Verschiebung ihres
 * Zeitpunkts.
 */
class AngebotSchemaIT extends AbstractIntegrationTest {

  private static final String INSERT_ANGEBOT =
      "INSERT INTO angebot (firma_id, status, angebot_datum) VALUES (?, ?, DATE '2026-09-20')";

  private static final String INSERT_ANGEBOT_MIT_ART =
      "INSERT INTO angebot (firma_id, status, intern, angebot_datum)"
          + " VALUES (?, ?, ?, DATE '2026-09-20')";

  private static final String INSERT_POSITION =
      "INSERT INTO angebot_position"
          + " (angebot_id, position, bezeichnung, abrechnungsmodus, menge, einheit, einzelpreis)"
          + " VALUES (?, ?, ?, ?, CAST(? AS numeric), ?, CAST(? AS numeric))";

  private static final String FIRMA = "Adler AG";

  private static final String TAUSCHE_PLATZ =
      "UPDATE angebot_position SET position = ? WHERE angebot_id = ? AND bezeichnung = ?";

  private final JdbcTemplate jdbc;
  private final TransactionTemplate transaktion;

  private long firmaId;

  @Autowired
  AngebotSchemaIT(final JdbcTemplate jdbc, final TransactionTemplate transaktion) {
    this.jdbc = jdbc;
    this.transaktion = transaktion;
  }

  @BeforeEach
  void leereFachtabellenUndLegeEineFirmaAn() {
    jdbc.execute(
        "TRUNCATE angebot_position, angebot, ansprechpartner, firma RESTART IDENTITY CASCADE");
    jdbc.update("INSERT INTO firma (name) VALUES (?)", FIRMA);
    firmaId =
        jdbc.queryForObject("SELECT id FROM firma WHERE name = ?", Long.class, FIRMA).longValue();
  }

  private int angebot(final @Nullable String status) {
    return jdbc.update(INSERT_ANGEBOT, firmaId, status);
  }

  private int angebot(final String status, final @Nullable Boolean intern) {
    return jdbc.update(INSERT_ANGEBOT_MIT_ART, firmaId, status, intern);
  }

  private Long angebotId() {
    angebot("ANGELEGT");
    return jdbc.queryForObject("SELECT id FROM angebot LIMIT 1", Long.class);
  }

  @Test
  void migration_thenBothTablesExistAndTheNumberRangeIsGone() {
    // When
    final Integer tabellen =
        jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public'"
                + " AND table_name IN ('angebot', 'angebot_position', 'angebot_nummernkreis')",
            Integer.class);

    // Then
    assertThat(tabellen).isEqualTo(2);
  }

  @Test
  void migration_thenOnlyTheFirmaIndexRemains() {
    // When
    final Integer indizes =
        jdbc.queryForObject(
            "SELECT count(*) FROM pg_indexes WHERE schemaname = 'public'"
                + " AND indexname IN ('angebot_firma_idx', 'angebot_zustand_gueltigkeit_idx')",
            Integer.class);

    // Then
    assertThat(indizes).isEqualTo(1);
  }

  @ParameterizedTest
  @CsvSource({
    "ANGELEGT, false",
    "ABGEGEBEN, false",
    "BESTELLT, false",
    "ERLEDIGT, false",
    "ABGERECHNET, false",
    "LAEUFT, true",
    "ABGESCHLOSSEN, true"
  })
  void angebot_givenEachOfTheSevenStatusWithItsArt_thenAccepted(
      final String status, final boolean intern) {
    // When — Issue #226: der CHECK kennt seit V20 sieben Werte.
    final int betroffen = angebot(status, Boolean.valueOf(intern));

    // Then
    assertThat(betroffen).isEqualTo(1);
  }

  @Test
  void angebotArtStatus_givenInternWithAStatusOfTheCustomerOffer_thenRejectedByTheDatabase() {
    // When / Then — E3: Die Datenbank haelt dieselbe Zusage wie der Konstruktor von Angebot.
    assertThatThrownBy(() -> angebot("BESTELLT", Boolean.TRUE))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("angebot_art_status");
  }

  @Test
  void angebotArtStatus_givenNotInternWithAStatusOfTheInternalWork_thenRejectedByTheDatabase() {
    // When / Then
    assertThatThrownBy(() -> angebot("LAEUFT", Boolean.FALSE))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("angebot_art_status");
  }

  @Test
  void angebotIntern_givenNoArt_thenRejectedByTheDatabase() {
    // When / Then — die Spalte ist NOT NULL; das Weglassen greift auf das DEFAULT zurueck, ein
    // ausdruecklicher NULL nicht.
    assertThatThrownBy(() -> angebot("ANGELEGT", null))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void angebotIntern_whenTheColumnIsOmitted_thenTheRowIsACustomerOffer() {
    // Given — bestehende Zeilen und jeder Weg ohne Angabe fuehren nach aussen (V20, DEFAULT false).
    angebot("ANGELEGT");

    // When / Then
    assertThat(jdbc.queryForObject("SELECT intern FROM angebot LIMIT 1", Boolean.class)).isFalse();
  }

  @Test
  void angebotStatus_givenAnUnknownStatus_thenRejectedByTheDatabase() {
    // When / Then
    assertThatThrownBy(() -> angebot("ENTWURF"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("angebot_status");
  }

  @Test
  void angebotStatus_givenNoStatus_thenRejectedByTheDatabase() {
    // When / Then
    assertThatThrownBy(() -> angebot(null)).isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void angebotFirmaId_givenAnUnknownFirma_thenRejectedByTheDatabase() {
    // When / Then
    assertThatThrownBy(() -> jdbc.update(INSERT_ANGEBOT, 4711L, "ANGELEGT"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void position_givenABlankLabel_thenAccepted() {
    // When — die fehlende Bezeichnung weist der Eingang der Maske ab, nicht die Datenbank.
    final int betroffen =
        jdbc.update(INSERT_POSITION, angebotId(), 1, "   ", "AUFWAND", "2.50", "PERSONENTAG", "0");

    // Then
    assertThat(betroffen).isEqualTo(1);
  }

  @Test
  void positionReihenfolge_thenTheUniqueConstraintIsDeferred() {
    // When — Plan #169, E2: Ohne das Aufschieben scheiterte der Tausch zweier Plaetze.
    final Boolean aufgeschoben =
        jdbc.queryForObject(
            "SELECT condeferred FROM pg_constraint WHERE conname = 'angebot_position_reihenfolge'",
            Boolean.class);

    // Then
    assertThat(aufgeschoben).isTrue();
  }

  @Test
  void positionReihenfolge_whenTwoPlacesAreSwappedInOneTransaction_thenCommitted() {
    // Given — zwei Positionen auf den Plaetzen 1 und 2.
    final Long angebotId = angebotId();
    jdbc.update(INSERT_POSITION, angebotId, 1, "Konzeption", "AUFWAND", "1.00", "STUNDE", "95.00");
    jdbc.update(INSERT_POSITION, angebotId, 2, "Schulung", "FESTPREIS", "1.00", "PAUSCHAL", "1200");

    // When — zwei UPDATE in einer Transaktion; nach dem ersten steht der Platz 2 zweimal da.
    transaktion.executeWithoutResult(
        status -> {
          jdbc.update(TAUSCHE_PLATZ, Integer.valueOf(2), angebotId, "Konzeption");
          jdbc.update(TAUSCHE_PLATZ, Integer.valueOf(1), angebotId, "Schulung");
        });

    // Then
    assertThat(
            jdbc.queryForList(
                "SELECT bezeichnung FROM angebot_position WHERE angebot_id = ? ORDER BY position",
                String.class,
                angebotId))
        .containsExactly("Schulung", "Konzeption");
  }

  @Test
  void positionReihenfolge_givenTheSamePlaceTwiceInOneTransaction_thenRejectedAtCommit() {
    // Given — aufgeschoben heisst nicht aufgegeben: Geprueft wird weiter, nur spaeter.
    final Long angebotId = angebotId();

    // When / Then — beide INSERT laufen durch, der Commit nicht.
    assertThatThrownBy(
            () ->
                transaktion.executeWithoutResult(
                    status -> {
                      jdbc.update(
                          INSERT_POSITION,
                          angebotId,
                          1,
                          "Konzeption",
                          "AUFWAND",
                          "1.00",
                          "STUNDE",
                          "95.00");
                      jdbc.update(
                          INSERT_POSITION,
                          angebotId,
                          1,
                          "Schulung",
                          "FESTPREIS",
                          "1.00",
                          "PAUSCHAL",
                          "1200.00");
                    }))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("angebot_position_reihenfolge");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM angebot_position", Integer.class))
        .isZero();
  }

  @Test
  void position_givenTheSamePlaceTwiceAtOneOffer_thenRejectedByTheDatabase() {
    // Given
    final Long angebotId = angebotId();
    jdbc.update(INSERT_POSITION, angebotId, 1, "Konzeption", "AUFWAND", "1.00", "STUNDE", "95.00");

    // When / Then
    assertThatThrownBy(
            () ->
                jdbc.update(
                    INSERT_POSITION,
                    angebotId,
                    1,
                    "Schulung",
                    "FESTPREIS",
                    "1.00",
                    "PAUSCHAL",
                    "1200.00"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("angebot_position_reihenfolge");
  }

  @Test
  void position_givenANegativeQuantity_thenRejectedByTheDatabase() {
    // Given
    final Long angebotId = angebotId();

    // When / Then
    assertThatThrownBy(
            () ->
                jdbc.update(
                    INSERT_POSITION,
                    angebotId,
                    1,
                    "Konzeption",
                    "AUFWAND",
                    "-1.00",
                    "STUNDE",
                    "95.00"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("angebot_position_menge");
  }

  @Test
  void position_givenANegativeUnitPrice_thenRejectedByTheDatabase() {
    // Given
    final Long angebotId = angebotId();

    // When / Then
    assertThatThrownBy(
            () ->
                jdbc.update(
                    INSERT_POSITION,
                    angebotId,
                    1,
                    "Konzeption",
                    "AUFWAND",
                    "1.00",
                    "STUNDE",
                    "-95.00"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("angebot_position_einzelpreis");
  }

  @Test
  void position_givenAnUnknownBillingMode_thenRejectedByTheDatabase() {
    // Given
    final Long angebotId = angebotId();

    // When / Then
    assertThatThrownBy(
            () ->
                jdbc.update(
                    INSERT_POSITION,
                    angebotId,
                    1,
                    "Konzeption",
                    "SCHAETZUNG",
                    "1.00",
                    "STUNDE",
                    "95.00"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("angebot_position_abrechnungsmodus");
  }

  @Test
  void position_givenAnUnknownUnit_thenRejectedByTheDatabase() {
    // Given
    final Long angebotId = angebotId();

    // When / Then
    assertThatThrownBy(
            () ->
                jdbc.update(
                    INSERT_POSITION,
                    angebotId,
                    1,
                    "Konzeption",
                    "AUFWAND",
                    "1.00",
                    "WOCHE",
                    "95.00"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("angebot_position_einheit");
  }

  @Test
  void positionAngebotId_givenAnUnknownOffer_thenRejectedByTheDatabase() {
    // When / Then
    assertThatThrownBy(
            () ->
                jdbc.update(
                    INSERT_POSITION, 4711L, 1, "Konzeption", "AUFWAND", "1.00", "STUNDE", "95.00"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void migration_thenNoStoredAmountOrSumColumnExists() {
    // When — E5: Betrag und Summe werden gerechnet, nicht gefuehrt.
    final Integer spalten =
        jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.columns WHERE table_schema = 'public'"
                + " AND table_name IN ('angebot', 'angebot_position')"
                + " AND column_name IN ('betrag', 'summe')",
            Integer.class);

    // Then
    assertThat(spalten).isZero();
  }
}
