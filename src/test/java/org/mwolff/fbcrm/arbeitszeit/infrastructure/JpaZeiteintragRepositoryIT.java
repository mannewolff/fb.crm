package org.mwolff.fbcrm.arbeitszeit.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.mwolff.fbcrm.arbeitszeit.domain.Zeiteintrag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Der Arbeitszeit-Adapter gegen eine echte PostgreSQL-Instanz.
 *
 * <p>Gegenstand ist, was nur mit Datenbank geprueft werden kann: der Zeitraum mit beiden
 * eingeschlossenen Grenzen und die beiden Summen ueber echte Zeilen — einmal ueber alle Monate,
 * einmal ueber einen Monat beziehungsweise Zeitraum — je mit und ohne Positionsmenge, dazu die
 * Monate mit Eintrag.
 *
 * <p>Seit Plan #426 gehoert jeder Eintrag einer Person (E1). Gelesen wird darum zusaetzlich je
 * Konto ({@code findImZeitraumFuerKonto}, E8), und die Summen zaehlen weiter ueber alle Personen
 * (E15). Die Eintraege gehoeren Anna, wo ein Fall keine Person nennt; Ben ist die zweite Person.
 *
 * <p>Seit Plan #415 (E17) wird mit Kennzeichen geloescht. Je Filterstelle steht ein Fall, dass ein
 * gekennzeichneter Eintrag nicht mitzaehlt — die Monatsliste aller und einer Person (zugleich die
 * Ueberschneidungspruefung je Person), die Summen fuer Kontingent, Rechnungsvorschlag und {@code
 * Zeitbindung} und die Auswertungen fuer Startseite und Jahresabschluss. Allein {@code findById}
 * liefert ihn weiter, und mit seiner Position entfaellt er hart.
 */
class JpaZeiteintragRepositoryIT extends AbstractIntegrationTest {

  private static final Instant ANGELEGT = Instant.parse("2026-11-12T08:00:00Z");
  private static final Instant GELOESCHT_UM = Instant.parse("2026-11-14T10:00:00Z");

  private final JpaZeiteintragRepository repository;
  private final JdbcTemplate jdbc;

  private long konzeption;
  private long beratung;
  private long anna;
  private long ben;

  @Autowired
  JpaZeiteintragRepositoryIT(final JpaZeiteintragRepository repository, final JdbcTemplate jdbc) {
    this.repository = repository;
    this.jdbc = jdbc;
  }

  @BeforeEach
  void leereFachtabellenUndLegeZweiPositionenAn() {
    jdbc.execute(
        "TRUNCATE arbeitszeit, rechnung_position, rechnung, angebot_position, angebot,"
            + " ansprechpartner, firma RESTART IDENTITY CASCADE");
    jdbc.update("INSERT INTO firma (name) VALUES ('IT Bildungshaus')");
    final long firmaId =
        jdbc.queryForObject("SELECT id FROM firma LIMIT 1", Long.class).longValue();
    jdbc.update(
        "INSERT INTO angebot (firma_id, status, angebot_datum)"
            + " VALUES (?, 'BESTELLT', DATE '2026-10-01')",
        Long.valueOf(firmaId));
    final long angebotId =
        jdbc.queryForObject("SELECT id FROM angebot LIMIT 1", Long.class).longValue();
    konzeption = position(angebotId, 1, "Konzeption");
    beratung = position(angebotId, 2, "Beratung");
    leereKonten(jdbc);
    anna = konto("anna@firma.de", "Anna");
    ben = konto("ben@firma.de", "Ben");
  }

  private long konto(final String email, final String name) {
    return jdbc.queryForObject(
            "INSERT INTO account (email, display_name, role) VALUES (?, ?, 'EMPLOYEE') RETURNING id",
            Long.class,
            email,
            name)
        .longValue();
  }

  private long position(final long angebotId, final int platz, final String bezeichnung) {
    jdbc.update(
        "INSERT INTO angebot_position"
            + " (angebot_id, position, bezeichnung, abrechnungsmodus, menge, einheit, einzelpreis,"
            + " leistungsjahr)"
            + " VALUES (?, ?, ?, 'AUFWAND', 20, 'STUNDE', 120.00, 2026)",
        Long.valueOf(angebotId),
        Integer.valueOf(platz),
        bezeichnung);
    return jdbc.queryForObject(
            "SELECT id FROM angebot_position WHERE bezeichnung = ?", Long.class, bezeichnung)
        .longValue();
  }

