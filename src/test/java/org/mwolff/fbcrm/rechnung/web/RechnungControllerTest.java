package org.mwolff.fbcrm.rechnung.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.common.web.GlobalExceptionHandler;
import org.mwolff.fbcrm.rechnung.application.Abrechnungsangabe;
import org.mwolff.fbcrm.rechnung.application.RechnungAendernUseCase;
import org.mwolff.fbcrm.rechnung.application.RechnungDaten;
import org.mwolff.fbcrm.rechnung.application.RechnungLesenUseCase;
import org.mwolff.fbcrm.rechnung.application.RechnungLoeschenUseCase;
import org.mwolff.fbcrm.rechnung.application.RechnungNichtGefunden;
import org.mwolff.fbcrm.rechnung.application.RechnungszustandPasstNicht;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Die Uebersetzung zwischen Anwendungsfall und HTTP fuer die Wege an der einzelnen Rechnung (#160,
 * Kriterien 3 bis 12).
 *
 * <p>Gegenstand sind die gerechneten Werte — Netto, Steuer, Brutto und je Zeile offen und
 * Ueberschreitung, obwohl keiner davon in einer Spalte steht —, die Pruefung der Anfrage und die
 * Statuscodes: {@code PUT} antwortet 200 mit dem neuen Stand, {@code DELETE} 204, beides an einer
 * gestellten Rechnung 409, eine unbekannte Rechnung 404.
 *
 * <p>Die Feldfehler stehen hier und nicht im Integrationstest: Sie sind eine Aussage der
 * Schnittstelle und haengen an keiner Datenbank.
 */
@ExtendWith(MockitoExtension.class)
class RechnungControllerTest {

  private static final String PFAD = "/api/rechnungen/" + Webdoppel.RECHNUNG;

  private static final String RUMPF =
      """
      {"rechnungDatum":"2026-09-30","leistungszeitraum":"September 2026",
       "positionen":[{"angebotPositionId":101,"bezeichnung":"Beratung","menge":"80.00"}]}
      """;

  @Mock private RechnungLesenUseCase lesen;
  @Mock private RechnungAendernUseCase aendern;
  @Mock private RechnungLoeschenUseCase loeschen;

  @Captor private ArgumentCaptor<RechnungDaten> daten;

  private MockMvc mockMvc;

