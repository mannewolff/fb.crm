package org.mwolff.fbcrm.startseite.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.angebot.application.AngebotMitFirma;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.Angebotsposition;
import org.mwolff.fbcrm.angebot.domain.Angebotsstatus;
import org.mwolff.fbcrm.common.Abrechnungsmodus;
import org.mwolff.fbcrm.common.Einheit;
import org.mwolff.fbcrm.startseite.application.Forecast;
import org.mwolff.fbcrm.startseite.application.ForecastUseCase;
import org.mwolff.fbcrm.startseite.application.Forecastjahr;
import org.mwolff.fbcrm.startseite.application.Forecastposition;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Die Uebersetzung zwischen dem Forecast und HTTP (Issue #308; Plan #303, E1, E9).
 *
 * <p>Gegenstand ist die Abbildung jedes Feldes des {@link Forecast} auf die Antwort: je Jahr die
 * drei Betraege und die Positionen mit Kennung, Firma, Angebotsdatum, Bezeichnung und Restbetrag.
 * Das Jahr ist schon hier Text, weil dieser Layer es als Text baut (E9); wie das Datum geschrieben
 * wird, entscheidet der ObjectMapper von Spring Boot, und das belegt {@link ForecastControllerIT}.
 */
@ExtendWith(MockitoExtension.class)
class ForecastControllerTest {

  private static final String PFAD = "/api/startseite/forecast";
  private static final Instant ANGELEGT = Instant.parse("2026-10-15T08:00:00Z");
  private static final LocalDate ANGEBOTSDATUM = LocalDate.of(2026, 10, 15);
  private static final long ANGEBOT_ID = 21L;
  private static final String FIRMA = "IT Bildungshaus";

  @Mock private ForecastUseCase useCase;

  private MockMvc mockMvc;

  @BeforeEach
  void baueDenController() {
    mockMvc = MockMvcBuilders.standaloneSetup(new ForecastController(useCase)).build();
  }

  private static Forecastposition position(final String bezeichnung, final String netto) {
    final Angebotsposition position =
        new Angebotsposition(
            Long.valueOf(301L),
            bezeichnung,
            Abrechnungsmodus.AUFWAND,
            new BigDecimal("135.00"),
            Einheit.STUNDE,
            new BigDecimal("60.00"),
            Year.of(2027));
    final AngebotMitFirma angebot =
        new AngebotMitFirma(
            new Angebot(
                Long.valueOf(ANGEBOT_ID),
                5L,
                null,
                false,
                Angebotsstatus.ANGELEGT,
                ANGEBOTSDATUM,
                "Schulungen 2027",
                List.of(position),
                ANGELEGT,
                ANGELEGT),
            FIRMA);
    return new Forecastposition(angebot, position, new BigDecimal(netto));
  }

  @Test
  void forecast_thenAnswers200WithOneLinePerYearAndTheYearAsText() throws Exception {
    // Given — E9: Das Jahr reist als Text JJJJ, ein Year schriebe Jackson als Zahl.
    when(useCase.forecast())
        .thenReturn(
            new Forecast(
                List.of(
                    new Forecastjahr(
                        Year.of(2026),
                        new BigDecimal("500.00"),
                        new BigDecimal("0.00"),
                        List.of(position("Rest 2026", "500.00"))),
                    new Forecastjahr(
                        Year.of(2027),
                        new BigDecimal("1000.00"),
                        new BigDecimal("8100.00"),
                        List.of(position("Aufwände Mai 2027", "8100.00"))))));

    // When / Then
    mockMvc
        .perform(get(PFAD))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.jahre.length()").value(2))
        .andExpect(jsonPath("$.jahre[0].jahr").value("2026"))
        .andExpect(jsonPath("$.jahre[1].jahr").value("2027"))
        .andExpect(jsonPath("$.jahre[1].jahr").isString())
        .andExpect(jsonPath("$.jahre[1].zugesagtNetto").value(1000.00))
        .andExpect(jsonPath("$.jahre[1].angebotenNetto").value(8100.00))
        .andExpect(jsonPath("$.jahre[1].summeNetto").value(9100.00));
  }

  @Test
  void forecast_thenEachPositionCarriesIdCompanyDateDescriptionAndAmount() throws Exception {
    // Given — Kriterium 5: Firma, Angebot, Position und Restbetrag, dazu die Kennung als Weg.
    when(useCase.forecast())
        .thenReturn(
            new Forecast(
                List.of(
                    new Forecastjahr(
                        Year.of(2027),
                        new BigDecimal("0.00"),
                        new BigDecimal("9720.00"),
                        List.of(
                            position("Aufwände Mai 2027", "8100.00"),
                            position("Aufwände 3 Tage Rest", "1620.00"))))));

    // When / Then
    mockMvc
        .perform(get(PFAD))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.jahre[0].positionen.length()").value(2))
        .andExpect(jsonPath("$.jahre[0].positionen[0].angebotId").value(Long.valueOf(ANGEBOT_ID)))
        .andExpect(jsonPath("$.jahre[0].positionen[0].firmaName").value(FIRMA))
        .andExpect(jsonPath("$.jahre[0].positionen[0].angebotDatum").exists())
        .andExpect(jsonPath("$.jahre[0].positionen[0].bezeichnung").value("Aufwände Mai 2027"))
        .andExpect(jsonPath("$.jahre[0].positionen[0].netto").value(8100.00))
        .andExpect(jsonPath("$.jahre[0].positionen[1].bezeichnung").value("Aufwände 3 Tage Rest"))
        .andExpect(jsonPath("$.jahre[0].positionen[1].netto").value(1620.00));
  }

  @Test
  void forecast_givenNoOpenPosition_thenAnswersWithAnEmptyListOfYears() throws Exception {
    // Given — Kriterium 6: ohne eine einzige offene Position gibt es kein Jahr.
    when(useCase.forecast()).thenReturn(new Forecast(List.of()));

    // When / Then
    mockMvc
        .perform(get(PFAD))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.jahre").isArray())
        .andExpect(jsonPath("$.jahre.length()").value(0));
  }
}
