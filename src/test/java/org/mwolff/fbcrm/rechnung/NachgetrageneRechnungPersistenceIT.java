package org.mwolff.fbcrm.rechnung;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.mwolff.fbcrm.rechnung.domain.NachgetrageneRechnung;
import org.mwolff.fbcrm.rechnung.domain.NachgetrageneRechnungRepository;
import org.mwolff.fbcrm.rechnung.domain.Rechnungszustand;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Der Bestand der nachgetragenen Rechnung gegen eine echte PostgreSQL-Instanz (Plan #259).
 *
 * <p>Gegenstand sind vor allem die beiden Nummernfragen: Sie stehen als {@code @Query} mit {@code
 * lower(…)}, und eine Abfrage ist gegen ein Mock immer gruen. Erst hier zeigt sich, dass „RE-9" und
 * „re-9" dieselbe Nummer sind (#254, Kriterium 4) und dass die eigene Zeile beim Aendern nicht als
 * fremde zaehlt. Dazu Anlegen, Lesen, Fortschreiben und Loeschen.
 *
 * <p>Jeder Zug setzt seine Transaktionsgrenze selbst ueber {@link TransactionTemplate}, wie in
 * {@code RechnungPersistenceIT}: Die Grenze gehoert dem Anwendungsfall, nicht dem Adapter.
 */
class NachgetrageneRechnungPersistenceIT extends AbstractIntegrationTest {

  private static final LocalDate RECHNUNGSDATUM = LocalDate.of(2026, 2, 15);
  private static final Instant ANGELEGT = Instant.parse("2026-10-06T08:00:00Z");
  private static final Instant GEAENDERT = Instant.parse("2026-10-07T09:15:00Z");

  private final NachgetrageneRechnungRepository repository;
  private final TransactionTemplate transaktion;
  private final JdbcTemplate jdbc;

  private long firmaId;

  @Autowired
  NachgetrageneRechnungPersistenceIT(
      final NachgetrageneRechnungRepository repository,
      final TransactionTemplate transaktion,
      final JdbcTemplate jdbc) {
    this.repository = repository;
    this.transaktion = transaktion;
    this.jdbc = jdbc;
  }

  @BeforeEach
  void leereFachtabellenUndLegeEineFirmaAn() {
    jdbc.execute("TRUNCATE rechnung_nachgetragen, ansprechpartner, firma RESTART IDENTITY CASCADE");
    jdbc.update("INSERT INTO firma (name) VALUES ('Adler AG')");
    firmaId = jdbc.queryForObject("SELECT id FROM firma LIMIT 1", Long.class).longValue();
  }

  private NachgetrageneRechnung neu(final String nummer) {
    return new NachgetrageneRechnung(
        null,
        firmaId,
        nummer,
        RECHNUNGSDATUM,
        new BigDecimal("1000.00"),
        new BigDecimal("1190.00"),
        Rechnungszustand.GESTELLT,
        null,
        ANGELEGT,
        ANGELEGT);
  }

  private NachgetrageneRechnung geschrieben(final NachgetrageneRechnung rechnung) {
    return transaktion.execute(status -> repository.save(rechnung));
  }

  private Optional<NachgetrageneRechnung> gelesen(final long id) {
    return transaktion.execute(status -> repository.findById(id));
  }

  private List<NachgetrageneRechnung> alle() {
    return transaktion.execute(status -> repository.findAlle());
  }

  private boolean vergeben(final String nummer) {
    return Boolean.TRUE.equals(
        transaktion.execute(status -> Boolean.valueOf(repository.existiertNummer(nummer))));
  }

  private boolean vergebenAusser(final String nummer, final long id) {
    return Boolean.TRUE.equals(
        transaktion.execute(
            status -> Boolean.valueOf(repository.existiertNummerAusser(nummer, id))));
  }

  @Test
  void save_thenReadsBackEveryField() {
    // Given
    final NachgetrageneRechnung gespeichert = geschrieben(neu("RE-9"));

    // When / Then
    assertThat(gelesen(gespeichert.requireId()))
        .hasValueSatisfying(
            rechnung -> {
              assertThat(rechnung.firmaId()).isEqualTo(firmaId);
              assertThat(rechnung.nummer()).isEqualTo("RE-9");
              assertThat(rechnung.rechnungDatum()).isEqualTo(RECHNUNGSDATUM);
              assertThat(rechnung.netto()).isEqualByComparingTo("1000.00");
              assertThat(rechnung.brutto()).isEqualByComparingTo("1190.00");
              assertThat(rechnung.zustand()).isEqualTo(Rechnungszustand.GESTELLT);
              assertThat(rechnung.pdfSchluessel()).isNull();
              assertThat(rechnung.createdAt()).isEqualTo(ANGELEGT);
              assertThat(rechnung.updatedAt()).isEqualTo(ANGELEGT);
            });
  }

  @Test
  void save_givenAChangedRechnung_thenWritesItForward() {
    // Given
    final NachgetrageneRechnung gespeichert = geschrieben(neu("RE-9"));

    // When
    geschrieben(
        gespeichert
            .mitZustand(Rechnungszustand.BEZAHLT, GEAENDERT)
            .mitDokument("nachgetragen/1/original.pdf", GEAENDERT));

    // Then — dieselbe Zeile, kein zweiter Datensatz.
    assertThat(gelesen(gespeichert.requireId()))
        .hasValueSatisfying(
            rechnung -> {
              assertThat(rechnung.zustand()).isEqualTo(Rechnungszustand.BEZAHLT);
              assertThat(rechnung.pdfSchluessel()).isEqualTo("nachgetragen/1/original.pdf");
              assertThat(rechnung.updatedAt()).isEqualTo(GEAENDERT);
            });
    assertThat(alle()).hasSize(1);
  }

  @Test
  void existiertNummer_givenRe9Stored_thenAnswersTrueForAnyCase() {
    // Given
    geschrieben(neu("RE-9"));

    // When / Then — #254, Kriterium 4: „RE-9" und „re-9" sind dieselbe Nummer.
    assertThat(vergeben("re-9")).isTrue();
    assertThat(vergeben("RE-9")).isTrue();
    assertThat(vergeben("RE-10")).isFalse();
  }

  @Test
  void existiertNummerAusser_givenItsOwnId_thenAnswersFalse() {
    // Given
    final NachgetrageneRechnung eigene = geschrieben(neu("RE-9"));

    // When / Then — beim Aendern zaehlt die eigene Nummer nicht als vergeben.
    assertThat(vergebenAusser("re-9", eigene.requireId())).isFalse();
  }

  @Test
  void existiertNummerAusser_givenAnotherRechnungWithTheNumber_thenAnswersTrue() {
    // Given
    geschrieben(neu("RE-9"));
    final NachgetrageneRechnung andere = geschrieben(neu("RE-10"));

    // When / Then
    assertThat(vergebenAusser("re-9", andere.requireId())).isTrue();
    assertThat(vergebenAusser("re-11", andere.requireId())).isFalse();
  }

  @Test
  void delete_thenTheRechnungIsGone() {
    // Given
    final NachgetrageneRechnung gespeichert = geschrieben(neu("RE-9"));

    // When
    transaktion.executeWithoutResult(status -> repository.delete(gespeichert.requireId()));

    // Then
    assertThat(gelesen(gespeichert.requireId())).isEmpty();
    assertThat(alle()).isEmpty();
  }
}
