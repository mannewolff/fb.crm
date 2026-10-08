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
import org.mwolff.fbcrm.common.Anschrift;
import org.mwolff.fbcrm.common.Einheit;
import org.mwolff.fbcrm.rechnung.domain.Belegabsender;
import org.mwolff.fbcrm.rechnung.domain.Belegempfaenger;
import org.mwolff.fbcrm.rechnung.domain.Rechnung;
import org.mwolff.fbcrm.rechnung.domain.RechnungRepository;
import org.mwolff.fbcrm.rechnung.domain.Rechnungsposition;
import org.mwolff.fbcrm.rechnung.domain.Rechnungszustand;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Der Bestand der Rechnung gegen eine echte PostgreSQL-Instanz.
 *
 * <p>Gegenstand ist die Rechnung als <b>eine</b> Einheit mit ihren Positionen: Anlegen, Lesen in
 * der Reihenfolge der Plaetze (E24), das Aufruecken nach einer weggefallenen Position — nur hier
 * pruefbar, weil es an der aufgeschobenen Eindeutigkeit von {@code rechnung_position_reihenfolge}
 * haengt —, die beiden Kopien ueber ihre sechzehn Spalten und die Frage nach einer vergebenen
 * Nummer.
 *
 * <p>Jeder Zug setzt seine Transaktionsgrenze selbst ueber {@link TransactionTemplate}: Der Adapter
 * schreibt die Positionszeilen im Zug des Aufrufers fort und hat keine eigene Grenze — sie gehoert
 * dem Anwendungsfall.
 */
class RechnungPersistenceIT extends AbstractIntegrationTest {

  private static final LocalDate RECHNUNGSDATUM = LocalDate.of(2026, 9, 30);
  private static final Instant ANGELEGT = Instant.parse("2026-09-30T08:00:00Z");
  private static final Instant GESTELLT_AM = Instant.parse("2026-10-01T09:15:00Z");
  private static final String ZEITRAUM = "September 2026";
  private static final BigDecimal NEUNZEHN = new BigDecimal("19.00");

  private static final Belegempfaenger EMPFAENGER =
      new Belegempfaenger(
          "Adler AG", new Anschrift("Hauptstrasse 1", "28195", "Bremen", "Deutschland"), null);

  private static final Belegabsender ABSENDER =
      new Belegabsender(
          "Manfred Wolff",
          "Softwarearchitekt",
          new Anschrift("Am Deich 2", "28199", "Bremen", "Deutschland"),
          "post@example.org",
          "0421 123456",
          "75/123/45678",
          "DE123456789",
          "DE02 1203 0000 0000 2020 51",
          "https://example.org");

  private final RechnungRepository repository;
  private final TransactionTemplate transaktion;
  private final JdbcTemplate jdbc;

  private long angebotId;
  private long beratungId;
  private long konzeptionId;

  @Autowired
  RechnungPersistenceIT(
      final RechnungRepository repository,
      final TransactionTemplate transaktion,
      final JdbcTemplate jdbc) {
    this.repository = repository;
    this.transaktion = transaktion;
    this.jdbc = jdbc;
  }

  @BeforeEach
  void leereFachtabellenUndLegeEinAngebotMitZweiPositionenAn() {
    jdbc.execute(
        "TRUNCATE rechnung_position, rechnung, angebot_position, angebot, ansprechpartner, firma"
            + " RESTART IDENTITY CASCADE");
    jdbc.update("INSERT INTO firma (name) VALUES ('Adler AG')");
    final Long firmaId = jdbc.queryForObject("SELECT id FROM firma LIMIT 1", Long.class);
    jdbc.update(
        "INSERT INTO angebot (firma_id, status, angebot_datum)"
            + " VALUES (?, 'BESTELLT', DATE '2026-09-20')",
        firmaId);
    angebotId = jdbc.queryForObject("SELECT id FROM angebot LIMIT 1", Long.class).longValue();
    beratungId = angebotsposition(1, "Beratung", "10.00", "STUNDE", "120.00");
    konzeptionId = angebotsposition(2, "Konzeption", "5.00", "PERSONENTAG", "999.00");
  }

