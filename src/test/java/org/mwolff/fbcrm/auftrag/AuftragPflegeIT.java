package org.mwolff.fbcrm.auftrag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.auftrag.domain.Auftragsstatus;
import org.mwolff.fbcrm.auftrag.web.AuftragResponse;
import org.mwolff.fbcrm.auth.domain.AccountRepository;
import org.mwolff.fbcrm.auth.domain.PasswordHasher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Das Pflegen eines Auftrags ueber HTTP, gegen eine echte PostgreSQL-Instanz (Kriterien 7, 8, 9,
 * 11).
 *
 * <p>Drei Zusagen haengen an der echten Transaktion und am echten Schema und sind darum nur hier
 * pruefbar. Erstens die Historie: Ein Statuswechsel schreibt genau eine Zeile mit Zeitpunkt, zwei
 * Wechsel schreiben zwei, und ein {@code PUT} ohne Wechsel schreibt keine (Plan E9). Zweitens ihre
 * Unveraenderlichkeit: Die geschriebene Zeile traegt die Art {@code EREIGNIS} und laesst sich ueber
 * den Eintragsweg nicht mehr aendern. Drittens der zweite Riegel des Leistungszeitraums (Plan E21):
 * Der {@code CHECK} im Schema weist einen halben Zeitraum auch dann ab, wenn die Bean Validation
 * umgangen wird.
 *
 * <p>Dazu Plan E14: Die Sperre am abgeschlossenen Vorgang reicht nur bis zum Anlegen — Pflege und
 * Statuswechsel bleiben offen.
 */
class AuftragPflegeIT extends AuftragHttpBasis {

  @Autowired
  AuftragPflegeIT(
      final TestRestTemplate rest,
      final AngebotRepository angebote,
      final AccountRepository accounts,
      final PasswordHasher hasher,
      final TransactionTemplate transaktion,
      final JdbcTemplate jdbc) {
    super(rest, angebote, accounts, hasher, transaktion, jdbc);
  }

  private List<Map<String, Object>> ereignisse() {
    return jdbc.queryForList(
        "SELECT text, geschehen_am FROM vorgang_eintrag WHERE vorgang_id = ? AND art = 'EREIGNIS'"
            + " ORDER BY id",
        Long.valueOf(vorgangId));
  }

  private ResponseEntity<AuftragResponse> pflege(
      final long auftragId, final String status, final String bestellnummer) {
    return ruf(
        "/api/auftraege/" + auftragId,
        HttpMethod.PUT,
        pflegerumpf(status, bestellnummer),
        AuftragResponse.class);
  }

  @Test
  void pflegen_thenCarriesAllFourFieldsBack() {
    // Given — Kriterium 7.
    final long auftragId = legeAuftragAn(angebotId).id();

    // When
    final ResponseEntity<AuftragResponse> antwort = pflege(auftragId, "IN_ARBEIT", "BST-0815");

    // Then
    final AuftragResponse auftrag = Objects.requireNonNull(antwort.getBody());
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(auftrag.status()).isEqualTo(Auftragsstatus.IN_ARBEIT);
    assertThat(auftrag.kundenbestellnummer()).isEqualTo("BST-0815");
    assertThat(auftrag.positionen()).hasSize(1);
  }

  @Test
  void pflegen_withTwoStatusChanges_thenTheHistorieCarriesTwoRowsWithTheirZeitpunkt() {
    // Given — Kriterium 8, Plan E9. Die Anlage schreibt die erste Zeile, die Wechsel die beiden
    // folgenden.
    final AuftragResponse angelegt = legeAuftragAn(angebotId);

    // When
    pflege(angelegt.id(), "IN_ARBEIT", null);
    pflege(angelegt.id(), "ABGESCHLOSSEN", null);

    // Then
    assertThat(ereignisse())
        .extracting(zeile -> zeile.get("text"))
        .containsExactly(
            "Auftrag " + angelegt.nummer() + " angelegt",
            "Auftrag " + angelegt.nummer() + " auf „in Arbeit\" gesetzt",
            "Auftrag " + angelegt.nummer() + " auf „abgeschlossen\" gesetzt");
    assertThat(ereignisse()).allSatisfy(zeile -> assertThat(zeile.get("geschehen_am")).isNotNull());
  }

