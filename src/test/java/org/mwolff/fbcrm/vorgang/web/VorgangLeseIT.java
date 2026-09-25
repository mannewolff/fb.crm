package org.mwolff.fbcrm.vorgang.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.mwolff.fbcrm.auth.domain.Account;
import org.mwolff.fbcrm.auth.domain.AccountRepository;
import org.mwolff.fbcrm.auth.domain.PasswordHasher;
import org.mwolff.fbcrm.auth.domain.Role;
import org.mwolff.fbcrm.firma.domain.Anschrift;
import org.mwolff.fbcrm.firma.domain.Ansprechpartner;
import org.mwolff.fbcrm.firma.domain.AnsprechpartnerRepository;
import org.mwolff.fbcrm.firma.domain.Firma;
import org.mwolff.fbcrm.firma.domain.FirmaRepository;
import org.mwolff.fbcrm.vorgang.domain.Eintrag;
import org.mwolff.fbcrm.vorgang.domain.EintragRepository;
import org.mwolff.fbcrm.vorgang.domain.Eintragsart;
import org.mwolff.fbcrm.vorgang.domain.Herkunft;
import org.mwolff.fbcrm.vorgang.domain.Phase;
import org.mwolff.fbcrm.vorgang.domain.Vorgang;
import org.mwolff.fbcrm.vorgang.domain.VorgangRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Die drei Lesewege des Vorgangs ueber HTTP, mit Sitzung und gegen eine echte PostgreSQL-Instanz.
 *
 * <p>Der Bestand wird ueber die Ports angelegt und nicht ueber die Schnittstelle: Die Schreibwege
 * gibt es in diesem Stand noch nicht, und die Lesewege sollen gegen einen Bestand stehen, dessen
 * Zeitpunkte der Test selbst in der Hand hat — nur so ist die Reihenfolge aus E16 pruefbar.
 *
 * <p>Der Nachweis der Zugangsregel steht hier und nicht nur in {@code AccessRuleIT}: Ein neuer Pfad
 * unter {@code /api} ist ohne Sitzung verschlossen, und das gehoert zu jedem neuen Weg dazu.
 */
class VorgangLeseIT extends AbstractIntegrationTest {

  private static final String MAIL = "manne@example.org";
  private static final String PASSWORT = "richtiges-passwort";
  private static final Instant ANGELEGT = Instant.parse("2026-09-01T08:00:00Z");
  private static final Instant FRUEH = Instant.parse("2026-09-05T08:00:00Z");
  private static final Instant SPAET = Instant.parse("2026-09-20T08:00:00Z");

  private final TestRestTemplate rest;
  private final AccountRepository accounts;
  private final PasswordHasher hasher;
  private final FirmaRepository firmen;
  private final AnsprechpartnerRepository ansprechpartner;
  private final VorgangRepository vorgaenge;
  private final EintragRepository eintraege;
  private final JdbcTemplate jdbc;

  private HttpHeaders sitzung = new HttpHeaders();

  @Autowired
  VorgangLeseIT(
      final TestRestTemplate rest,
      final AccountRepository accounts,
      final PasswordHasher hasher,
      final FirmaRepository firmen,
      final AnsprechpartnerRepository ansprechpartner,
      final VorgangRepository vorgaenge,
      final EintragRepository eintraege,
      final JdbcTemplate jdbc) {
    this.rest = rest;
    this.accounts = accounts;
    this.hasher = hasher;
    this.firmen = firmen;
    this.ansprechpartner = ansprechpartner;
    this.vorgaenge = vorgaenge;
    this.eintraege = eintraege;
    this.jdbc = jdbc;
  }

