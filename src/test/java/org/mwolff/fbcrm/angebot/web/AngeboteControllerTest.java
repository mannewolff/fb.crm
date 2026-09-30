package org.mwolff.fbcrm.angebot.web;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.angebot.application.AngebotMitFirma;
import org.mwolff.fbcrm.angebot.application.AngeboteUebersichtUseCase;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.Angebotsposition;
import org.mwolff.fbcrm.angebot.domain.Angebotsstatus;
import org.mwolff.fbcrm.common.Abrechnungsmodus;
import org.mwolff.fbcrm.common.Einheit;
import org.mwolff.fbcrm.common.web.GlobalExceptionHandler;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Die Uebersetzung zwischen Anwendungsfall und HTTP fuer die Uebersicht aller Angebote (Issue #127,
 * Kriterium 8): mit und ohne Filter, und ein unbekannter Status ist 400 und nicht 500.
 */
@ExtendWith(MockitoExtension.class)
class AngeboteControllerTest {

  private static final Instant ANGELEGT = Instant.parse("2026-09-20T08:00:00Z");

  @Mock private AngeboteUebersichtUseCase uebersicht;

  private MockMvc mockMvc;

  @BeforeEach
  void baueDenController() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new AngeboteController(uebersicht))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  private static AngebotMitFirma zeile(final long id, final Angebotsstatus status) {
    return new AngebotMitFirma(
        new Angebot(
            Long.valueOf(id),
            3L,
            null,
            status,
            LocalDate.of(2026, 9, 20),
            null,
            List.of(
                new Angebotsposition(
                    null,
                    "Konzeption",
                    Abrechnungsmodus.AUFWAND,
                    new BigDecimal("2.50"),
                    Einheit.PERSONENTAG,
                    new BigDecimal("1000.01"))),
            ANGELEGT,
            ANGELEGT),
        "Adler AG");
  }

  @Test
  void angebote_withoutAFilter_thenAnswersEveryRowInTheOrderOfTheUseCase() throws Exception {
    // Given
    when(uebersicht.angebote(Optional.empty()))
        .thenReturn(
            List.of(zeile(12L, Angebotsstatus.BESTELLT), zeile(11L, Angebotsstatus.ANGELEGT)));

    // When / Then
    mockMvc
        .perform(get("/api/angebote"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.angebote.length()").value(2))
        .andExpect(jsonPath("$.angebote[0].id").value(12))
        .andExpect(jsonPath("$.angebote[0].firmaId").value(3))
        .andExpect(jsonPath("$.angebote[0].firmaName").value("Adler AG"))
        .andExpect(jsonPath("$.angebote[0].status").value("BESTELLT"))
        .andExpect(jsonPath("$.angebote[0].summe").value(2500.03))
        .andExpect(jsonPath("$.angebote[1].id").value(11))
        .andExpect(jsonPath("$.angebote[1].status").value("ANGELEGT"));
  }

  @Test
  void angebote_withAStatus_thenPassesItOn() throws Exception {
    // Given
    when(uebersicht.angebote(Optional.of(Angebotsstatus.BESTELLT)))
        .thenReturn(List.of(zeile(12L, Angebotsstatus.BESTELLT)));

    // When / Then
    mockMvc
        .perform(get("/api/angebote").param("status", "BESTELLT"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.angebote.length()").value(1));
    verify(uebersicht).angebote(Optional.of(Angebotsstatus.BESTELLT));
  }

  @Test
  void angebote_withAnUnknownStatus_thenAnswers400() throws Exception {
    // When / Then
    mockMvc
        .perform(get("/api/angebote").param("status", "VERHANDELT"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(uebersicht);
  }

  @Test
  void angebote_whenThereAreNone_thenAnswersAnEmptyList() throws Exception {
    // Given
    when(uebersicht.angebote(Optional.empty())).thenReturn(List.of());

    // When / Then
    mockMvc
        .perform(get("/api/angebote"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.angebote.length()").value(0));
  }
}
