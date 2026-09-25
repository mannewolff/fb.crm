package org.mwolff.fbcrm.vorgang.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.mwolff.fbcrm.vorgang.domain.Vorgang;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Der Vorgang-Adapter gegen eine echte PostgreSQL-Instanz.
 *
 * <p>Gegenstand ist vor allem die Uebersichtsabfrage: die Reihenfolge aus E16 — juengster
 * Eintragszeitpunkt oben, ein Vorgang ohne Eintrag mit seinem Anlagezeitpunkt, bei gleichem
 * Zeitpunkt der zuletzt angelegte oben — und die Suche aus E17, die Text als Text und nicht als
 * Muster nimmt.
 *
 * <p>Die Historieneintraege der Vorgaenge legt dieser Test mit {@link JdbcTemplate} an und nicht
 * ueber den Eintrag-Adapter: Gegenstand ist die Sortierung der Uebersicht, und der Zeitpunkt des
 * Geschehens ist dafuer die einzige interessante Angabe.
 */
class JpaVorgangRepositoryIT extends AbstractIntegrationTest {

  private static final String INSERT_EINTRAG =
      "INSERT INTO vorgang_eintrag (vorgang_id, art, text, geschehen_am, herkunft)"
          + " VALUES (?, 'KOMMENTAR', 'Angerufen', ?, 'VON_HAND')";

  private static final Instant ANGELEGT = Instant.parse("2026-09-01T08:00:00Z");
  private static final Instant GEAENDERT = Instant.parse("2026-09-18T12:00:00Z");

  private final JpaVorgangRepository repository;
  private final JdbcTemplate jdbc;

  @Autowired
  JpaVorgangRepositoryIT(final JpaVorgangRepository repository, final JdbcTemplate jdbc) {
    this.repository = repository;
    this.jdbc = jdbc;
  }

  @BeforeEach
  void leereFachtabellen() {
    jdbc.execute(
        "TRUNCATE vorgang_eintrag, vorgang, ansprechpartner, firma RESTART IDENTITY CASCADE");
  }

  private long firmaId(final String name) {
    jdbc.update("INSERT INTO firma (name) VALUES (?)", name);
    return jdbc.queryForObject(
        "SELECT id FROM firma WHERE name = ? ORDER BY id DESC LIMIT 1", Long.class, name);
  }

  private long ansprechpartnerId(final long firmaId) {
    jdbc.update(
        "INSERT INTO ansprechpartner (firma_id, nachname) VALUES (?, 'Meier')",
        Long.valueOf(firmaId));
    return jdbc.queryForObject(
        "SELECT id FROM ansprechpartner ORDER BY id DESC LIMIT 1", Long.class);
  }

  private static Vorgang offen(final long nummer, final String titel, final long firmaId) {
    return new Vorgang(null, nummer, titel, firmaId, null, false, ANGELEGT, ANGELEGT);
  }

  private static Vorgang offenAngelegtAm(
      final long nummer, final String titel, final long firmaId, final Instant angelegtAm) {
    return new Vorgang(null, nummer, titel, firmaId, null, false, angelegtAm, angelegtAm);
  }

  private static Vorgang abgeschlossen(final long nummer, final String titel, final long firmaId) {
    return new Vorgang(null, nummer, titel, firmaId, null, true, ANGELEGT, ANGELEGT);
  }

  private void eintrag(final long vorgangId, final String geschehenAm) {
    jdbc.update(
        INSERT_EINTRAG,
        Long.valueOf(vorgangId),
        OffsetDateTime.ofInstant(Instant.parse(geschehenAm), ZoneOffset.UTC));
  }

  private List<String> titelDerUebersicht(
      final String suche, final Long nummer, final boolean auchAbgeschlossene) {
    return repository.uebersicht(suche, nummer, auchAbgeschlossene).stream()
        .map(Vorgang::titel)
        .toList();
  }

  @Test
  void save_thenAssignsAnIdFromTheDatabase() {
    // Given
    final long firmaId = firmaId("Adler AG");

    // When
    final Vorgang gesichert = repository.save(offen(1L, "Website-Relaunch", firmaId));

    // Then
    assertThat(gesichert.id()).isNotNull();
  }

  @Test
  void findById_afterSave_thenReturnsEveryStoredValue() {
    // Given
    final long firmaId = firmaId("Adler AG");
    final long ansprechpartnerId = ansprechpartnerId(firmaId);
    final Vorgang mitAnsprechpartner =
        new Vorgang(
            null, 1L, "Website-Relaunch", firmaId, ansprechpartnerId, false, ANGELEGT, ANGELEGT);
    final Vorgang gesichert = repository.save(mitAnsprechpartner);

    // When
    final Optional<Vorgang> gefunden = repository.findById(gesichert.requireId());

    // Then
    assertThat(gefunden).contains(gesichert);
  }

