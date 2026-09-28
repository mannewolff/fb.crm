package org.mwolff.fbcrm.auftrag;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.angebot.domain.Angebotsposition;
import org.mwolff.fbcrm.angebot.domain.Angebotszustand;
import org.mwolff.fbcrm.angebot.domain.Belegabsender;
import org.mwolff.fbcrm.angebot.domain.Belegempfaenger;
import org.mwolff.fbcrm.auftrag.domain.Auftrag;
import org.mwolff.fbcrm.auftrag.domain.AuftragRepository;
import org.mwolff.fbcrm.auftrag.domain.Auftragsposition;
import org.mwolff.fbcrm.auftrag.domain.Auftragsstatus;
import org.mwolff.fbcrm.common.Abrechnungsmodus;
import org.mwolff.fbcrm.common.Anschrift;
import org.mwolff.fbcrm.common.Einheit;
import org.mwolff.fbcrm.vorgang.application.VorgaengeDerFirma;
import org.mwolff.fbcrm.vorgang.application.VorgaengeDerFirmaUseCase;
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
 * Kriterium 10: Die Phase des Vorgangs steigt mit dem Auftrag — abgeleitet und nicht gepflegt (R1).
 *
 * <p>Mit {@code AuftragBelegstand} ist der Port aus Issue #117 erstmals <b>zweifach</b> besetzt,
 * und genau das ist hier der Gegenstand: An einem Vorgang mit Angebot <b>und</b> Auftrag haengen
 * zwei nachgewiesene Phasen, und {@code Vorgang.phase} nimmt das Maximum der Kette. Ein Auftrag
 * loescht das Angebot nicht, auf das er folgt — ohne die Maximumsregel gewaenne die zuletzt
 * gefragte Belegart.
 *
 * <p>Geprueft wird durch den echten Anwendungskontext gegen eine echte PostgreSQL-Instanz: Nur dort
 * steht die Auswahlregel des Bestands wirklich auf dem Pruefstand, und nur dort sind alle
 * Umsetzungen des Ports wirklich eingesammelt.
 *
 * <p><b>Alle drei Ansichten, nicht nur eine.</b> Detailansicht, Uebersicht und die Liste an der
 * Firma leiten die Phase auf drei Wegen ab; eine Ansicht, die es vergisst, faellt nur auf, wenn sie
 * gefragt wird.
 */
class PhaseAuftragIT extends AbstractIntegrationTest {

  private static final LocalDate ANGEBOTSDATUM = LocalDate.of(2026, 9, 20);
  private static final LocalDate GUELTIG_BIS = LocalDate.of(2026, 10, 20);
  private static final Instant ANGELEGT = Instant.parse("2026-09-20T08:00:00Z");
  private static final Instant VERSANDZEITPUNKT = Instant.parse("2026-09-21T09:00:00Z");
  private static final Instant REAKTION = Instant.parse("2026-09-22T11:00:00Z");
  private static final String PDF_SCHLUESSEL = "angebot/1/6f1c9a.pdf";

  private static final Belegempfaenger EMPFAENGER =
      new Belegempfaenger(
          "Adler AG", new Anschrift("Hauptstrasse 1", "28195", "Bremen", "Deutschland"), null);

  private static final Belegabsender ABSENDER =
      new Belegabsender(
          "Manfred Wolff",
          new Anschrift("Am Deich 2", "28199", "Hansestadt", "Bundesrepublik"),
          null,
          null,
          null,
          null,
          null);

  private static final Angebotsposition KONZEPTION =
      new Angebotsposition(
          "Konzeption",
          Abrechnungsmodus.AUFWAND,
          new BigDecimal("2.50"),
          Einheit.PERSONENTAG,
          new BigDecimal("1000.01"));

  private final AngebotRepository angebote;
  private final AuftragRepository auftraege;
  private final VorgangRepository vorgaenge;
  private final VorgangLesenUseCase lesen;
  private final VorgaengeUebersichtUseCase uebersicht;
  private final VorgaengeDerFirmaUseCase derFirma;
  private final TransactionTemplate transaktion;
  private final JdbcTemplate jdbc;

  private long firmaId;

  @Autowired
  PhaseAuftragIT(
      final AngebotRepository angebote,
      final AuftragRepository auftraege,
      final VorgangRepository vorgaenge,
      final VorgangLesenUseCase lesen,
      final VorgaengeUebersichtUseCase uebersicht,
      final VorgaengeDerFirmaUseCase derFirma,
      final TransactionTemplate transaktion,
      final JdbcTemplate jdbc) {
    this.angebote = angebote;
    this.auftraege = auftraege;
    this.vorgaenge = vorgaenge;
    this.lesen = lesen;
    this.uebersicht = uebersicht;
    this.derFirma = derFirma;
    this.transaktion = transaktion;
    this.jdbc = jdbc;
  }