  @Test
  void pflegen_withoutAStatusChange_thenTheHistorieGrowsNoFurther() {
    // Given — Plan E9: ein PUT, das nur die Bestellnummer aendert, ist kein Ereignis.
    final long auftragId = legeAuftragAn(angebotId).id();

    // When
    pflege(auftragId, "OFFEN", "BST-0815");
    pflege(auftragId, "OFFEN", "BST-0816");

    // Then — nur die Zeile der Anlage steht da.
    assertThat(ereignisse()).hasSize(1);
  }

  @Test
  void pflegen_thenTheEreignisOfTheStatusChangeCannotBeChanged() {
    // Given — R2: die Historie haelt fest, was geschehen ist.
    final long auftragId = legeAuftragAn(angebotId).id();
    pflege(auftragId, "IN_ARBEIT", null);
    final long eintragId =
        Objects.requireNonNull(
                jdbc.queryForObject(
                    "SELECT id FROM vorgang_eintrag WHERE art = 'EREIGNIS' ORDER BY id DESC"
                        + " LIMIT 1",
                    Long.class))
            .longValue();

    // When
    final ResponseEntity<String> antwort =
        ruf(
            "/api/vorgaenge/" + vorgangId + "/eintraege/" + eintragId,
            HttpMethod.PUT,
            Map.of("text", "etwas anderes", "geschehenAm", "2026-09-28T10:00:00Z"),
            String.class);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void pflegen_onAClosedVorgang_thenStillWorks() {
    // Given — Plan E14, Kriterium 11: die Sperre reicht nur bis zum Anlegen.
    final long auftragId = legeAuftragAn(angebotId).id();
    jdbc.update("UPDATE vorgang SET abgeschlossen = true WHERE id = ?", Long.valueOf(vorgangId));

    // When / Then
    assertThat(pflege(auftragId, "ABGESCHLOSSEN", null).getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void schema_givenAHalfLeistungszeitraumBesideTheBeanValidation_thenRejects() {
    // Given — Plan E21, zweiter Riegel: der CHECK im Schema steht unabhaengig von der Maske.
    final long auftragId = legeAuftragAn(angebotId).id();

    // When / Then
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE auftrag SET leistung_bis = NULL WHERE id = ?", Long.valueOf(auftragId)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void auftraegeDesVorgangs_thenAnswersTheRowsWithTheYoungestFirst() {
    // Given — Kriterium 9.
    legeAuftragAn(angebotId);
    final AuftragResponse zweiter = legeAuftragAn(zweitesAngebotId);

    // When
    final ResponseEntity<String> antwort =
        ruf("/api/vorgaenge/" + vorgangId + "/auftraege", HttpMethod.GET, null, String.class);

    // Then — gleicher Tag, also entscheidet die hoehere Kennung (Plan E13).
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(Objects.requireNonNull(antwort.getBody()))
        .contains("\"nummer\":\"" + zweiter.nummer() + "\"")
        .contains("\"summe\":2500.03");
  }

  @Test
  void jederWeg_withoutASession_thenAnswersUnauthorized() {
    // When / Then — die Zugangsregel gilt fuer jeden neuen Pfad unter /api (K1).
    assertThat(
            rest.exchange("/api/auftraege/1", HttpMethod.PUT, HttpEntity.EMPTY, String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(
            rest.exchange("/api/auftraege/1", HttpMethod.DELETE, HttpEntity.EMPTY, String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(
            rest.exchange(
                    "/api/vorgaenge/" + vorgangId + "/auftraege",
                    HttpMethod.GET,
                    HttpEntity.EMPTY,
                    String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }
}