  private Zeiteintrag gesichert(
      final long positionId, final String tag, final String von, final String bis) {
    return gesichert(anna, positionId, tag, von, bis);
  }

  private Zeiteintrag gesichert(
      final long accountId,
      final long positionId,
      final String tag,
      final String von,
      final String bis) {
    return repository.save(
        new Zeiteintrag(
            null,
            accountId,
            positionId,
            LocalDate.parse(tag),
            LocalTime.parse(von),
            LocalTime.parse(bis),
            ANGELEGT,
            ANGELEGT));
  }

  @Test
  void save_thenAssignsAnIdFromTheDatabase() {
    // When
    final Zeiteintrag angelegt = gesichert(konzeption, "2026-11-12", "09:00", "10:45");

    // Then
    assertThat(angelegt.id()).isNotNull();
  }

  @Test
  void findById_afterSave_thenReturnsEveryStoredValue() {
    // Given
    final Zeiteintrag angelegt = gesichert(konzeption, "2026-11-12", "09:00", "10:45");

    // When
    final Optional<Zeiteintrag> gefunden = repository.findById(angelegt.requireId());

    // Then
    assertThat(gefunden).contains(angelegt);
  }

  /*
   * Ein Eintrag, den Ben geloescht hat (E17) — gespeichert ueber denselben Weg wie der
   * Anwendungsfall: Kennzeichen setzen und schreiben.
   */
  private Zeiteintrag geloescht(
      final long positionId, final String tag, final String von, final String bis) {
    return repository.save(gesichert(positionId, tag, von, bis).geloescht(ben, GELOESCHT_UM));
  }

  @Test
  void findById_givenADeletedEntry_thenStillReturnsItWithTheMark() {
    // Given — E16: der Anwendungsfall braucht Urheber und Zeitpunkt der Loeschung.
    final Zeiteintrag gekennzeichnet = geloescht(konzeption, "2026-11-12", "09:00", "10:45");

    // When
    final Optional<Zeiteintrag> gefunden = repository.findById(gekennzeichnet.requireId());

    // Then
    assertThat(gefunden)
        .get()
        .extracting(Zeiteintrag::geloeschtAm, Zeiteintrag::geloeschtVon)
        .containsExactly(GELOESCHT_UM, Long.valueOf(ben));
  }

