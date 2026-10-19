package org.mwolff.fbcrm.rechnung;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.mwolff.fbcrm.auth.domain.Account;
import org.mwolff.fbcrm.auth.domain.AccountRepository;
import org.mwolff.fbcrm.auth.domain.PasswordHasher;
import org.mwolff.fbcrm.auth.domain.Role;
import org.mwolff.fbcrm.rechnung.web.BriefbogenResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Stand und Pflege des Briefbogens ueber HTTP, mit Sitzung und gegen eine echte PostgreSQL-Instanz
 * (fachliche Quelle #361, Kriterien 13, 15 und 16; Plan #363, E1 und E15).
 *
 * <p>Eine frische Instanz liefert die Vorgaben der Migration und beide Schalter an, ohne einen
 * Schluessel des Objektspeichers; ein {@code PUT} steht anschliessend wirklich im Bestand, und ein
 * abgewiesener laesst die bisherige Zeile unberuehrt.
 *
 * <p>Die eine Zeile wird vor jeder Methode auf den Stand der Migration zurueckgesetzt; die
 * Datenbank der Suite ist geteilt.
 */
class BriefpapierIT extends AbstractIntegrationTest {

  private static final String PFAD = "/api/briefpapier";
  private static final String MAIL = "manne@example.org";
  private static final String PASSWORT = "richtiges-passwort";
  private static final Instant ANGELEGT = Instant.parse("2026-09-01T08:00:00Z");

  private final TestRestTemplate rest;
  private final AccountRepository accounts;
  private final PasswordHasher hasher;
  private final JdbcTemplate jdbc;

  private HttpHeaders sitzung = new HttpHeaders();

  @Autowired
  BriefpapierIT(
      final TestRestTemplate rest,
      final AccountRepository accounts,
      final PasswordHasher hasher,
      final JdbcTemplate jdbc) {
    this.rest = rest;
    this.accounts = accounts;
    this.hasher = hasher;
    this.jdbc = jdbc;
  }

  @BeforeEach
  void stelleDenStandDerMigrationHerUndMeldeAn() {
    jdbc.execute("DELETE FROM briefbogen");
    jdbc.execute("INSERT INTO briefbogen DEFAULT VALUES");
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

  private <T> ResponseEntity<T> ruf(
      final HttpMethod methode, final Object rumpf, final Class<T> typ) {
    return rest.exchange(PFAD, methode, new HttpEntity<>(rumpf, sitzung), typ);
  }

  private BriefbogenResponse lies() {
    return Objects.requireNonNull(ruf(HttpMethod.GET, null, BriefbogenResponse.class).getBody());
  }

  private static Map<String, Object> satz(
      final boolean absenderzeile,
      final boolean steuerBankZeile,
      final int links,
      final int rechts,
      final int inhaltOben,
      final int unten,
      final int folgeOben,
      final int folgeUnten) {
    return Map.of(
        "absenderzeile", absenderzeile,
        "steuerBankZeile", steuerBankZeile,
        "links", links,
        "rechts", rechts,
        "inhaltOben", inhaltOben,
        "unten", unten,
        "folgeOben", folgeOben,
        "folgeUnten", folgeUnten);
  }

  @Test
  void lesen_givenTheFreshInstance_thenAnswersWithTheDefaultsAndNoKey() {
    // When
    final ResponseEntity<String> roh = ruf(HttpMethod.GET, null, String.class);

    // Then — Kriterium 15: 25, 20, 98,5, 20, 20, 20 mm; beide Schalter an (E15).
    assertThat(roh.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(roh.getBody()).doesNotContainIgnoringCase("schluessel");
    assertThat(lies())
        .isEqualTo(
            new BriefbogenResponse(
                false, null, null, null, true, true, 250, 200, 985, 200, 200, 200));
  }

  @Test
  void pflegen_thenTheNextReadAnswersWithTheStoredValues() {
    // When
    final ResponseEntity<BriefbogenResponse> antwort =
        ruf(
            HttpMethod.PUT,
            satz(false, true, 300, 150, 1_000, 250, 180, 220),
            BriefbogenResponse.class);

    // Then
    final BriefbogenResponse erwartet =
        new BriefbogenResponse(
            false, null, null, null, false, true, 300, 150, 1_000, 250, 180, 220);
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(antwort.getBody()).isEqualTo(erwartet);
    assertThat(lies()).isEqualTo(erwartet);
  }

  @Test
  void pflegen_givenAnImpossibleMargin_thenAnswersWith422AndKeepsTheRow() {
    // When — Kriterium 13: der untere Rand traegt hoechstens 70,0 mm.
    final ResponseEntity<String> antwort =
        ruf(HttpMethod.PUT, satz(false, true, 250, 200, 985, 701, 200, 200), String.class);

    // Then — auch der Schalter, der fuer sich gepasst haette, ist nicht geschrieben.
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(antwort.getBody()).contains("70,0 mm");
    assertThat(lies())
        .isEqualTo(
            new BriefbogenResponse(
                false, null, null, null, true, true, 250, 200, 985, 200, 200, 200));
  }
}