  @BeforeEach
  void baueDenController() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new RechnungController(lesen, aendern, loeschen))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  private String rumpfMit(final String bezeichnung, final String menge, final String zeitraum) {
    return """
        {"rechnungDatum":"2026-09-30","leistungszeitraum":"%s",
         "positionen":[{"angebotPositionId":101,"bezeichnung":"%s","menge":"%s"}]}
        """
        .formatted(zeitraum, bezeichnung, menge);
  }

  @Test
  void lesen_thenTheAnswerCarriesTheRechnungWithItsCalculatedValues() throws Exception {
    // Given — 80 Stunden zu 100,00 €: 8.000,00 netto, 19 Prozent, 9.520,00 brutto.
    when(lesen.lese(Webdoppel.RECHNUNG))
        .thenReturn(Webdoppel.ansicht(Webdoppel.entwurf("80.00"), "0"));

    // When / Then
    mockMvc
        .perform(get(PFAD))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(Webdoppel.RECHNUNG))
        .andExpect(jsonPath("$.angebotId").value(Webdoppel.ANGEBOT))
        .andExpect(jsonPath("$.firmaId").value(Webdoppel.FIRMA))
        .andExpect(jsonPath("$.firmaName").value(Webdoppel.FIRMENNAME))
        .andExpect(jsonPath("$.leistungszeitraum").value(Webdoppel.ZEITRAUM))
        .andExpect(jsonPath("$.zustand").value("ENTWURF"))
        .andExpect(jsonPath("$.nummer").doesNotExist())
        .andExpect(jsonPath("$.steuersatz").value(19.00))
        .andExpect(jsonPath("$.netto").value(8000.00))
        .andExpect(jsonPath("$.steuer").value(1520.00))
        .andExpect(jsonPath("$.brutto").value(9520.00));
  }

  @Test
  void lesen_thenEveryZeileCarriesTheStandOfItsAngebotsposition() throws Exception {
    // Given — 100 sind fremd abgerechnet, dieser Entwurf traegt 80: zusammen 20 zu viel.
    when(lesen.lese(Webdoppel.RECHNUNG))
        .thenReturn(Webdoppel.ansicht(Webdoppel.entwurf("80.00"), "100.00"));

    // When / Then
    mockMvc
        .perform(get(PFAD))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.zeilen.length()").value(1))
        .andExpect(jsonPath("$.zeilen[0].angebotPositionId").value(Webdoppel.BERATUNG_ID))
        .andExpect(jsonPath("$.zeilen[0].bezeichnung").value("Beratung"))
        .andExpect(jsonPath("$.zeilen[0].einheit").value("STUNDE"))
        .andExpect(jsonPath("$.zeilen[0].einzelpreis").value(100.00))
        .andExpect(jsonPath("$.zeilen[0].angeboten").value(160.00))
        .andExpect(jsonPath("$.zeilen[0].abgerechnet").value(100.00))
        .andExpect(jsonPath("$.zeilen[0].offen").value(60.00))
        .andExpect(jsonPath("$.zeilen[0].menge").value(80.00))
        .andExpect(jsonPath("$.zeilen[0].ueberschreitung").value(20.00));
  }

  @Test
  void lesen_withAnUnknownRechnung_thenNotFound() throws Exception {
    // Given
    when(lesen.lese(Webdoppel.RECHNUNG)).thenThrow(new RechnungNichtGefunden());

    // When / Then
    mockMvc.perform(get(PFAD)).andExpect(status().isNotFound());
  }

  @Test
  void aendern_thenTheUseCaseSeesTheAngabenAndTheAnswerCarriesTheNewStand() throws Exception {
    // Given
    when(lesen.lese(Webdoppel.RECHNUNG))
        .thenReturn(Webdoppel.ansicht(Webdoppel.entwurf("80.00"), "0"));

    // When
    mockMvc
        .perform(put(PFAD).contentType(MediaType.APPLICATION_JSON).content(RUMPF))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.netto").value(8000.00));

    // Then
    verify(aendern).aendere(eq(Webdoppel.RECHNUNG), daten.capture());
    assertThat(daten.getValue().leistungszeitraum()).isEqualTo(Webdoppel.ZEITRAUM);
    assertThat(daten.getValue().angaben())
        .singleElement()
        .satisfies(
            angabe -> assertThat(angabe.angebotPositionId()).isEqualTo(Webdoppel.BERATUNG_ID),
            angabe -> assertThat(angabe.bezeichnung()).isEqualTo("Beratung"),
            angabe -> assertThat(angabe.menge()).isEqualByComparingTo("80.00"));
  }

  @Test
  void aendern_withAMengeOfZero_thenItPassesThroughAndTheUseCaseDecides() throws Exception {
    // Given — 0 laesst die Position wegfallen; das entscheidet der Anwendungsfall, nicht die Form.
    when(lesen.lese(Webdoppel.RECHNUNG))
        .thenReturn(Webdoppel.ansicht(Webdoppel.entwurf("80.00"), "0"));

    // When
    mockMvc
        .perform(
            put(PFAD)
                .contentType(MediaType.APPLICATION_JSON)
                .content(rumpfMit("Beratung", "0", Webdoppel.ZEITRAUM)))
        .andExpect(status().isOk());

    // Then
    verify(aendern).aendere(eq(Webdoppel.RECHNUNG), daten.capture());
    assertThat(daten.getValue().angaben())
        .extracting(Abrechnungsangabe::menge)
        .singleElement()
        .satisfies(menge -> assertThat(menge).isEqualByComparingTo("0"));
  }

  @Test
  void aendern_atAGestellteRechnung_thenConflict() throws Exception {
    // Given
    when(aendern.aendere(eq(Webdoppel.RECHNUNG), any()))
        .thenThrow(new RechnungszustandPasstNicht());

    // When / Then
    mockMvc
        .perform(put(PFAD).contentType(MediaType.APPLICATION_JSON).content(RUMPF))
        .andExpect(status().isConflict());
    verifyNoInteractions(lesen);
  }

  @Test
  void aendern_withANegativeMenge_thenTheFieldIsNamed() throws Exception {
    // When / Then
    mockMvc
        .perform(
            put(PFAD)
                .contentType(MediaType.APPLICATION_JSON)
                .content(rumpfMit("Beratung", "-1.00", Webdoppel.ZEITRAUM)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors['positionen[0].menge']").isNotEmpty());
    verifyNoInteractions(aendern, lesen);
  }

  @Test
  void aendern_withThreeDecimalPlaces_thenTheFieldIsNamed() throws Exception {
    // When / Then — dieselbe Grenze wie numeric(12,2) im Schema.
    mockMvc
        .perform(
            put(PFAD)
                .contentType(MediaType.APPLICATION_JSON)
                .content(rumpfMit("Beratung", "1.005", Webdoppel.ZEITRAUM)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors['positionen[0].menge']").isNotEmpty());
    verifyNoInteractions(aendern, lesen);
  }

  @Test
  void aendern_withAnEmptyBezeichnung_thenTheFieldIsNamed() throws Exception {
    // When / Then
    mockMvc
        .perform(
            put(PFAD)
                .contentType(MediaType.APPLICATION_JSON)
                .content(rumpfMit("   ", "80.00", Webdoppel.ZEITRAUM)))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.fieldErrors['positionen[0].bezeichnung'][0]")
                .value(RechnungPositionRequest.BEZEICHNUNG_FEHLT));
    verifyNoInteractions(aendern, lesen);
  }

  @Test
  void aendern_withABezeichnungOf301Characters_thenTheFieldIsNamed() throws Exception {
    // When / Then — dieselbe Grenze wie varchar(300) im Schema.
    mockMvc
        .perform(
            put(PFAD)
                .contentType(MediaType.APPLICATION_JSON)
                .content(rumpfMit("B".repeat(301), "80.00", Webdoppel.ZEITRAUM)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors['positionen[0].bezeichnung']").isNotEmpty());
    verifyNoInteractions(aendern, lesen);
  }

  @Test
  void aendern_withAnEmptyLeistungszeitraum_thenTheFieldIsNamed() throws Exception {
    // When / Then
    mockMvc
        .perform(
            put(PFAD)
                .contentType(MediaType.APPLICATION_JSON)
                .content(rumpfMit("Beratung", "80.00", "  ")))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.fieldErrors.leistungszeitraum[0]").value(RechnungRequest.ZEITRAUM_FEHLT));
    verifyNoInteractions(aendern, lesen);
  }

  @Test
  void aendern_withALeistungszeitraumOf101Characters_thenTheFieldIsNamed() throws Exception {
    // When / Then — dieselbe Grenze wie varchar(100) im Schema.
    mockMvc
        .perform(
            put(PFAD)
                .contentType(MediaType.APPLICATION_JSON)
                .content(rumpfMit("Beratung", "80.00", "S".repeat(101))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.leistungszeitraum").isNotEmpty());
    verifyNoInteractions(aendern, lesen);
  }

  @Test
  void loeschen_thenNoContent() throws Exception {
    // When / Then
    mockMvc.perform(delete(PFAD)).andExpect(status().isNoContent());
    verify(loeschen).loesche(Webdoppel.RECHNUNG);
  }

  @Test
  void loeschen_atAGestellteRechnung_thenConflict() throws Exception {
    // Given
    doThrow(new RechnungszustandPasstNicht()).when(loeschen).loesche(Webdoppel.RECHNUNG);

    // When / Then
    mockMvc.perform(delete(PFAD)).andExpect(status().isConflict());
  }

  @Test
  void loeschen_withAnUnknownRechnung_thenNotFound() throws Exception {
    // Given
    doThrow(new RechnungNichtGefunden()).when(loeschen).loesche(Webdoppel.RECHNUNG);

    // When / Then
    mockMvc.perform(delete(PFAD)).andExpect(status().isNotFound());
  }

  @Test
  void aendern_withoutAPositionsliste_thenTheFieldIsNamed() throws Exception {
    // When / Then — die Liste darf leer sein, aber sie darf nicht fehlen.
    mockMvc
        .perform(
            put(PFAD)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rechnungDatum\":\"2026-09-30\",\"leistungszeitraum\":\"Sept\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.positionen").isNotEmpty());
    verifyNoInteractions(aendern, lesen);
  }

  @Test
  void aendern_withAnEmptyPositionsliste_thenItPassesThrough() throws Exception {
    // Given — eine Rechnung ohne Position ist zulaessig (Kriterium 5).
    when(lesen.lese(Webdoppel.RECHNUNG))
        .thenReturn(Webdoppel.ansicht(Webdoppel.entwurf("80.00"), "0"));

    // When
    mockMvc
        .perform(
            put(PFAD)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"rechnungDatum\":\"2026-09-30\",\"leistungszeitraum\":\"Sept\","
                        + "\"positionen\":[]}"))
        .andExpect(status().isOk());

    // Then
    verify(aendern).aendere(eq(Webdoppel.RECHNUNG), daten.capture());
    assertThat(daten.getValue().angaben()).isEmpty();
  }

  @Test
  void aendern_withoutARechnungDatum_thenTheFieldIsNamed() throws Exception {
    // When / Then
    mockMvc
        .perform(
            put(PFAD)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"leistungszeitraum\":\"Sept\",\"positionen\":"
                        + "[{\"angebotPositionId\":101,\"bezeichnung\":\"B\",\"menge\":\"1\"}]}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.rechnungDatum").isNotEmpty());
    verifyNoInteractions(aendern, lesen);
  }

  @Test
  void aendern_withoutAnAngebotPositionId_thenTheFieldIsNamed() throws Exception {
    // When / Then
    mockMvc
        .perform(
            put(PFAD)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"rechnungDatum\":\"2026-09-30\",\"leistungszeitraum\":\"Sept\","
                        + "\"positionen\":[{\"bezeichnung\":\"B\",\"menge\":\"1\"}]}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors['positionen[0].angebotPositionId']").isNotEmpty());
    verifyNoInteractions(aendern, lesen);
  }

  @Test
  void aendern_withoutAMenge_thenTheFieldIsNamed() throws Exception {
    // When / Then
    mockMvc
        .perform(
            put(PFAD)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"rechnungDatum\":\"2026-09-30\",\"leistungszeitraum\":\"Sept\","
                        + "\"positionen\":[{\"angebotPositionId\":101,\"bezeichnung\":\"B\"}]}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors['positionen[0].menge']").isNotEmpty());
    verifyNoInteractions(aendern, lesen);
  }
}
