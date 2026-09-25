package org.mwolff.fbcrm.vorgang.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.mwolff.fbcrm.vorgang.domain.Eintrag;
import org.mwolff.fbcrm.vorgang.domain.Eintragsart;
import org.mwolff.fbcrm.vorgang.domain.Herkunft;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Der Eintrag-Adapter gegen eine echte PostgreSQL-Instanz.
 *
 * <p>Gegenstand ist die Historie eines Vorgangs als <b>eine</b> Folge beider Arten (E6) in der
 * Reihenfolge {@code geschehen_am DESC, id DESC} — juengstes Geschehen oben, bei gleichem Zeitpunkt
 * der zuletzt erfasste Eintrag oben (Kriterium 15).
 */
class JpaEintragRepositoryIT extends AbstractIntegrationTest {

  private static final Instant FRUEH = Instant.parse("2026-09-10T09:00:00Z");
  private static final Instant SPAET = Instant.parse("2026-09-12T09:00:00Z");
  private static final Instant ERFASST = Instant.parse("2026-09-13T08:00:00Z");
  private static final Instant GEAENDERT = Instant.parse("2026-09-18T12:00:00Z");

  private final JpaEintragRepository repository;
  private final JdbcTemplate jdbc;

  private long firmaId;

  @Autowired
  JpaEintragRepositoryIT(final JpaEintragRepository repository, final JdbcTemplate jdbc) {
    this.repository = repository;
    this.jdbc = jdbc;
  }

  @BeforeEach
  void leereFachtabellenUndLegeFirmaAn() {
    jdbc.execute(
        "TRUNCATE vorgang_eintrag, vorgang, ansprechpartner, firma RESTART IDENTITY CASCADE");
    jdbc.update("INSERT INTO firma (name) VALUES ('Adler AG')");
    firmaId = jdbc.queryForObject("SELECT id FROM firma", Long.class);
  }

  private long vorgangId(final long nummer) {
    jdbc.update(
        "INSERT INTO vorgang (nummer, titel, firma_id) VALUES (?, 'Website-Relaunch', ?)",
        Long.valueOf(nummer),
        Long.valueOf(firmaId));
    return jdbc.queryForObject(
        "SELECT id FROM vorgang WHERE nummer = ?", Long.class, Long.valueOf(nummer));
  }

  private static Eintrag kommentar(final long vorgangId, final String text, final Instant wann) {
    return Eintrag.kommentar(vorgangId, text, wann, Herkunft.VON_HAND, ERFASST);
  }

  private static Eintrag anhang(final long vorgangId, final Instant wann) {
    return Eintrag.anhang(
        vorgangId,
        "Das Angebot",
        wann,
        Herkunft.VON_HAND,
        "Angebot.pdf",
        4096L,
        "vorgang/" + vorgangId + "/abc",
        ERFASST);
  }

  @Test
  void save_thenAssignsAnIdFromTheDatabase() {
    // Given
    final long vorgangId = vorgangId(1L);

    // When
    final Eintrag gesichert = repository.save(kommentar(vorgangId, "Angerufen", FRUEH));

    // Then
    assertThat(gesichert.id()).isNotNull();
  }

  @Test
  void findById_afterSavingAKommentar_thenReturnsEveryStoredValue() {
    // Given
    final long vorgangId = vorgangId(1L);
    final Eintrag gesichert = repository.save(kommentar(vorgangId, "Angerufen", FRUEH));

    // When
    final Optional<Eintrag> gefunden = repository.findById(gesichert.requireId());

    // Then
    assertThat(gefunden).contains(gesichert);
  }

  @Test
  void findById_afterSavingAnAnhang_thenReturnsTheThreeFileValues() {
    // Given
    final long vorgangId = vorgangId(1L);
    final Eintrag gesichert = repository.save(anhang(vorgangId, FRUEH));

    // When
    final Optional<Eintrag> gefunden = repository.findById(gesichert.requireId());

    // Then
    assertThat(gefunden)
        .hasValueSatisfying(
            eintrag -> {
              assertThat(eintrag.art()).isEqualTo(Eintragsart.ANHANG);
              assertThat(eintrag.dateiName()).isEqualTo("Angebot.pdf");
              assertThat(eintrag.dateiGroesse()).isEqualTo(4096L);
              assertThat(eintrag.objektSchluessel()).isEqualTo("vorgang/" + vorgangId + "/abc");
              assertThat(eintrag.geaendertAm()).isNull();
            });
  }

  @Test
  void findById_givenAnUnknownId_thenEmpty() {
    // When
    final Optional<Eintrag> gefunden = repository.findById(4711L);

    // Then
    assertThat(gefunden).isEmpty();
  }

