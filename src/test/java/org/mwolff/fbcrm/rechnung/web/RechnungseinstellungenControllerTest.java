package org.mwolff.fbcrm.rechnung.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.common.Urheber;
import org.mwolff.fbcrm.common.web.Bedingungskopf;
import org.mwolff.fbcrm.common.web.GlobalExceptionHandler;
import org.mwolff.fbcrm.rechnung.application.RechnungseinstellungenGeaendert;
import org.mwolff.fbcrm.rechnung.application.RechnungseinstellungenLesenUseCase;
import org.mwolff.fbcrm.rechnung.application.RechnungseinstellungenMitNummer;
import org.mwolff.fbcrm.rechnung.application.RechnungseinstellungenPflegenUseCase;
import org.mwolff.fbcrm.rechnung.domain.Nummernmuster;
import org.mwolff.fbcrm.rechnung.domain.Rechnungseinstellungen;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Die Uebersetzung zwischen Anwendungsfall und HTTP fuer die beiden Wege der Rechnungseinstellungen
 * (fachliche Quelle #159, Kriterien 5 und 7 bis 10).
 *
 * <p>Zwei Blickwinkel in einer Klasse, wie bei {@code EigeneAngabenControllerTest}: die Abbildung
 * direkt an den Methoden, und die Faelle, bei denen die <b>Form</b> der Antwort die Aussage ist —
 * Statuscodes und Feldfehler — ueber eine schlanke MockMvc-Strecke mit dem echten {@link
 * GlobalExceptionHandler}.
 *
 * <p>Der {@code PUT} legt den Stand vor (#411, Kriterium 4; Plan #415, E2, E3, E14): ohne {@code
 * If-Match} 428, auf veraltetem Stand 409 mit der Erweiterung {@code konflikt}, sonst 200 mit dem
 * neuen Stand samt erhoehter Version.
 */
@ExtendWith(MockitoExtension.class)
class RechnungseinstellungenControllerTest {

  private static final String PFAD = "/api/rechnung/einstellungen";
  private static final String GUELTIGES_MUSTER = "\"{NNNN}-{JJJJ}\"";

  /** Der Stand, den die Maske vorlegt (Plan #415, E2). */
  private static final long STAND = 3L;

  /** Derselbe Stand als starkes Etikett im Kopf {@code If-Match}. */
  private static final String VORGELEGT = "\"" + STAND + "\"";

  /** Das angemeldete Konto. */
  private static final long ANGEMELDET = 31L;

  /** Das Konto, das inzwischen geaendert hat. */
  private static final long ANNA = 30L;

  private static final Instant ANNAS_AENDERUNG = Instant.parse("2026-10-06T08:42:00Z");

  private static final Rechnungseinstellungen EINGEREICHT =
      new Rechnungseinstellungen(new Nummernmuster("R{JJ}-{NNNN}"), new BigDecimal("19.50"), 14);

  @Mock private RechnungseinstellungenLesenUseCase lesen;
  @Mock private RechnungseinstellungenPflegenUseCase pflegen;

  private RechnungseinstellungenController controller;
  private MockMvc mockMvc;

  @BeforeEach
  void baueDenController() {
    // Die Sitzung wie im Betrieb: die Kennung des Kontos als Principal (Plan #415, E14).
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                Long.valueOf(ANGEMELDET), null, List.of()));
    controller = new RechnungseinstellungenController(lesen, pflegen);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(
                new GlobalExceptionHandler(
                    konto ->
                        konto == ANNA ? Optional.of(new Urheber(ANNA, "Anna")) : Optional.empty()))
            .setCustomArgumentResolvers(
                new Bedingungskopf.Leser(), new AuthenticationPrincipalArgumentResolver())
            .build();
  }

  @AfterEach
  void raeumeDieSitzungAb() {
    SecurityContextHolder.clearContext();
  }

  private static RechnungseinstellungenMitNummer vorbelegt() {
    return new RechnungseinstellungenMitNummer(
        new Rechnungseinstellungen(new Nummernmuster("{NNNN}-{JJJJ}"), new BigDecimal("19.00"), 10),
        1,
        STAND);
  }

  /** Der Anwendungsfall nimmt jede gueltige Eingabe an und meldet den erhoehten Stand. */
  private void nimmtAn() {
    when(pflegen.pflege(any(), anyInt(), anyLong(), anyLong()))
        .thenReturn(new RechnungseinstellungenMitNummer(EINGEREICHT, 4, STAND + 1));
  }

  /**
   * Der Rumpf des {@code PUT}. Die Stuecke kommen als JSON-Schnipsel herein, damit ein Test auch
   * {@code null} und einen falsch getypten Wert einreichen kann.
   */
  private static String rumpf(
      final String muster, final String nummer, final String steuersatz, final String ziel) {
    return "{\"nummerMuster\":"
        + muster
        + ",\"naechsteNummer\":"
        + nummer
        + ",\"steuersatz\":"
        + steuersatz
        + ",\"zahlungszielTage\":"
        + ziel
        + "}";
  }

  private void weiseAb(final String rumpf, final String feld) throws Exception {
    mockMvc
        .perform(
            put(PFAD)
                .header(HttpHeaders.IF_MATCH, VORGELEGT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(rumpf))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors." + feld).isArray());

    // Kriterium 10: bei einem Fehler geht nichts in den Bestand.
    verifyNoInteractions(pflegen);
  }

  private void weiseMusterAb(final String musterAlsJson) throws Exception {
    weiseAb(rumpf(musterAlsJson, "1", "\"19.00\"", "10"), "nummerMuster");
  }

  @Test
  void lesen_thenAnswersWithEveryValue() {
    // Given
    when(lesen.lese()).thenReturn(vorbelegt());

    // When
    final RechnungseinstellungenResponse antwort = controller.lesen();

    // Then — das Muster geht als Text hinaus, nicht als geschachteltes Objekt.
    assertThat(antwort)
        .isEqualTo(
            new RechnungseinstellungenResponse(
                "{NNNN}-{JJJJ}", 1, new BigDecimal("19.00"), 10, STAND));
  }

  @Test
  void lesen_thenCarriesTheTaxRateAsAJsonNumber() throws Exception {
    // Given — Kriterium 5. Der Steuersatz geht wie {@code summe} in den Angebots-Antworten als
    // Dezimalzahl hinaus; als Zeichenkette muesste das Frontend zwei Formen desselben Wertes
    // lesen.
    when(lesen.lese()).thenReturn(vorbelegt());

    // When
    final String inhalt =
        mockMvc
            .perform(get(PFAD))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nummerMuster").value("{NNNN}-{JJJJ}"))
            .andExpect(jsonPath("$.naechsteNummer").value(1))
            .andExpect(jsonPath("$.zahlungszielTage").value(10))
            .andExpect(jsonPath("$.version").value(STAND))
            .andReturn()
            .getResponse()
            .getContentAsString();

    // Then — ohne Anfuehrungszeichen um den Wert.
    assertThat(inhalt).contains("\"steuersatz\":19.00");
  }

  @Test
  void pflegen_thenPassesTheValuesStandAndKontoOn() {
    // Given
    nimmtAn();

    // When
    final RechnungseinstellungenResponse antwort =
        controller.pflegen(
            new Bedingungskopf(STAND),
            ANGEMELDET,
            new RechnungseinstellungenRequest("R{JJ}-{NNNN}", 4, new BigDecimal("19.50"), 14));

    // Then — die Nummer geht getrennt mit; sie gehoert dem Nummernkreis (#175).
    verify(pflegen).pflege(EINGEREICHT, 4, STAND, ANGEMELDET);
    assertThat(antwort)
        .isEqualTo(
            new RechnungseinstellungenResponse(
                "R{JJ}-{NNNN}", 4, new BigDecimal("19.50"), 14, STAND + 1));
  }

  @Test
  void pflegen_withTheCurrentStand_thenAnswers200WithTheRaisedVersion() throws Exception {
    // Given — #411, Kriterium 8: auf dem aktuellen Stand merkt man vom Schutz nichts.
    nimmtAn();

    // When — der Steuersatz kommt als Dezimaltext aus der Maske.
    mockMvc
        .perform(
            put(PFAD)
                .header(HttpHeaders.IF_MATCH, VORGELEGT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(rumpf("\"R{JJ}-{NNNN}\"", "4", "\"19.50\"", "14")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nummerMuster").value("R{JJ}-{NNNN}"))
        .andExpect(jsonPath("$.naechsteNummer").value(4))
        .andExpect(jsonPath("$.version").value(STAND + 1));

    // Then — Stand und Konto aus Kopf und Sitzung.
    verify(pflegen).pflege(EINGEREICHT, 4, STAND, ANGEMELDET);
  }

  @Test
  void pflegen_withoutIfMatch_thenAnswers428AndChangesNothing() throws Exception {
    // When / Then — E3: ohne vorgelegten Stand wird nicht geschrieben, auch nicht vorher
    // geprueft.
    mockMvc
        .perform(
            put(PFAD)
                .contentType(MediaType.APPLICATION_JSON)
                .content(rumpf("\"R{JJ}-{NNNN}\"", "4", "\"19.50\"", "14")))
        .andExpect(status().isPreconditionRequired())
        .andExpect(jsonPath("$.detail").value(Bedingungskopf.MELDUNG));
    verifyNoInteractions(pflegen, lesen);
  }

  @Test
  void pflegen_withAnOutdatedStand_thenAnswers409WithTheConflict() throws Exception {
    // Given — #411, Kriterium 4: Anna hat die Einstellungen inzwischen gespeichert.
    when(pflegen.pflege(EINGEREICHT, 4, STAND, ANGEMELDET))
        .thenThrow(new RechnungseinstellungenGeaendert(ANNA, ANGEMELDET, ANNAS_AENDERUNG));

    // When / Then
    mockMvc
        .perform(
            put(PFAD)
                .header(HttpHeaders.IF_MATCH, VORGELEGT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(rumpf("\"R{JJ}-{NNNN}\"", "4", "\"19.50\"", "14")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.konflikt.art").value("veraltet"))
        .andExpect(jsonPath("$.konflikt.geaendertVon").value("Anna"))
        .andExpect(jsonPath("$.konflikt.geaendertAm").value("2026-10-06T08:42:00Z"))
        .andExpect(jsonPath("$.konflikt.eigenesFenster").value(false));
  }

  @ParameterizedTest
  @CsvSource({"1, 0.00, 0", "1, 100.00, 0", "1, 19.50, 14"})
  void pflegen_givenAnAcceptedBoundary_thenAnswersWith200(
      final String nummer, final String steuersatz, final String ziel) throws Exception {
    // Given
    nimmtAn();

    // When / Then — die Grenzen selbst sind gueltig: naechste Nummer 1, Steuersatz 0 und 100,
    // Zahlungsziel 0.
    mockMvc
        .perform(
            put(PFAD)
                .header(HttpHeaders.IF_MATCH, VORGELEGT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(rumpf(GUELTIGES_MUSTER, nummer, "\"" + steuersatz + "\"", ziel)))
        .andExpect(status().isOk());
  }

  @ParameterizedTest
  @CsvSource({
    "0, 19.00, 10, naechsteNummer",
    "1, -0.01, 10, steuersatz",
    "1, 100.01, 10, steuersatz",
    "1, 19.555, 10, steuersatz",
    "1, 19.00, -1, zahlungszielTage"
  })
  void pflegen_givenARefusedNumber_thenAnswersWithTheFieldErrorOnThatField(
      final String nummer, final String steuersatz, final String ziel, final String feld)
      throws Exception {
    // When / Then — Kriterium 9: die Meldung haengt an dem Feld, das sie ausgeloest hat.
    weiseAb(rumpf(GUELTIGES_MUSTER, nummer, "\"" + steuersatz + "\"", ziel), feld);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "ohne-nummer-{JJJJ}", "{NNNN}-{NNNN}", "{JJJ}-{NNNN}"})
  void pflegen_givenARefusedPattern_thenAnswersWithTheFieldErrorOnThePattern(final String muster)
      throws Exception {
    // When / Then — Kriterium 8: die eigene Pruefregel befragt {@code Nummernmuster}, und der
    // Fehler kommt am Feld an. Ohne sie zerbraeche der Record-Konstruktor des Musters erst beim
    // Umsetzen und die Antwort waere ein 500.
    weiseMusterAb("\"" + muster + "\"");
  }

  @Test
  void pflegen_givenAPatternLongerThanTheColumn_thenAnswersWithTheFieldErrorOnThePattern()
      throws Exception {
    // Given — ein Zeichen mehr als {@code rechnung_einstellungen.nummer_muster} traegt.
    final String zuLang = "{NNNN}" + "a".repeat(Nummernmuster.MAX_LAENGE + 1 - "{NNNN}".length());
    assertThat(zuLang).hasSize(Nummernmuster.MAX_LAENGE + 1);

    // When / Then — die Grenze steht an der Schnittstelle; sonst antwortete die Anwendung mit
    // einem Datenbankfehler statt mit einer Meldung am Feld.
    weiseMusterAb("\"" + zuLang + "\"");
  }

  @Test
  void pflegen_givenNoPattern_thenAnswersWithTheFieldErrorOnThePattern() throws Exception {
    // When / Then — das Muster ist Pflicht (Kriterium 8): ein fehlendes Feld ist kein leeres
    // Muster, sondern gar keines, und beide bekommen dieselbe Meldung am Feld.
    weiseMusterAb("null");
  }

  @Test
  void message_thenNamesThePlaceholders() {
    // When / Then — die Meldung soll sagen, woran es lag; ohne die Platzhalter muesste der
    // Benutzer raten.
    assertThat(NummernmusterConstraint.MELDUNG).contains("{NNNN}");
  }
}
