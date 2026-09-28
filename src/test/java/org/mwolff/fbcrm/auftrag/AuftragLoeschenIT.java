package org.mwolff.fbcrm.auftrag;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.auftrag.web.AngebotAuftragResponse;
import org.mwolff.fbcrm.auftrag.web.AuftragResponse;
import org.mwolff.fbcrm.auth.domain.AccountRepository;
import org.mwolff.fbcrm.auth.domain.PasswordHasher;
import org.mwolff.fbcrm.vorgang.application.VorgangLesenUseCase;
import org.mwolff.fbcrm.vorgang.domain.Phase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Das Loeschen eines Auftrags ueber HTTP, gegen eine echte PostgreSQL-Instanz (Kriterien 3, 10, 15;
 * F9).
 *
 * <p>Vier Folgen der Loeschung entstehen ohne eigenes Zutun, und genau darum stehen sie hier: Die
 * Positionen verschwinden mit der Zeile, das Angebot bleibt {@code ANGENOMMEN} und bekommt {@code
 * anlegbar} zurueck (folgt aus Plan E3), die Phase des Vorgangs faellt auf „Angebot" zurueck (folgt
 * aus Plan E2), und die Nummer bleibt verbraucht — der naechste Auftrag traegt die folgende
 * (Kriterium 3, Plan E6). Wer sie nicht belegt, merkt erst spaeter, dass eine davon nicht gilt.
 *
 * <p>Geprueft wird nichts ausser der Existenz (Plan E15): Die Bedingung „solange weder Zeiten noch
 * Rechnungen daran haengen" ist bis zu den Ideen #6 und #7 immer erfuellt.
 */
class AuftragLoeschenIT extends AuftragHttpBasis {

  private final VorgangLesenUseCase vorgaenge;

  @Autowired
  AuftragLoeschenIT(
      final TestRestTemplate rest,
      final AngebotRepository angebote,
      final AccountRepository accounts,
      final PasswordHasher hasher,
      final TransactionTemplate transaktion,
      final JdbcTemplate jdbc,
      final VorgangLesenUseCase vorgaenge) {
    super(rest, angebote, accounts, hasher, transaktion, jdbc);
    this.vorgaenge = vorgaenge;
  }

  private ResponseEntity<String> loesche(final long auftragId) {
    return ruf("/api/auftraege/" + auftragId, HttpMethod.DELETE, null, String.class);
  }

  private long zaehle(final String tabelle) {
    return Objects.requireNonNull(
            jdbc.queryForObject("SELECT count(*) FROM " + tabelle, Long.class))
        .longValue();
  }

  @Test
  void loeschen_thenTheAuftragItsPositionenAndNothingElseAreGone() {
    // Given — Kriterium 15.
    final AuftragResponse angelegt = legeAuftragAn(angebotId);

    // When
    final ResponseEntity<String> antwort = loesche(angelegt.id());

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(zaehle("auftrag")).isZero();
    assertThat(zaehle("auftrag_position")).isZero();
    assertThat(
            ruf("/api/auftraege/" + angelegt.id(), HttpMethod.GET, null, String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void loeschen_thenTheHistorieCarriesTheGeloeschtEreignis() {
    // Given — Kriterium 8: die Zeile entsteht in derselben Transaktion wie die Loeschung.
    final AuftragResponse angelegt = legeAuftragAn(angebotId);

    // When
    loesche(angelegt.id());

    // Then
    assertThat(
            jdbc.queryForList(
                "SELECT text FROM vorgang_eintrag WHERE vorgang_id = ? AND art = 'EREIGNIS'"
                    + " ORDER BY id",
                String.class,
                Long.valueOf(vorgangId)))
        .containsExactly(
            "Auftrag " + angelegt.nummer() + " angelegt",
            "Auftrag " + angelegt.nummer() + " geloescht");
  }

  @Test
  void loeschen_thenTheAngebotStaysAngenommenAndBecomesAnlegbarAgain() {
    // Given — F9, folgt aus Plan E3.
    loesche(legeAuftragAn(angebotId).id());

    // When
    final AngebotAuftragResponse amAngebot =
        Objects.requireNonNull(
            ruf(
                    "/api/angebote/" + angebotId + "/auftrag",
                    HttpMethod.GET,
                    null,
                    AngebotAuftragResponse.class)
                .getBody());

    // Then
    assertThat(amAngebot.auftrag()).isNull();
    assertThat(amAngebot.anlegbar()).isTrue();
    assertThat(
            jdbc.queryForObject(
                "SELECT zustand FROM angebot WHERE id = ?", String.class, Long.valueOf(angebotId)))
        .isEqualTo("ANGENOMMEN");
  }

  @Test
  void loeschen_thenThePhaseOfTheVorgangFallsBackToAngebot() {
    // Given — Kriterium 10, folgt aus Plan E2.
    final AuftragResponse angelegt = legeAuftragAn(angebotId);
    assertThat(vorgaenge.lese(vorgangId).phase()).isEqualTo(Phase.AUFTRAG);

    // When
    loesche(angelegt.id());

    // Then
    assertThat(vorgaenge.lese(vorgangId).phase()).isEqualTo(Phase.ANGEBOT);
  }

  @Test
  void loeschen_thenTheNummerStaysConsumedAndTheNextAuftragCarriesTheFollowing() {
    // Given — Kriterium 3, Plan E6: eine Loeschung reisst eine Luecke, und das ist erlaubt.
    final AuftragResponse erster = legeAuftragAn(angebotId);
    loesche(erster.id());

    // When
    final AuftragResponse zweiter = legeAuftragAn(zweitesAngebotId);

    // Then
    assertThat(erster.nummer()).endsWith("-001");
    assertThat(zweiter.nummer()).endsWith("-002");
  }

  @Test
  void loeschen_onAClosedVorgang_thenStillWorks() {
    // Given — Plan E14: die Sperre am abgeschlossenen Vorgang reicht nur bis zum Anlegen.
    final AuftragResponse angelegt = legeAuftragAn(angebotId);
    jdbc.update("UPDATE vorgang SET abgeschlossen = true WHERE id = ?", Long.valueOf(vorgangId));

    // When / Then
    assertThat(loesche(angelegt.id()).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  }

  @Test
  void loeschen_givenAnUnknownAuftrag_thenAnswers404() {
    // When / Then
    assertThat(loesche(999L).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }
}
