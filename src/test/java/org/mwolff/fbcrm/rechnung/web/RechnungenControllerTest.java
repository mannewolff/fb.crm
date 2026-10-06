package org.mwolff.fbcrm.rechnung.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.common.web.GlobalExceptionHandler;
import org.mwolff.fbcrm.rechnung.application.AbrechenbareAngeboteUseCase;
import org.mwolff.fbcrm.rechnung.application.AbrechenbaresAngebot;
import org.mwolff.fbcrm.rechnung.application.RechnungenUebersichtUseCase;
import org.mwolff.fbcrm.rechnung.application.Rechnungslistenzeile;
import org.mwolff.fbcrm.rechnung.domain.Rechnungszustand;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Die Uebersetzung zwischen Anwendungsfall und HTTP fuer die Liste der Rechnungen und die Wahl
 * „Neue Rechnung" (#160, Kriterien 1 und 2).
 *
 * <p>Dieselbe Strecke wie in {@code AngeboteControllerTest}: {@code standaloneSetup} mit dem echten
 * {@link GlobalExceptionHandler}. Gegenstand sind die Reihenfolge des Anwendungsfalls, die leere
 * Nummer am Entwurf und der Bruttobetrag, der in keiner Spalte steht — dazu die Art der Zeile und
 * ob ein Dokument hinterlegt ist (Plan #259, E18).
 */
@ExtendWith(MockitoExtension.class)
class RechnungenControllerTest {

  @Mock private RechnungenUebersichtUseCase uebersicht;
  @Mock private AbrechenbareAngeboteUseCase abrechenbare;

  private MockMvc mockMvc;

  @BeforeEach
  void baueDenController() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new RechnungenController(uebersicht, abrechenbare))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void rechnungen_thenEveryZeileComesInTheOrderOfTheUseCase() throws Exception {
    // Given — ein Entwurf ohne Nummer, eine gestellte Rechnung mit Dokument und eine nachgetragene
    // ohne.
    when(uebersicht.rechnungen())
        .thenReturn(
            List.of(
                new Rechnungslistenzeile(
                    false,
                    Webdoppel.RECHNUNG,
                    null,
                    Webdoppel.FIRMA,
                    Webdoppel.FIRMENNAME,
                    LocalDate.of(2026, 9, 30),
                    new BigDecimal("9520.00"),
                    Rechnungszustand.ENTWURF,
                    false),
                new Rechnungslistenzeile(
                    false,
                    6L,
                    "R26-0001",
                    Webdoppel.FIRMA,
                    Webdoppel.FIRMENNAME,
                    LocalDate.of(2026, 9, 30),
                    new BigDecimal("10700.00"),
                    Rechnungszustand.GESTELLT,
                    true),
                new Rechnungslistenzeile(
                    true,
                    6L,
                    "AR-17",
                    Webdoppel.FIRMA,
                    Webdoppel.FIRMENNAME,
                    LocalDate.of(2026, 9, 1),
                    new BigDecimal("119.00"),
                    Rechnungszustand.BEZAHLT,
                    false)));

    // When / Then — der Entwurf traegt keine Nummer, der Brutto kommt aus dem Anwendungsfall.
    mockMvc
        .perform(get("/api/rechnungen"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rechnungen.length()").value(3))
        .andExpect(jsonPath("$.rechnungen[0].id").value(Webdoppel.RECHNUNG))
        .andExpect(jsonPath("$.rechnungen[0].nummer").doesNotExist())
        .andExpect(jsonPath("$.rechnungen[0].zustand").value("ENTWURF"))
        .andExpect(jsonPath("$.rechnungen[0].firmaId").value(Webdoppel.FIRMA))
        .andExpect(jsonPath("$.rechnungen[0].firmaName").value(Webdoppel.FIRMENNAME))
        .andExpect(jsonPath("$.rechnungen[0].rechnungDatum").exists())
        .andExpect(jsonPath("$.rechnungen[0].brutto").value(9520.00))
        .andExpect(jsonPath("$.rechnungen[0].nachgetragen").value(false))
        .andExpect(jsonPath("$.rechnungen[0].dokument").value(false))
        .andExpect(jsonPath("$.rechnungen[1].nummer").value("R26-0001"))
        .andExpect(jsonPath("$.rechnungen[1].zustand").value("GESTELLT"))
        .andExpect(jsonPath("$.rechnungen[1].nachgetragen").value(false))
        .andExpect(jsonPath("$.rechnungen[1].dokument").value(true))
        .andExpect(jsonPath("$.rechnungen[2].id").value(6L))
        .andExpect(jsonPath("$.rechnungen[2].nummer").value("AR-17"))
        .andExpect(jsonPath("$.rechnungen[2].zustand").value("BEZAHLT"))
        .andExpect(jsonPath("$.rechnungen[2].nachgetragen").value(true))
        .andExpect(jsonPath("$.rechnungen[2].dokument").value(false));
  }

  @Test
  void rechnungen_withoutARechnung_thenTheListIsEmpty() throws Exception {
    // Given
    when(uebersicht.rechnungen()).thenReturn(List.of());

    // When / Then
    mockMvc
        .perform(get("/api/rechnungen"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rechnungen").isEmpty());
  }

  @Test
  void abrechenbareAngebote_thenEveryZeileCarriesItsFirmaAndOffenenBetrag() throws Exception {
    // Given
    when(abrechenbare.abrechenbare())
        .thenReturn(
            List.of(
                new AbrechenbaresAngebot(
                    Webdoppel.angebot(), Webdoppel.FIRMENNAME, new BigDecimal("8000.00"))));

    // When / Then
    mockMvc
        .perform(get("/api/rechnungen/abrechenbare-angebote"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.angebote.length()").value(1))
        .andExpect(jsonPath("$.angebote[0].angebotId").value(Webdoppel.ANGEBOT))
        .andExpect(jsonPath("$.angebote[0].firmaId").value(Webdoppel.FIRMA))
        .andExpect(jsonPath("$.angebote[0].firmaName").value(Webdoppel.FIRMENNAME))
        .andExpect(jsonPath("$.angebote[0].angebotDatum").exists())
        .andExpect(jsonPath("$.angebote[0].offenerBetrag").value(8000.00));
  }

  @Test
  void abrechenbareAngebote_withoutACandidate_thenTheListIsEmpty() throws Exception {
    // Given
    when(abrechenbare.abrechenbare()).thenReturn(List.of());

    // When / Then
    mockMvc
        .perform(get("/api/rechnungen/abrechenbare-angebote"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.angebote").isEmpty());
  }
}
