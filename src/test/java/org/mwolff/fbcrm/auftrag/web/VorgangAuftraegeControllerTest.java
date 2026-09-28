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
import org.mwolff.fbcrm.auftrag.application.AuftraegeDesVorgangsUseCase;
import org.mwolff.fbcrm.auftrag.domain.Auftrag;
import org.mwolff.fbcrm.auftrag.domain.Auftragsposition;
import org.mwolff.fbcrm.auftrag.domain.Auftragsstatus;
import org.mwolff.fbcrm.common.Abrechnungsmodus;
import org.mwolff.fbcrm.common.Einheit;
import org.mwolff.fbcrm.common.web.GlobalExceptionHandler;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Die Auftragsliste eines Vorgangs ueber HTTP (Kriterium 9).
 *
 * <p>Die Zeile zeigt Nummer, Datum, Status, Leistungszeitraum und Summe — die Positionen bleiben
 * draussen, wie in {@code AngebotZeileResponse}: Die Liste zeigt sie nicht, und die Detailansicht
 * holt sie ohnehin.
 *
 * <p>Die Reihenfolge kommt aus der Anwendungsschicht und wird hier <b>durchgereicht</b>, nicht neu
 * hergestellt (Plan E13) — der Controller entscheidet nichts (CLAUDE-java.md §6.3).
 */
@ExtendWith(MockitoExtension.class)
class VorgangAuftraegeControllerTest {

  private static final long VORGANG = 3L;
  private static final Instant ANGELEGT = Instant.parse("2026-09-28T08:00:00Z");

  @Mock private AuftraegeDesVorgangsUseCase liste;

  private MockMvc mockMvc;

  @BeforeEach
  void baueDenController() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new VorgangAuftraegeController(liste))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  private static Auftrag auftrag(final long id, final String nummer, final LocalDate tag) {
    return new Auftrag(
        Long.valueOf(id),
        VORGANG,
        11L,
        nummer,
        Auftragsstatus.IN_ARBEIT,
        tag,
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
                new BigDecimal("7.50"))),
        ANGELEGT,
        ANGELEGT);
  }

  @Test
  void auftraege_thenAnswersWithTheRowsInTheOrderOfTheApplicationLayer() throws Exception {
    // Given — Kriterium 9.
    when(liste.auftraege(VORGANG))
        .thenReturn(
            List.of(
                auftrag(5L, "AU-2026-002", LocalDate.of(2026, 9, 28)),
                auftrag(2L, "AU-2026-001", LocalDate.of(2026, 9, 1))));

    // When / Then
    mockMvc
        .perform(get("/api/vorgaenge/{vorgangId}/auftraege", Long.valueOf(VORGANG)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.auftraege.length()").value(2))
        .andExpect(jsonPath("$.auftraege[0].id").value(5))
        .andExpect(jsonPath("$.auftraege[0].nummer").value("AU-2026-002"))
        .andExpect(jsonPath("$.auftraege[0].status").value("IN_ARBEIT"))
        .andExpect(jsonPath("$.auftraege[0].auftragDatum").exists())
        .andExpect(jsonPath("$.auftraege[0].leistungAb").exists())
        .andExpect(jsonPath("$.auftraege[0].leistungBis").exists())
        .andExpect(jsonPath("$.auftraege[0].summe").value(2500.03))
        .andExpect(jsonPath("$.auftraege[0].positionen").doesNotExist())
        .andExpect(jsonPath("$.auftraege[1].id").value(2));
  }

  @Test
  void auftraege_givenAVorgangWithoutAny_thenAnswersAnEmptyList() throws Exception {
    // Given — eine leere Liste heisst „dieser Vorgang hat noch keinen Auftrag".
    when(liste.auftraege(VORGANG)).thenReturn(List.of());

    // When / Then
    mockMvc
        .perform(get("/api/vorgaenge/{vorgangId}/auftraege", Long.valueOf(VORGANG)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.auftraege.length()").value(0));
  }
}
