package org.mwolff.fbcrm.rechnung;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Prueft das Schema der Rechnung nach {@code V18__rechnung.sql} gegen eine echte
 * PostgreSQL-Instanz.
 *
 * <p>Gegenstand sind die Zusagen, die allein die Datenbank haelt: die beiden gegenlaeufigen CHECKs
 * um den Zustand, die Eindeutigkeit der Nummer, die Wertebereiche der Positionen und die
 * Fremdschluessel. Gearbeitet wird mit {@link JdbcTemplate} und direkten Anweisungen — der Weg
 * ueber die Entities kaeme an einigen dieser Faelle gar nicht vorbei.
 */
class RechnungSchemaIT extends AbstractIntegrationTest {

  private static final String INSERT_ENTWURF =
      "INSERT INTO rechnung (angebot_id, zustand, rechnung_datum, nummer)"
          + " VALUES (?, 'ENTWURF', DATE '2026-09-30', ?)";

  private static final String INSERT_GESTELLT =
      "INSERT INTO rechnung"
          + " (angebot_id, zustand, rechnung_datum, nummer, steuersatz, zahlungsziel_tage,"
          + " gestellt_am, empfaenger_firma, absender_name)"
          + " VALUES (?, 'GESTELLT', DATE '2026-09-30', ?, CAST(? AS numeric), 10, now(),"
          + " 'Adler AG', 'Manfred Wolff')";

  private static final String INSERT_POSITION =
      "INSERT INTO rechnung_position"
          + " (rechnung_id, angebot_position_id, position, bezeichnung, einheit, menge,"
          + " einzelpreis)"
          + " VALUES (?, ?, ?, 'Beratung', 'STUNDE', CAST(? AS numeric), CAST('120.00' AS numeric))";

  private static final String STEUERSATZ = "19.00";

  private final JdbcTemplate jdbc;

  private long angebotId;
  private long angebotPositionId;

  @Autowired
  RechnungSchemaIT(final JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @BeforeEach
  void leereFachtabellenUndLegeEinAngebotMitPositionAn() {
    jdbc.execute(
        "TRUNCATE rechnung_position, rechnung, angebot_position, angebot, ansprechpartner, firma"
            + " RESTART IDENTITY CASCADE");
    jdbc.update("INSERT INTO firma (name) VALUES ('Adler AG')");
    final long firmaId =
        jdbc.queryForObject("SELECT id FROM firma LIMIT 1", Long.class).longValue();
    jdbc.update(
        "INSERT INTO angebot (firma_id, status, angebot_datum)"
            + " VALUES (?, 'BESTELLT', DATE '2026-09-20')",
        Long.valueOf(firmaId));
    angebotId = jdbc.queryForObject("SELECT id FROM angebot LIMIT 1", Long.class).longValue();
    jdbc.update(
        "INSERT INTO angebot_position"
            + " (angebot_id, position, bezeichnung, abrechnungsmodus, menge, einheit, einzelpreis)"
            + " VALUES (?, 1, 'Beratung', 'AUFWAND', 10, 'STUNDE', 120.00)",
        Long.valueOf(angebotId));
    angebotPositionId =
        jdbc.queryForObject("SELECT id FROM angebot_position LIMIT 1", Long.class).longValue();
  }

  private long rechnungId() {
    jdbc.update(INSERT_ENTWURF, Long.valueOf(angebotId), null);
    return jdbc.queryForObject("SELECT id FROM rechnung LIMIT 1", Long.class).longValue();
  }

  private int position(final long rechnungId, final int platz, final String menge) {
    return jdbc.update(
        INSERT_POSITION,
        Long.valueOf(rechnungId),
        Long.valueOf(angebotPositionId),
        Integer.valueOf(platz),
        menge);
  }

  @Test
  void rechnungEntwurf_givenANumberOnADraft_thenRejectedByTheDatabase() {
    final Long angebot = Long.valueOf(angebotId);

    // When / Then — ein Entwurf traegt keine Nummer; erst das Stellen vergibt sie.
    assertThatThrownBy(() -> jdbc.update(INSERT_ENTWURF, angebot, "R26-0004"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("rechnung_entwurf");
  }

  @Test
  void rechnungGestellt_givenAnIssuedInvoiceWithoutANumber_thenRejectedByTheDatabase() {
    final Long angebot = Long.valueOf(angebotId);

    // When / Then
    assertThatThrownBy(() -> jdbc.update(INSERT_GESTELLT, angebot, null, STEUERSATZ))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("rechnung_gestellt");
  }

  @Test
  void rechnungGestellt_givenEveryMandatoryField_thenAccepted() {
    // When — dieselbe Anweisung mit Nummer geht durch; sonst belegte der Test nur einen Tippfehler.
    final int betroffen =
        jdbc.update(INSERT_GESTELLT, Long.valueOf(angebotId), "R26-0004", STEUERSATZ);

    // Then
    assertThat(betroffen).isEqualTo(1);
  }

  @Test
  void rechnungNummer_givenTheSameNumberTwice_thenRejectedByTheDatabase() {
    // Given
    jdbc.update(INSERT_GESTELLT, Long.valueOf(angebotId), "R26-0004", STEUERSATZ);

    final Long angebot = Long.valueOf(angebotId);

    // When / Then
    assertThatThrownBy(() -> jdbc.update(INSERT_GESTELLT, angebot, "R26-0004", STEUERSATZ))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("rechnung_nummer_key");
  }

  @Test
  void position_givenAQuantityOfZero_thenRejectedByTheDatabase() {
    // Given — eine Position ueber nichts ist keine Position; wer nicht abrechnet, laesst sie weg.
    final long rechnungId = rechnungId();

    // When / Then
    assertThatThrownBy(() -> position(rechnungId, 1, "0"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("rechnung_position_menge");
  }

  @Test
  void position_givenTheSameAngebotspositionTwiceAtOneInvoice_thenRejectedByTheDatabase() {
    // Given
    final long rechnungId = rechnungId();
    position(rechnungId, 1, "3.00");

    // When / Then
    assertThatThrownBy(() -> position(rechnungId, 2, "2.00"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("rechnung_position_je_angebotsposition");
  }

  @Test
  void position_givenAnUnknownAngebotsposition_thenRejectedByTheDatabase() {
    // Given
    final long rechnungId = rechnungId();

    final Long rechnung = Long.valueOf(rechnungId);
    final Long unbekanntePosition = Long.valueOf(4711L);
    final Integer platz = Integer.valueOf(1);

    // When / Then
    assertThatThrownBy(
            () -> jdbc.update(INSERT_POSITION, rechnung, unbekanntePosition, platz, "3.00"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void positionReihenfolge_thenTheUniqueConstraintIsDeferred() {
    // When — ohne das Aufschieben scheiterte das Aufruecken nach einer weggefallenen Position.
    final Boolean aufgeschoben =
        jdbc.queryForObject(
            "SELECT condeferred FROM pg_constraint"
                + " WHERE conname = 'rechnung_position_reihenfolge'",
            Boolean.class);

    // Then
    assertThat(aufgeschoben).isTrue();
  }

  @Test
  void migration_thenNoStoredAmountOrSumColumnExists() {
    // When — E5: Betrag, Summe, Steuer und Brutto werden gerechnet, nicht gefuehrt.
    final Integer spalten =
        jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.columns WHERE table_schema = 'public'"
                + " AND table_name IN ('rechnung', 'rechnung_position')"
                + " AND column_name IN ('betrag', 'summe', 'netto', 'steuer', 'brutto')",
            Integer.class);

    // Then
    assertThat(spalten).isZero();
  }
}