  @Test
  void findImZeitraum_givenADeletedEntry_thenLeavesItOut() {
    // Given — Filterstelle: Monatsliste in der Sicht „Alle".
    final Zeiteintrag offener = gesichert(konzeption, "2026-11-12", "09:00", "10:00");
    geloescht(konzeption, "2026-11-12", "10:00", "11:00");

    // When / Then
    assertThat(repository.findImZeitraum(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)))
        .containsExactly(offener);
  }

  @Test
  void findImZeitraumFuerKonto_givenADeletedEntry_thenLeavesItOut() {
    // Given — Filterstelle: Monatsliste einer Person und Ueberschneidungspruefung je Person (#410).
    geloescht(konzeption, "2026-11-12", "09:00", "10:00");

    // When / Then
    assertThat(
            repository.findImZeitraumFuerKonto(
                LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 12), anna))
        .isEmpty();
  }

  @Test
  void angefallenJePosition_givenADeletedEntry_thenItDoesNotCount() {
    // Given — Filterstelle: Kontingent, Rechnungsvorschlag und Zeitbindung.
    gesichert(konzeption, "2026-11-12", "09:00", "10:00");
    geloescht(konzeption, "2026-11-13", "09:00", "17:00");
    geloescht(beratung, "2026-11-13", "09:00", "17:00");

    // When
    final Map<Long, BigDecimal> angefallen =
        repository.angefallenJePosition(Set.of(konzeption, beratung));

    // Then — die Beratung traegt nur geloeschte Zeit und steht mit 0,00 darin.
    assertThat(angefallen.get(konzeption)).isEqualByComparingTo("1.00");
    assertThat(angefallen.get(beratung)).isEqualByComparingTo("0.00");
  }

  @Test
  void stundenJePositionImMonat_givenADeletedEntry_thenItDoesNotCount() {
    // Given — Filterstelle: Vorschlag des Rechnungsentwurfs.
    gesichert(konzeption, "2026-11-12", "09:00", "10:00");
    geloescht(konzeption, "2026-11-13", "09:00", "17:00");

    // When / Then
    assertThat(
            repository
                .stundenJePositionImMonat(Set.of(konzeption), YearMonth.of(2026, 11))
                .get(konzeption))
        .isEqualByComparingTo("1.00");
  }

  @Test
  void alleAngefallenJePosition_givenADeletedEntry_thenItDoesNotCount() {
    // Given — Filterstelle: Startseite ueber Arbeitszeitauskunft.
    gesichert(konzeption, "2026-11-12", "09:00", "10:00");
    geloescht(konzeption, "2026-11-13", "09:00", "17:00");
    geloescht(beratung, "2026-11-13", "09:00", "17:00");

    // When / Then — die Beratung traegt nur geloeschte Zeit und fehlt.
    assertThat(repository.alleAngefallenJePosition())
        .containsOnlyKeys(Long.valueOf(konzeption))
        .hasEntrySatisfying(
            Long.valueOf(konzeption), stunden -> assertThat(stunden).isEqualByComparingTo("1.00"));
  }

  @Test
  void alleStundenJePositionImZeitraum_givenADeletedEntry_thenItDoesNotCount() {
    // Given — Filterstelle: Startseite und Jahresabschluss ueber Arbeitszeitauskunft.
    gesichert(konzeption, "2026-11-12", "09:00", "10:00");
    geloescht(konzeption, "2026-11-13", "09:00", "17:00");
    geloescht(beratung, "2026-11-13", "09:00", "17:00");

    // When
    final Map<Long, BigDecimal> jahr =
        repository.alleStundenJePositionImZeitraum(
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

    // Then
    assertThat(jahr).containsOnlyKeys(Long.valueOf(konzeption));
    assertThat(jahr.get(konzeption)).isEqualByComparingTo("1.00");
  }

  @Test
  void monateMitEintragImZeitraum_givenOnlyADeletedEntryInAMonth_thenThatMonthIsMissing() {
    // Given — Filterstelle: Jahresabschluss ueber Arbeitszeitauskunft.
    gesichert(konzeption, "2026-11-12", "09:00", "10:00");
    geloescht(konzeption, "2026-12-03", "09:00", "17:00");

    // When / Then
    assertThat(
            repository.monateMitEintragImZeitraum(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)))
        .containsExactly(YearMonth.of(2026, 11));
  }

  @Test
  void loescheGekennzeichnete_thenRemovesOnlyTheDeletedEntriesOfThePosition() {
    // Given — E17: ein geloeschter und ein offener Eintrag auf der Konzeption, ein geloeschter auf
    // der Beratung.
    final Zeiteintrag offener = gesichert(konzeption, "2026-11-12", "09:00", "10:00");
    final Zeiteintrag weg = geloescht(konzeption, "2026-11-13", "09:00", "17:00");
    final Zeiteintrag bleibt = geloescht(beratung, "2026-11-13", "09:00", "17:00");

    // When
    repository.loescheGekennzeichnete(Set.of(konzeption));

    // Then — nur der geloeschte Eintrag der Konzeption ist fort.
    assertThat(repository.findById(weg.requireId())).isEmpty();
    assertThat(repository.findById(offener.requireId())).isPresent();
    assertThat(repository.findById(bleibt.requireId())).isPresent();
  }

  @Test
  void findImZeitraum_thenIncludesBothBoundsAndNothingBeyond() {
    // Given
    gesichert(konzeption, "2026-10-31", "09:00", "10:00");
    gesichert(konzeption, "2026-11-01", "09:00", "10:00");
    gesichert(beratung, "2026-11-30", "09:00", "10:00");
    gesichert(konzeption, "2026-12-01", "09:00", "10:00");

    // When
    final List<Zeiteintrag> gefunden =
        repository.findImZeitraum(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));

    // Then
    assertThat(gefunden)
        .extracting(Zeiteintrag::tag)
        .containsExactlyInAnyOrder(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));
  }

  @Test
  void findImZeitraum_givenTheSameDayAsBothBounds_thenReturnsThatDayOnly() {
    // Given — so fragt die Ueberschneidungspruefung (A8).
    gesichert(konzeption, "2026-11-12", "09:00", "10:00");
    gesichert(beratung, "2026-11-13", "09:00", "10:00");

    // When
    final List<Zeiteintrag> gefunden =
        repository.findImZeitraum(LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 12));

    // Then
    assertThat(gefunden).extracting(Zeiteintrag::tag).containsExactly(LocalDate.of(2026, 11, 12));
  }

  @Test
  void findById_givenAnEntryOfBen_thenItBelongsToBen() {
    // Given — die Person geht durch die Zeile und kommt unveraendert zurueck (E1).
    final Zeiteintrag angelegt = gesichert(ben, konzeption, "2026-11-03", "10:00", "11:00");

    // When
    final Optional<Zeiteintrag> gefunden = repository.findById(angelegt.requireId());

    // Then
    assertThat(gefunden).map(Zeiteintrag::accountId).contains(ben);
  }

  @Test
  void findImZeitraumFuerKonto_thenReturnsOnlyTheRowsOfThatAccountIncludingBothBounds() {
    // Given — Anna und Ben am selben Tag zur selben Zeit; dazu Bens Zeilen auf beiden Grenzen
    // und je eine davor und danach.
    gesichert(anna, konzeption, "2026-11-03", "09:00", "12:00");
    gesichert(ben, konzeption, "2026-11-03", "10:00", "11:00");
    gesichert(ben, beratung, "2026-11-01", "09:00", "10:00");
    gesichert(ben, beratung, "2026-11-30", "09:00", "10:00");
    gesichert(ben, beratung, "2026-10-31", "09:00", "10:00");
    gesichert(ben, beratung, "2026-12-01", "09:00", "10:00");

    // When
    final List<Zeiteintrag> gefunden =
        repository.findImZeitraumFuerKonto(
            LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), ben);

    // Then — nur Bens drei Zeilen im November, keine von Anna (Plan #426, E8).
    assertThat(gefunden)
        .extracting(Zeiteintrag::accountId, Zeiteintrag::tag)
        .containsExactlyInAnyOrder(
            tuple(ben, LocalDate.of(2026, 11, 1)),
            tuple(ben, LocalDate.of(2026, 11, 3)),
            tuple(ben, LocalDate.of(2026, 11, 30)));
  }

  @Test
  void findImZeitraumFuerKonto_givenAnAccountWithoutEntries_thenEmpty() {
    // Given
    gesichert(anna, konzeption, "2026-11-03", "09:00", "12:00");

    // When
    final List<Zeiteintrag> gefunden =
        repository.findImZeitraumFuerKonto(
            LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 3), ben);

    // Then
    assertThat(gefunden).isEmpty();
  }

  @Test
  void findImZeitraum_givenEntriesOfTwoAccounts_thenReturnsBoth() {
    // Given — der Weg ohne Konto bleibt fuer die Sicht „Alle" (E8).
    gesichert(anna, konzeption, "2026-11-03", "09:00", "12:00");
    gesichert(ben, konzeption, "2026-11-03", "10:00", "11:00");

    // When
    final List<Zeiteintrag> gefunden =
        repository.findImZeitraum(LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 3));

    // Then
    assertThat(gefunden).extracting(Zeiteintrag::accountId).containsExactlyInAnyOrder(anna, ben);
  }

  @Test
  void angefallenJePosition_givenEntriesOfTwoAccounts_thenSumsAcrossBoth() {
    // Given — Anna 9:00 bis 12:00, Ben 10:00 bis 11:00 auf derselben Position (E15).
    gesichert(anna, konzeption, "2026-11-03", "09:00", "12:00");
    gesichert(ben, konzeption, "2026-11-03", "10:00", "11:00");

    // When
    final Map<Long, BigDecimal> angefallen = repository.angefallenJePosition(Set.of(konzeption));

    // Then — „angefallen" zaehlt beide Personen zusammen.
    assertThat(angefallen).containsEntry(konzeption, new BigDecimal("4.00"));
  }

  @Test
  void stundenJePositionImMonatUndAlleSummen_givenEntriesOfTwoAccounts_thenSumAcrossBoth() {
    // Given — dieselben beiden Eintraege; auch die drei uebrigen Summen trennen nicht (E15).
    gesichert(anna, konzeption, "2026-11-03", "09:00", "12:00");
    gesichert(ben, konzeption, "2026-11-03", "10:00", "11:00");

    // When
    final Map<Long, BigDecimal> imMonat =
        repository.stundenJePositionImMonat(Set.of(konzeption), YearMonth.of(2026, 11));
    final Map<Long, BigDecimal> alle = repository.alleAngefallenJePosition();
    final Map<Long, BigDecimal> imZeitraum =
        repository.alleStundenJePositionImZeitraum(
            LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));

    // Then
    assertThat(List.of(imMonat, alle, imZeitraum))
        .allSatisfy(summe -> assertThat(summe).containsEntry(konzeption, new BigDecimal("4.00")));
  }

  @Test
  void angefallenJePosition_thenSumsEveryMonthAndKeepsPositionsWithoutTime() {
    // Given — das Beispiel aus #193: im Oktober 10, im November 12 Stunden an einer Position.
    gesichert(konzeption, "2026-10-05", "09:00", "17:00");
    gesichert(konzeption, "2026-10-06", "09:00", "11:00");
    gesichert(konzeption, "2026-11-12", "09:00", "17:00");
    gesichert(konzeption, "2026-11-13", "09:00", "13:00");

    // When
    final Map<Long, BigDecimal> angefallen =
        repository.angefallenJePosition(Set.of(konzeption, beratung));

    // Then
    assertThat(angefallen)
        .hasSize(2)
        .containsEntry(konzeption, new BigDecimal("22.00"))
        .containsEntry(beratung, new BigDecimal("0.00"));
  }

  @Test
  void angefallenJePosition_givenNoPositionAtAll_thenAnEmptyResult() {
    // Given — eine leere Anfrage ist keine Abfrage ohne Bedingung.
    gesichert(konzeption, "2026-11-12", "09:00", "17:00");

    // When
    final Map<Long, BigDecimal> angefallen = repository.angefallenJePosition(Set.of());

    // Then
    assertThat(angefallen).isEmpty();
  }

  @Test
  void stundenJePositionImMonat_thenCountsOnlyThatMonth() {
    // Given — dieselben Eintraege wie oben.
    gesichert(konzeption, "2026-10-05", "09:00", "17:00");
    gesichert(konzeption, "2026-10-06", "09:00", "11:00");
    gesichert(konzeption, "2026-11-12", "09:00", "17:00");
    gesichert(konzeption, "2026-11-13", "09:00", "13:00");

    // When
    final Map<Long, BigDecimal> november =
        repository.stundenJePositionImMonat(Set.of(konzeption), YearMonth.of(2026, 11));

    // Then
    assertThat(november).containsEntry(konzeption, new BigDecimal("12.00"));
  }

  @Test
  void stundenJePositionImMonat_thenIncludesTheFirstAndTheLastDayOfTheMonth() {
    // Given — die Monatsgrenzen zaehlen mit.
    gesichert(konzeption, "2026-11-01", "09:00", "10:00");
    gesichert(konzeption, "2026-11-30", "09:00", "10:45");
    gesichert(konzeption, "2026-10-31", "09:00", "17:00");
    gesichert(konzeption, "2026-12-01", "09:00", "17:00");

    // When
    final Map<Long, BigDecimal> november =
        repository.stundenJePositionImMonat(Set.of(konzeption), YearMonth.of(2026, 11));

    // Then
    assertThat(november).containsEntry(konzeption, new BigDecimal("2.75"));
  }

  @Test
  void stundenJePositionImMonat_givenAMonthWithoutAnyEntry_thenZero() {
    // Given
    gesichert(konzeption, "2026-11-12", "09:00", "17:00");

    // When
    final Map<Long, BigDecimal> dezember =
        repository.stundenJePositionImMonat(Set.of(konzeption), YearMonth.of(2026, 12));

    // Then
    assertThat(dezember).containsEntry(konzeption, new BigDecimal("0.00"));
  }

  @Test
  void alleAngefallenJePosition_thenSumsEveryMonthAndOmitsPositionsWithoutTime() {
    // Given — nur die Konzeption traegt Zeit, die Beratung nicht.
    gesichert(konzeption, "2026-10-05", "09:00", "17:00");
    gesichert(konzeption, "2026-11-13", "09:00", "13:00");

    // When
    final Map<Long, BigDecimal> alle = repository.alleAngefallenJePosition();

    // Then — ohne Positionsmenge fehlt die Beratung, statt mit 0,00 darin zu stehen.
    assertThat(alle).containsExactly(entry(Long.valueOf(konzeption), new BigDecimal("12.00")));
  }

  @Test
  void alleStundenJePositionImZeitraum_givenAMonth_thenIncludesTheFirstAndTheLastDay() {
    // Given — die Grenzen zaehlen mit, der Tag davor und danach nicht.
    gesichert(konzeption, "2026-10-31", "09:00", "17:00");
    gesichert(konzeption, "2026-11-01", "09:00", "10:00");
    gesichert(beratung, "2026-11-30", "09:00", "10:45");
    gesichert(konzeption, "2026-12-01", "09:00", "17:00");

    // When
    final Map<Long, BigDecimal> november =
        repository.alleStundenJePositionImZeitraum(
            LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));

    // Then
    assertThat(november)
        .hasSize(2)
        .containsEntry(konzeption, new BigDecimal("1.00"))
        .containsEntry(beratung, new BigDecimal("1.75"));
  }

  @Test
  void alleStundenJePositionImZeitraum_givenAYear_thenIncludesTheFirstAndTheLastDay() {
    // Given — derselbe Weg ueber ein ganzes Jahr: Silvester davor und Neujahr danach zaehlen nicht.
    gesichert(konzeption, "2025-12-31", "09:00", "17:00");
    gesichert(konzeption, "2026-01-01", "09:00", "10:00");
    gesichert(konzeption, "2026-06-15", "09:00", "17:00");
    gesichert(beratung, "2026-12-31", "09:00", "10:45");
    gesichert(konzeption, "2027-01-01", "09:00", "17:00");

    // When
    final Map<Long, BigDecimal> jahr =
        repository.alleStundenJePositionImZeitraum(
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

    // Then
    assertThat(jahr)
        .hasSize(2)
        .containsEntry(konzeption, new BigDecimal("9.00"))
        .containsEntry(beratung, new BigDecimal("1.75"));
  }

  @Test
  void alleStundenJePositionImZeitraum_givenAPeriodWithoutAnyEntry_thenAnEmptyResult() {
    // Given
    gesichert(konzeption, "2026-11-12", "09:00", "17:00");

    // When
    final Map<Long, BigDecimal> dezember =
        repository.alleStundenJePositionImZeitraum(
            LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 31));

    // Then — kein Eintrag, keine Zeile: ohne Positionsmenge bleibt die Abbildung leer.
    assertThat(dezember).isEmpty();
  }

  @Test
  void monateMitEintragImZeitraum_thenNamesTheMonthsWithAnEntryIncludingBothBounds() {
    // Given — Eintraege genau auf dem ersten und dem letzten Tag, zwei im Maerz, keiner im Februar;
    // die Tage direkt vor und nach dem Zeitraum zaehlen nicht.
    gesichert(konzeption, "2025-12-31", "09:00", "17:00");
    gesichert(konzeption, "2026-01-01", "09:00", "10:00");
    gesichert(konzeption, "2026-03-04", "09:00", "17:00");
    gesichert(beratung, "2026-03-20", "09:00", "10:45");
    gesichert(beratung, "2026-12-31", "09:00", "10:00");
    gesichert(konzeption, "2027-01-01", "09:00", "17:00");

    // When
    final Set<YearMonth> monate =
        repository.monateMitEintragImZeitraum(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

    // Then
    assertThat(monate)
        .containsExactlyInAnyOrder(
            YearMonth.of(2026, 1), YearMonth.of(2026, 3), YearMonth.of(2026, 12));
  }

  @Test
  void monateMitEintragImZeitraum_givenAPeriodWithoutAnyEntry_thenAnEmptySet() {
    // Given
    gesichert(konzeption, "2026-11-12", "09:00", "17:00");

    // When
    final Set<YearMonth> monate =
        repository.monateMitEintragImZeitraum(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31));

    // Then
    assertThat(monate).isEmpty();
  }
}
