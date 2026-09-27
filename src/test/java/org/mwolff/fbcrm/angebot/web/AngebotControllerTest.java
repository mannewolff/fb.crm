package org.mwolff.fbcrm.angebot.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.angebot.application.AngebotAnsicht;
import org.mwolff.fbcrm.angebot.application.AngebotEntwurfAendernUseCase;
import org.mwolff.fbcrm.angebot.application.AngebotLesenUseCase;
import org.mwolff.fbcrm.angebot.application.AngebotNichtAenderbar;
import org.mwolff.fbcrm.angebot.application.AngebotNichtGefunden;
import org.mwolff.fbcrm.angebot.application.AngebotVerwerfenUseCase;
import org.mwolff.fbcrm.angebot.application.EntwurfDaten;
import org.mwolff.fbcrm.angebot.domain.Abrechnungsmodus;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.Angebotsposition;
import org.mwolff.fbcrm.angebot.domain.Angebotsstand;
import org.mwolff.fbcrm.angebot.domain.Angebotszustand;
import org.mwolff.fbcrm.angebot.domain.Einheit;
import org.mwolff.fbcrm.common.web.GlobalExceptionHandler;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Die Uebersetzung zwischen Anwendungsfall und HTTP fuer die Wege am einzelnen Angebot.
 *
 * <p>Dieselbe Strecke wie in {@code VorgangControllerTest}: {@code standaloneSetup} mit dem echten
 * {@link GlobalExceptionHandler}, damit die Statuscodes der Ausnahmen mitgeprueft werden.
 *
 * <p>Zwei Aussagen sind hier der Gegenstand. Erstens die gerechneten Werte aus E5: Die Antwort
 * traegt {@code stand}, {@code summe} und je Position den {@code betrag}, obwohl keiner davon in
 * einer Spalte steht. Zweitens die Statuscodes: {@code PUT} antwortet 200 mit dem neuen Stand —
 * auch am abgeschlossenen Vorgang, denn dieser Weg fragt den Vorgang gar nicht (E13) —, {@code
 * DELETE} antwortet 204 ohne Rumpf (E19), und ein festgeschriebenes Angebot ist auf beiden Wegen
 * 409.
 */
@ExtendWith(MockitoExtension.class)
class AngebotControllerTest {

  private static final long ANGEBOT = 11L;
  private static final LocalDate ANGEBOTSDATUM = LocalDate.of(2026, 9, 20);
  private static final LocalDate GUELTIG_BIS = LocalDate.of(2026, 10, 20);
  private static final Instant ANGELEGT = Instant.parse("2026-09-20T08:00:00Z");

  private static final String RUMPF =
      """
      {"gueltigBis":"2026-11-30","leistungsbeschreibung":"Neu","zahlungsbedingungen":null,
       "positionen":[{"bezeichnung":"Konzeption","abrechnungsmodus":"AUFWAND","menge":"2.50",
                      "einheit":"PERSONENTAG","einzelpreis":"1000.01"}]}
      """;

  @Mock private AngebotLesenUseCase lesen;
  @Mock private AngebotEntwurfAendernUseCase aendern;
  @Mock private AngebotVerwerfenUseCase verwerfen;

  @Captor private ArgumentCaptor<EntwurfDaten> daten;

  private AngebotController controller;
  private MockMvc mockMvc;