  private long angebotsposition(
      final int platz,
      final String bezeichnung,
      final String menge,
      final String einheit,
      final String einzelpreis) {
    jdbc.update(
        "INSERT INTO angebot_position"
            + " (angebot_id, position, bezeichnung, abrechnungsmodus, menge, einheit, einzelpreis)"
            + " VALUES (?, ?, ?, 'AUFWAND', CAST(? AS numeric), ?, CAST(? AS numeric))",
        Long.valueOf(angebotId),
        Integer.valueOf(platz),
        bezeichnung,
        menge,
        einheit,
        einzelpreis);
    return jdbc.queryForObject(
            "SELECT id FROM angebot_position WHERE angebot_id = ? AND position = ?",
            Long.class,
            Long.valueOf(angebotId),
            Integer.valueOf(platz))
        .longValue();
  }

  private Rechnungsposition beratung(final String menge) {
    return new Rechnungsposition(
        beratungId, "Beratung", new BigDecimal(menge), Einheit.STUNDE, new BigDecimal("120.00"));
  }

  private Rechnungsposition konzeption() {
    return new Rechnungsposition(
        konzeptionId,
        "Konzeption",
        new BigDecimal("2.50"),
        Einheit.PERSONENTAG,
        new BigDecimal("999.00"));
  }

  private Rechnung entwurf(final List<Rechnungsposition> positionen) {
    return new Rechnung(
        null,
        angebotId,
        Rechnungszustand.ENTWURF,
        RECHNUNGSDATUM,
        ZEITRAUM,
        positionen,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        ANGELEGT,
        ANGELEGT);
  }

  private Rechnung geschrieben(final Rechnung rechnung) {
    return transaktion.execute(status -> repository.save(rechnung));
  }

  private Optional<Rechnung> gelesen(final long id) {
    return transaktion.execute(status -> repository.findById(id));
  }

  private boolean vergeben(final String nummer) {
    return Boolean.TRUE.equals(
        transaktion.execute(status -> Boolean.valueOf(repository.existiertNummer(nummer))));
  }

  private List<Rechnung> zumAngebot() {
    return transaktion.execute(status -> repository.findByAngebot(angebotId));
  }

  private List<Rechnung> alle() {
    return transaktion.execute(status -> repository.findAlle());
  }

  @Test
  void save_givenADraftWithTwoPositions_thenReadsBackEveryFieldAndThePositionsInOrder() {
    // Given
    final Rechnung gespeichert = geschrieben(entwurf(List.of(konzeption(), beratung("3.00"))));

    // When
    final Optional<Rechnung> gelesen = gelesen(gespeichert.requireId());

    // Then — die Reihenfolge ist die der eingereichten Liste, nicht die der Angebotspositionen.
    assertThat(gelesen)
        .hasValueSatisfying(
            rechnung -> {
              assertThat(rechnung.angebotId()).isEqualTo(angebotId);
              assertThat(rechnung.zustand()).isEqualTo(Rechnungszustand.ENTWURF);
              assertThat(rechnung.rechnungDatum()).isEqualTo(RECHNUNGSDATUM);
              assertThat(rechnung.leistungszeitraum()).isEqualTo(ZEITRAUM);
              assertThat(rechnung.nummer()).isNull();
              assertThat(rechnung.empfaenger()).isNull();
              assertThat(rechnung.absender()).isNull();
              assertThat(rechnung.createdAt()).isEqualTo(ANGELEGT);
              assertThat(rechnung.positionen()).containsExactly(konzeption(), beratung("3.00"));
              assertThat(rechnung.netto()).isEqualByComparingTo("2857.50");
            });
  }

  @Test
  void save_thenStoresThePlacesFromOneUpwards() {
    // Given
    final Rechnung gespeichert = geschrieben(entwurf(List.of(konzeption(), beratung("3.00"))));

    // When
    final List<Short> plaetze =
        jdbc.queryForList(
            "SELECT position FROM rechnung_position WHERE rechnung_id = ? ORDER BY position",
            Short.class,
            Long.valueOf(gespeichert.requireId()));

    // Then
    assertThat(plaetze).containsExactly((short) 1, (short) 2);
  }

