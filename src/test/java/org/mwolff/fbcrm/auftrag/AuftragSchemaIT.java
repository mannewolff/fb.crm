package org.mwolff.fbcrm.auftrag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Prueft die Migration {@code V8__auftrag.sql} gegen eine echte PostgreSQL-Instanz.
 *
 * <p>Gegenstand sind die Zusagen, die allein die Datenbank haelt: der Wertebereich des Status, der
 * Leistungszeitraum „ganz oder gar nicht, Ende nicht vor Beginn" (E21), die zwei gegenlaeufigen
 * Checks um „Stunden je Personentag" (E10), die Einmaligkeit von Nummer und Angebot und die
 * Wertebereiche der Positionen. Gearbeitet wird mit {@link JdbcTemplate} und direkten Anweisungen —
 * der Weg ueber die Entities kaeme an einigen dieser Faelle gar nicht vorbei.
 */
class AuftragSchemaIT extends AbstractIntegrationTest {

  private static final String INSERT_AUFTRAG =
      "INSERT INTO auftrag"
          + " (vorgang_id, angebot_id, nummer, status, auftrag_datum, kundenbestellnummer,"
          + " leistung_ab, leistung_bis)"
          + " VALUES (?, ?, ?, ?, DATE '2026-09-22', ?, CAST(? AS date), CAST(? AS date))";

  private static final String INSERT_POSITION =
      "INSERT INTO auftrag_position"
          + " (auftrag_id, position, bezeichnung, abrechnungsmodus, menge, einheit, einzelpreis,"
          + " stunden_je_personentag)"
          + " VALUES (?, ?, ?, ?, CAST(? AS numeric), ?, CAST(? AS numeric), CAST(? AS numeric))";

  private static final String NUMMER = "AU-2026-001";
  private static final String BESTELLNUMMER = "BST-4711";
  private static final String BEGINN = "2026-10-01";
  private static final String ENDE = "2026-12-31";

  private final JdbcTemplate jdbc;

  private long vorgangId;
  private long angebotId;

  @Autowired
  AuftragSchemaIT(final JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @BeforeEach
  void leereFachtabellenUndLegeVorgangUndAngebotAn() {
    jdbc.execute(
        "TRUNCATE auftrag_position, auftrag, angebot_position, angebot, vorgang_eintrag, vorgang,"
            + " ansprechpartner, firma RESTART IDENTITY CASCADE");
    jdbc.execute("DELETE FROM auftrag_nummernkreis");
    jdbc.update("INSERT INTO firma (name) VALUES ('Adler AG')");
    final Long firmaId =
        jdbc.queryForObject("SELECT id FROM firma WHERE name = 'Adler AG'", Long.class);
    jdbc.update(
        "INSERT INTO vorgang (nummer, titel, firma_id) VALUES (1, 'Website-Relaunch', ?)", firmaId);
    vorgangId =
        jdbc.queryForObject("SELECT id FROM vorgang WHERE nummer = 1", Long.class).longValue();
    jdbc.update(
        "INSERT INTO angebot (vorgang_id, zustand, angebot_datum, gueltig_bis)"
            + " VALUES (?, 'ENTWURF', DATE '2026-09-20', DATE '2026-10-20')",
        vorgangId);
    angebotId = jdbc.queryForObject("SELECT id FROM angebot LIMIT 1", Long.class).longValue();
  }

  private int auftrag(
      final long angebot,
      final String nummer,
      final String status,
      final String leistungAb,
      final String leistungBis) {
    return jdbc.update(
        INSERT_AUFTRAG, vorgangId, angebot, nummer, status, BESTELLNUMMER, leistungAb, leistungBis);
  }

  private long auftragId() {
    auftrag(angebotId, NUMMER, "OFFEN", null, null);
    return jdbc.queryForObject("SELECT id FROM auftrag LIMIT 1", Long.class).longValue();
  }

  private int position(
      final String abrechnungsmodus, final String einheit, final String stundenJePersonentag) {
    return jdbc.update(
        INSERT_POSITION,
        Long.valueOf(auftragId()),
        1,
        "Konzeption",
        abrechnungsmodus,
        "2.50",
        einheit,
        "1000.01",
        stundenJePersonentag);
  }

  private int positionMitMengeUndPreis(final String menge, final String einzelpreis) {
    return jdbc.update(
        INSERT_POSITION,
        Long.valueOf(auftragId()),
        1,
        "Konzeption",
        "AUFWAND",
        menge,
        "PERSONENTAG",
        einzelpreis,
        "8.00");
  }

  @Test
  void migration_thenTheThreeTablesExist() {
    // When
    final Integer tabellen =
        jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public'"
                + " AND table_name IN ('auftrag', 'auftrag_position', 'auftrag_nummernkreis')",
            Integer.class);

    // Then
    assertThat(tabellen).isEqualTo(3);
  }

