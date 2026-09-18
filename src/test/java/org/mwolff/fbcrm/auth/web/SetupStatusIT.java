package org.mwolff.fbcrm.auth.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * Die Statusauskunft der Einrichtung (E11).
 *
 * <p>Sie ist der einzige Endpunkt, den eine noch nicht eingerichtete Instanz ohne Sitzung
 * beantworten muss — sonst wuesste die Oberflaeche nicht, ob sie zur Anmeldung oder zur Einrichtung
 * fuehren soll.
 *
 * <p>Der schaerfste Nachweis hier ist die <b>Zahl der Felder</b>: Die Antwort geht an jeden, der
 * den Pfad kennt. Ein zweites Feld — Version, Adresse des Betreibers, Zeitpunkt der Einrichtung —
 * waere eine Auskunft an jeden Scanner im Netz.
 */
@TestPropertySource(properties = "fbcrm.setup.bootstrap-token=" + SetupControllerIT.SCHLUESSEL)
class SetupStatusIT extends AbstractIntegrationTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  private final TestRestTemplate rest;
  private final JdbcTemplate jdbc;

  @Autowired
  SetupStatusIT(final TestRestTemplate rest, final JdbcTemplate jdbc) {
    this.rest = rest;
    this.jdbc = jdbc;
  }

  @BeforeEach
  void leereDieDatenbank() {
    jdbc.execute("TRUNCATE outbox_message, password_reset_token, account RESTART IDENTITY CASCADE");
  }

  private ResponseEntity<String> status() {
    return rest.getForEntity("/api/setup/status", String.class);
  }

  private Map<String, Object> statusRumpf() throws Exception {
    return JSON.readValue(String.valueOf(status().getBody()), new TypeReference<>() {});
  }

  private void richteEin() {
    SetupControllerIT.richteEin(
        rest,
        SetupControllerIT.rumpf(
            SetupControllerIT.MAIL,
            SetupControllerIT.MAIL,
            SetupControllerIT.PASSWORT,
            SetupControllerIT.SCHLUESSEL));
  }

  @Test
  void status_withoutASession_thenIsReachable() {
    // When — E11: die Oberflaeche fragt, bevor sich jemand anmelden kann.
    final ResponseEntity<String> antwort = status();

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void status_givenAFreshInstance_thenSaysNotInitialized() throws Exception {
    // When
    final Map<String, Object> rumpf = statusRumpf();

    // Then
    assertThat(rumpf).containsEntry("initialized", false);
  }

  @Test
  void status_givenAnInstanceThatIsSetUp_thenSaysInitialized() throws Exception {
    // Given
    richteEin();

    // When
    final Map<String, Object> rumpf = statusRumpf();

    // Then
    assertThat(rumpf).containsEntry("initialized", true);
  }

  @Test
  void status_givenAFreshInstance_thenCarriesExactlyOneField() throws Exception {
    // When
    final Map<String, Object> rumpf = statusRumpf();

    // Then — E11: genau ein Feld, kein weiteres.
    assertThat(rumpf).containsOnlyKeys("initialized");
  }

  @Test
  void status_givenAnInstanceThatIsSetUp_thenStillCarriesExactlyOneField() throws Exception {
    // Given — auch nach der Einrichtung faellt kein Feld vom Konto in die Antwort.
    richteEin();

    // When
    final Map<String, Object> rumpf = statusRumpf();

    // Then
    assertThat(rumpf).containsOnlyKeys("initialized");
  }
}