  @Test
  void save_givenAnOmittedPosition_thenTheRemainingOneMovesUp() {
    // Given — der Zwischenstand traegt zwangslaeufig zweimal den Platz 1; nur die aufgeschobene
    // Eindeutigkeit laesst ihn durch.
    final Rechnung gespeichert = geschrieben(entwurf(List.of(konzeption(), beratung("3.00"))));

    // When
    geschrieben(
        gespeichert.geaendert(RECHNUNGSDATUM, ZEITRAUM, List.of(beratung("3.00")), ANGELEGT));

    // Then
    assertThat(gelesen(gespeichert.requireId()))
        .hasValueSatisfying(
            rechnung -> assertThat(rechnung.positionen()).containsExactly(beratung("3.00")));
    assertThat(jdbc.queryForObject("SELECT count(*) FROM rechnung_position", Integer.class))
        .isEqualTo(1);
  }

  @Test
  void save_givenAnIssuedRechnung_thenReadsBackBothCopiesAndTheFrozenValues() {
    // Given
    final Rechnung entwurf = geschrieben(entwurf(List.of(beratung("3.00"))));

    // When
    final Rechnung gestellt =
        geschrieben(
            entwurf
                .gestellt("R26-0004", NEUNZEHN, 10, EMPFAENGER, ABSENDER, GESTELLT_AM)
                .mitDokument("rechnung/1/abc.pdf"));

    // Then
    assertThat(gelesen(gestellt.requireId()))
        .hasValueSatisfying(
            rechnung -> {
              assertThat(rechnung.zustand()).isEqualTo(Rechnungszustand.GESTELLT);
              assertThat(rechnung.nummer()).isEqualTo("R26-0004");
              assertThat(rechnung.steuersatz()).isEqualByComparingTo(NEUNZEHN);
              assertThat(rechnung.zahlungszielTage()).isEqualTo(10);
              assertThat(rechnung.gestelltAm()).isEqualTo(GESTELLT_AM);
              assertThat(rechnung.pdfSchluessel()).isEqualTo("rechnung/1/abc.pdf");
              assertThat(rechnung.empfaenger()).isEqualTo(EMPFAENGER);
              assertThat(rechnung.absender()).isEqualTo(ABSENDER);
            });
  }

  @Test
  void existiertNummer_givenATakenAndAFreeNumber_thenAnswersBothCorrectly() {
    // Given
    final Rechnung entwurf = geschrieben(entwurf(List.of(beratung("3.00"))));
    geschrieben(entwurf.gestellt("R26-0004", NEUNZEHN, 10, EMPFAENGER, ABSENDER, GESTELLT_AM));

    // When / Then
    assertThat(vergeben("R26-0004")).isTrue();
    assertThat(vergeben("R26-0005")).isFalse();
  }

  @Test
  void existiertNummer_givenTheSameNumberInAnotherCase_thenAnswersTrue() {
    // Given
    final Rechnung entwurf = geschrieben(entwurf(List.of(beratung("3.00"))));
    geschrieben(entwurf.gestellt("RE-1", NEUNZEHN, 10, EMPFAENGER, ABSENDER, GESTELLT_AM));

    // When / Then — #254, Kriterium 4: „RE-1" und „re-1" sind dieselbe Nummer.
    assertThat(vergeben("re-1")).isTrue();
  }

  @Test
  void findByAngebot_thenLoadsEveryRechnungOfTheAngebotWithItsPositions() {
    // Given
    geschrieben(entwurf(List.of(beratung("3.00"))));
    geschrieben(entwurf(List.of(konzeption())));

    // When / Then
    assertThat(zumAngebot())
        .hasSize(2)
        .allSatisfy(rechnung -> assertThat(rechnung.positionen()).hasSize(1));
  }

  @Test
  void findAlle_thenLoadsEveryRechnung() {
    // Given
    geschrieben(entwurf(List.of(beratung("3.00"))));

    // When / Then
    assertThat(alle()).hasSize(1);
  }

  @Test
  void delete_thenRemovesTheRechnungWithItsPositions() {
    // Given — ein Entwurf, aus dem nichts wird, verschwindet (#160, Kriterium 12).
    final Rechnung gespeichert = geschrieben(entwurf(List.of(beratung("3.00"))));

    // When
    transaktion.executeWithoutResult(status -> repository.delete(gespeichert.requireId()));

    // Then
    assertThat(gelesen(gespeichert.requireId())).isEmpty();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM rechnung_position", Integer.class))
        .isZero();
  }

  @Test
  void findById_givenAnUnknownId_thenEmpty() {
    // When / Then
    assertThat(gelesen(4711L)).isEmpty();
  }
}
