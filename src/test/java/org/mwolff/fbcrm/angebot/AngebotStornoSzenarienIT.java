package org.mwolff.fbcrm.angebot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.mwolff.fbcrm.angebot.domain.Angebotsstatus;
import org.mwolff.fbcrm.angebot.web.AngebotResponse;
import org.mwolff.fbcrm.angebot.web.AngeboteUebersichtResponse;
import org.mwolff.fbcrm.arbeitszeit.web.BuchungspositionResponse;
import org.mwolff.fbcrm.auth.Konten;
import org.mwolff.fbcrm.auth.domain.AccountRepository;
import org.mwolff.fbcrm.auth.domain.SecretHasher;
import org.mwolff.fbcrm.rechnung.web.RechnungResponse;
import org.mwolff.fbcrm.rechnung.web.RechnungZeileResponse;
import org.mwolff.fbcrm.startseite.web.ForecastResponse;
import org.mwolff.fbcrm.startseite.web.StartseiteResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Die vier Szenarien des Stornos, die im Backend beobachtbar sind, als Durchstich ueber HTTP, mit
 * Sitzung und gegen eine echte PostgreSQL-Instanz (Issue #342; Plan #332, fachliche Quelle #330).
 *
 * <p>Jedes Szenario laeuft quer durch die Module: Storno im Angebot, Kennzahlen in {@code
 * startseite}, Auswahlliste in {@code arbeitszeit}, Entwurf in {@code rechnung}. Jedes Modul prueft
 * seinen Anteil in eigenen Tests; hier steht der Weg als Ganzes. Darum entsteht der Bestand nur
 * ueber die HTTP-Wege — geprueft wird das beobachtbare Ergebnis, nicht die Spalte.
 *
 * <p>Das fuenfte Szenario, „Versehentliches Stornieren abbrechen", hat hier bewusst kein
 * Gegenstueck: Der Abbruch geschieht vollstaendig in der Oberflaeche und ist in {@code
 * AngebotPage.test.tsx} geprueft.
 */
class AngebotStornoSzenarienIT extends AbstractIntegrationTest {

  private static final String MAIL = "manne@example.org";
  private static final String PASSWORT = "richtiges-passwort";
  private static final Instant ANGELEGT = Instant.parse("2026-09-01T08:00:00Z");
  private static final String STUNDENSATZ = "100.00";

  private final TestRestTemplate rest;
  private final AccountRepository accounts;
  private final SecretHasher hasher;
  private final JdbcTemplate jdbc;

  private HttpHeaders sitzung = new HttpHeaders();
  private long firmaId;

  @Autowired
  AngebotStornoSzenarienIT(
      final TestRestTemplate rest,
      final AccountRepository accounts,
      final SecretHasher hasher,
      final JdbcTemplate jdbc) {
    this.rest = rest;
    this.accounts = accounts;
    this.hasher = hasher;
    this.jdbc = jdbc;
  }

  @BeforeEach
  void leereDenBestandUndMeldeAn() {
    jdbc.execute(
        "TRUNCATE arbeitszeit, rechnung_position, rechnung, angebot_position, angebot,"
            + " ansprechpartner, firma RESTART IDENTITY CASCADE");
    jdbc.execute("DELETE FROM rechnung_einstellungen");
    jdbc.execute("INSERT INTO rechnung_einstellungen DEFAULT VALUES");
    jdbc.update("INSERT INTO firma (name) VALUES (?)", "Adler AG");
    firmaId =
        Objects.requireNonNull(
                jdbc.queryForObject("SELECT id FROM firma WHERE name = ?", Long.class, "Adler AG"))
            .longValue();
    jdbc.execute("TRUNCATE outbox_message, account RESTART IDENTITY CASCADE");
    accounts.save(Konten.neu(MAIL, hasher.hash(PASSWORT), ANGELEGT));
    sitzung = angemeldeterKopf();
  }

  private HttpHeaders angemeldeterKopf() {
    final ResponseEntity<String> anmeldung =
        rest.postForEntity(
            "/api/auth/login", Map.of("email", MAIL, "password", PASSWORT), String.class);
    final String gesetzt = String.valueOf(anmeldung.getHeaders().getFirst(HttpHeaders.SET_COOKIE));
    final HttpHeaders kopf = new HttpHeaders();
    kopf.add(HttpHeaders.COOKIE, gesetzt.substring(0, gesetzt.indexOf(';')));
    return kopf;
  }

  private <T> ResponseEntity<T> ruf(
      final String pfad, final HttpMethod methode, final Object rumpf, final Class<T> typ) {
    return rest.exchange(pfad, methode, new HttpEntity<>(rumpf, sitzung), typ);
  }

  private <T> T lies(final String pfad, final Class<T> typ) {
    return Objects.requireNonNull(ruf(pfad, HttpMethod.GET, null, typ).getBody());
  }

  /**
   * Ein Kundenangebot mit einer Position Beratung ueber die angegebenen Stunden zu 100,00, so oft
   * weitergeschaltet wie verlangt: einmal heisst „abgegeben", zweimal „bestellt".
   */
  private AngebotResponse angebot(final String stunden, final int schritte) {
    final long angebotId =
        Objects.requireNonNull(
                ruf(
                        "/api/firmen/" + firmaId + "/angebote",
                        HttpMethod.POST,
                        Map.of("titel", "Website-Relaunch"),
                        AngebotResponse.class)
                    .getBody())
            .id();
    final Map<String, Object> position = new LinkedHashMap<>();
    position.put("bezeichnung", "Beratung");
    position.put("abrechnungsmodus", "AUFWAND");
    position.put("menge", stunden);
    position.put("einheit", "STUNDE");
    position.put("einzelpreis", STUNDENSATZ);
    final Map<String, Object> rumpf = new LinkedHashMap<>();
    rumpf.put("angebotDatum", "2026-09-25");
    rumpf.put("titel", "Website-Relaunch");
    rumpf.put("ansprechpartnerId", null);
    rumpf.put("beschreibung", "Neugestaltung der Website");
    rumpf.put("positionen", List.of(position));
    ruf("/api/angebote/" + angebotId, HttpMethod.PUT, rumpf, AngebotResponse.class);
    for (int schritt = 0; schritt < schritte; schritt++) {
      ruf("/api/angebote/" + angebotId + "/status/weiter", HttpMethod.POST, null, String.class);
    }
    return lies("/api/angebote/" + angebotId, AngebotResponse.class);
  }

  /** Storniert mit Grund, wie nach der bestaetigten Rueckfrage der Oberflaeche. */
  private ResponseEntity<AngebotResponse> storniere(final long angebotId, final String grund) {
    return ruf(
        "/api/angebote/" + angebotId + "/storno",
        HttpMethod.POST,
        Map.of("grund", grund),
        AngebotResponse.class);
  }

  private ForecastResponse forecast() {
    return lies("/api/startseite/forecast", ForecastResponse.class);
  }

  private StartseiteResponse startseite() {
    return lies("/api/startseite", StartseiteResponse.class);
  }

  /** Die Angebote, von denen eine Position im Forecast steht. */
  private List<Long> imForecast() {
    return forecast().jahre().stream()
        .flatMap(jahr -> jahr.positionen().stream())
        .map(zeile -> Long.valueOf(zeile.angebotId()))
        .toList();
  }

  private BigDecimal angebotenNetto() {
    return forecast().jahre().stream()
        .map(ForecastResponse.Jahreszeile::angebotenNetto)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private BigDecimal zugesagtNetto() {
    return forecast().jahre().stream()
        .map(ForecastResponse.Jahreszeile::zugesagtNetto)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private List<Long> inArbeit() {
    return startseite().inArbeit().stream().map(zeile -> Long.valueOf(zeile.angebotId())).toList();
  }

  private List<Long> buchbareAngebote() {
    return List.of(lies("/api/arbeitszeit/buchbare-positionen", BuchungspositionResponse[].class))
        .stream()
        .map(position -> Long.valueOf(position.angebotId()))
        .toList();
  }

  @Test
  void szenarioKundeLehntEinAbgegebenesAngebotAb() {
    // Given — ein abgegebenes Angebot ueber 10 h zu 100,00, im Forecast unter „angeboten".
    final AngebotResponse angebot = angebot("10", 1);
    assertThat(angebot.status()).isEqualTo(Angebotsstatus.ABGEGEBEN);
    assertThat(imForecast()).containsExactly(Long.valueOf(angebot.id()));
    assertThat(angebotenNetto()).isEqualByComparingTo("1000.00");

    // When
    final ResponseEntity<AngebotResponse> antwort = storniere(angebot.id(), "vom Kunden abgelehnt");

    // Then — storniert in der Uebersicht aller Angebote, und „angeboten" ohne seine Summe.
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(lies("/api/angebote", AngeboteUebersichtResponse.class).angebote())
        .extracting(
            zeile -> Long.valueOf(zeile.id()),
            AngeboteUebersichtResponse.Zeile::status,
            AngeboteUebersichtResponse.Zeile::storniert)
        .containsExactly(tuple(Long.valueOf(angebot.id()), Angebotsstatus.ABGEGEBEN, true));
    assertThat(lies("/api/angebote?storniert=true", AngeboteUebersichtResponse.class).angebote())
        .extracting(zeile -> Long.valueOf(zeile.id()))
        .containsExactly(Long.valueOf(angebot.id()));
    assertThat(imForecast()).isEmpty();
    assertThat(angebotenNetto()).isEqualByComparingTo("0.00");
  }

  @Test
  void szenarioKundeZiehtEinenErteiltenAuftragZurueck() {
    // Given — ein bestelltes Angebot: in Arbeit, zugesagt und in der Zeiterfassung waehlbar.
    final AngebotResponse angebot = angebot("10", 2);
    final Long kennung = Long.valueOf(angebot.id());
    assertThat(angebot.status()).isEqualTo(Angebotsstatus.BESTELLT);
    assertThat(inArbeit()).containsExactly(kennung);
    assertThat(imForecast()).containsExactly(kennung);
    assertThat(zugesagtNetto()).isEqualByComparingTo("1000.00");
    assertThat(buchbareAngebote()).containsExactly(kennung);

    // When
    final ResponseEntity<AngebotResponse> antwort =
        storniere(angebot.id(), "Auftrag zurückgezogen");

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(inArbeit()).isEmpty();
    assertThat(imForecast()).isEmpty();
    assertThat(zugesagtNetto()).isEqualByComparingTo("0.00");
    assertThat(buchbareAngebote()).doesNotContain(kennung);
  }

  @Test
  void szenarioStornoZuruecknehmen() {
    // Given — ein aus „bestellt" storniertes Angebot.
    final AngebotResponse angebot = angebot("10", 2);
    final Long kennung = Long.valueOf(angebot.id());
    storniere(angebot.id(), "Auftrag zurückgezogen");
    assertThat(inArbeit()).isEmpty();
    assertThat(imForecast()).isEmpty();

    // When
    final ResponseEntity<AngebotResponse> antwort =
        ruf(
            "/api/angebote/" + angebot.id() + "/storno",
            HttpMethod.DELETE,
            null,
            AngebotResponse.class);

    // Then — wieder bestellt, in Arbeit und zugesagt; der Grund ist fort (Kriterium 9).
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.OK);
    final AngebotResponse gelesen = lies("/api/angebote/" + angebot.id(), AngebotResponse.class);
    assertThat(gelesen.status()).isEqualTo(Angebotsstatus.BESTELLT);
    assertThat(gelesen.storniert()).isFalse();
    assertThat(gelesen.stornoGrund()).isNull();
    assertThat(inArbeit()).containsExactly(kennung);
    assertThat(imForecast()).containsExactly(kennung);
    assertThat(zugesagtNetto()).isEqualByComparingTo("1000.00");
  }

  @Test
  void szenarioRestEinesAuftragsWirdNichtAbgerufen() {
    // Given — 8 h bestellt, 3 h gebucht und noch nicht berechnet.
    final AngebotResponse angebot = angebot("8", 2);
    final Long kennung = Long.valueOf(angebot.id());
    final long positionId = Objects.requireNonNull(angebot.positionen().getFirst().id());
    final Map<String, Object> eintrag = new LinkedHashMap<>();
    eintrag.put("angebotPositionId", Long.valueOf(positionId));
    eintrag.put("tag", "2026-09-28");
    eintrag.put("von", "09:00");
    eintrag.put("bis", "12:00");
    assertThat(ruf("/api/arbeitszeit", HttpMethod.POST, eintrag, String.class).getStatusCode())
        .isEqualTo(HttpStatus.CREATED);
    assertThat(zugesagtNetto()).isEqualByComparingTo("800.00");

    // When
    final ResponseEntity<AngebotResponse> antwort = storniere(angebot.id(), "Rest nicht abgerufen");

    // Then — der Forecast ohne seine Summe, die 3 h weiter unter „Noch nicht abgerechnet" ...
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(imForecast()).isEmpty();
    assertThat(zugesagtNetto()).isEqualByComparingTo("0.00");
    final List<StartseiteResponse.Anteilszeile> nichtAbgerechnet =
        startseite().nichtAbgerechnet().angebote();
    assertThat(nichtAbgerechnet)
        .extracting(zeile -> Long.valueOf(zeile.angebotId()))
        .containsExactly(kennung);
    assertThat(nichtAbgerechnet.getFirst().netto()).isEqualByComparingTo("300.00");
    // ... und ein Rechnungsentwurf, der genau die 3 h vorbelegt.
    final ResponseEntity<RechnungResponse> entwurf =
        ruf(
            "/api/angebote/" + angebot.id() + "/rechnungen",
            HttpMethod.POST,
            null,
            RechnungResponse.class);
    assertThat(entwurf.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    final List<RechnungZeileResponse> zeilen = Objects.requireNonNull(entwurf.getBody()).zeilen();
    assertThat(zeilen)
        .extracting(zeile -> Long.valueOf(zeile.angebotPositionId()))
        .containsExactly(Long.valueOf(positionId));
    assertThat(zeilen.getFirst().menge()).isEqualByComparingTo("3");
  }
}
