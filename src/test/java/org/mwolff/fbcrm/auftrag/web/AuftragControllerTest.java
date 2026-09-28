package org.mwolff.fbcrm.auftrag.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.auftrag.application.AuftragAnsicht;
import org.mwolff.fbcrm.auftrag.application.AuftragLesenUseCase;
import org.mwolff.fbcrm.auftrag.application.AuftragNichtGefunden;
import org.mwolff.fbcrm.auftrag.domain.Auftrag;
import org.mwolff.fbcrm.auftrag.domain.Auftragsposition;
import org.mwolff.fbcrm.auftrag.domain.Auftragsstatus;
import org.mwolff.fbcrm.common.Abrechnungsmodus;
import org.mwolff.fbcrm.common.Einheit;
import org.mwolff.fbcrm.common.web.GlobalExceptionHandler;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Die Ansicht eines einzelnen Auftrags ueber HTTP (Kriterien 3, 4, 5).
 *
 * <p>Zwei Werte kommen gerechnet und stehen in keiner Spalte (E11): die Summe und je Position ihr
 * Betrag. Die Nummer des Quell-Angebots steht <b>immer</b> in der Antwort, auch wenn eine Ansicht
 * sie gerade nicht zeigt — ein Feld, das je Weg mal da und mal nicht da ist, zwingt die Oberflaeche
 * zu zwei Formen fuer dieselbe Antwort.
 */
@ExtendWith(MockitoExtension.class)
class AuftragControllerTest {

  private static final long AUFTRAG = 7L;
  private static final long VORGANG = 3L;
  private static final long ANGEBOT = 11L;
  private static final Instant ANGELEGT = Instant.parse("2026-09-28T08:00:00Z");

  @Mock private AuftragLesenUseCase lesen;

  private MockMvc mockMvc;

  @BeforeEach
  void baueDenController() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new AuftragController(lesen))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void lesen_thenAnswersWithTheAuftragAndItsCalculatedValues() throws Exception {
    // Given — Kriterien 3, 4, 5.
    when(lesen.lese(AUFTRAG))
        .thenReturn(
            new AuftragAnsicht(
                new Auftrag(
                    Long.valueOf(AUFTRAG),
                    VORGANG,
                    ANGEBOT,
                    "AU-2026-001",
                    Auftragsstatus.IN_ARBEIT,
                    LocalDate.of(2026, 9, 28),
                    "BST-4711",
                    LocalDate.of(2026, 10, 1),
                    LocalDate.of(2026, 12, 31),
                    List.of(
                        new Auftragsposition(
                            "Konzeption",
                            Abrechnungsmodus.AUFWAND,
                            new BigDecimal("2.50"),
                            Einheit.PERSONENTAG,
                            new BigDecimal("1000.01"),
                            new BigDecimal("7.50")),
                        new Auftragsposition(
                            "Schulungstag",
                            Abrechnungsmodus.FESTPREIS,
                            new BigDecimal("1.00"),
                            Einheit.PAUSCHAL,
                            new BigDecimal("1200.00"),
                            null)),
                    ANGELEGT,
                    ANGELEGT),
                "A-2026-011"));

    // When / Then
    mockMvc
        .perform(get("/api/auftraege/{id}", Long.valueOf(AUFTRAG)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(Long.valueOf(AUFTRAG)))
        .andExpect(jsonPath("$.vorgangId").value(Long.valueOf(VORGANG)))
        .andExpect(jsonPath("$.angebotId").value(Long.valueOf(ANGEBOT)))
        .andExpect(jsonPath("$.angebotNummer").value("A-2026-011"))
        .andExpect(jsonPath("$.status").value("IN_ARBEIT"))
        .andExpect(jsonPath("$.kundenbestellnummer").value("BST-4711"))
        .andExpect(jsonPath("$.auftragDatum").exists())
        .andExpect(jsonPath("$.leistungAb").exists())
        .andExpect(jsonPath("$.leistungBis").exists())
        .andExpect(jsonPath("$.positionen.length()").value(2))
        .andExpect(jsonPath("$.positionen[0].stundenJePersonentag").value(7.50))
        .andExpect(jsonPath("$.positionen[0].betrag").value(2500.03))
        .andExpect(jsonPath("$.positionen[1].stundenJePersonentag").doesNotExist())
        .andExpect(jsonPath("$.summe").value(3700.03));
  }

  @Test
  void lesen_givenAnUnknownAuftrag_thenAnswers404() throws Exception {
    // Given
    when(lesen.lese(AUFTRAG)).thenThrow(new AuftragNichtGefunden());

    // When / Then
    mockMvc
        .perform(get("/api/auftraege/{id}", Long.valueOf(AUFTRAG)))
        .andExpect(status().isNotFound());
  }
}