  @Test
  void findById_afterSavingAVorgangWithoutAnsprechpartner_thenReadsItBackAsAbsent() {
    // Given
    final long firmaId = firmaId("Adler AG");
    final Vorgang gesichert = repository.save(abgeschlossen(1L, "Website-Relaunch", firmaId));

    // When
    final Optional<Vorgang> gefunden = repository.findById(gesichert.requireId());

    // Then
    assertThat(gefunden)
        .hasValueSatisfying(
            vorgang -> {
              assertThat(vorgang.ansprechpartnerId()).isNull();
              assertThat(vorgang.abgeschlossen()).isTrue();
            });
  }

  @Test
  void findById_givenAnUnknownId_thenEmpty() {
    // When
    final Optional<Vorgang> gefunden = repository.findById(4711L);

    // Then
    assertThat(gefunden).isEmpty();
  }

  @Test
  void save_givenAChangedVorgang_thenStoresTheNewValues() {
    // Given
    final long firmaId = firmaId("Adler AG");
    final long zweiteFirmaId = firmaId("Baum GmbH");
    final Vorgang gesichert = repository.save(offen(1L, "Website-Relaunch", firmaId));

    // When
    repository.save(gesichert.geaendert("Shop-Relaunch", zweiteFirmaId, null, GEAENDERT));

    // Then
    assertThat(repository.findById(gesichert.requireId()))
        .hasValueSatisfying(
            vorgang -> {
              assertThat(vorgang.titel()).isEqualTo("Shop-Relaunch");
              assertThat(vorgang.firmaId()).isEqualTo(zweiteFirmaId);
              assertThat(vorgang.updatedAt()).isEqualTo(GEAENDERT);
            });
  }

  @Test
  void save_givenAClosedVorgang_thenStoresTheSwitch() {
    // Given
    final long firmaId = firmaId("Adler AG");
    final Vorgang gesichert = repository.save(offen(1L, "Website-Relaunch", firmaId));

    // When
    repository.save(gesichert.abgeschlossen(GEAENDERT));

    // Then
    assertThat(repository.findById(gesichert.requireId()))
        .hasValueSatisfying(vorgang -> assertThat(vorgang.abgeschlossen()).isTrue());
  }

  @Test
  void uebersicht_thenSortsByTheNewestEntryAndFallsBackToTheCreationTime() {
    // Given
    final long firmaId = firmaId("Adler AG");
    final Vorgang mitZweiEintraegen = repository.save(offen(1L, "Alpha", firmaId));
    eintrag(mitZweiEintraegen.requireId(), "2026-09-05T10:00:00Z");
    eintrag(mitZweiEintraegen.requireId(), "2026-09-10T10:00:00Z");
    repository.save(offenAngelegtAm(2L, "Beta", firmaId, Instant.parse("2026-09-11T10:00:00Z")));
    final Vorgang frueherAngelegt =
        repository.save(
            offenAngelegtAm(3L, "Gamma", firmaId, Instant.parse("2026-09-01T00:00:00Z")));
    eintrag(frueherAngelegt.requireId(), "2026-09-12T10:00:00Z");
    final Vorgang spaeterAngelegt =
        repository.save(
            offenAngelegtAm(4L, "Delta", firmaId, Instant.parse("2026-09-02T00:00:00Z")));
    eintrag(spaeterAngelegt.requireId(), "2026-09-12T10:00:00Z");

    // When
    final List<String> titel = titelDerUebersicht("", null, false);

    // Then
    assertThat(titel).containsExactly("Delta", "Gamma", "Beta", "Alpha");
  }

  @Test
  void uebersicht_thenKeepsTheOrderBetweenTwoCalls() {
    // Given
    final long firmaId = firmaId("Adler AG");
    final Vorgang erster = repository.save(offen(1L, "Alpha", firmaId));
    final Vorgang zweiter = repository.save(offen(2L, "Beta", firmaId));

    // When
    final List<Vorgang> ersterAufruf = repository.uebersicht("", null, false);
    final List<Vorgang> zweiterAufruf = repository.uebersicht("", null, false);

    // Then
    assertThat(List.of(ersterAufruf, zweiterAufruf))
        .allSatisfy(
            aufruf ->
                assertThat(aufruf)
                    .extracting(Vorgang::id)
                    .containsExactly(zweiter.requireId(), erster.requireId()));
  }

  @Test
  void uebersicht_givenANumber_thenFindsTheVorgangByItsNumberAlone() {
    // Given
    bestandFuerDieSuche();

    // When
    final List<String> titel = titelDerUebersicht("12", 12L, false);

    // Then
    assertThat(titel).containsExactly("Website-Relaunch");
  }

  @Test
  void uebersicht_givenANumberWithALeadingHash_thenStillFindsTheVorgang() {
    // Given
    bestandFuerDieSuche();

    // When
    final List<String> titel = titelDerUebersicht("#12", 12L, false);

    // Then
    assertThat(titel).containsExactly("Website-Relaunch");
  }