  @BeforeEach
  void leereFachtabellenUndLegeEineFirmaAn() {
    jdbc.execute(
        "TRUNCATE auftrag_position, auftrag, angebot_position, angebot, vorgang_eintrag, vorgang,"
            + " ansprechpartner, firma RESTART IDENTITY CASCADE");
    jdbc.update("INSERT INTO firma (name) VALUES ('Adler AG')");
    firmaId =
        Objects.requireNonNull(
                jdbc.queryForObject("SELECT id FROM firma WHERE name = 'Adler AG'", Long.class))
            .longValue();
  }

  private long vorgang(final long nummer, final String titel) {
    return vorgaenge
        .save(
            new Vorgang(null, nummer, titel, firmaId, null, null, null, false, ANGELEGT, ANGELEGT))
        .requireId();
  }

  /* Ein angenommenes Angebot am Vorgang — der einzige Zustand, aus dem ein Auftrag entsteht. */
  private long angenommenesAngebot(final long vorgangId, final String nummer) {
    final Angebot entwurf =
        new Angebot(
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
    return transaktion
        .execute(
            status ->
                angebote.save(
                    entwurf
                        .versendet(nummer, EMPFAENGER, ABSENDER, PDF_SCHLUESSEL, VERSANDZEITPUNKT)
                        .angenommen(REAKTION)))
        .requireId();
  }

  private void auftrag(final long vorgangId, final long angebotId, final String nummer) {
    transaktion.executeWithoutResult(
        status ->
            auftraege.save(
                new Auftrag(
                    null,
                    vorgangId,
                    angebotId,
                    nummer,
                    Auftragsstatus.OFFEN,
                    LocalDate.of(2026, 9, 28),
                    null,
                    null,
                    null,
                    List.of(
                        new Auftragsposition(
                            KONZEPTION.bezeichnung(),
                            KONZEPTION.abrechnungsmodus(),
                            KONZEPTION.menge(),
                            KONZEPTION.einheit(),
                            KONZEPTION.einzelpreis(),
                            new BigDecimal("7.50"))),
                    ANGELEGT,
                    ANGELEGT)));
  }

  private Phase phaseDerDetailansicht(final long vorgangId) {
    return lesen.lese(vorgangId).phase();
  }

  private Phase phaseDerUebersicht(final long vorgangId) {
    final VorgaengeUebersicht liste = uebersicht.uebersicht("", true);
    return phaseIn(liste.zeilen(), vorgangId);
  }

  private Phase phaseDerFirmenliste(final long vorgangId) {
    final VorgaengeDerFirma liste = derFirma.vorgaenge(firmaId);
    return phaseIn(liste.offene(), vorgangId);
  }

  private static Phase phaseIn(final List<VorgangZeile> zeilen, final long vorgangId) {
    return zeilen.stream()
        .filter(zeile -> zeile.id() == vorgangId)
        .map(VorgangZeile::phase)
        .findFirst()
        .orElseThrow();
  }

  @Test
  void phase_givenAVorgangWithAnAuftrag_thenAuftragInAllThreeViews() {
    // Given — Kriterium 10: zwei Belegarten am Port, und das Maximum der Kette gewinnt.
    final long vorgangId = vorgang(1L, "Website-Relaunch");
    auftrag(vorgangId, angenommenesAngebot(vorgangId, "A-2026-001"), "AU-2026-001");

    // When / Then
    assertThat(phaseDerDetailansicht(vorgangId)).isEqualTo(Phase.AUFTRAG);
    assertThat(phaseDerUebersicht(vorgangId)).isEqualTo(Phase.AUFTRAG);
    assertThat(phaseDerFirmenliste(vorgangId)).isEqualTo(Phase.AUFTRAG);
  }

  @Test
  void phase_givenAVorgangWithAnAngebotButNoAuftrag_thenStaysAngebot() {
    // Given — die zweite Belegart aendert an der ersten nichts.
    final long vorgangId = vorgang(2L, "Schulung");
    angenommenesAngebot(vorgangId, "A-2026-002");

    // When / Then
    assertThat(phaseDerDetailansicht(vorgangId)).isEqualTo(Phase.ANGEBOT);
    assertThat(phaseDerUebersicht(vorgangId)).isEqualTo(Phase.ANGEBOT);
    assertThat(phaseDerFirmenliste(vorgangId)).isEqualTo(Phase.ANGEBOT);
  }

  @Test
  void phase_givenBothVorgaenge_thenOnlyTheOneWithAnAuftragMovesOn() {
    // Given — die Auswahl trifft genau den Vorgang, an dem der Auftrag haengt.
    final long mitAuftrag = vorgang(1L, "Website-Relaunch");
    final long ohneAuftrag = vorgang(2L, "Schulung");
    auftrag(mitAuftrag, angenommenesAngebot(mitAuftrag, "A-2026-001"), "AU-2026-001");
    angenommenesAngebot(ohneAuftrag, "A-2026-002");

    // When / Then — eine Abfrage je Belegart fuer die ganze Liste, und trotzdem je Zeile richtig.
    assertThat(phaseDerUebersicht(mitAuftrag)).isEqualTo(Phase.AUFTRAG);
    assertThat(phaseDerUebersicht(ohneAuftrag)).isEqualTo(Phase.ANGEBOT);
  }
}
