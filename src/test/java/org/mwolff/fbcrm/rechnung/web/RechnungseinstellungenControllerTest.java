package org.mwolff.fbcrm.rechnung.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.common.web.GlobalExceptionHandler;
import org.mwolff.fbcrm.rechnung.application.RechnungseinstellungenLesenUseCase;
import org.mwolff.fbcrm.rechnung.application.RechnungseinstellungenMitNummer;
import org.mwolff.fbcrm.rechnung.application.RechnungseinstellungenPflegenUseCase;
import org.mwolff.fbcrm.rechnung.domain.Nummernmuster;
import org.mwolff.fbcrm.rechnung.domain.Rechnungseinstellungen;
import org.springframework.http.MediaType;
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
 */
@ExtendWith(MockitoExtension.class)
class RechnungseinstellungenControllerTest {

  private static final String PFAD = "/api/rechnung/einstellungen";
  private static final String GUELTIGES_MUSTER = "\"{NNNN}-{JJJJ}\"";

  @Mock private RechnungseinstellungenLesenUseCase lesen;
  @Mock private RechnungseinstellungenPflegenUseCase pflegen;

  private RechnungseinstellungenController controller;
  private MockMvc mockMvc;

  @BeforeEach
  void baueDenController() {
    controller = new RechnungseinstellungenController(lesen, pflegen);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  private static RechnungseinstellungenMitNummer vorbelegt() {
    return new RechnungseinstellungenMitNummer(
        new Rechnungseinstellungen(new Nummernmuster("{NNNN}-{JJJJ}"), new BigDecimal("19.00"), 10),
        1);
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
        .perform(put(PFAD).contentType(MediaType.APPLICATION_JSON).content(rumpf))
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
            new RechnungseinstellungenResponse("{NNNN}-{JJJJ}", 1, new BigDecimal("19.00"), 10));
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
            .andReturn()
            .getResponse()
            .getContentAsString();

    // Then — ohne Anfuehrungszeichen um den Wert.
    assertThat(inhalt).contains("\"steuersatz\":19.00");
  }

  @Test
  void pflegen_thenPassesTheValuesOn() {
    // When
    controller.pflegen(
        new RechnungseinstellungenRequest("R{JJ}-{NNNN}", 4, new BigDecimal("19.50"), 14));

    // Then — die Nummer geht getrennt mit; sie gehoert dem Nummernkreis (#175).
    verify(pflegen)
        .pflege(
            new Rechnungseinstellungen(
                new Nummernmuster("R{JJ}-{NNNN}"), new BigDecimal("19.50"), 14),
            4);
  }

  @Test
  void pflegen_thenAnswersWithNoContentAndAnEmptyBody() throws Exception {
    // When — der Steuersatz kommt als Dezimaltext aus der Maske.
    final String inhalt =
        mockMvc
            .perform(
                put(PFAD)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(rumpf("\"R{JJ}-{NNNN}\"", "4", "\"19.50\"", "14")))
            .andExpect(status().isNoContent())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // Then — wie {@code EigeneAngabenController}: kein Rumpf, den neuen Stand liest die
    // Oberflaeche ueber den GET.
    assertThat(inhalt).isEmpty();
    verify(pflegen)
        .pflege(
            new Rechnungseinstellungen(
                new Nummernmuster("R{JJ}-{NNNN}"), new BigDecimal("19.50"), 14),
            4);
  }

  @ParameterizedTest
  @CsvSource({"1, 0.00, 0", "1, 100.00, 0", "1, 19.50, 14"})
  void pflegen_givenAnAcceptedBoundary_thenAnswersWithNoContent(
      final String nummer, final String steuersatz, final String ziel) throws Exception {
    // When / Then — die Grenzen selbst sind gueltig: naechste Nummer 1, Steuersatz 0 und 100,
    // Zahlungsziel 0.
    mockMvc
        .perform(
            put(PFAD)
                .contentType(MediaType.APPLICATION_JSON)
                .content(rumpf(GUELTIGES_MUSTER, nummer, "\"" + steuersatz + "\"", ziel)))
        .andExpect(status().isNoContent());
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
    assertThat(NummernmusterConstraint.MESSAGE).contains("{NNNN}");
  }
}