  @Test
  void uebersicht_givenASearchTextInAnotherCase_thenStillFindsTheTitel() {
    // Given
    bestandFuerDieSuche();

    // When
    final List<String> titel = titelDerUebersicht("RELAUNCH", null, false);

    // Then
    assertThat(titel).containsExactly("Website-Relaunch");
  }

  @Test
  void uebersicht_givenAPartOfTheFirmaName_thenFindsItsVorgaenge() {
    // Given
    bestandFuerDieSuche();

    // When
    final List<String> titel = titelDerUebersicht("bAuM", null, false);

    // Then
    assertThat(titel).containsExactly("Prozent % und Unter_strich");
  }

  @Test
  void uebersicht_givenAPercentSign_thenMatchesOnlyTitlesContainingIt() {
    // Given
    bestandFuerDieSuche();

    // When
    final List<String> titel = titelDerUebersicht("%", null, false);

    // Then
    assertThat(titel).containsExactly("Prozent % und Unter_strich");
  }

  @Test
  void uebersicht_givenAnUnderscore_thenMatchesOnlyTitlesContainingIt() {
    // Given
    bestandFuerDieSuche();

    // When
    final List<String> titel = titelDerUebersicht("_", null, false);

    // Then
    assertThat(titel).containsExactly("Prozent % und Unter_strich");
  }

  @Test
  void uebersicht_givenAPatternCharacterNoTitleContains_thenFindsNothing() {
    // Given
    final long firmaId = firmaId("Adler AG");
    repository.save(offen(12L, "Website-Relaunch", firmaId));

    // When
    final List<String> titel = titelDerUebersicht("%", null, false);

    // Then
    assertThat(titel).isEmpty();
  }

  @Test
  void uebersicht_givenTheSwitchIsOff_thenLeavesOutClosedVorgaenge() {
    // Given
    bestandFuerDieSuche();

    // When
    final List<String> titel = titelDerUebersicht("adler", null, false);

    // Then
    assertThat(titel).containsExactly("Website-Relaunch");
  }

  @Test
  void uebersicht_givenTheSwitchIsOn_thenIncludesClosedVorgaenge() {
    // Given
    bestandFuerDieSuche();

    // When
    final List<String> titel = titelDerUebersicht("adler", null, true);

    // Then
    assertThat(titel).containsExactly("Datenmigration", "Website-Relaunch");
  }

  @Test
  void findByFirma_thenReturnsOpenAndClosedVorgaengeOfThatFirmaOnly() {
    // Given
    bestandFuerDieSuche();
    final long adlerId =
        jdbc.queryForObject("SELECT id FROM firma WHERE name = 'Adler AG'", Long.class);

    // When
    final List<String> titel =
        repository.findByFirma(adlerId).stream().map(Vorgang::titel).toList();

    // Then
    assertThat(titel).containsExactly("Datenmigration", "Website-Relaunch");
  }

  @Test
  void findByFirma_givenAFirmaWithoutVorgaenge_thenEmptyList() {
    // Given
    bestandFuerDieSuche();
    final long leereFirmaId = firmaId("Cedern KG");

    // When
    final List<Vorgang> vorgaenge = repository.findByFirma(leereFirmaId);

    // Then
    assertThat(vorgaenge).isEmpty();
  }

  @Test
  void zaehleAlle_thenCountsClosedVorgaengeAsWell() {
    // Given
    bestandFuerDieSuche();

    // When
    final long anzahl = repository.zaehleAlle();

    // Then
    assertThat(anzahl).isEqualTo(3L);
  }

  @Test
  void zaehleAlle_givenAnEmptyTable_thenZero() {
    // When
    final long anzahl = repository.zaehleAlle();

    // Then
    assertThat(anzahl).isZero();
  }

  /**
   * Drei Vorgaenge bei zwei Firmen: ein offener und ein abgeschlossener bei „Adler AG", einer bei
   * „Baum GmbH" mit Musterzeichen im Titel. Die Zeitpunkte legen die Reihenfolge fest, in der die
   * Uebersicht sie erwartet: der abgeschlossene oben.
   */
  private void bestandFuerDieSuche() {
    final long adlerId = firmaId("Adler AG");
    final long baumId = firmaId("Baum GmbH");
    repository.save(
        offenAngelegtAm(12L, "Website-Relaunch", adlerId, Instant.parse("2026-09-01T00:00:00Z")));
    repository.save(
        new Vorgang(
            null,
            13L,
            "Prozent % und Unter_strich",
            baumId,
            null,
            false,
            Instant.parse("2026-09-02T00:00:00Z"),
            Instant.parse("2026-09-02T00:00:00Z")));
    repository.save(
        new Vorgang(
            null,
            14L,
            "Datenmigration",
            adlerId,
            null,
            true,
            Instant.parse("2026-09-03T00:00:00Z"),
            Instant.parse("2026-09-03T00:00:00Z")));
  }
}
