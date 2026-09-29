package org.mwolff.fbcrm.angebot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Prueft {@code V11__angebot_ohne_beleg.sql}: Aus dem Beleg wird die Mappe mit Status (Issue #133).
 *
 * <p>Wie in {@code KundenMigrationIT} migriert der Test ein eigenes Schema zuerst bis Version 10,
 * legt dort Angebote von damals an — ein Entwurf und je ein versendetes, angenommenes, abgelehntes
 * und abgeloestes Angebot, mit und ohne Leistungsbeschreibung — und migriert danach zu Ende. Im
 * Schema der Suite gibt es den Zustand vor V11 nicht mehr.
 *
 * <p>Gegenstand ist, dass kein bestehendes Angebot verloren geht: Jedes traegt danach den Status,
 * der seinem Zustand entspricht, und seine Zahlungsbedingungen stehen am Ende seines Texts. Ein
 * leerer bisheriger Text verschluckt dabei nichts und laesst auch keine Leerzeilen davor stehen.
 */
class AngebotStatusMigrationIT extends AbstractIntegrationTest {

  private static final String PROBE = "migration_v11";
  private static final String BEDINGUNGEN = "Zahlbar innerhalb von 14 Tagen.";

  private final JdbcTemplate jdbc;
  private final DataSource dataSource;

  private long firma;
  private int laufendeNummer;

  @Autowired
  AngebotStatusMigrationIT(final JdbcTemplate jdbc, final DataSource dataSource) {
    this.jdbc = jdbc;
    this.dataSource = dataSource;
  }

  private Flyway flywayBis(final String ziel) {
    return Flyway.configure()
        .dataSource(dataSource)
        .schemas(PROBE)
        .locations("classpath:db/migration")
        .target(ziel)
        .cleanDisabled(false)
        .load();
  }

  @BeforeEach
  void migriereBisZehnUndLegeEineFirmaAn() {
    final Flyway bisZehn = flywayBis("10");
    bisZehn.clean();
    bisZehn.migrate();
    jdbc.update("INSERT INTO " + PROBE + ".firma (name) VALUES ('Adler AG')");
    firma = jdbc.queryForObject("SELECT id FROM " + PROBE + ".firma", Long.class).longValue();
  }

  @AfterEach
  void raeumeDasSchemaAb() {
    flywayBis("latest").clean();
  }

  private void entwurf(final @Nullable String beschreibung, final @Nullable String bedingungen) {
    jdbc.update(
        "INSERT INTO "
            + PROBE
            + ".angebot (firma_id, zustand, angebot_datum, gueltig_bis,"
            + " leistungsbeschreibung, zahlungsbedingungen)"
            + " VALUES (?, 'ENTWURF', DATE '2026-09-20', DATE '2026-10-20', ?, ?)",
        Long.valueOf(firma),
        beschreibung,
        bedingungen);
  }

  private void festgeschrieben(
      final String zustand,
      final @Nullable String beschreibung,
      final @Nullable String bedingungen) {
    laufendeNummer++;
    jdbc.update(
        "INSERT INTO "
            + PROBE
            + ".angebot (firma_id, zustand, nummer, angebot_datum, gueltig_bis,"
            + " leistungsbeschreibung, zahlungsbedingungen, versendet_am, pdf_schluessel,"
            + " empfaenger_firma, absender_name)"
            + " VALUES (?, ?, ?, DATE '2026-09-20', DATE '2026-10-20', ?, ?,"
            + " TIMESTAMPTZ '2026-09-21 09:00:00+00', 'angebot/beleg.pdf', 'Adler AG',"
            + " 'Manfred Wolff')",
        Long.valueOf(firma),
        zustand,
        "A-2026-%03d".formatted(laufendeNummer),
        beschreibung,
        bedingungen);
  }

  private List<Map<String, Object>> zeilenNachMigration() {
    flywayBis("latest").migrate();
    return jdbc.queryForList(
        "SELECT status, leistungsbeschreibung FROM " + PROBE + ".angebot ORDER BY id");
  }

  @Test
  void migration_givenEveryOldState_thenEachAngebotCarriesItsStatus() {
    // Given
    entwurf("Konzeption", null);
    festgeschrieben("VERSENDET", "Konzeption", null);
    festgeschrieben("ANGENOMMEN", "Konzeption", null);
    festgeschrieben("ABGELEHNT", "Konzeption", null);
    festgeschrieben("ABGELOEST", "Konzeption", null);

    // When / Then — liegen gelassen wird als „abgegeben" gefuehrt; angenommen heisst bestellt.
    assertThat(zeilenNachMigration())
        .extracting(zeile -> zeile.get("status"))
        .containsExactly("ANGELEGT", "ABGEGEBEN", "BESTELLT", "ABGEGEBEN", "ABGEGEBEN");
  }

  @Test
  void migration_givenATextAndPaymentTerms_thenTheTermsFollowTheText() {
    // Given — je ein Entwurf und ein festgeschriebenes Angebot mit beidem.
    entwurf("Konzeption der Website", BEDINGUNGEN);
    festgeschrieben("ANGENOMMEN", "Schulungstag", BEDINGUNGEN);

    // When / Then
    assertThat(zeilenNachMigration())
        .extracting(zeile -> zeile.get("status"), zeile -> zeile.get("leistungsbeschreibung"))
        .containsExactly(
            tuple("ANGELEGT", "Konzeption der Website\n\n" + BEDINGUNGEN),
            tuple("BESTELLT", "Schulungstag\n\n" + BEDINGUNGEN));
  }

  @Test
  void migration_givenNoOrABlankText_thenTheTermsStandAlone() {
    // Given — ohne bisherigen Text und mit einem Text aus Leerzeichen.
    entwurf(null, BEDINGUNGEN);
    festgeschrieben("VERSENDET", "   ", BEDINGUNGEN);

    // When / Then
    assertThat(zeilenNachMigration())
        .extracting(zeile -> zeile.get("leistungsbeschreibung"))
        .containsExactly(BEDINGUNGEN, BEDINGUNGEN);
  }

  @Test
  void migration_givenNoOrBlankTerms_thenTheTextStaysAsItWas() {
    // Given — leere Zahlungsbedingungen haengen nichts an, auch keine Leerzeilen.
    entwurf("Konzeption", null);
    festgeschrieben("ABGELEHNT", "Schulungstag", "  ");
    entwurf(null, null);

    // When / Then
    assertThat(zeilenNachMigration())
        .extracting(zeile -> zeile.get("leistungsbeschreibung"))
        .containsExactly("Konzeption", "Schulungstag", null);
  }

  @Test
  void migration_thenTheColumnsOfTheBelegAndTheNumberRangeAreGone() {
    // When
    flywayBis("latest").migrate();
    final List<String> spalten =
        jdbc.queryForList(
            "SELECT column_name FROM information_schema.columns"
                + " WHERE table_schema = ? AND table_name = 'angebot' ORDER BY column_name",
            String.class,
            PROBE);
    final Integer nummernkreis =
        jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.tables"
                + " WHERE table_schema = ? AND table_name = 'angebot_nummernkreis'",
            Integer.class,
            PROBE);

    // Then
    assertThat(spalten)
        .containsExactly(
            "angebot_datum",
            "ansprechpartner_id",
            "created_at",
            "firma_id",
            "id",
            "leistungsbeschreibung",
            "status",
            "updated_at");
    assertThat(nummernkreis).isZero();
  }

  @Test
  void migration_thenTheStatusIsMandatory() {
    // When
    flywayBis("latest").migrate();
    final String nullable =
        jdbc.queryForObject(
            "SELECT is_nullable FROM information_schema.columns"
                + " WHERE table_schema = ? AND table_name = 'angebot' AND column_name = 'status'",
            String.class,
            PROBE);

    // Then
    assertThat(nullable).isEqualTo("NO");
  }
}
