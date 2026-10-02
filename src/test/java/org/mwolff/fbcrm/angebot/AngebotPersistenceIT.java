package org.mwolff.fbcrm.angebot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.angebot.domain.Angebotsposition;
import org.mwolff.fbcrm.angebot.domain.Angebotsstatus;
import org.mwolff.fbcrm.common.Abrechnungsmodus;
import org.mwolff.fbcrm.common.Einheit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Der Bestand des Angebots gegen eine echte PostgreSQL-Instanz.
 *
 * <p>Gegenstand ist das Angebot als <b>eine</b> Einheit mit seinen Positionen: Anlegen, Aendern mit
 * umgestellter Reihenfolge und Lesen in genau dieser Reihenfolge (E24), dazu der Status als Text in
 * seiner Spalte.
 *
 * <p><b>Und die dauerhafte Kennung der Position</b> (Plan #169, E2): Umordnen und Umbenennen lassen
 * sie unveraendert, eine neue Position bekommt eine neue, und eine weggelassene ist geloescht. Nur
 * hier pruefbar, weil es an der echten Identitaetsspalte und an der aufgeschobenen Eindeutigkeit
 * von {@code angebot_position_reihenfolge} haengt: Der Tausch zweier Plaetze traegt einen
 * Zwischenstand, der sich widerspricht.
 *
 * <p>Die Konstanten dieser Klasse tragen keine Kennung — sie sind die eingereichte Sicht. Was
 * zurueckgelesen wird, traegt eine; Vergleiche dagegen lassen das Feld {@code id} darum aus.
 *
 * <p>Jeder Zug setzt seine Transaktionsgrenze selbst ueber {@link TransactionTemplate}: Der Adapter
 * schreibt die Positionszeilen im Zug des Aufrufers fort und hat keine eigene Grenze — sie gehoert
 * dem Anwendungsfall.
 */
class AngebotPersistenceIT extends AbstractIntegrationTest {

  private static final LocalDate ANGEBOTSDATUM = LocalDate.of(2026, 9, 20);
  private static final Instant ANGELEGT = Instant.parse("2026-09-20T08:00:00Z");
  private static final String BESCHREIBUNG = "Neugestaltung der Website";
  private static final Angebotsposition KONZEPTION =
      new Angebotsposition(
          null,
          "Konzeption",
          Abrechnungsmodus.AUFWAND,
          new BigDecimal("2.50"),
          Einheit.PERSONENTAG,
          new BigDecimal("1000.01"));

  private static final Angebotsposition SCHULUNG =
      new Angebotsposition(
          null,
          "Schulungstag",
          Abrechnungsmodus.FESTPREIS,
          new BigDecimal("1.00"),
          Einheit.PAUSCHAL,
          new BigDecimal("1200.00"));

  private static final Angebotsposition BETREUUNG =
      new Angebotsposition(
          null,
          "Betreuung",
          Abrechnungsmodus.AUFWAND,
          new BigDecimal("8.00"),
          Einheit.STUNDE,
          new BigDecimal("95.00"));

  private final AngebotRepository repository;
  private final TransactionTemplate transaktion;
  private final JdbcTemplate jdbc;

  private long firmaId;
  private Long ansprechpartnerId;

  @Autowired
  AngebotPersistenceIT(
      final AngebotRepository repository,
      final TransactionTemplate transaktion,
      final JdbcTemplate jdbc) {
    this.repository = repository;
    this.transaktion = transaktion;
    this.jdbc = jdbc;
  }

  @BeforeEach
  void leereFachtabellenUndLegeFirmaUndAnsprechpartnerAn() {
    jdbc.execute(
        "TRUNCATE angebot_position, angebot, ansprechpartner, firma RESTART IDENTITY CASCADE");
    jdbc.update("INSERT INTO firma (name) VALUES ('Adler AG')");
    firmaId =
        jdbc.queryForObject("SELECT id FROM firma WHERE name = 'Adler AG'", Long.class).longValue();
    jdbc.update(
        "INSERT INTO ansprechpartner (firma_id, nachname) VALUES (?, 'Adler')",
        Long.valueOf(firmaId));
    ansprechpartnerId = jdbc.queryForObject("SELECT id FROM ansprechpartner", Long.class);
  }

  private Angebot angebot(final List<Angebotsposition> positionen) {
    return angebot(positionen, Angebotsstatus.ANGELEGT);
  }

  private Angebot angebot(final List<Angebotsposition> positionen, final Angebotsstatus status) {
    return new Angebot(
        null,
        firmaId,
        ansprechpartnerId,
        status.intern(),
        status,
        ANGEBOTSDATUM,
        BESCHREIBUNG,
        positionen,
        ANGELEGT,
        ANGELEGT);
  }

  private Angebot geschrieben(final Angebot angebot) {
    return transaktion.execute(status -> repository.save(angebot));
  }

  private Optional<Angebot> gelesen(final long id) {
    return transaktion.execute(status -> repository.findById(id));
  }

  /** Die Positionen des Angebots, so wie der Bestand sie fuehrt — samt ihren Kennungen. */
  private List<Angebotsposition> positionenVon(final long id) {
    return gelesen(id).orElseThrow().positionen();
  }

  /** Dasselbe Angebot mit einer neuen Positionsliste, geschrieben in einem eigenen Zug. */
  private Angebot geschriebenMit(final Angebot angebot, final List<Angebotsposition> positionen) {
    return geschrieben(
        angebot.geaendert(ANGEBOTSDATUM, ansprechpartnerId, BESCHREIBUNG, positionen, ANGELEGT));
  }

  private static Angebotsposition umbenannt(final Angebotsposition position, final String name) {
    return new Angebotsposition(
        position.id(),
        name,
        position.abrechnungsmodus(),
        position.menge(),
        position.einheit(),
        position.einzelpreis());
  }

  @Test
  void save_givenANewAngebot_thenReadsBackEveryFieldAndThePositionsInOrder() {
    // Given
    final Angebot gespeichert = geschrieben(angebot(List.of(KONZEPTION, SCHULUNG)));

    // When
    final Optional<Angebot> gelesen = gelesen(gespeichert.requireId());

    // Then
    assertThat(gelesen)
        .hasValueSatisfying(
            angebot -> {
              assertThat(angebot.id()).isEqualTo(gespeichert.requireId());
              assertThat(angebot.firmaId()).isEqualTo(firmaId);
              assertThat(angebot.ansprechpartnerId()).isEqualTo(ansprechpartnerId);
              assertThat(angebot.intern()).isFalse();
              assertThat(angebot.status()).isEqualTo(Angebotsstatus.ANGELEGT);
              assertThat(angebot.angebotDatum()).isEqualTo(ANGEBOTSDATUM);
              assertThat(angebot.beschreibung()).isEqualTo(BESCHREIBUNG);
              assertThat(angebot.createdAt()).isEqualTo(ANGELEGT);
              assertThat(angebot.updatedAt()).isEqualTo(ANGELEGT);
              assertThat(angebot.positionen())
                  .usingRecursiveFieldByFieldElementComparatorIgnoringFields("id")
                  .containsExactly(KONZEPTION, SCHULUNG);
            });
  }

  @Test
  void save_givenInternalWork_thenReadsBackTheFlagAndTheInternalStatus() {
    // Given — Issue #226: die Art geht in ihre eigene Spalte und kommt von dort zurueck.
    final Angebot gespeichert = geschrieben(angebot(List.of(KONZEPTION), Angebotsstatus.LAEUFT));

    // When
    final Optional<Angebot> gelesen = gelesen(gespeichert.requireId());

    // Then
    assertThat(gelesen)
        .hasValueSatisfying(
            angebot -> {
              assertThat(angebot.intern()).isTrue();
              assertThat(angebot.status()).isEqualTo(Angebotsstatus.LAEUFT);
            });
    assertThat(
            jdbc.queryForObject(
                "SELECT intern FROM angebot WHERE id = ?",
                Boolean.class,
                Long.valueOf(gespeichert.requireId())))
        .isTrue();
  }

  @Test
  void save_thenStoresThePlacesFromOneUpwards() {
    // Given
    final Angebot gespeichert = geschrieben(angebot(List.of(SCHULUNG, KONZEPTION)));

    // When
    final List<Short> plaetze =
        jdbc.queryForList(
            "SELECT position FROM angebot_position WHERE angebot_id = ? ORDER BY position",
            Short.class,
            Long.valueOf(gespeichert.requireId()));

    // Then
    assertThat(plaetze).containsExactly((short) 1, (short) 2);
  }

  @Test
  void save_givenAChangedAngebot_thenStoresThePositionsInTheNewOrder() {
    // Given
    final Angebot gespeichert = geschrieben(angebot(List.of(KONZEPTION, SCHULUNG)));
    final List<Angebotsposition> gefuehrt = positionenVon(gespeichert.requireId());

    // When — dieselben Positionen, umgestellt, und eine dritte dazu.
    geschriebenMit(gespeichert, List.of(gefuehrt.get(1), BETREUUNG, gefuehrt.get(0)));

    // Then
    assertThat(positionenVon(gespeichert.requireId()))
        .usingRecursiveFieldByFieldElementComparatorIgnoringFields("id")
        .containsExactly(SCHULUNG, BETREUUNG, KONZEPTION);
  }

  @Test
  void save_givenAReorderedList_thenBothPositionsKeepTheirIds() {
    // Given — Plan #169, E2: Eine Rechnung soll sich auf eine Position berufen koennen, auch wenn
    // das Angebot danach umgeordnet wird.
    final Angebot gespeichert = geschrieben(angebot(List.of(KONZEPTION, SCHULUNG)));
    final List<Angebotsposition> gefuehrt = positionenVon(gespeichert.requireId());

    // When — getauscht; der Zwischenstand traegt zweimal denselben Platz.
    geschriebenMit(gespeichert, List.of(gefuehrt.get(1), gefuehrt.get(0)));

    // Then
    assertThat(positionenVon(gespeichert.requireId()))
        .extracting(Angebotsposition::id, Angebotsposition::bezeichnung)
        .containsExactly(
            tuple(gefuehrt.get(1).id(), "Schulungstag"), tuple(gefuehrt.get(0).id(), "Konzeption"));
  }

  @Test
  void save_givenARenamedPosition_thenItKeepsItsId() {
    // Given
    final Angebot gespeichert = geschrieben(angebot(List.of(KONZEPTION, SCHULUNG)));
    final List<Angebotsposition> gefuehrt = positionenVon(gespeichert.requireId());

    // When
    geschriebenMit(
        gespeichert, List.of(umbenannt(gefuehrt.get(0), "Feinkonzept"), gefuehrt.get(1)));

    // Then
    assertThat(positionenVon(gespeichert.requireId()))
        .extracting(Angebotsposition::id, Angebotsposition::bezeichnung)
        .containsExactly(
            tuple(gefuehrt.get(0).id(), "Feinkonzept"),
            tuple(gefuehrt.get(1).id(), "Schulungstag"));
  }

  @Test
  void save_givenAnAddedPosition_thenItGetsANewIdAndTheOldOnesKeepTheirs() {
    // Given — ohne Kennung eingereicht heisst „neu".
    final Angebot gespeichert = geschrieben(angebot(List.of(KONZEPTION, SCHULUNG)));
    final List<Angebotsposition> gefuehrt = positionenVon(gespeichert.requireId());

    // When
    geschriebenMit(gespeichert, List.of(gefuehrt.get(0), gefuehrt.get(1), BETREUUNG));

    // Then
    final List<Angebotsposition> danach = positionenVon(gespeichert.requireId());
    assertThat(danach)
        .extracting(Angebotsposition::id)
        .startsWith(gefuehrt.get(0).id(), gefuehrt.get(1).id())
        .hasSize(3)
        .doesNotHaveDuplicates();
    assertThat(danach.get(2).bezeichnung()).isEqualTo("Betreuung");
  }

  @Test
  void save_givenAnOmittedPosition_thenItsRowIsGone() {
    // Given
    final Angebot gespeichert = geschrieben(angebot(List.of(KONZEPTION, SCHULUNG)));
    final List<Angebotsposition> gefuehrt = positionenVon(gespeichert.requireId());

    // When — die zweite Position fehlt in der neuen Liste.
    geschriebenMit(gespeichert, List.of(gefuehrt.get(0)));

    // Then
    assertThat(positionenVon(gespeichert.requireId()))
        .extracting(Angebotsposition::id)
        .containsExactly(gefuehrt.get(0).id());
    assertThat(jdbc.queryForObject("SELECT count(*) FROM angebot_position", Integer.class))
        .isEqualTo(1);
  }

  @Test
  void save_givenAChangedAngebot_thenLeavesNoOrphanedPositionRow() {
    // Given
    final Angebot gespeichert = geschrieben(angebot(List.of(KONZEPTION, SCHULUNG)));

    // When — alle bisherigen weggelassen, eine neue dazu.
    geschriebenMit(gespeichert, List.of(BETREUUNG));

    // Then
    assertThat(jdbc.queryForObject("SELECT count(*) FROM angebot_position", Integer.class))
        .isEqualTo(1);
  }

  @Test
  void save_givenAStatusChange_thenStoresTheStatusAsText() {
    // Given
    final Angebot gespeichert = geschrieben(angebot(List.of(KONZEPTION)));

    // When
    geschrieben(gespeichert.statusWeiter(ANGELEGT).statusWeiter(ANGELEGT));

    // Then — als Text und nicht als Ordnungszahl, damit der CHECK der Migration ihn kennt.
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM angebot WHERE id = ?",
                String.class,
                Long.valueOf(gespeichert.requireId())))
        .isEqualTo("BESTELLT");
    assertThat(gelesen(gespeichert.requireId()))
        .hasValueSatisfying(
            angebot -> assertThat(angebot.status()).isEqualTo(Angebotsstatus.BESTELLT));
  }

  @Test
  void save_givenNoContactAndNoText_thenReadsBackBothAbsent() {
    // Given — Ansprechpartner und Beschreibung sind optional.
    final Angebot gespeichert =
        geschrieben(angebot(List.of()).geaendert(ANGEBOTSDATUM, null, null, List.of(), ANGELEGT));

    // Then
    assertThat(gelesen(gespeichert.requireId()))
        .hasValueSatisfying(
            angebot -> {
              assertThat(angebot.ansprechpartnerId()).isNull();
              assertThat(angebot.beschreibung()).isNull();
              assertThat(angebot.positionen()).isEmpty();
            });
  }

  @Test
  void save_givenAPositionWithABlankLabel_thenAccepted() {
    // Given — die fehlende Bezeichnung weist der Eingang der Maske ab, nicht die Datenbank.
    final Angebotsposition ohneBezeichnung =
        new Angebotsposition(
            null,
            "   ",
            Abrechnungsmodus.AUFWAND,
            new BigDecimal("1.00"),
            Einheit.STUNDE,
            new BigDecimal("0.00"));
    final Angebot gespeichert = geschrieben(angebot(List.of(ohneBezeichnung)));

    // Then
    assertThat(gelesen(gespeichert.requireId()))
        .hasValueSatisfying(
            angebot ->
                assertThat(angebot.positionen())
                    .usingRecursiveFieldByFieldElementComparatorIgnoringFields("id")
                    .containsExactly(ohneBezeichnung));
  }

  @Test
  void findById_givenAnUnknownId_thenEmpty() {
    // When / Then
    assertThat(gelesen(4711L)).isEmpty();
  }
}
