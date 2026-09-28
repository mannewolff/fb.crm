package org.mwolff.fbcrm.angebot;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.angebot.domain.Angebotsposition;
import org.mwolff.fbcrm.angebot.domain.Angebotszustand;
import org.mwolff.fbcrm.angebot.domain.Belegabsender;
import org.mwolff.fbcrm.angebot.domain.Belegempfaenger;
import org.mwolff.fbcrm.common.Abrechnungsmodus;
import org.mwolff.fbcrm.common.Anschrift;
import org.mwolff.fbcrm.common.Einheit;
import org.mwolff.fbcrm.vorgang.application.VorgaengeUebersicht;
import org.mwolff.fbcrm.vorgang.application.VorgaengeUebersichtUseCase;
import org.mwolff.fbcrm.vorgang.application.VorgangLesenUseCase;
import org.mwolff.fbcrm.vorgang.application.VorgangZeile;
import org.mwolff.fbcrm.vorgang.domain.Phase;
import org.mwolff.fbcrm.vorgang.domain.Vorgang;
import org.mwolff.fbcrm.vorgang.domain.VorgangRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Kriterium 22: Die Phase des Vorgangs folgt seinen Angeboten — abgeleitet und nicht gepflegt (R1).
 *
 * <p>Der Weg dorthin ist die Abhaengigkeitsumkehr aus Plan E2: {@code vorgang.domain} schreibt den
 * Port {@code Belegstand} aus, {@code angebot.application} setzt ihn um. Dieser Test laeuft deshalb
 * durch den echten Anwendungskontext gegen eine echte PostgreSQL-Instanz — nur dort steht die
 * Auswahlregel des Bestands wirklich auf dem Pruefstand.
 *
 * <p><b>Die Auswahl ist „festgeschrieben", nicht „versendet".</b> Ein angenommenes Angebot traegt
 * seine Nummer und sein Dokument weiter; die Phase bleibt darum „Angebot" (F6). Ein reiner Entwurf
 * hat beides nicht und aendert an der Phase nichts.
 */
class PhaseAbleitungIT extends AbstractIntegrationTest {

  private static final LocalDate ANGEBOTSDATUM = LocalDate.of(2026, 9, 20);
  private static final LocalDate GUELTIG_BIS = LocalDate.of(2026, 10, 20);
  private static final Instant ANGELEGT = Instant.parse("2026-09-20T08:00:00Z");
  private static final Instant VERSANDZEITPUNKT = Instant.parse("2026-09-21T09:00:00Z");
  private static final Instant REAKTION = Instant.parse("2026-09-22T11:00:00Z");
  private static final String NUMMER = "A-2026-001";
  private static final String PDF_SCHLUESSEL = "angebot/1/6f1c9a.pdf";

  private static final Belegempfaenger EMPFAENGER =
      new Belegempfaenger(
          "Adler AG",
          new Anschrift("Hauptstrasse 1", "28195", "Bremen", "Deutschland"),
          "Frau Adler");

  private static final Belegabsender ABSENDER =
      new Belegabsender(
          "Manfred Wolff",
          new Anschrift("Am Deich 2", "28199", "Hansestadt", "Bundesrepublik"),
          "manne@example.org",
          "0421 123456",
          "75/123/45678",
          "DE123456789",
          "IBAN DE00 1234");

  private static final Angebotsposition KONZEPTION =
      new Angebotsposition(
          "Konzeption",
          Abrechnungsmodus.AUFWAND,
          new BigDecimal("2.50"),
          Einheit.PERSONENTAG,
          new BigDecimal("1000.01"));

  private final AngebotRepository angebote;
  private final VorgangRepository vorgaenge;
  private final VorgangLesenUseCase lesen;
  private final VorgaengeUebersichtUseCase uebersicht;
  private final TransactionTemplate transaktion;
  private final JdbcTemplate jdbc;

  private long firmaId;

  @Autowired
  PhaseAbleitungIT(
      final AngebotRepository angebote,
      final VorgangRepository vorgaenge,
      final VorgangLesenUseCase lesen,
      final VorgaengeUebersichtUseCase uebersicht,
      final TransactionTemplate transaktion,
      final JdbcTemplate jdbc) {
    this.angebote = angebote;
    this.vorgaenge = vorgaenge;
    this.lesen = lesen;
    this.uebersicht = uebersicht;
    this.transaktion = transaktion;
    this.jdbc = jdbc;
  }

  @BeforeEach
  void leereFachtabellenUndLegeEineFirmaAn() {
    jdbc.execute(
        "TRUNCATE angebot_position, angebot, vorgang_eintrag, vorgang, ansprechpartner, firma"
            + " RESTART IDENTITY CASCADE");
    jdbc.update("INSERT INTO firma (name) VALUES ('Adler AG')");
    firmaId =
        jdbc.queryForObject("SELECT id FROM firma WHERE name = 'Adler AG'", Long.class).longValue();
  }