  @Test
  void migration_thenTheVorgangIndexExistsAndNoneOnTheStatus() {
    // When — drei Werte auf der Tabelle eines Freiberuflers tragen keinen Index.
    final Integer aufVorgang =
        jdbc.queryForObject(
            "SELECT count(*) FROM pg_indexes WHERE schemaname = 'public'"
                + " AND indexname = 'auftrag_vorgang_idx'",
            Integer.class);
    final Integer aufStatus =
        jdbc.queryForObject(
            "SELECT count(*) FROM pg_indexes WHERE schemaname = 'public'"
                + " AND tablename = 'auftrag' AND indexdef LIKE '%(status)%'",
            Integer.class);

    // Then
    assertThat(aufVorgang).isEqualTo(1);
    assertThat(aufStatus).isZero();
  }

  @Test
  void migration_thenNoStoredAmountOrSumColumnExists() {
    // When — E11: Betrag und Summe werden gerechnet, nicht gefuehrt.
    final Integer spalten =
        jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.columns WHERE table_schema = 'public'"
                + " AND table_name IN ('auftrag', 'auftrag_position')"
                + " AND column_name IN ('betrag', 'summe')",
            Integer.class);

    // Then
    assertThat(spalten).isZero();
  }

  @Test
  void auftragStatus_givenEachOfTheThreeValues_thenAccepted() {
    // When / Then — jeder Status steht fuer sich; gewechselt wird in jede Richtung frei (F6).
    assertThat(auftrag(angebotId, "AU-2026-001", "OFFEN", null, null)).isEqualTo(1);
    jdbc.update(
        "INSERT INTO angebot (vorgang_id, zustand, angebot_datum, gueltig_bis)"
            + " VALUES (?, 'ENTWURF', DATE '2026-09-20', DATE '2026-10-20')",
        vorgangId);
    final long zweites = jdbc.queryForObject("SELECT max(id) FROM angebot", Long.class).longValue();
    assertThat(auftrag(zweites, "AU-2026-002", "IN_ARBEIT", null, null)).isEqualTo(1);
    jdbc.update(
        "INSERT INTO angebot (vorgang_id, zustand, angebot_datum, gueltig_bis)"
            + " VALUES (?, 'ENTWURF', DATE '2026-09-20', DATE '2026-10-20')",
        vorgangId);
    final long drittes = jdbc.queryForObject("SELECT max(id) FROM angebot", Long.class).longValue();
    assertThat(auftrag(drittes, "AU-2026-003", "ABGESCHLOSSEN", null, null)).isEqualTo(1);
  }

  @Test
  void auftragStatus_givenAnUnknownStatus_thenRejectedByTheDatabase() {
    // When / Then — „storniert" ist kein Status des Auftrags; geloescht wird echt (Kriterium 15).
    assertThatThrownBy(() -> auftrag(angebotId, NUMMER, "STORNIERT", null, null))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("auftrag_status");
  }

  @Test
  void auftragLeistungszeitraum_givenBothDatesEmpty_thenAccepted() {
    // When — der Leistungszeitraum ist freiwillig (Kriterium 3).
    final int betroffen = auftrag(angebotId, NUMMER, "OFFEN", null, null);

    // Then
    assertThat(betroffen).isEqualTo(1);
  }

  @Test
  void auftragLeistungszeitraum_givenTheEndAfterTheStart_thenAccepted() {
    // When
    final int betroffen = auftrag(angebotId, NUMMER, "OFFEN", BEGINN, ENDE);

    // Then
    assertThat(betroffen).isEqualTo(1);
  }

  @Test
  void auftragLeistungszeitraum_givenAOneDayPeriod_thenAccepted() {
    // When — der Beginn darf der letzte Tag sein; „nicht vor" schliesst den gleichen Tag ein.
    final int betroffen = auftrag(angebotId, NUMMER, "OFFEN", BEGINN, BEGINN);

    // Then
    assertThat(betroffen).isEqualTo(1);
  }

  @Test
  void auftragLeistungszeitraum_givenOnlyTheStart_thenRejectedByTheDatabase() {
    // When / Then — E21: ganz oder gar nicht.
    assertThatThrownBy(() -> auftrag(angebotId, NUMMER, "OFFEN", BEGINN, null))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("auftrag_leistungszeitraum");
  }

  @Test
  void auftragLeistungszeitraum_givenOnlyTheEnd_thenRejectedByTheDatabase() {
    // When / Then
    assertThatThrownBy(() -> auftrag(angebotId, NUMMER, "OFFEN", null, ENDE))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("auftrag_leistungszeitraum");
  }

  @Test
  void auftragLeistungszeitraum_givenTheEndBeforeTheStart_thenRejectedByTheDatabase() {
    // When / Then
    assertThatThrownBy(() -> auftrag(angebotId, NUMMER, "OFFEN", ENDE, BEGINN))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("auftrag_leistungszeitraum");
  }

  @Test
  void auftragAngebotId_givenTheSameOfferTwice_thenRejectedByTheDatabase() {
    // Given — F9: hoechstens ein Auftrag je Angebot.
    auftrag(angebotId, NUMMER, "OFFEN", null, null);

    // When / Then
    assertThatThrownBy(() -> auftrag(angebotId, "AU-2026-002", "OFFEN", null, null))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("auftrag_angebot_id_key");
  }

  @Test
  void auftragNummer_givenTheSameNumberTwice_thenRejectedByTheDatabase() {
    // Given
    auftrag(angebotId, NUMMER, "OFFEN", null, null);
    jdbc.update(
        "INSERT INTO angebot (vorgang_id, zustand, angebot_datum, gueltig_bis)"
            + " VALUES (?, 'ENTWURF', DATE '2026-09-20', DATE '2026-10-20')",
        vorgangId);
    final long zweites = jdbc.queryForObject("SELECT max(id) FROM angebot", Long.class).longValue();

    // When / Then
    assertThatThrownBy(() -> auftrag(zweites, NUMMER, "OFFEN", null, null))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("auftrag_nummer_key");
  }

  @Test
  void auftragVorgangId_givenAnUnknownVorgang_thenRejectedByTheDatabase() {
    // When / Then
    assertThatThrownBy(
            () ->
                jdbc.update(
                    INSERT_AUFTRAG,
                    4711L,
                    Long.valueOf(angebotId),
                    NUMMER,
                    "OFFEN",
                    BESTELLNUMMER,
                    null,
                    null))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void auftragAngebotId_givenAnUnknownOffer_thenRejectedByTheDatabase() {
    // When / Then
    assertThatThrownBy(() -> auftrag(4711L, NUMMER, "OFFEN", null, null))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void position_givenAnExpensePositionWithAFactor_thenAccepted() {
    // When — E10: 7,50 Stunden je Personentag geht durch.
    final int betroffen = position("AUFWAND", "PERSONENTAG", "7.50");

    // Then
    assertThat(betroffen).isEqualTo(1);
  }

  @Test
  void position_givenAnExpensePositionInHoursWithAFactor_thenAccepted() {
    // When — der Faktor haengt am Abrechnungsmodus und nicht an der Einheit (E10).
    final int betroffen = position("AUFWAND", "STUNDE", "8.00");

    // Then
    assertThat(betroffen).isEqualTo(1);
  }

  @Test
  void position_givenAnExpensePositionWithoutAFactor_thenRejectedByTheDatabase() {
    // When / Then
    assertThatThrownBy(() -> position("AUFWAND", "PERSONENTAG", null))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("auftrag_position_aufwand");
  }

  @Test
  void position_givenAnExpensePositionWithAFactorOfZero_thenRejectedByTheDatabase() {
    // When / Then — ein Personentag ohne Stunden waere keine Umrechnung.
    assertThatThrownBy(() -> position("AUFWAND", "PERSONENTAG", "0"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("auftrag_position_aufwand");
  }

  @Test
  void position_givenAFixedPricePositionWithoutAFactor_thenAccepted() {
    // When
    final int betroffen = position("FESTPREIS", "PAUSCHAL", null);

    // Then
    assertThat(betroffen).isEqualTo(1);
  }

  @Test
  void position_givenAFixedPricePositionWithAFactor_thenRejectedByTheDatabase() {
    // When / Then — E10: beim Festpreis bleibt die Spalte leer.
    assertThatThrownBy(() -> position("FESTPREIS", "PAUSCHAL", "8.00"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("auftrag_position_festpreis");
  }

  @Test
  void position_givenAnUnknownBillingMode_thenRejectedByTheDatabase() {
    // When / Then — die Werte stehen Wort fuer Wort wie in V6.
    assertThatThrownBy(() -> position("SCHAETZUNG", "STUNDE", "8.00"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("auftrag_position_abrechnungsmodus");
  }

  @Test
  void position_givenAnUnknownUnit_thenRejectedByTheDatabase() {
    // When / Then
    assertThatThrownBy(() -> position("AUFWAND", "WOCHE", "8.00"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("auftrag_position_einheit");
  }

  @Test
  void position_givenANegativeQuantity_thenRejectedByTheDatabase() {
    // When / Then
    assertThatThrownBy(() -> positionMitMengeUndPreis("-1.00", "95.00"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("auftrag_position_menge");
  }

  @Test
  void position_givenANegativeUnitPrice_thenRejectedByTheDatabase() {
    // When / Then
    assertThatThrownBy(() -> positionMitMengeUndPreis("1.00", "-95.00"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("auftrag_position_einzelpreis");
  }

  @Test
  void position_givenTheSamePlaceTwiceAtOneOrder_thenRejectedByTheDatabase() {
    // Given
    final Long auftragId = Long.valueOf(auftragId());
    jdbc.update(
        INSERT_POSITION, auftragId, 1, "Konzeption", "AUFWAND", "1.00", "STUNDE", "95.00", "8.00");

    // When / Then
    assertThatThrownBy(
            () ->
                jdbc.update(
                    INSERT_POSITION,
                    auftragId,
                    1,
                    "Schulung",
                    "FESTPREIS",
                    "1.00",
                    "PAUSCHAL",
                    "1200.00",
                    null))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("auftrag_position_reihenfolge");
  }

  @Test
  void positionAuftragId_givenAnUnknownOrder_thenRejectedByTheDatabase() {
    // When / Then
    assertThatThrownBy(
            () ->
                jdbc.update(
                    INSERT_POSITION,
                    4711L,
                    1,
                    "Konzeption",
                    "AUFWAND",
                    "1.00",
                    "STUNDE",
                    "95.00",
                    "8.00"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void nummernkreis_givenTheSameYearTwice_thenRejectedByTheDatabase() {
    // Given
    jdbc.update("INSERT INTO auftrag_nummernkreis (jahr, naechste) VALUES (2026, 1)");

    // When / Then
    assertThatThrownBy(
            () -> jdbc.update("INSERT INTO auftrag_nummernkreis (jahr, naechste) VALUES (2026, 5)"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void migration_thenNoForeignKeyCarriesAnOnDeleteAction() {
    // When — Kriterium 15: geloescht wird nur ueber den Anwendungsfall, erst die Positionen, dann
    // die Zeile, die sie traegt.
    final Integer mitAktion =
        jdbc.queryForObject(
            "SELECT count(*) FROM pg_constraint c JOIN pg_class t ON t.oid = c.conrelid"
                + " WHERE c.contype = 'f' AND t.relname IN ('auftrag', 'auftrag_position')"
                + " AND c.confdeltype <> 'a'",
            Integer.class);

    // Then
    assertThat(mitAktion).isZero();
  }
}
