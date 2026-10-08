package org.mwolff.fbcrm.rechnung.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.common.web.GlobalExceptionHandler;
import org.mwolff.fbcrm.rechnung.application.BetraegePassenNicht;
import org.mwolff.fbcrm.rechnung.application.DokumentNichtAnnehmbar;
import org.mwolff.fbcrm.rechnung.application.Dokumentablehnung;
import org.mwolff.fbcrm.rechnung.application.NachgetrageneRechnungMitFirma;
import org.mwolff.fbcrm.rechnung.application.NachtragAendernUseCase;
import org.mwolff.fbcrm.rechnung.application.NachtragAnlegenUseCase;
import org.mwolff.fbcrm.rechnung.application.NachtragDokumentUseCase;
import org.mwolff.fbcrm.rechnung.application.NachtragLesenUseCase;
import org.mwolff.fbcrm.rechnung.application.NachtragLoeschenUseCase;
import org.mwolff.fbcrm.rechnung.application.NachtragNichtGefunden;
import org.mwolff.fbcrm.rechnung.application.NachtragZustandSetzenUseCase;
import org.mwolff.fbcrm.rechnung.application.Nachtragsdaten;
import org.mwolff.fbcrm.rechnung.application.NummerSchonVergeben;
import org.mwolff.fbcrm.rechnung.application.RechnungsdatumAusserhalb;
import org.mwolff.fbcrm.rechnung.application.RechnungszustandPasstNicht;
import org.mwolff.fbcrm.rechnung.domain.NachgetrageneRechnung;
import org.mwolff.fbcrm.rechnung.domain.Rechnungszustand;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Die Uebersetzung zwischen den Anwendungsfaellen des Nachtrags und HTTP (#254; Plan #259, E11,
 * E17, E21, E23, E24, E26, E27; Issue #268).
 *
 * <p>Gegenstand sind die acht Wege und ihre Statuscodes: 201 mit {@code Location} beim Anlegen, 200
 * beim Lesen, Aendern, Umstellen, Herunterladen und Ablegen des Dokuments, 204 beim Loeschen und
 * Entfernen. Dazu die Fehler: 400 mit {@code fieldErrors} fuer die Form des Rumpfs (E9, E10), 422
 * mit {@code fieldErrors} fuer die fachlichen Abweisungen (E23), 404 fuer eine unbekannte Kennung
 * und fuer den Download ohne hinterlegtes Dokument (E17).
 */
@ExtendWith(MockitoExtension.class)
class NachgetrageneRechnungControllerTest {

  private static final long ID = 42L;
  private static final long FIRMA = 5L;
  private static final String FIRMENNAME = "Adler AG";
  private static final String NUMMER = "RE-2026-17";
  private static final LocalDate DATUM = LocalDate.of(2026, 3, 14);
  private static final Instant ZEIT = Instant.parse("2026-10-06T08:00:00Z");
  private static final String PFAD = "/api/nachgetragene-rechnungen";
  private static final String EINZELN = PFAD + "/" + ID;
  private static final byte[] PDF = "%PDF-1.7 Original".getBytes(StandardCharsets.US_ASCII);

  @Mock private NachtragAnlegenUseCase anlegen;
  @Mock private NachtragLesenUseCase lesen;
  @Mock private NachtragAendernUseCase aendern;
  @Mock private NachtragLoeschenUseCase loeschen;
  @Mock private NachtragZustandSetzenUseCase zustand;
  @Mock private NachtragDokumentUseCase dokument;

  @Captor private ArgumentCaptor<Nachtragsdaten> daten;

  private MockMvc mockMvc;

  @BeforeEach
  void baueDenController() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new NachgetrageneRechnungController(
                    anlegen, lesen, aendern, loeschen, zustand, dokument))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  private static NachgetrageneRechnung rechnung(
      final Rechnungszustand stand, final @Nullable String schluessel) {
    return new NachgetrageneRechnung(
        Long.valueOf(ID),
        FIRMA,
        NUMMER,
        DATUM,
        new BigDecimal("1000.00"),
        new BigDecimal("1190.00"),
        stand,
        schluessel,
        ZEIT,
        ZEIT);
  }

  private static NachgetrageneRechnungMitFirma mitFirma(
      final Rechnungszustand stand, final @Nullable String schluessel) {
    return new NachgetrageneRechnungMitFirma(rechnung(stand, schluessel), FIRMA, FIRMENNAME);
  }

  private static String rumpf(
      final String firmaId, final String nummer, final String netto, final String brutto) {
    return """
        {"firmaId":%s,"nummer":%s,"rechnungDatum":"2026-03-14","netto":%s,"brutto":%s}
        """
        .formatted(firmaId, nummer, netto, brutto);
  }

  private static String gueltig() {
    return rumpf(String.valueOf(FIRMA), "\" RE-2026-17 \"", "1000.00", "1190.00");
  }

  @Test
  void anlegen_thenCreatedWithLocationAndTheAnswerCarriesTheRechnung() throws Exception {
    // Given
    when(anlegen.anlege(daten.capture())).thenReturn(rechnung(Rechnungszustand.GESTELLT, null));
    when(lesen.lese(ID)).thenReturn(mitFirma(Rechnungszustand.GESTELLT, null));

    // When / Then
    mockMvc
        .perform(post(PFAD).contentType(MediaType.APPLICATION_JSON).content(gueltig()))
        .andExpect(status().isCreated())
        .andExpect(header().string(HttpHeaders.LOCATION, EINZELN))
        .andExpect(jsonPath("$.id").value(ID))
        .andExpect(jsonPath("$.firmaId").value(FIRMA))
        .andExpect(jsonPath("$.firmaName").value(FIRMENNAME))
        .andExpect(jsonPath("$.nummer").value(NUMMER))
        .andExpect(jsonPath("$.netto").value(1000.00))
        .andExpect(jsonPath("$.brutto").value(1190.00))
        .andExpect(jsonPath("$.zustand").value("GESTELLT"))
        .andExpect(jsonPath("$.dokument").value(false));

    // Then — getrimmt wird im Anwendungsdatensatz (E10), nicht hier.
    assertThat(daten.getValue())
        .isEqualTo(
            new Nachtragsdaten(
                FIRMA, NUMMER, DATUM, new BigDecimal("1000.00"), new BigDecimal("1190.00")));
  }

  @Test
  void anlegen_withoutFirma_thenBadRequestAtFirmaId() throws Exception {
    mockMvc
        .perform(
            post(PFAD)
                .contentType(MediaType.APPLICATION_JSON)
                .content(rumpf("null", "\"RE-1\"", "1.00", "1.00")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.firmaId").isArray());
    verifyNoInteractions(anlegen);
  }

  @Test
  void anlegen_withABlankNummer_thenBadRequestAtNummer() throws Exception {
    mockMvc
        .perform(
            post(PFAD)
                .contentType(MediaType.APPLICATION_JSON)
                .content(rumpf("5", "\"   \"", "1.00", "1.00")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.nummer").isArray());
    verifyNoInteractions(anlegen);
  }

  @Test
  void anlegen_withANummerOverFiftyCharacters_thenBadRequestAtNummer() throws Exception {
    mockMvc
        .perform(
            post(PFAD)
                .contentType(MediaType.APPLICATION_JSON)
                .content(rumpf("5", "\"" + "N".repeat(51) + "\"", "1.00", "1.00")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.nummer").isArray());
    verifyNoInteractions(anlegen);
  }

  @Test
  void anlegen_withANummerOfExactlyFiftyCharacters_thenCreated() throws Exception {
    // Given — die Grenze selbst ist zulaessig.
    when(anlegen.anlege(any())).thenReturn(rechnung(Rechnungszustand.GESTELLT, null));
    when(lesen.lese(ID)).thenReturn(mitFirma(Rechnungszustand.GESTELLT, null));

    // When / Then
    mockMvc
        .perform(
            post(PFAD)
                .contentType(MediaType.APPLICATION_JSON)
                .content(rumpf("5", "\"" + "N".repeat(50) + "\"", "0.00", "0.00")))
        .andExpect(status().isCreated());
  }

  @Test
  void anlegen_withANegativeNetto_thenBadRequestAtNetto() throws Exception {
    mockMvc
        .perform(
            post(PFAD)
                .contentType(MediaType.APPLICATION_JSON)
                .content(rumpf("5", "\"RE-1\"", "-0.01", "1.00")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.netto").isArray())
        .andExpect(jsonPath("$.fieldErrors.brutto").doesNotExist());
    verifyNoInteractions(anlegen);
  }

  @Test
  void anlegen_withThreeDecimalsInBrutto_thenBadRequestAtBrutto() throws Exception {
    // Kriterium 3: abgewiesen, nicht gerundet.
    mockMvc
        .perform(
            post(PFAD)
                .contentType(MediaType.APPLICATION_JSON)
                .content(rumpf("5", "\"RE-1\"", "1.00", "1.005")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.brutto").isArray())
        .andExpect(jsonPath("$.fieldErrors.netto").doesNotExist());
    verifyNoInteractions(anlegen);
  }

  @Test
  void anlegen_withoutBetraegeAndDatum_thenBadRequestAtEachField() throws Exception {
    mockMvc
        .perform(post(PFAD).contentType(MediaType.APPLICATION_JSON).content("{\"firmaId\":5}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.nummer").isArray())
        .andExpect(jsonPath("$.fieldErrors.rechnungDatum").isArray())
        .andExpect(jsonPath("$.fieldErrors.netto").isArray())
        .andExpect(jsonPath("$.fieldErrors.brutto").isArray());
    verifyNoInteractions(anlegen);
  }

  @Test
  void anlegen_withANummerAlreadyTaken_thenUnprocessableAtNummer() throws Exception {
    when(anlegen.anlege(any())).thenThrow(new NummerSchonVergeben());

    mockMvc
        .perform(post(PFAD).contentType(MediaType.APPLICATION_JSON).content(gueltig()))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.fieldErrors.nummer[0]").value(NummerSchonVergeben.MELDUNG));
  }

  @Test
  void anlegen_withADatumOutsideTheYear_thenUnprocessableAtRechnungDatum() throws Exception {
    when(anlegen.anlege(any())).thenThrow(new RechnungsdatumAusserhalb());

    mockMvc
        .perform(post(PFAD).contentType(MediaType.APPLICATION_JSON).content(gueltig()))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(
            jsonPath("$.fieldErrors.rechnungDatum[0]").value(RechnungsdatumAusserhalb.MELDUNG));
  }

  @Test
  void anlegen_withBruttoBelowNetto_thenUnprocessableAtBrutto() throws Exception {
    when(anlegen.anlege(any())).thenThrow(new BetraegePassenNicht());

    mockMvc
        .perform(post(PFAD).contentType(MediaType.APPLICATION_JSON).content(gueltig()))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.fieldErrors.brutto[0]").value(BetraegePassenNicht.MELDUNG));
  }

  @Test
  void lesen_thenOkWithTheRechnungAndItsDokumentFlag() throws Exception {
    when(lesen.lese(ID))
        .thenReturn(mitFirma(Rechnungszustand.BEZAHLT, "rechnung-nachtrag/42/a.pdf"));

    mockMvc
        .perform(get(EINZELN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(ID))
        .andExpect(jsonPath("$.zustand").value("BEZAHLT"))
        .andExpect(jsonPath("$.dokument").value(true))
        .andExpect(jsonPath("$.pdfSchluessel").doesNotExist());
  }

  @Test
  void lesen_withAnUnknownId_thenNotFound() throws Exception {
    when(lesen.lese(ID)).thenThrow(new NachtragNichtGefunden());

    mockMvc.perform(get(EINZELN)).andExpect(status().isNotFound());
  }

  @Test
  void aendern_thenOkWithTheNewStand() throws Exception {
    when(lesen.lese(ID)).thenReturn(mitFirma(Rechnungszustand.GESTELLT, null));

    mockMvc
        .perform(put(EINZELN).contentType(MediaType.APPLICATION_JSON).content(gueltig()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nummer").value(NUMMER));

    verify(aendern).aendere(eq(ID), daten.capture());
    assertThat(daten.getValue().nummer()).isEqualTo(NUMMER);
  }

  @Test
  void aendern_withAnInvalidRumpf_thenBadRequest() throws Exception {
    mockMvc
        .perform(
            put(EINZELN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(rumpf("5", "\"\"", "1.00", "1.00")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.nummer").isArray());
    verifyNoInteractions(aendern);
  }

  @Test
  void aendern_withAnUnknownId_thenNotFound() throws Exception {
    when(aendern.aendere(eq(ID), any())).thenThrow(new NachtragNichtGefunden());

    mockMvc
        .perform(put(EINZELN).contentType(MediaType.APPLICATION_JSON).content(gueltig()))
        .andExpect(status().isNotFound());
  }

  @Test
  void aendern_withANummerAlreadyTaken_thenUnprocessable() throws Exception {
    when(aendern.aendere(eq(ID), any())).thenThrow(new NummerSchonVergeben());

    mockMvc
        .perform(put(EINZELN).contentType(MediaType.APPLICATION_JSON).content(gueltig()))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.fieldErrors.nummer").isArray());
  }

  @Test
  void loeschen_thenNoContent() throws Exception {
    mockMvc.perform(delete(EINZELN)).andExpect(status().isNoContent());

    verify(loeschen).loesche(ID);
  }

  @Test
  void loeschen_withAnUnknownId_thenNotFound() throws Exception {
    doThrow(new NachtragNichtGefunden()).when(loeschen).loesche(ID);

    mockMvc.perform(delete(EINZELN)).andExpect(status().isNotFound());
  }

  @Test
  void zustand_toBezahlt_thenOkWithTheNewStand() throws Exception {
    when(lesen.lese(ID)).thenReturn(mitFirma(Rechnungszustand.BEZAHLT, null));

    mockMvc
        .perform(
            put(EINZELN + "/zustand")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"zustand\":\"BEZAHLT\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.zustand").value("BEZAHLT"));

    verify(zustand).setze(ID, Rechnungszustand.BEZAHLT);
  }

  @Test
  void zustand_withoutZiel_thenBadRequest() throws Exception {
    mockMvc
        .perform(put(EINZELN + "/zustand").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.zustand").isArray());
    verifyNoInteractions(zustand);
  }

  @Test
  void zustand_withAnEdgeTheRuleForbids_thenConflict() throws Exception {
    when(zustand.setze(ID, Rechnungszustand.ENTWURF)).thenThrow(new RechnungszustandPasstNicht());

    mockMvc
        .perform(
            put(EINZELN + "/zustand")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"zustand\":\"ENTWURF\"}"))
        .andExpect(status().isConflict());
  }

  @Test
  void zustand_withAnUnknownId_thenNotFound() throws Exception {
    when(zustand.setze(ID, Rechnungszustand.BEZAHLT)).thenThrow(new NachtragNichtGefunden());

    mockMvc
        .perform(
            put(EINZELN + "/zustand")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"zustand\":\"BEZAHLT\"}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void dokumentLesen_thenOkWithThePdfAndTheHeadersOfTheGestellteRechnung() throws Exception {
    // Given — der Name kommt aus der Nummer, nicht aus dem hochgeladenen Original (E26).
    when(lesen.lese(ID))
        .thenReturn(mitFirma(Rechnungszustand.GESTELLT, "rechnung-nachtrag/42/a.pdf"));
    when(dokument.lies(ID)).thenReturn(PDF.clone());

    // When / Then
    mockMvc
        .perform(get(EINZELN + "/dokument"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_PDF))
        .andExpect(content().bytes(PDF.clone()))
        .andExpect(
            header()
                .string(
                    HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"Rechnung-RE-2026-17.pdf\";"
                        + " filename*=UTF-8''Rechnung-RE-2026-17.pdf"))
        .andExpect(header().string("Content-Security-Policy", "sandbox"))
        .andExpect(header().string(HttpHeaders.CONTENT_LENGTH, String.valueOf(PDF.length)));
  }

  @Test
  void dokumentLesen_withoutDokument_thenNotFound() throws Exception {
    // E17: ohne hinterlegtes Original fehlt die Sache selbst.
    when(lesen.lese(ID)).thenReturn(mitFirma(Rechnungszustand.GESTELLT, null));
    when(dokument.lies(ID)).thenThrow(new NachtragNichtGefunden());

    mockMvc.perform(get(EINZELN + "/dokument")).andExpect(status().isNotFound());
  }

  @Test
  void dokumentLesen_withAnUnknownId_thenNotFound() throws Exception {
    when(lesen.lese(ID)).thenThrow(new NachtragNichtGefunden());

    mockMvc.perform(get(EINZELN + "/dokument")).andExpect(status().isNotFound());
  }

  @Test
  void dokumentAblegen_thenOkWithTheDokumentFlagAndTheBytesOfThePartDatei() throws Exception {
    // Given
    when(lesen.lese(ID))
        .thenReturn(mitFirma(Rechnungszustand.GESTELLT, "rechnung-nachtrag/42/a.pdf"));
    final ArgumentCaptor<byte[]> inhalt = ArgumentCaptor.forClass(byte[].class);

    // When
    mockMvc
        .perform(
            multipart(EINZELN + "/dokument")
                .file(new MockMultipartFile("datei", "original.pdf", "application/pdf", PDF)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.dokument").value(true));

    // Then
    verify(dokument).lege(eq(ID), inhalt.capture());
    assertThat(inhalt.getValue()).isEqualTo(PDF);
  }

  @Test
  void dokumentAblegen_withAFileThatIsNoPdf_thenUnprocessableAtDatei() throws Exception {
    when(dokument.lege(eq(ID), any())).thenThrow(Dokumentablehnung.keinPdf());

    mockMvc
        .perform(
            multipart(EINZELN + "/dokument")
                .file(new MockMultipartFile("datei", "rechnung.pdf", "application/pdf", PDF)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(
            jsonPath("$.fieldErrors.datei[0]").value(DokumentNichtAnnehmbar.MELDUNG_KEIN_PDF));
  }

  @Test
  void dokumentAblegen_withAnUnknownId_thenNotFound() throws Exception {
    when(dokument.lege(anyLong(), any())).thenThrow(new NachtragNichtGefunden());

    mockMvc
        .perform(
            multipart(EINZELN + "/dokument")
                .file(new MockMultipartFile("datei", "original.pdf", "application/pdf", PDF)))
        .andExpect(status().isNotFound());
  }

  @Test
  void dokumentEntfernen_thenNoContent() throws Exception {
    mockMvc.perform(delete(EINZELN + "/dokument")).andExpect(status().isNoContent());

    verify(dokument).entferne(ID);
  }

  @Test
  void dokumentEntfernen_withoutDokument_thenNotFound() throws Exception {
    when(dokument.entferne(ID)).thenThrow(new NachtragNichtGefunden());

    mockMvc.perform(delete(EINZELN + "/dokument")).andExpect(status().isNotFound());
  }
}
