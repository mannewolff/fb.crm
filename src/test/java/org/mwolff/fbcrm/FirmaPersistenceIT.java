package org.mwolff.fbcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Instant;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.firma.application.FirmaLesenUseCase;
import org.mwolff.fbcrm.firma.application.FirmaMitAnsprechpartnern;
import org.mwolff.fbcrm.firma.domain.Anschrift;
import org.mwolff.fbcrm.firma.domain.Ansprechpartner;
import org.mwolff.fbcrm.firma.domain.AnsprechpartnerRepository;
import org.mwolff.fbcrm.firma.domain.Firma;
import org.mwolff.fbcrm.firma.domain.FirmaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Kriterium 18: Firma, Ansprechpartner und Stilllegungsstand ueberstehen einen Neustart.
 *
 * <p>Der Neustart ist echt — es entsteht ein zweiter Anwendungskontext gegen dieselbe Datenbank, im
 * Muster von {@link AccountPersistenceIT}. Nur so ist belegt, dass nichts an den Stammdaten im
 * Speicher haengt: Ein zweiter Test im selben Kontext bewiese allein, dass die Datenbank ein SELECT
 * beantwortet.
 */
class FirmaPersistenceIT extends AbstractIntegrationTest {

  private static final Instant ANGELEGT = Instant.parse("2026-09-18T10:00:00Z");
  private static final String NEUES_GEHEIMNIS = "ein-zweites-geheimnis-mit-genug-zeichen-drin";

  private final FirmaRepository firmen;
  private final AnsprechpartnerRepository ansprechpartner;
  private final JdbcTemplate jdbc;

  private long firmaId;

  @Autowired
  FirmaPersistenceIT(
      final FirmaRepository firmen,
      final AnsprechpartnerRepository ansprechpartner,
      final JdbcTemplate jdbc) {
    this.firmen = firmen;
    this.ansprechpartner = ansprechpartner;
    this.jdbc = jdbc;
  }

  @BeforeEach
  void legeDenStammsatzAn() {
    jdbc.execute("TRUNCATE ansprechpartner, firma RESTART IDENTITY CASCADE");
    firmaId =
        firmen
            .save(
                new Firma(
                    null,
                    "Adler AG",
                    new Anschrift("Am Wall 1", "28195", "Bremen", "Deutschland"),
                    "75/123/45678",
                    "DE123456789",
                    false,
                    ANGELEGT,
                    ANGELEGT))
            .requireId();
    ansprechpartner.save(partner("Aktiv", true));
    ansprechpartner.save(partner("Ruhend", false));
  }

  private Ansprechpartner partner(final String nachname, final boolean aktiv) {
    return new Ansprechpartner(
        null,
        firmaId,
        "Max",
        nachname,
        "Einkauf",
        "max@firma.de",
        "0421 1234",
        "0170 1234",
        aktiv,
        ANGELEGT,
        ANGELEGT);
  }

  /**
   * Eine zweite Instanz gegen dieselbe Datenbank.
   *
   * <p>Die Werte gehen als Kommandozeilenargumente hinein und nicht als {@code properties(...)}:
   * Letztere sind Vorgabewerte und stehen in der Rangfolge <b>unter</b> der {@code application.yml}
   * — die Instanz verbaende sich dann mit {@code localhost:5432}.
   *
   * <p>Der Objektspeicher der Suite geht aus demselben Grund mit hinein: Seine Zugangsdaten haben
   * keinen brauchbaren Default, ohne sie kaeme die zweite Instanz nicht hoch.
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
  void lesen_afterRestartingTheInstance_thenTheFirmaKeepsEveryValue() {
    // When
    try (ConfigurableApplicationContext neu = neueInstanz()) {
      final Firma gelesen = neu.getBean(FirmaLesenUseCase.class).lese(firmaId).firma();

      // Then
      assertThat(gelesen)
          .extracting(Firma::name, Firma::anschrift, Firma::steuernummer, Firma::umsatzsteuerId)
          .containsExactly(
              "Adler AG",
              new Anschrift("Am Wall 1", "28195", "Bremen", "Deutschland"),
              "75/123/45678",
              "DE123456789");
    }
  }

  @Test
  void lesen_afterRestartingTheInstance_thenEveryRetirementStateSurvived() {
    // When — Kriterium 18: der Stand der Firma und der jedes Ansprechpartners.
    try (ConfigurableApplicationContext neu = neueInstanz()) {
      final FirmaMitAnsprechpartnern gelesen = neu.getBean(FirmaLesenUseCase.class).lese(firmaId);

      // Then
      assertThat(gelesen.firma().aktiv()).isFalse();
      assertThat(gelesen.ansprechpartner())
          .extracting(Ansprechpartner::nachname, Ansprechpartner::aktiv)
          .containsExactly(tuple("Aktiv", true), tuple("Ruhend", false));
    }
  }
}
