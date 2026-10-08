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
 * Prueft das Schema der nachgetragenen Rechnung nach {@code V22__rechnung_nachgetragen.sql} gegen
 * eine echte PostgreSQL-Instanz (Plan #259, E1).
 *
 * <p>Gegenstand sind die Zusagen, die allein die Datenbank haelt: die drei CHECKs auf Zustand und
 * Betraege, der Fremdschluessel auf die Firma und die Eindeutigkeit der Nummer ohne Ansehen der
 * Schreibweise. Gearbeitet wird mit {@link JdbcTemplate} und direkten Anweisungen — ein Aggregat
 * gibt es zu dieser Tabelle noch nicht.
 */
class NachgetrageneRechnungSchemaIT extends AbstractIntegrationTest {

  private static final String INSERT =
      "INSERT INTO rechnung_nachgetragen"
          + " (firma_id, nummer, rechnung_datum, netto, brutto, zustand)"
          + " VALUES (?, ?, DATE '2026-03-15', CAST(? AS numeric), CAST(? AS numeric), ?)";

  private static final String GESTELLT = "GESTELLT";

  private final JdbcTemplate jdbc;

  private long firmaId;

  @Autowired
  NachgetrageneRechnungSchemaIT(final JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @BeforeEach
  void leereFachtabellenUndLegeEineFirmaAn() {
    jdbc.execute("TRUNCATE rechnung_nachgetragen, ansprechpartner, firma RESTART IDENTITY CASCADE");
    jdbc.update("INSERT INTO firma (name) VALUES ('Adler AG')");
    firmaId = jdbc.queryForObject("SELECT id FROM firma LIMIT 1", Long.class).longValue();
  }

  private int nachgetragen(
      final String nummer, final String netto, final String brutto, final String zustand) {
    return jdbc.update(INSERT, Long.valueOf(firmaId), nummer, netto, brutto, zustand);
  }

  @Test
  void insert_givenEveryMandatoryField_thenAccepted() {
    // When — die gueltige Zeile geht durch; sonst belegten die Abweisungen nur einen Tippfehler.
    final int betroffen = nachgetragen("RE-1", "1000.00", "1190.00", GESTELLT);

    // Then
    assertThat(betroffen).isEqualTo(1);
  }

  @Test
  void zustand_givenBezahltAndAbgeschrieben_thenAccepted() {
    // When — E6: beide Ausgaenge aus #253 gelten auch hier.
    final int bezahlt = nachgetragen("RE-1", "100.00", "119.00", "BEZAHLT");
    final int abgeschrieben = nachgetragen("RE-2", "100.00", "119.00", "ABGESCHRIEBEN");

    // Then
    assertThat(bezahlt).isEqualTo(1);
    assertThat(abgeschrieben).isEqualTo(1);
  }

  @Test
  void zustand_givenEntwurf_thenRejectedByTheDatabase() {
    // When / Then — E6: einen Entwurf gibt es bei der nachgetragenen Rechnung nicht.
    assertThatThrownBy(() -> nachgetragen("RE-1", "100.00", "119.00", "ENTWURF"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("rechnung_nachgetragen_zustand");
  }

  @Test
  void netto_givenANegativeAmount_thenRejectedByTheDatabase() {
    // When / Then
    assertThatThrownBy(() -> nachgetragen("RE-1", "-0.01", "0.00", GESTELLT))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("rechnung_nachgetragen_netto");
  }

  @Test
  void brutto_givenLessThanNetto_thenRejectedByTheDatabase() {
    // When / Then
    assertThatThrownBy(() -> nachgetragen("RE-1", "100.00", "99.99", GESTELLT))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("rechnung_nachgetragen_brutto");
  }

  @Test
  void brutto_givenEqualToNettoAndBothZero_thenAccepted() {
    // When — #254, Kriterium 3: Brutto gleich Netto ist zulaessig, 0,00 die Untergrenze.
    final int betroffen = nachgetragen("RE-1", "0.00", "0.00", GESTELLT);

    // Then
    assertThat(betroffen).isEqualTo(1);
  }

  @Test
  void firma_givenAnUnknownFirma_thenRejectedByTheDatabase() {
    // When / Then
    assertThatThrownBy(
            () -> jdbc.update(INSERT, Long.valueOf(4711L), "RE-1", "100.00", "119.00", GESTELLT))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("rechnung_nachgetragen_firma_id_fkey");
  }

  @Test
  void nummer_givenTheSameNumberTwice_thenRejectedByTheDatabase() {
    // Given
    nachgetragen("RE-1", "100.00", "119.00", GESTELLT);

    // When / Then
    assertThatThrownBy(() -> nachgetragen("RE-1", "200.00", "238.00", GESTELLT))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("rechnung_nachgetragen_nummer_lower_key");
  }

  @Test
  void nummer_givenTheSameNumberInAnotherCase_thenRejectedByTheDatabase() {
    // Given
    nachgetragen("RE-1", "100.00", "119.00", GESTELLT);

    // When / Then — #254, Kriterium 4: „RE-1" und „re-1" sind dieselbe Nummer.
    assertThatThrownBy(() -> nachgetragen("re-1", "200.00", "238.00", GESTELLT))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("rechnung_nachgetragen_nummer_lower_key");
  }

  @Test
  void migration_thenNoFileNameColumnExists() {
    // When — E26: der Dateiname des Originals wird nicht gespeichert.
    final Integer spalten =
        jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.columns WHERE table_schema = 'public'"
                + " AND table_name = 'rechnung_nachgetragen' AND column_name = 'datei_name'",
            Integer.class);

    // Then
    assertThat(spalten).isZero();
  }
}