  @BeforeEach
  void leereDenBestandUndMeldeAn() {
    jdbc.execute(
        "TRUNCATE vorgang_eintrag, vorgang, ansprechpartner, firma RESTART IDENTITY CASCADE");
    jdbc.execute("TRUNCATE outbox_message, password_reset_token, account RESTART IDENTITY CASCADE");
    accounts.save(
        new Account(null, MAIL, "Manne", hasher.hash(PASSWORT), Role.ADMIN, 0, ANGELEGT, ANGELEGT));
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

  private <T> ResponseEntity<T> hole(final String pfad, final Class<T> typ) {
    return rest.exchange(pfad, HttpMethod.GET, new HttpEntity<>(sitzung), typ);
  }

  private long firma(final String name) {
    return firmen
        .save(
            new Firma(
                null,
                name,
                new Anschrift("Am Wall 1", "28195", "Bremen", "Deutschland"),
                null,
                null,
                true,
                ANGELEGT,
                ANGELEGT))
        .requireId();
  }

  private long partner(final long firmaId, final boolean aktiv) {
    return ansprechpartner
        .save(
            new Ansprechpartner(
                null, firmaId, "Max", "Mueller", null, null, null, null, aktiv, ANGELEGT, ANGELEGT))
        .requireId();
  }

  private long vorgang(
      final long nummer,
      final String titel,
      final long firmaId,
      final Long ansprechpartnerId,
      final boolean abgeschlossen,
      final Instant angelegt) {
    return vorgaenge
        .save(
            new Vorgang(
                null, nummer, titel, firmaId, ansprechpartnerId, abgeschlossen, angelegt, angelegt))
        .requireId();
  }

  /*
   * Der Suchtext geht als Platzhalter in die Adresse und nicht zusammengesetzt hinein: Nur so
   * kodiert RestTemplate ein fuehrendes # richtig als %23, statt es als Fragmentzeichen zu lesen
   * — und genau dieser Fall ist Kriterium 3.
   */
  private VorgaengeUebersichtResponse uebersicht(
      final String suche, final boolean auchAbgeschlossene) {
    return Objects.requireNonNull(
        rest.exchange(
                "/api/vorgaenge?suche={suche}&auchAbgeschlossene={schalter}",
                HttpMethod.GET,
                new HttpEntity<>(sitzung),
                VorgaengeUebersichtResponse.class,
                suche,
                Boolean.valueOf(auchAbgeschlossene))
            .getBody());
  }

  private VorgangResponse detail(final long id) {
    return Objects.requireNonNull(hole("/api/vorgaenge/" + id, VorgangResponse.class).getBody());
  }

  private VorgaengeDerFirmaResponse derFirma(final long firmaId) {
    return Objects.requireNonNull(
        hole("/api/firmen/" + firmaId + "/vorgaenge", VorgaengeDerFirmaResponse.class).getBody());
  }

  @Test
  void uebersicht_givenNoVorgang_thenEmptyAndTheTotalIsZero() {
    // When — Kriterium 2: daran erkennt die Oberflaeche „noch kein Vorgang".
    final VorgaengeUebersichtResponse liste = uebersicht("", false);

    // Then
    assertThat(liste).isEqualTo(new VorgaengeUebersichtResponse(List.of(), 0L));
  }

  @Test
  void uebersicht_thenSortsByTheLatestEntryAndFallsBackToTheCreationTime() {
    // Given — E16: der frueher angelegte Vorgang steht wegen seines juengeren Eintrags oben.
    final long firmaId = firma("Adler AG");
    final long mitEintrag = vorgang(1L, "Website-Relaunch", firmaId, null, false, ANGELEGT);
    final long ohneEintrag = vorgang(2L, "Schulung", firmaId, null, false, FRUEH);
    eintraege.save(Eintrag.kommentar(mitEintrag, "Angerufen", SPAET, Herkunft.VON_HAND, SPAET));

    // When
    final VorgaengeUebersichtResponse liste = uebersicht("", false);

    // Then
    assertThat(liste.vorgaenge())
        .extracting(VorgangZeileResponse::id, VorgangZeileResponse::letzteAktivitaet)
        .containsExactly(tuple(mitEintrag, SPAET), tuple(ohneEintrag, FRUEH));
  }

  @Test
  void uebersicht_thenCarriesNumberTitleFirmaAndPhasePerRow() {
    // Given — Kriterium 2.
    final long firmaId = firma("Adler AG");
    final long id = vorgang(12L, "Website-Relaunch", firmaId, null, false, ANGELEGT);

    // When
    final VorgaengeUebersichtResponse liste = uebersicht("", false);

    // Then — die Nummer geht als Zahl hinaus; das # setzt die Oberflaeche.
    assertThat(liste.vorgaenge())
        .containsExactly(
            new VorgangZeileResponse(
                id, 12L, "Website-Relaunch", "Adler AG", Phase.ANBAHNUNG, false, ANGELEGT));
  }

  @Test
  void uebersicht_givenATitleFragment_thenShowsOnlyTheMatchingVorgang() {
    // Given — Kriterium 3.
    final long firmaId = firma("Adler AG");
    vorgang(1L, "Website-Relaunch", firmaId, null, false, ANGELEGT);
    vorgang(2L, "Schulung", firmaId, null, false, ANGELEGT);

    // When
    final VorgaengeUebersichtResponse liste = uebersicht("relaunch", false);

    // Then
    assertThat(liste.vorgaenge())
        .extracting(VorgangZeileResponse::titel)
        .containsExactly("Website-Relaunch");
  }

  @Test
  void uebersicht_givenAFirmaName_thenShowsOnlyTheVorgaengeOfThatFirma() {
    // Given — Kriterium 3.
    vorgang(1L, "Website-Relaunch", firma("Adler AG"), null, false, ANGELEGT);
    vorgang(2L, "Schulung", firma("Baum GmbH"), null, false, ANGELEGT);

    // When
    final VorgaengeUebersichtResponse liste = uebersicht("baum", false);

    // Then
    assertThat(liste.vorgaenge())
        .extracting(VorgangZeileResponse::firma)
        .containsExactly("Baum GmbH");
  }

  @ParameterizedTest(name = "Suche nach \"{0}\"")
  @ValueSource(strings = {"12", "#12"})
  void uebersicht_givenTheNumber_thenFindsItWithAndWithoutTheHash(final String suche) {
    // Given — Kriterium 3, E17.
    final long firmaId = firma("Adler AG");
    vorgang(1L, "Schulung", firmaId, null, false, ANGELEGT);
    final long gesucht = vorgang(12L, "Website-Relaunch", firmaId, null, false, ANGELEGT);

    // When
    final VorgaengeUebersichtResponse liste = uebersicht(suche, false);

    // Then
    assertThat(liste.vorgaenge()).extracting(VorgangZeileResponse::id).containsExactly(gesucht);
  }

  @Test
  void uebersicht_givenAClosedVorgang_thenLeavesItOutByDefault() {
    // Given — Kriterium 20.
    final long firmaId = firma("Adler AG");
    vorgang(1L, "Website-Relaunch", firmaId, null, true, ANGELEGT);

    // When
    final VorgaengeUebersichtResponse liste = uebersicht("", false);

    // Then — und gesamt zaehlt ihn trotzdem mit.
    assertThat(liste).isEqualTo(new VorgaengeUebersichtResponse(List.of(), 1L));
  }

  @Test
  void uebersicht_givenAClosedVorgangAndTheSwitch_thenShowsItAsClosed() {
    // Given — Kriterium 20.
    final long firmaId = firma("Adler AG");
    final long id = vorgang(1L, "Website-Relaunch", firmaId, null, true, ANGELEGT);

    // When
    final VorgaengeUebersichtResponse liste = uebersicht("", true);

    // Then
    assertThat(liste.vorgaenge())
        .extracting(VorgangZeileResponse::id, VorgangZeileResponse::abgeschlossen)
        .containsExactly(tuple(id, true));
  }

  @Test
  void detail_thenCarriesHistoryFirmaAndAnsprechpartnerInOneAnswer() {
    // Given — Kriterien 9, 15, E25.
    final long firmaId = firma("Adler AG");
    final long partnerId = partner(firmaId, true);
    final long id = vorgang(12L, "Website-Relaunch", firmaId, partnerId, false, ANGELEGT);
    eintraege.save(Eintrag.kommentar(id, "Angerufen", FRUEH, Herkunft.VON_HAND, FRUEH));
    eintraege.save(
        Eintrag.anhang(
            id,
            "Das Angebot",
            SPAET,
            Herkunft.VON_HAND,
            "Angebot.pdf",
            4096L,
            "vorgang/" + id + "/abc",
            SPAET));

    // When
    final VorgangResponse gelesen = detail(id);

    // Then — beide Eintragsarten als eine Folge, juengstes Geschehen oben.
    assertThat(gelesen)
        .extracting(
            VorgangResponse::nummer, VorgangResponse::firma, VorgangResponse::ansprechpartner)
        .containsExactly(
            12L,
            new ZuordnungResponse(firmaId, "Adler AG", true),
            new ZuordnungResponse(partnerId, "Max Mueller", true));
    assertThat(gelesen.historie())
        .extracting(EintragResponse::art, EintragResponse::herkunft)
        .containsExactly(
            tuple(Eintragsart.ANHANG, Herkunft.VON_HAND),
            tuple(Eintragsart.KOMMENTAR, Herkunft.VON_HAND));
  }

  @Test
  void detail_givenARetiredFirmaAndAnsprechpartner_thenBothStayVisibleAndMarked() {
    // Given — Kriterium 23.
    final long firmaId = firma("Adler AG");
    final long partnerId = partner(firmaId, false);
    final long id = vorgang(12L, "Website-Relaunch", firmaId, partnerId, false, ANGELEGT);
    firmen.save(Objects.requireNonNull(firmen.findById(firmaId).orElse(null)).stillgelegt(SPAET));

    // When
    final VorgangResponse gelesen = detail(id);

    // Then
    assertThat(gelesen)
        .extracting(a -> a.firma().aktiv(), a -> a.ansprechpartner().aktiv())
        .containsExactly(false, false);
  }

  @Test
  void detail_givenAnUnknownId_thenAnswersNotFound() {
    // When
    final ResponseEntity<String> antwort = hole("/api/vorgaenge/4711", String.class);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void derFirma_thenSeparatesOpenFromClosedVorgaenge() {
    // Given — Kriterium 12.
    final long firmaId = firma("Adler AG");
    final long fremdeFirma = firma("Baum GmbH");
    final long offen = vorgang(1L, "Website-Relaunch", firmaId, null, false, SPAET);
    final long geschlossen = vorgang(2L, "Schulung", firmaId, null, true, FRUEH);
    vorgang(3L, "Fremder Vorgang", fremdeFirma, null, false, SPAET);

    // When
    final VorgaengeDerFirmaResponse liste = derFirma(firmaId);

    // Then
    assertThat(liste.offene()).extracting(VorgangZeileResponse::id).containsExactly(offen);
    assertThat(liste.abgeschlossene())
        .extracting(VorgangZeileResponse::id)
        .containsExactly(geschlossen);
  }

  @Test
  void derFirma_givenAFirmaWithoutAnyVorgang_thenTwoEmptyLists() {
    // Given — Kriterium 12.
    final long firmaId = firma("Adler AG");

    // When
    final VorgaengeDerFirmaResponse liste = derFirma(firmaId);

    // Then
    assertThat(liste).isEqualTo(new VorgaengeDerFirmaResponse(List.of(), List.of()));
  }

  @ParameterizedTest(name = "{0} ohne Sitzung")
  @ValueSource(strings = {"/api/vorgaenge", "/api/vorgaenge/1", "/api/firmen/1/vorgaenge"})
  void jederLeseweg_givenNoSession_thenAnswersUnauthorized(final String pfad) {
    // When — ohne Sitzungs-Cookie.
    final ResponseEntity<String> antwort = rest.getForEntity(pfad, String.class);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }
}
