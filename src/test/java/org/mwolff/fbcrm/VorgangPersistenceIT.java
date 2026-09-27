package org.mwolff.fbcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.common.Anschrift;
import org.mwolff.fbcrm.firma.domain.Firma;
import org.mwolff.fbcrm.firma.domain.FirmaRepository;
import org.mwolff.fbcrm.vorgang.application.AnhangLesenUseCase;
import org.mwolff.fbcrm.vorgang.application.EintragAnsicht;
import org.mwolff.fbcrm.vorgang.application.VorgangLesenUseCase;
import org.mwolff.fbcrm.vorgang.application.VorgangMitHistorie;
import org.mwolff.fbcrm.vorgang.domain.AnhangSpeicher;
import org.mwolff.fbcrm.vorgang.domain.Eintrag;
import org.mwolff.fbcrm.vorgang.domain.EintragRepository;
import org.mwolff.fbcrm.vorgang.domain.Eintragsart;
import org.mwolff.fbcrm.vorgang.domain.Herkunft;
import org.mwolff.fbcrm.vorgang.domain.Vorgang;
import org.mwolff.fbcrm.vorgang.domain.VorgangRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Kriterium 24: Vorgang, Historie, Anhang und Abschlussstand ueberstehen einen Neustart.
 *
 * <p>Der Neustart ist echt — es entsteht ein zweiter Anwendungskontext gegen dieselbe Datenbank und
 * denselben Objektspeicher, im Muster von {@link FirmaPersistenceIT}. Nur so ist belegt, dass
 * nichts davon im Speicher der ersten Instanz haengt: Ein zweiter Test im selben Kontext bewiese
 * allein, dass die Datenbank ein SELECT beantwortet.
 *
 * <p>Der Anhang geht dabei ueber beide Haelften des Stands: Die Zeile mit Name, Groesse und
 * Objektschluessel liegt in PostgreSQL, die Bytes in MinIO. Erst der Vergleich Byte fuer Byte
 * zeigt, dass beide Haelften den Neustart zusammen ueberstehen.
 */
class VorgangPersistenceIT extends AbstractIntegrationTest {

  private static final Instant ANGELEGT = Instant.parse("2026-09-01T08:00:00Z");
  private static final Instant VORGESTERN = Instant.parse("2026-09-10T09:00:00Z");
  private static final Instant GESTERN = Instant.parse("2026-09-11T14:30:00Z");
  private static final Instant ABGESCHLOSSEN_AM = Instant.parse("2026-09-12T09:00:00Z");
  private static final String TITEL = "Website-Relaunch";
  private static final String NEUES_GEHEIMNIS = "ein-zweites-geheimnis-mit-genug-zeichen-drin";
  private static final byte[] INHALT =
      "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8);

  private final VorgangRepository vorgaenge;
  private final EintragRepository eintraege;
  private final FirmaRepository firmen;
  private final AnhangSpeicher speicher;
  private final JdbcTemplate jdbc;

  private long vorgangId;
  private long anhangId;

  @Autowired
  VorgangPersistenceIT(
      final VorgangRepository vorgaenge,
      final EintragRepository eintraege,
      final FirmaRepository firmen,
      final AnhangSpeicher speicher,
      final JdbcTemplate jdbc) {
    this.vorgaenge = vorgaenge;
    this.eintraege = eintraege;
    this.firmen = firmen;
    this.speicher = speicher;
    this.jdbc = jdbc;
  }

  @BeforeEach
  void legeEinenAbgeschlossenenVorgangMitHistorieAn() {
    jdbc.execute(
        "TRUNCATE vorgang_eintrag, vorgang, ansprechpartner, firma RESTART IDENTITY CASCADE");
    final Vorgang angelegt =
        vorgaenge.save(new Vorgang(null, 1L, TITEL, neueFirma(), null, false, ANGELEGT, ANGELEGT));
    vorgangId = angelegt.requireId();
    eintraege.save(Eintrag.kommentar(vorgangId, "Angerufen", GESTERN, Herkunft.VON_HAND, ANGELEGT));
    anhangId = neuerAnhang();
    vorgaenge.save(angelegt.abgeschlossen(ABGESCHLOSSEN_AM));
  }

