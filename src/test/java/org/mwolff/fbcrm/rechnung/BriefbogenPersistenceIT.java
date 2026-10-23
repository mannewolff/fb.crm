package org.mwolff.fbcrm.rechnung;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.mwolff.fbcrm.Schutzspalten;
import org.mwolff.fbcrm.rechnung.domain.Briefbogen;
import org.mwolff.fbcrm.rechnung.domain.BriefbogenRepository;
import org.mwolff.fbcrm.rechnung.domain.Briefpapierblatt;
import org.mwolff.fbcrm.rechnung.domain.Randmasse;
import org.mwolff.fbcrm.rechnung.domain.Zeilenstand;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

/**
 * Der Port auf den Briefbogen gegen eine echte PostgreSQL-Instanz.
 *
 * <p>Auf der frischen Datenbank liest der Port ohne Sonderzweig die Vorgaben — die Migration hat
 * die eine Zeile schon angelegt. {@code speichere} schreibt diese Zeile fort und legt keine zweite
 * an. Und das Entfernen des Briefpapiers leert allein die vier Spalten des Blattes; Schalter und
 * Raender bleiben, wie sie eingestellt waren (fachliche Quelle #361, Kriterium 16; Plan #363, E15).
 *
 * <p>Dazu der Stand der Zeile (Plan #415, E1, E11): Jedes Schreiben erhoeht ihn und setzt den
 * Urheber, und ein Schreiben auf einem alten Stand scheitert an der Sperre der Zeile.
 *
 * <p>Der Ausgangszustand wird vor jeder Methode wiederhergestellt, wie ihn die Migration
 * hinterlaesst; die Datenbank der Suite ist geteilt.
 */
class BriefbogenPersistenceIT extends AbstractIntegrationTest {

  private static final Instant GEAENDERT = Instant.parse("2026-10-19T12:00:00Z");

  private static final Randmasse EINGESTELLT = new Randmasse(300, 150, 1_000, 250, 180, 220);

  private static final Briefpapierblatt BLATT =
      new Briefpapierblatt("briefpapier/abc.pdf", "kopf.pdf", 98_765L, 2);

  private final BriefbogenRepository repository;
  private final JdbcTemplate jdbc;

  @Autowired
  BriefbogenPersistenceIT(final BriefbogenRepository repository, final JdbcTemplate jdbc) {
    this.repository = repository;
    this.jdbc = jdbc;
  }

  private long konto;

  @BeforeEach
  void stelleDenStandDerMigrationHer() {
    jdbc.execute("DELETE FROM briefbogen");
    jdbc.execute("INSERT INTO briefbogen DEFAULT VALUES");
    leereKonten(jdbc);
    konto = Schutzspalten.konto(jdbc);
  }

  @Test
  void lies_givenTheFreshDatabase_thenAnswersWithTheDefaults() {
    // When / Then — Kriterium 15 und E15.
    assertThat(repository.lies())
        .isEqualTo(new Briefbogen(null, true, true, new Randmasse(250, 200, 985, 200, 200, 200)));
  }

  @Test
  void speichere_givenSwitchesAndMargins_thenLiesAnswersWithThem() {
    // Given
    final Briefbogen neu = new Briefbogen(null, false, false, EINGESTELLT);

    // When
    repository.speichere(neu, GEAENDERT, 0L, konto);

    // Then
    assertThat(repository.lies()).isEqualTo(neu);
  }

  @Test
  void speichere_givenABlatt_thenLiesAnswersWithIt() {
    // Given
    final Briefbogen mitBlatt = new Briefbogen(BLATT, true, false, EINGESTELLT);

    // When
    repository.speichere(mitBlatt, GEAENDERT, 0L, konto);

    // Then
    assertThat(repository.lies()).isEqualTo(mitBlatt);
  }

  @Test
  void speichere_givenTheBlattRemoved_thenOnlyItsFourColumnsBecomeNull() {
    // Given
    repository.speichere(new Briefbogen(BLATT, false, true, EINGESTELLT), GEAENDERT, 0L, konto);

    // When — Kriterium 16: Entfernen laesst Schalter und Raender stehen.
    final Briefbogen bisher = repository.lies();
    repository.speichere(
        new Briefbogen(null, bisher.absenderzeile(), bisher.steuerBankZeile(), bisher.raender()),
        GEAENDERT,
        1L,
        konto);

    // Then
    final Map<String, Object> zeile = jdbc.queryForMap("SELECT * FROM briefbogen");
    assertThat(zeile)
        .containsEntry("pdf_schluessel", null)
        .containsEntry("dateiname", null)
        .containsEntry("groesse", null)
        .containsEntry("seiten", null)
        .containsEntry("absenderzeile", false)
        .containsEntry("steuer_bank_zeile", true)
        .containsEntry("rand_links", 300)
        .containsEntry("rand_rechts", 150)
        .containsEntry("inhalt_oben", 1_000)
        .containsEntry("rand_unten", 250)
        .containsEntry("folge_oben", 180)
        .containsEntry("folge_unten", 220);
  }

  @Test
  void speichere_thenUpdatesTheOneRowAndAddsNoSecond() {
    // When
    repository.speichere(new Briefbogen(null, true, true, EINGESTELLT), GEAENDERT, 0L, konto);

    // Then
    final Integer zeilen = jdbc.queryForObject("SELECT count(*) FROM briefbogen", Integer.class);
    final OffsetDateTime zuletzt =
        jdbc.queryForObject("SELECT updated_at FROM briefbogen", OffsetDateTime.class);
    assertThat(zeilen).isEqualTo(1);
    assertThat(zuletzt).isNotNull();
    assertThat(zuletzt.toInstant()).isEqualTo(GEAENDERT);
  }

  @Test
  void stand_givenTheFreshDatabase_thenVersionZeroWithoutUrheber() {
    // When / Then — die Vorbelegung der Spalte (V33).
    assertThat(repository.stand().version()).isZero();
    assertThat(repository.stand().geaendertVon()).isNull();
  }

  @Test
  void speichere_thenRaisesTheVersionAndSetsTheUrheber() {
    // When
    final Zeilenstand neu =
        repository.speichere(new Briefbogen(BLATT, true, true, EINGESTELLT), GEAENDERT, 0L, konto);

    // Then — die Antwort und die Zeile tragen denselben, erhoehten Stand.
    assertThat(neu).isEqualTo(new Zeilenstand(1L, konto, GEAENDERT));
    assertThat(repository.stand()).isEqualTo(neu);
  }

  @Test
  void speichere_givenAnOutdatedStand_thenTheLockRefusesAndTheRowStays() {
    // Given — E1, E11: ein anderer hat inzwischen das Briefpapier hinterlegt.
    repository.speichere(new Briefbogen(BLATT, true, true, EINGESTELLT), GEAENDERT, 0L, konto);

    // When / Then — Raender auf dem Stand vor dem Hochladen: die Sperre greift auch ohne den
    // Vergleich im Anwendungsfall.
    assertThatThrownBy(
            () ->
                repository.speichere(
                    new Briefbogen(null, false, false, EINGESTELLT), GEAENDERT, 0L, konto))
        .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    assertThat(repository.lies()).isEqualTo(new Briefbogen(BLATT, true, true, EINGESTELLT));
  }
}