  @Test
  void save_givenAChangedKommentar_thenStoresTheNewValues() {
    // Given
    final long vorgangId = vorgangId(1L);
    final Eintrag gesichert = repository.save(kommentar(vorgangId, "Angerufen", FRUEH));

    // When
    repository.save(gesichert.geaendert("Zurueckgerufen", SPAET, GEAENDERT));

    // Then
    assertThat(repository.findById(gesichert.requireId()))
        .hasValueSatisfying(
            eintrag -> {
              assertThat(eintrag.text()).isEqualTo("Zurueckgerufen");
              assertThat(eintrag.geschehenAm()).isEqualTo(SPAET);
              assertThat(eintrag.geaendertAm()).isEqualTo(GEAENDERT);
            });
  }

  @Test
  void findByVorgang_thenSortsByTheTimeOfTheEventDescending() {
    // Given
    final long vorgangId = vorgangId(1L);
    repository.save(kommentar(vorgangId, "Zuerst geschehen", FRUEH));
    repository.save(anhang(vorgangId, SPAET));

    // When
    final List<Eintrag> historie = repository.findByVorgang(vorgangId);

    // Then
    assertThat(historie)
        .extracting(Eintrag::art)
        .containsExactly(Eintragsart.ANHANG, Eintragsart.KOMMENTAR);
  }

  @Test
  void findByVorgang_givenTwoEntriesAtTheSameTime_thenPutsTheLastRecordedOnTop() {
    // Given
    final long vorgangId = vorgangId(1L);
    final Eintrag frueher = repository.save(kommentar(vorgangId, "Zuerst erfasst", FRUEH));
    final Eintrag spaeter = repository.save(kommentar(vorgangId, "Danach erfasst", FRUEH));

    // When
    final List<Eintrag> historie = repository.findByVorgang(vorgangId);

    // Then
    assertThat(historie)
        .extracting(Eintrag::id)
        .containsExactly(spaeter.requireId(), frueher.requireId());
  }

  @Test
  void findByVorgang_thenLeavesOutTheEntriesOfAnotherVorgang() {
    // Given
    final long ersterVorgang = vorgangId(1L);
    final long zweiterVorgang = vorgangId(2L);
    repository.save(kommentar(ersterVorgang, "Zum ersten Vorgang", FRUEH));
    repository.save(kommentar(zweiterVorgang, "Zum zweiten Vorgang", FRUEH));

    // When
    final List<Eintrag> historie = repository.findByVorgang(ersterVorgang);

    // Then
    assertThat(historie).extracting(Eintrag::text).containsExactly("Zum ersten Vorgang");
  }

  @Test
  void findByVorgang_givenAVorgangWithoutEntries_thenEmptyList() {
    // Given
    final long vorgangId = vorgangId(1L);

    // When
    final List<Eintrag> historie = repository.findByVorgang(vorgangId);

    // Then
    assertThat(historie).isEmpty();
  }

  @Test
  void juengstesGeschehenJeVorgang_thenReportsTheLatestTimeOfEachVorgang() {
    // Given — Kriterium 2: der Tag je Zeile kommt vom juengsten Eintrag.
    final long ersterVorgang = vorgangId(1L);
    final long zweiterVorgang = vorgangId(2L);
    repository.save(kommentar(ersterVorgang, "Zuerst geschehen", FRUEH));
    repository.save(kommentar(ersterVorgang, "Danach geschehen", SPAET));
    repository.save(kommentar(zweiterVorgang, "Zum zweiten Vorgang", FRUEH));

    // When
    final Map<Long, Instant> juengste =
        repository.juengstesGeschehenJeVorgang(List.of(ersterVorgang, zweiterVorgang));

    // Then
    assertThat(juengste).containsOnly(entry(ersterVorgang, SPAET), entry(zweiterVorgang, FRUEH));
  }

  @Test
  void juengstesGeschehenJeVorgang_givenAVorgangWithoutEntries_thenLeavesItOut() {
    // Given — ohne Eintrag zaehlt der Vorgang mit seinem Anlagezeitpunkt; den kennt der Aufrufer.
    final long vorgangId = vorgangId(1L);

    // When
    final Map<Long, Instant> juengste = repository.juengstesGeschehenJeVorgang(List.of(vorgangId));

    // Then
    assertThat(juengste).isEmpty();
  }

  @Test
  void juengstesGeschehenJeVorgang_givenNoVorgaenge_thenEmptyMapWithoutFailing() {
    // Given — die leere Uebersicht fragt mit einer leeren Liste.

    // When
    final Map<Long, Instant> juengste = repository.juengstesGeschehenJeVorgang(List.of());

    // Then
    assertThat(juengste).isEmpty();
  }

  @Test
  void juengstesGeschehenJeVorgang_thenIgnoresVorgaengeThatWereNotAsked() {
    // Given
    final long ersterVorgang = vorgangId(1L);
    final long zweiterVorgang = vorgangId(2L);
    repository.save(kommentar(ersterVorgang, "Zum ersten Vorgang", FRUEH));
    repository.save(kommentar(zweiterVorgang, "Zum zweiten Vorgang", SPAET));

    // When
    final Map<Long, Instant> juengste =
        repository.juengstesGeschehenJeVorgang(List.of(ersterVorgang));

    // Then
    assertThat(juengste).containsOnlyKeys(ersterVorgang);
  }
}
