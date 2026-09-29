package org.mwolff.fbcrm.angebot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Prueft die Migration {@code V6__angebot.sql} gegen eine echte PostgreSQL-Instanz.
 *
 * <p>Gegenstand sind die Zusagen, die allein die Datenbank haelt: die beiden gegenlaeufigen Checks
 * um Entwurf und festgeschriebenes Dokument, die Gueltigkeitsregel, die Wertebereiche der
 * Positionen und die Reihenfolge als Schluessel. Gearbeitet wird mit {@link JdbcTemplate} und
 * direkten Anweisungen — der Weg ueber die Entities kaeme an einigen dieser Faelle gar nicht
 * vorbei.
 */
class AngebotSchemaIT extends AbstractIntegrationTest {

  private static final String INSERT_ANGEBOT =
      "INSERT INTO angebot"
          + " (firma_id, zustand, angebot_datum, gueltig_bis, nummer, versendet_am,"
          + " pdf_schluessel, empfaenger_firma, absender_name)"
          + " VALUES (?, ?, DATE '2026-09-20', CAST(? AS date), ?, CAST(? AS timestamptz),"
          + " ?, ?, ?)";

  private static final String INSERT_POSITION =
      "INSERT INTO angebot_position"
          + " (angebot_id, position, bezeichnung, abrechnungsmodus, menge, einheit, einzelpreis)"
          + " VALUES (?, ?, ?, ?, CAST(? AS numeric), ?, CAST(? AS numeric))";

  private static final String GUELTIG = "2026-10-20";
  private static final String VOR_DEM_ANGEBOTSDATUM = "2026-09-19";
  private static final String NUMMER = "A-2026-001";
  private static final String PDF_SCHLUESSEL = "angebot/1/abc.pdf";
  private static final String FIRMA = "Adler AG";
  private static final String ABSENDER = "Manfred Wolff";
  private static final String VERSANDZEITPUNKT = "2026-09-21 09:00:00+00";

  private final JdbcTemplate jdbc;

  private long firmaId;

  @Autowired
  AngebotSchemaIT(final JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @BeforeEach
  void leereFachtabellenUndLegeEineFirmaAn() {
    jdbc.execute(
        "TRUNCATE angebot_position, angebot, ansprechpartner, firma RESTART IDENTITY CASCADE");
    jdbc.execute("DELETE FROM angebot_nummernkreis");
    jdbc.update("INSERT INTO firma (name) VALUES (?)", FIRMA);
    firmaId =
        jdbc.queryForObject("SELECT id FROM firma WHERE name = ?", Long.class, FIRMA).longValue();
  }

  private int entwurf(final String gueltigBis) {
    return jdbc.update(
        INSERT_ANGEBOT, firmaId, "ENTWURF", gueltigBis, null, null, null, null, null);
  }

  private int versendet(final String gueltigBis, final String nummer) {
    return jdbc.update(
        INSERT_ANGEBOT,
        firmaId,
        "VERSENDET",
        gueltigBis,
        nummer,
        VERSANDZEITPUNKT,
        PDF_SCHLUESSEL,
        FIRMA,
        ABSENDER);
  }

  private Long angebotId() {
    entwurf(GUELTIG);
    return jdbc.queryForObject("SELECT id FROM angebot LIMIT 1", Long.class);
  }

  @Test
  void migration_thenTheThreeTablesExist() {
    // When
    final Integer tabellen =
        jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public'"
                + " AND table_name IN ('angebot', 'angebot_position', 'angebot_nummernkreis')",
            Integer.class);

    // Then
    assertThat(tabellen).isEqualTo(3);
  }

  @Test
  void migration_thenTheTwoIndexesExist() {
    // When
    final Integer indizes =
        jdbc.queryForObject(
            "SELECT count(*) FROM pg_indexes WHERE schemaname = 'public'"
                + " AND indexname IN ('angebot_firma_idx', 'angebot_zustand_gueltigkeit_idx')",
            Integer.class);

    // Then
    assertThat(indizes).isEqualTo(2);
  }

  @Test
  void angebot_givenADraftWithoutNumberAndCopies_thenAccepted() {
    // When
    final int betroffen = entwurf(GUELTIG);

    // Then
    assertThat(betroffen).isEqualTo(1);
  }

