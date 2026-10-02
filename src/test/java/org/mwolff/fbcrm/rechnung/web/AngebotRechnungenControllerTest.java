package org.mwolff.fbcrm.rechnung.web;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.angebot.application.AngebotNichtGefunden;
import org.mwolff.fbcrm.common.web.GlobalExceptionHandler;
import org.mwolff.fbcrm.rechnung.application.AbrechnungsstandUseCase;
import org.mwolff.fbcrm.rechnung.application.AngebotNichtAbrechenbar;
import org.mwolff.fbcrm.rechnung.application.Angebotsabrechnung;
import org.mwolff.fbcrm.rechnung.application.Positionsabrechnung;
import org.mwolff.fbcrm.rechnung.application.RechnungAnlegenUseCase;
import org.mwolff.fbcrm.rechnung.application.RechnungLesenUseCase;
import org.mwolff.fbcrm.rechnung.application.RechnungMitBetrag;
import org.mwolff.fbcrm.rechnung.domain.Positionsstand;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Die Uebersetzung zwischen Anwendungsfall und HTTP fuer die beiden Wege am Angebot (#160,
 * Kriterien 2, 3 und 26).
 *
 * <p>Gegenstand sind der Statuscode des Anlegens — 201 mit dem frischen Entwurf, 409 an einem
 * Angebot, aus dem nichts zu holen ist, 404 an einem unbekannten — und die beiden Listen des
 * Abrechnungsstands. <b>Der Server entscheidet</b>: Die Abweisung kommt aus dem Anwendungsfall und
 * nicht aus einer Vorpruefung im Controller.
 *
 * <p>Die Zeile des Abrechnungsstands traegt dabei auch, was aus der Zeiterfassung kommt: {@code
 * buchbar} und die angefallenen Stunden (Issue #193, Kriterien 7 und 8). Sie gehen unveraendert
 * durch — was sie bedeuten, entscheidet der Anwendungsfall.
 *
 * <p>Dazu die drei Formen des Rumpfs beim Anlegen (Issue #193, Antwort 7): ganz weggelassen, mit
 * einem Monat und mit {@code null} als Monat. Die erste und die dritte sind dieselbe Anfrage an den
 * Anwendungsfall — ein Entwurf ohne Arbeitszeit —, und das ist hier nachgewiesen und nicht nur
 * beabsichtigt.
 */
@ExtendWith(MockitoExtension.class)
class AngebotRechnungenControllerTest {

  private static final String RECHNUNGEN = "/api/angebote/" + Webdoppel.ANGEBOT + "/rechnungen";
  private static final String ABRECHNUNG = "/api/angebote/" + Webdoppel.ANGEBOT + "/abrechnung";

  @Mock private RechnungAnlegenUseCase anlegen;
  @Mock private RechnungLesenUseCase lesen;
  @Mock private AbrechnungsstandUseCase abrechnungsstand;

  private MockMvc mockMvc;

  @BeforeEach
  void baueDenController() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new AngebotRechnungenController(anlegen, lesen, abrechnungsstand))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void anlegen_withoutARumpf_thenCreatedWithTheFreshEntwurf() throws Exception {
    // Given — ohne Rumpf fragt der Controller ohne Monat, und der Entwurf ist mit der offenen
    // Menge vorbelegt.
    when(anlegen.anlegen(Webdoppel.ANGEBOT, Optional.empty()))
        .thenReturn(Webdoppel.entwurf("160.00"));
    when(lesen.lese(Webdoppel.RECHNUNG))
        .thenReturn(Webdoppel.ansicht(Webdoppel.entwurf("160.00"), "0"));

    // When / Then
    mockMvc
        .perform(post(RECHNUNGEN))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(Webdoppel.RECHNUNG))
        .andExpect(jsonPath("$.angebotId").value(Webdoppel.ANGEBOT))
        .andExpect(jsonPath("$.zustand").value("ENTWURF"))
        .andExpect(jsonPath("$.nummer").doesNotExist())
        .andExpect(jsonPath("$.zeilen[0].menge").value(160.00))
        .andExpect(jsonPath("$.netto").value(16000.00));
  }

  @Test
  void anlegen_withAMonat_thenTheMonatReachesTheUseCase() throws Exception {
    // Given — der Monat steht als JJJJ-MM im Rumpf (Antwort 7).
    when(anlegen.anlegen(Webdoppel.ANGEBOT, Optional.of(YearMonth.of(2026, 11))))
        .thenReturn(Webdoppel.entwurf("12.00"));
    when(lesen.lese(Webdoppel.RECHNUNG))
        .thenReturn(Webdoppel.ansicht(Webdoppel.entwurf("12.00"), "10.00"));

    // When / Then
    mockMvc
        .perform(
            post(RECHNUNGEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"monat\":\"2026-11\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.zeilen[0].menge").value(12.00));
  }

  @Test
  void anlegen_withARumpfWithoutAMonat_thenTheUseCaseIsAskedWithoutOne() throws Exception {
    // Given — „ohne Arbeitszeit" schickt die Oberflaeche als null; das ist dasselbe wie kein Rumpf.
    when(anlegen.anlegen(Webdoppel.ANGEBOT, Optional.empty()))
        .thenReturn(Webdoppel.entwurf("160.00"));
    when(lesen.lese(Webdoppel.RECHNUNG))
        .thenReturn(Webdoppel.ansicht(Webdoppel.entwurf("160.00"), "0"));

    // When / Then
    mockMvc
        .perform(
            post(RECHNUNGEN).contentType(MediaType.APPLICATION_JSON).content("{\"monat\":null}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.zeilen[0].menge").value(160.00));
  }

  @Test
  void anlegen_atAnAngebotWithNothingToBill_thenConflict() throws Exception {
    // Given — vor „bestellt" oder ohne Offenes: beides sagt dieselbe Ausnahme.
    when(anlegen.anlegen(Webdoppel.ANGEBOT, Optional.empty()))
        .thenThrow(new AngebotNichtAbrechenbar());

    // When / Then
    mockMvc.perform(post(RECHNUNGEN)).andExpect(status().isConflict());
    verifyNoInteractions(lesen);
  }

  @Test
  void anlegen_atAnUnknownAngebot_thenNotFound() throws Exception {
    // Given
    when(anlegen.anlegen(Webdoppel.ANGEBOT, Optional.empty()))
        .thenThrow(new AngebotNichtGefunden());

    // When / Then
    mockMvc.perform(post(RECHNUNGEN)).andExpect(status().isNotFound());
    verifyNoInteractions(lesen);
  }

  @Test
  void abrechnung_thenEveryPositionAndEveryRechnungComesAlong() throws Exception {
    // Given — 180 auf 160 angebotene Stunden: nichts offen, 20 zu viel.
    when(abrechnungsstand.zu(Webdoppel.ANGEBOT))
        .thenReturn(
            new Angebotsabrechnung(
                List.of(
                    new Positionsabrechnung(
                        new Positionsstand(Webdoppel.BERATUNG, new BigDecimal("180.00")),
                        true,
                        new BigDecimal("182.00"))),
                List.of(
                    new RechnungMitBetrag(Webdoppel.entwurf("80.00"), new BigDecimal("9520.00")),
                    new RechnungMitBetrag(
                        Webdoppel.gestellt(6L, "R26-0001", "100.00"), new BigDecimal("10700.00"))),
                new BigDecimal("182.00")));

    // When / Then
    mockMvc
        .perform(get(ABRECHNUNG))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.positionen.length()").value(1))
        .andExpect(jsonPath("$.positionen[0].angebotPositionId").value(Webdoppel.BERATUNG_ID))
        .andExpect(jsonPath("$.positionen[0].bezeichnung").value("Beratung"))
        .andExpect(jsonPath("$.positionen[0].einheit").value("STUNDE"))
        .andExpect(jsonPath("$.positionen[0].angeboten").value(160.00))
        .andExpect(jsonPath("$.positionen[0].abgerechnet").value(180.00))
        .andExpect(jsonPath("$.positionen[0].offen").value(0))
        .andExpect(jsonPath("$.positionen[0].ueberschreitung").value(20.00))
        .andExpect(jsonPath("$.positionen[0].buchbar").value(true))
        .andExpect(jsonPath("$.positionen[0].angefallen").value(182.00))
        .andExpect(jsonPath("$.angefallen").value(182.00))
        .andExpect(jsonPath("$.rechnungen.length()").value(2))
        .andExpect(jsonPath("$.rechnungen[0].id").value(Webdoppel.RECHNUNG))
        .andExpect(jsonPath("$.rechnungen[0].nummer").doesNotExist())
        .andExpect(jsonPath("$.rechnungen[0].brutto").value(9520.00))
        .andExpect(jsonPath("$.rechnungen[0].zustand").value("ENTWURF"))
        .andExpect(jsonPath("$.rechnungen[1].nummer").value("R26-0001"))
        .andExpect(jsonPath("$.rechnungen[1].zustand").value("GESTELLT"));
  }

  @Test
  void abrechnung_atAnUnknownAngebot_thenNotFound() throws Exception {
    // Given
    when(abrechnungsstand.zu(Webdoppel.ANGEBOT)).thenThrow(new AngebotNichtGefunden());

    // When / Then
    mockMvc.perform(get(ABRECHNUNG)).andExpect(status().isNotFound());
  }
}