  @BeforeEach
  void baueDenController() {
    controller = new AngebotController(lesen, aendern, verwerfen);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  private static AngebotAnsicht entwurf() {
    return new AngebotAnsicht(
        new Angebot(
            Long.valueOf(ANGEBOT),
            3L,
            null,
            Angebotszustand.ENTWURF,
            ANGEBOTSDATUM,
            GUELTIG_BIS,
            "Neugestaltung",
            "Zahlbar in 14 Tagen.",
            null,
            null,
            null,
            null,
            null,
            List.of(
                new Angebotsposition(
                    "Konzeption",
                    Abrechnungsmodus.AUFWAND,
                    new BigDecimal("2.50"),
                    Einheit.PERSONENTAG,
                    new BigDecimal("1000.01"))),
            ANGELEGT,
            ANGELEGT),
        Angebotsstand.ENTWURF);
  }

  @Test
  void lesen_thenAnswersWithTheDerivedStandAndTheCalculatedAmounts() throws Exception {
    // Given — E5: Betrag und Summe stehen in keiner Spalte.
    when(lesen.lese(ANGEBOT)).thenReturn(entwurf());

    // When / Then
    mockMvc
        .perform(get("/api/angebote/{id}", Long.valueOf(ANGEBOT)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(Long.valueOf(ANGEBOT)))
        .andExpect(jsonPath("$.vorgangId").value(3))
        .andExpect(jsonPath("$.nummer").doesNotExist())
        .andExpect(jsonPath("$.stand").value("ENTWURF"))
        .andExpect(jsonPath("$.summe").value(2500.03))
        .andExpect(jsonPath("$.positionen[0].betrag").value(2500.03))
        .andExpect(jsonPath("$.positionen[0].einheit").value("PERSONENTAG"));
  }

  @Test
  void lesen_whenTheAngebotIsUnknown_thenAnswers404() throws Exception {
    // Given
    when(lesen.lese(ANGEBOT)).thenThrow(new AngebotNichtGefunden());

    // When / Then
    mockMvc
        .perform(get("/api/angebote/{id}", Long.valueOf(ANGEBOT)))
        .andExpect(status().isNotFound());
  }

  @Test
  void aendern_thenAnswers200WithTheNewStand() throws Exception {
    // Given — auch am abgeschlossenen Vorgang: dieser Weg fragt den Vorgang nicht (E13).
    when(aendern.aendere(eq(ANGEBOT), daten.capture())).thenReturn(entwurf());

    // When / Then
    mockMvc
        .perform(
            put("/api/angebote/{id}", Long.valueOf(ANGEBOT))
                .contentType(MediaType.APPLICATION_JSON)
                .content(RUMPF))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(Long.valueOf(ANGEBOT)));
  }

  @Test
  void aendern_thenPassesTextsAndTheCompletePositionList() throws Exception {
    // Given — E8: der Entwurf wird als Ganzes geschrieben.
    when(aendern.aendere(eq(ANGEBOT), daten.capture())).thenReturn(entwurf());

    // When
    mockMvc
        .perform(
            put("/api/angebote/{id}", Long.valueOf(ANGEBOT))
                .contentType(MediaType.APPLICATION_JSON)
                .content(RUMPF))
        .andExpect(status().isOk());

    // Then
    final EntwurfDaten uebergeben = daten.getValue();
    assertThat(uebergeben.gueltigBis()).isEqualTo(LocalDate.of(2026, 11, 30));
    assertThat(uebergeben.leistungsbeschreibung()).isEqualTo("Neu");
    assertThat(uebergeben.zahlungsbedingungen()).isNull();
    assertThat(uebergeben.positionen())
        .containsExactly(
            new Angebotsposition(
                "Konzeption",
                Abrechnungsmodus.AUFWAND,
                new BigDecimal("2.50"),
                Einheit.PERSONENTAG,
                new BigDecimal("1000.01")));
  }

  @Test
  void aendern_whenTheAngebotIsFestgeschrieben_thenAnswers409() throws Exception {
    // Given — Kriterium 13.
    when(aendern.aendere(eq(ANGEBOT), daten.capture())).thenThrow(new AngebotNichtAenderbar());

    // When / Then
    mockMvc
        .perform(
            put("/api/angebote/{id}", Long.valueOf(ANGEBOT))
                .contentType(MediaType.APPLICATION_JSON)
                .content(RUMPF))
        .andExpect(status().isConflict());
  }

  @Test
  void aendern_withANegativeMenge_thenAnswersWithAFieldError() throws Exception {
    // Given — die Form wird an der Anfrage geprueft, nicht erst in der Datenbank.
    final String negativ =
        """
        {"gueltigBis":"2026-11-30","positionen":[{"bezeichnung":"Konzeption",
          "abrechnungsmodus":"AUFWAND","menge":"-1.00","einheit":"STUNDE","einzelpreis":"10.00"}]}
        """;

    // When / Then
    mockMvc
        .perform(
            put("/api/angebote/{id}", Long.valueOf(ANGEBOT))
                .contentType(MediaType.APPLICATION_JSON)
                .content(negativ))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors['positionen[0].menge']").isNotEmpty());
  }

  @Test
  void aendern_withoutGueltigBis_thenAnswersWithAFieldError() throws Exception {
    // Given — die Gueltigkeit ist die eine Pflichtangabe des Entwurfs.
    final String ohneDatum = "{\"positionen\":[]}";

    // When / Then
    mockMvc
        .perform(
            put("/api/angebote/{id}", Long.valueOf(ANGEBOT))
                .contentType(MediaType.APPLICATION_JSON)
                .content(ohneDatum))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.gueltigBis").isNotEmpty());
  }

  @Test
  void verwerfen_thenAnswers204WithoutABody() throws Exception {
    // When / Then — E19: danach gibt es nichts mehr zurueckzugeben.
    mockMvc
        .perform(delete("/api/angebote/{id}", Long.valueOf(ANGEBOT)))
        .andExpect(status().isNoContent());
    verify(verwerfen).verwirf(ANGEBOT);
  }

  @Test
  void verwerfen_whenTheAngebotIsFestgeschrieben_thenAnswers409() throws Exception {
    // Given — Kriterium 7 gilt nur dem Entwurf.
    doThrow(new AngebotNichtAenderbar()).when(verwerfen).verwirf(ANGEBOT);

    // When / Then
    mockMvc
        .perform(delete("/api/angebote/{id}", Long.valueOf(ANGEBOT)))
        .andExpect(status().isConflict());
  }

  @Test
  void verwerfen_whenTheAngebotIsUnknown_thenAnswers404() throws Exception {
    // Given
    doThrow(new AngebotNichtGefunden()).when(verwerfen).verwirf(ANGEBOT);

    // When / Then
    mockMvc
        .perform(delete("/api/angebote/{id}", Long.valueOf(ANGEBOT)))
        .andExpect(status().isNotFound());
  }
}