  private long neueFirma() {
    return firmen
        .save(
            new Firma(
                null,
                "Adler AG",
                new Anschrift("Am Wall 1", "28195", "Bremen", "Deutschland"),
                null,
                null,
                true,
                ANGELEGT,
                ANGELEGT))
        .requireId();
  }

  private long neuerAnhang() {
    final String schluessel =
        speicher.ablegen(vorgangId, new ByteArrayInputStream(INHALT), INHALT.length);
    return eintraege
        .save(
            Eintrag.anhang(
                vorgangId,
                null,
                VORGESTERN,
                Herkunft.VON_HAND,
                "Angebot.pdf",
                INHALT.length,
                schluessel,
                ANGELEGT))
        .requireId();
  }

  /**
   * Eine zweite Instanz gegen dieselbe Datenbank und denselben Objektspeicher.
   *
   * <p>Die Werte gehen als Kommandozeilenargumente hinein und nicht als {@code properties(...)} —
   * die Begruendung steht bei {@link FirmaPersistenceIT} und gilt hier unveraendert.
   */
  private static ConfigurableApplicationContext neueInstanz() {
    return new SpringApplicationBuilder(FbCrmApplication.class)
        .run(
            Stream.concat(
                    Stream.of(
                        "server.port=0",
                        "spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                        "spring.datasource.username=" + POSTGRES.getUsername(),
                        "spring.datasource.password=" + POSTGRES.getPassword(),
                        "fbcrm.auth.session-secret=" + NEUES_GEHEIMNIS),
                    Stream.of(objektspeicherSchalter()))
                .map(eintrag -> "--" + eintrag)
                .toArray(String[]::new));
  }

  @Test
  void lesen_afterRestartingTheInstance_thenTheVorgangKeepsNumberTitleAndClosedState() {
    // When — Kriterium 24.
    try (ConfigurableApplicationContext neu = neueInstanz()) {
      final Vorgang gelesen = neu.getBean(VorgangLesenUseCase.class).lese(vorgangId).vorgang();

      // Then
      assertThat(gelesen)
          .extracting(Vorgang::nummer, Vorgang::titel, Vorgang::abgeschlossen)
          .containsExactly(Long.valueOf(1L), TITEL, true);
    }
  }

  @Test
  void lesen_afterRestartingTheInstance_thenTheHistoryIsUnchanged() {
    // When — Kriterium 24: Kommentar und Anhang, in der Reihenfolge des Geschehens.
    try (ConfigurableApplicationContext neu = neueInstanz()) {
      final VorgangMitHistorie gelesen = neu.getBean(VorgangLesenUseCase.class).lese(vorgangId);

      // Then
      assertThat(gelesen.historie())
          .extracting(
              EintragAnsicht::art,
              EintragAnsicht::text,
              EintragAnsicht::geschehenAm,
              EintragAnsicht::dateiName,
              EintragAnsicht::dateiGroesse)
          .containsExactly(
              tuple(Eintragsart.KOMMENTAR, "Angerufen", GESTERN, null, null),
              tuple(
                  Eintragsart.ANHANG,
                  null,
                  VORGESTERN,
                  "Angebot.pdf",
                  Long.valueOf(INHALT.length)));
    }
  }

  @Test
  void lesen_afterRestartingTheInstance_thenTheAttachmentComesBackByteForByte() throws IOException {
    // When — Kriterium 24: die Bytes liegen in MinIO, die Zeile dazu in PostgreSQL.
    try (ConfigurableApplicationContext neu = neueInstanz()) {
      final byte[] gelesen =
          neu.getBean(AnhangLesenUseCase.class).lese(vorgangId, anhangId).inhalt().readAllBytes();

      // Then
      assertThat(gelesen).isEqualTo(INHALT);
    }
  }
}