  @Test
  void angebot_givenADraftWithValidityBeforeTheOfferDate_thenAccepted() {
    // When — E27: der Entwurf darf halbfertig sein.
    final int betroffen = entwurf(VOR_DEM_ANGEBOTSDATUM);

    // Then
    assertThat(betroffen).isEqualTo(1);
  }

  @Test
  void angebot_givenADraftWithANumber_thenRejectedByTheDatabase() {
    // When / Then
    assertThatThrownBy(
            () ->
                jdbc.update(
                    INSERT_ANGEBOT, firmaId, "ENTWURF", GUELTIG, NUMMER, null, null, null, null))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("angebot_entwurf");
  }

  @Test
  void angebot_givenADraftWithARecipientCopy_thenRejectedByTheDatabase() {
    // When / Then
    assertThatThrownBy(
            () ->
                jdbc.update(
                    INSERT_ANGEBOT, firmaId, "ENTWURF", GUELTIG, null, null, null, FIRMA, null))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("angebot_entwurf");
  }

  @Test
  void angebot_givenACommittedOfferWithNumberCopiesAndPdf_thenAccepted() {
    // When
    final int betroffen = versendet(GUELTIG, NUMMER);

    // Then
    assertThat(betroffen).isEqualTo(1);
  }

  @Test
  void angebot_givenACommittedOfferWithoutANumber_thenRejectedByTheDatabase() {
    // When / Then
    assertThatThrownBy(() -> versendet(GUELTIG, null))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("angebot_festgeschrieben");
  }

  @Test
  void angebot_givenACommittedOfferWithValidityBeforeTheOfferDate_thenRejectedByTheDatabase() {
    // When / Then
    assertThatThrownBy(() -> versendet(VOR_DEM_ANGEBOTSDATUM, NUMMER))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("angebot_gueltigkeit");
  }

  @Test
  void angebotNummer_givenTheSameNumberTwice_thenRejectedByTheDatabase() {
    // Given — zwei Angebote an dieselbe Firma, beide mit derselben Nummer.
    versendet(GUELTIG, NUMMER);

    // When / Then
    assertThatThrownBy(() -> versendet(GUELTIG, NUMMER))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("angebot_nummer_key");
  }

  @Test
  void angebotZustand_givenAnUnknownState_thenRejectedByTheDatabase() {
    // When / Then — welchen der drei Checks Postgres nennt, haengt an der Reihenfolge der
    // Auswertung; gemeint ist, dass kein Weg hineinfuehrt.
    assertThatThrownBy(
            () ->
                jdbc.update(
                    INSERT_ANGEBOT, firmaId, "VERHANDELT", GUELTIG, null, null, null, null, null))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void angebotZustand_givenASixthStateWithEveryCommittedValue_thenRejectedByTheDatabase() {
    // Given — jeder der beiden Checks benennt die Gegenseite; ein sechster Zustand faellt durch
    // beide, ohne dass es dafuer eine eigene Regel braucht.
    // When / Then
    assertThatThrownBy(
            () ->
                jdbc.update(
                    INSERT_ANGEBOT,
                    firmaId,
                    "VERHANDELT",
                    GUELTIG,
                    NUMMER,
                    VERSANDZEITPUNKT,
                    PDF_SCHLUESSEL,
                    FIRMA,
                    ABSENDER))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void angebotFirmaId_givenAnUnknownFirma_thenRejectedByTheDatabase() {
    // When / Then
    assertThatThrownBy(
            () ->
                jdbc.update(
                    INSERT_ANGEBOT, 4711L, "ENTWURF", GUELTIG, null, null, null, null, null))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void position_givenABlankLabel_thenAccepted() {
    // When — E27: was zum Versenden fehlt, meldet die Anwendung Feld fuer Feld, nicht die
    // Datenbank.
    final int betroffen =
        jdbc.update(INSERT_POSITION, angebotId(), 1, "   ", "AUFWAND", "2.50", "PERSONENTAG", "0");

    // Then
    assertThat(betroffen).isEqualTo(1);
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
  void nummernkreis_givenTheSameYearTwice_thenRejectedByTheDatabase() {
    // Given
    jdbc.update("INSERT INTO angebot_nummernkreis (jahr, naechste) VALUES (2026, 1)");

    // When / Then
    assertThatThrownBy(
            () -> jdbc.update("INSERT INTO angebot_nummernkreis (jahr, naechste) VALUES (2026, 5)"))
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