  private long vorgang(final long nummer, final String titel) {
    return vorgaenge
        .save(
            new Vorgang(null, nummer, titel, firmaId, null, null, null, false, ANGELEGT, ANGELEGT))
        .requireId();
  }

  private Angebot entwurf(final long vorgangId) {
    return new Angebot(
        null,
        vorgangId,
        null,
        Angebotszustand.ENTWURF,
        ANGEBOTSDATUM,
        GUELTIG_BIS,
        "Neugestaltung der Website",
        "Zahlbar innerhalb von 14 Tagen ohne Abzug.",
        null,
        null,
        null,
        null,
        null,
        List.of(KONZEPTION),
        ANGELEGT,
        ANGELEGT);
  }

  private void schreibe(final Angebot angebot) {
    transaktion.executeWithoutResult(status -> angebote.save(angebot));
  }

  private Phase phaseDerDetailansicht(final long vorgangId) {
    return lesen.lese(vorgangId).phase();
  }

  private Phase phaseDerUebersicht(final long vorgangId) {
    final VorgaengeUebersicht liste = uebersicht.uebersicht("", true);
    return liste.zeilen().stream()
        .filter(zeile -> zeile.id() == vorgangId)
        .map(VorgangZeile::phase)
        .findFirst()
        .orElseThrow();
  }

  @Test
  void phase_givenAVorgangWithASentOffer_thenAngebot() {
    // Given — Kriterium 22.
    final long vorgangId = vorgang(1L, "Website-Relaunch");
    schreibe(
        entwurf(vorgangId)
            .versendet(NUMMER, EMPFAENGER, ABSENDER, PDF_SCHLUESSEL, VERSANDZEITPUNKT));

    // When / Then
    assertThat(phaseDerDetailansicht(vorgangId)).isEqualTo(Phase.ANGEBOT);
  }

  @Test
  void phase_givenAVorgangWithASentOffer_thenTheOverviewShowsItToo() {
    // Given — dieselbe Ableitung in der Liste, nicht nur in der Detailansicht.
    final long vorgangId = vorgang(1L, "Website-Relaunch");
    schreibe(
        entwurf(vorgangId)
            .versendet(NUMMER, EMPFAENGER, ABSENDER, PDF_SCHLUESSEL, VERSANDZEITPUNKT));

    // When / Then
    assertThat(phaseDerUebersicht(vorgangId)).isEqualTo(Phase.ANGEBOT);
  }

  @Test
  void phase_givenAVorgangWithAnAcceptedOffer_thenStaysAngebot() {
    // Given — F6: ein angenommenes Angebot ist ebenso festgeschrieben wie ein versendetes.
    final long vorgangId = vorgang(1L, "Website-Relaunch");
    schreibe(
        entwurf(vorgangId)
            .versendet(NUMMER, EMPFAENGER, ABSENDER, PDF_SCHLUESSEL, VERSANDZEITPUNKT)
            .angenommen(REAKTION));

    // When / Then
    assertThat(phaseDerDetailansicht(vorgangId)).isEqualTo(Phase.ANGEBOT);
  }

  @Test
  void phase_givenAVorgangWithOnlyADraft_thenAnbahnung() {
    // Given — F6: ein Entwurf traegt keine Nummer und kein Dokument.
    final long vorgangId = vorgang(1L, "Website-Relaunch");
    schreibe(entwurf(vorgangId));

    // When / Then
    assertThat(phaseDerDetailansicht(vorgangId)).isEqualTo(Phase.ANBAHNUNG);
  }

  @Test
  void phase_givenAVorgangWithoutAnyOffer_thenAnbahnung() {
    // Given
    final long vorgangId = vorgang(1L, "Website-Relaunch");

    // When / Then
    assertThat(phaseDerDetailansicht(vorgangId)).isEqualTo(Phase.ANBAHNUNG);
  }

  @Test
  void phase_givenTwoVorgaenge_thenOnlyTheOneWithACommittedOfferMovesOn() {
    // Given — die Auswahl trifft genau den Vorgang, an dem das Angebot haengt.
    final long mitAngebot = vorgang(1L, "Website-Relaunch");
    final long ohneAngebot = vorgang(2L, "Schulung");
    schreibe(
        entwurf(mitAngebot)
            .versendet(NUMMER, EMPFAENGER, ABSENDER, PDF_SCHLUESSEL, VERSANDZEITPUNKT));

    // When / Then
    assertThat(phaseDerUebersicht(mitAngebot)).isEqualTo(Phase.ANGEBOT);
    assertThat(phaseDerUebersicht(ohneAngebot)).isEqualTo(Phase.ANBAHNUNG);
  }
}
