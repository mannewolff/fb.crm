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
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.common.Bearbeitungskonflikt;
import org.mwolff.fbcrm.common.Urheber;
import org.mwolff.fbcrm.common.web.Bedingungskopf;
import org.mwolff.fbcrm.common.web.GlobalExceptionHandler;
import org.mwolff.fbcrm.rechnung.application.BetraegePassenNicht;
import org.mwolff.fbcrm.rechnung.application.DokumentNichtAnnehmbar;
import org.mwolff.fbcrm.rechnung.application.Dokumentablehnung;
import org.mwolff.fbcrm.rechnung.application.NachgetrageneRechnungMitFirma;
import org.mwolff.fbcrm.rechnung.application.NachtragAendernUseCase;
import org.mwolff.fbcrm.rechnung.application.NachtragAnlegenUseCase;
import org.mwolff.fbcrm.rechnung.application.NachtragDokumentUseCase;
import org.mwolff.fbcrm.rechnung.application.NachtragGeaendert;
import org.mwolff.fbcrm.rechnung.application.NachtragGeloescht;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Die Uebersetzung zwischen den Anwendungsfaellen des Nachtrags und HTTP (#254; Plan #259, E11,
 * E17, E21, E23, E24, E26, E27; Issue #268).
 *
 * <p>Gegenstand sind die acht Wege und ihre Statuscodes: 201 mit {@code Location} beim Anlegen, 200
 * beim Lesen, Aendern, Umstellen, Herunterladen, Ablegen und Entfernen des Dokuments, 204 beim
 * Loeschen. Dazu die Fehler: 400 mit {@code fieldErrors} fuer die Form des Rumpfs (E9, E10), 422
 * mit {@code fieldErrors} fuer die fachlichen Abweisungen (E23), 404 fuer eine unbekannte Kennung
 * und fuer den Download ohne hinterlegtes Dokument (E17).
 *
 * <p><b>Aendern, Zustand, Ablegen und Entfernen des Originals legen den Stand vor</b> (#411,
 * Kriterium 4; Plan #415, E3, E14): Fuer jeden der vier Wege gilt dasselbe Dreigespann — ohne
 * {@code If-Match} 428, auf einem alten Stand 409 mit {@code konflikt}, auf dem aktuellen 200 mit
 * dem erhoehten Stand; die vier Wege laufen je durch dieselben drei parametrisierten Tests. Das
 * Loeschen legt den Stand ebenso vor und antwortet auf dem aktuellen 204; an einer inzwischen
 * geloeschten Rechnung antworten alle fuenf Wege 409 mit {@code art = "gelöscht"} (Kriterium 6;
 * E16).
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

  /** Der Stand, den die Maske vorlegt (Plan #415, E2). */
  private static final long STAND = 3L;

  /** Derselbe Stand als starkes Etikett im Kopf {@code If-Match}. */
  private static final String VORGELEGT = "\"" + STAND + "\"";

  /** Das angemeldete Konto. */
  private static final long ANGEMELDET = 31L;

  /** Das Konto, das inzwischen geaendert hat. */
  private static final long ANNA = 30L;

  private static final Instant ANNAS_AENDERUNG = Instant.parse("2026-10-06T08:42:00Z");

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
    // Die Sitzung wie im Betrieb: die Kennung des Kontos als Principal (Plan #415, E14).
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                Long.valueOf(ANGEMELDET), null, List.of()));
    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new NachgetrageneRechnungController(
                    anlegen, lesen, aendern, loeschen, zustand, dokument))
            .setControllerAdvice(
                new GlobalExceptionHandler(
                    konto ->
                        konto == ANNA ? Optional.of(new Urheber(ANNA, "Anna")) : Optional.empty()))
            .setCustomArgumentResolvers(
                new Bedingungskopf.Leser(), new AuthenticationPrincipalArgumentResolver())
            .build();
  }

  @AfterEach
  void raeumeDieSitzungAb() {
    SecurityContextHolder.clearContext();
  }

  private static NachgetrageneRechnung rechnung(
      final Rechnungszustand stand, final @Nullable String schluessel) {
    return rechnung(stand, schluessel, 0L);
  }

  private static NachgetrageneRechnung rechnung(
      final Rechnungszustand stand, final @Nullable String schluessel, final long version) {
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
        ZEIT,
        version,
        null);
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
        .perform(
            put(EINZELN)
                .header(HttpHeaders.IF_MATCH, VORGELEGT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(gueltig()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nummer").value(NUMMER));

    verify(aendern).aendere(eq(ID), daten.capture(), eq(STAND), eq(ANGEMELDET));
    assertThat(daten.getValue().nummer()).isEqualTo(NUMMER);
  }

  @Test
  void aendern_withAnInvalidRumpf_thenBadRequest() throws Exception {
    mockMvc
        .perform(
            put(EINZELN)
                .header(HttpHeaders.IF_MATCH, VORGELEGT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(rumpf("5", "\"\"", "1.00", "1.00")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.nummer").isArray());
    verifyNoInteractions(aendern);
  }

  @Test
  void aendern_withAnUnknownId_thenNotFound() throws Exception {
    when(aendern.aendere(eq(ID), any(), eq(STAND), eq(ANGEMELDET)))
        .thenThrow(new NachtragNichtGefunden());

    mockMvc
        .perform(
            put(EINZELN)
                .header(HttpHeaders.IF_MATCH, VORGELEGT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(gueltig()))
        .andExpect(status().isNotFound());
  }

  @Test
  void aendern_withANummerAlreadyTaken_thenUnprocessable() throws Exception {
    when(aendern.aendere(eq(ID), any(), eq(STAND), eq(ANGEMELDET)))
        .thenThrow(new NummerSchonVergeben());

    mockMvc
        .perform(
            put(EINZELN)
                .header(HttpHeaders.IF_MATCH, VORGELEGT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(gueltig()))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.fieldErrors.nummer").isArray());
  }

  @Test
  void loeschen_withTheCurrentStand_thenNoContentAndTheUseCaseSeesStandAndKonto() throws Exception {
    mockMvc
        .perform(delete(EINZELN).header(HttpHeaders.IF_MATCH, VORGELEGT))
        .andExpect(status().isNoContent());

    verify(loeschen).loesche(ID, STAND, ANGEMELDET);
  }

  @Test
  void loeschen_withAnUnknownId_thenNotFound() throws Exception {
    doThrow(new NachtragNichtGefunden()).when(loeschen).loesche(ID, STAND, ANGEMELDET);

    mockMvc
        .perform(delete(EINZELN).header(HttpHeaders.IF_MATCH, VORGELEGT))
        .andExpect(status().isNotFound());
  }

  @Test
  void zustand_toBezahlt_thenOkWithTheNewStand() throws Exception {
    when(lesen.lese(ID)).thenReturn(mitFirma(Rechnungszustand.BEZAHLT, null));

    mockMvc
        .perform(
            put(EINZELN + "/zustand")
                .header(HttpHeaders.IF_MATCH, VORGELEGT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"zustand\":\"BEZAHLT\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.zustand").value("BEZAHLT"));

    verify(zustand).setze(ID, Rechnungszustand.BEZAHLT, STAND, ANGEMELDET);
  }

  @Test
  void zustand_withoutZiel_thenBadRequest() throws Exception {
    mockMvc
        .perform(
            put(EINZELN + "/zustand")
                .header(HttpHeaders.IF_MATCH, VORGELEGT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.zustand").isArray());
    verifyNoInteractions(zustand);
  }

  @Test
  void zustand_withAnEdgeTheRuleForbids_thenConflict() throws Exception {
    when(zustand.setze(ID, Rechnungszustand.ENTWURF, STAND, ANGEMELDET))
        .thenThrow(new RechnungszustandPasstNicht());

    mockMvc
        .perform(
            put(EINZELN + "/zustand")
                .header(HttpHeaders.IF_MATCH, VORGELEGT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"zustand\":\"ENTWURF\"}"))
        .andExpect(status().isConflict());
  }

  @Test
  void zustand_withAnUnknownId_thenNotFound() throws Exception {
    when(zustand.setze(ID, Rechnungszustand.BEZAHLT, STAND, ANGEMELDET))
        .thenThrow(new NachtragNichtGefunden());

    mockMvc
        .perform(
            put(EINZELN + "/zustand")
                .header(HttpHeaders.IF_MATCH, VORGELEGT)
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
                .file(new MockMultipartFile("datei", "original.pdf", "application/pdf", PDF))
                .header(HttpHeaders.IF_MATCH, VORGELEGT))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.dokument").value(true));

    // Then
    verify(dokument).lege(eq(ID), inhalt.capture(), eq(STAND), eq(ANGEMELDET));
    assertThat(inhalt.getValue()).isEqualTo(PDF);
  }

  @Test
  void dokumentAblegen_withAFileThatIsNoPdf_thenUnprocessableAtDatei() throws Exception {
    when(dokument.lege(eq(ID), any(), eq(STAND), eq(ANGEMELDET)))
        .thenThrow(Dokumentablehnung.keinPdf());

    mockMvc
        .perform(
            multipart(EINZELN + "/dokument")
                .file(new MockMultipartFile("datei", "rechnung.pdf", "application/pdf", PDF))
                .header(HttpHeaders.IF_MATCH, VORGELEGT))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(
            jsonPath("$.fieldErrors.datei[0]").value(DokumentNichtAnnehmbar.MELDUNG_KEIN_PDF));
  }

  @Test
  void dokumentAblegen_withAnUnknownId_thenNotFound() throws Exception {
    when(dokument.lege(anyLong(), any(), anyLong(), anyLong()))
        .thenThrow(new NachtragNichtGefunden());

    mockMvc
        .perform(
            multipart(EINZELN + "/dokument")
                .file(new MockMultipartFile("datei", "original.pdf", "application/pdf", PDF))
                .header(HttpHeaders.IF_MATCH, VORGELEGT))
        .andExpect(status().isNotFound());
  }

  @Test
  void dokumentEntfernen_thenOkWithoutTheDokumentFlag() throws Exception {
    when(lesen.lese(ID)).thenReturn(mitFirma(Rechnungszustand.GESTELLT, null));

    mockMvc
        .perform(delete(EINZELN + "/dokument").header(HttpHeaders.IF_MATCH, VORGELEGT))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.dokument").value(false));

    verify(dokument).entferne(ID, STAND, ANGEMELDET);
  }

  @Test
  void dokumentEntfernen_withoutDokument_thenNotFound() throws Exception {
    when(dokument.entferne(ID, STAND, ANGEMELDET)).thenThrow(new NachtragNichtGefunden());

    mockMvc
        .perform(delete(EINZELN + "/dokument").header(HttpHeaders.IF_MATCH, VORGELEGT))
        .andExpect(status().isNotFound());
  }

  // --- Stand vorlegen (Plan #415) -----------------------------------------------------------

  /**
   * Die Anfrage des genannten Wegs, ohne Kopf — jeder Test setzt ihn selbst oder laesst ihn weg.
   */
  private static MockHttpServletRequestBuilder anfrageZu(final String weg) {
    return switch (weg) {
      case "aendern" -> put(EINZELN).contentType(MediaType.APPLICATION_JSON).content(gueltig());
      case "zustand" ->
          put(EINZELN + "/zustand")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"zustand\":\"BEZAHLT\"}");
      case "ablegen" ->
          multipart(EINZELN + "/dokument")
              .file(new MockMultipartFile("datei", "original.pdf", "application/pdf", PDF));
      case "entfernen" -> delete(EINZELN + "/dokument");
      case "loeschen" -> delete(EINZELN);
      default -> throw new IllegalArgumentException(weg);
    };
  }

  /** Der Anwendungsfall des Wegs wirft den Konflikt — mit Stand und Konto aus der Anfrage. */
  private void wirftKonflikt(final String weg) {
    wirft(weg, new NachtragGeaendert(ANNA, ANGEMELDET, ANNAS_AENDERUNG));
  }

  /** Der Anwendungsfall des Wegs wirft den genannten Konflikt. */
  private void wirft(final String weg, final Bearbeitungskonflikt konflikt) {
    switch (weg) {
      case "aendern" ->
          when(aendern.aendere(eq(ID), any(), eq(STAND), eq(ANGEMELDET))).thenThrow(konflikt);
      case "zustand" ->
          when(zustand.setze(ID, Rechnungszustand.BEZAHLT, STAND, ANGEMELDET)).thenThrow(konflikt);
      case "ablegen" ->
          when(dokument.lege(eq(ID), any(), eq(STAND), eq(ANGEMELDET))).thenThrow(konflikt);
      case "entfernen" -> when(dokument.entferne(ID, STAND, ANGEMELDET)).thenThrow(konflikt);
      case "loeschen" -> doThrow(konflikt).when(loeschen).loesche(ID, STAND, ANGEMELDET);
      default -> throw new IllegalArgumentException(weg);
    }
  }

  /** Prueft, dass der Anwendungsfall des Wegs Stand und Konto bekommen hat. */
  private void bekamStandUndKonto(final String weg) {
    switch (weg) {
      case "aendern" -> verify(aendern).aendere(eq(ID), any(), eq(STAND), eq(ANGEMELDET));
      case "zustand" -> verify(zustand).setze(ID, Rechnungszustand.BEZAHLT, STAND, ANGEMELDET);
      case "ablegen" -> verify(dokument).lege(eq(ID), any(), eq(STAND), eq(ANGEMELDET));
      case "entfernen" -> verify(dokument).entferne(ID, STAND, ANGEMELDET);
      case "loeschen" -> verify(loeschen).loesche(ID, STAND, ANGEMELDET);
      default -> throw new IllegalArgumentException(weg);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"aendern", "zustand", "ablegen", "entfernen", "loeschen"})
  void schreibenderWeg_withoutIfMatch_thenAnswers428AndChangesNothing(final String weg)
      throws Exception {
    // Given — E3: Ohne vorgelegten Stand wird nicht geschrieben.

    // When / Then
    mockMvc
        .perform(anfrageZu(weg))
        .andExpect(status().isPreconditionRequired())
        .andExpect(jsonPath("$.detail").value(Bedingungskopf.MELDUNG));
    verifyNoInteractions(aendern, zustand, dokument, loeschen, lesen);
  }

  @ParameterizedTest
  @ValueSource(strings = {"aendern", "zustand", "ablegen", "entfernen", "loeschen"})
  void schreibenderWeg_withAnOutdatedStand_thenAnswers409WithTheConflict(final String weg)
      throws Exception {
    // Given — #411, Kriterium 4: Anna hat die nachgetragene Rechnung inzwischen geaendert.
    wirftKonflikt(weg);

    // When / Then
    mockMvc
        .perform(anfrageZu(weg).header(HttpHeaders.IF_MATCH, VORGELEGT))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.konflikt.art").value("veraltet"))
        .andExpect(jsonPath("$.konflikt.geaendertVon").value("Anna"))
        .andExpect(jsonPath("$.konflikt.geaendertAm").value("2026-10-06T08:42:00Z"))
        .andExpect(jsonPath("$.konflikt.eigenesFenster").value(false));
    verifyNoInteractions(lesen);
  }

  @ParameterizedTest
  @ValueSource(strings = {"aendern", "zustand", "ablegen", "entfernen"})
  void schreibenderWeg_withTheCurrentStand_thenAnswers200WithTheRaisedVersion(final String weg)
      throws Exception {
    // Given — #411, Kriterium 8: Auf dem aktuellen Stand geht der Schritt unveraendert durch; die
    // Antwort traegt den erhoehten Stand, den die Oberflaeche beim naechsten Schritt vorlegt.
    when(lesen.lese(ID))
        .thenReturn(
            new NachgetrageneRechnungMitFirma(
                rechnung(Rechnungszustand.GESTELLT, null, STAND + 1), FIRMA, FIRMENNAME));

    // When
    mockMvc
        .perform(anfrageZu(weg).header(HttpHeaders.IF_MATCH, VORGELEGT))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(STAND + 1));

    // Then
    bekamStandUndKonto(weg);
  }

  @ParameterizedTest
  @ValueSource(strings = {"aendern", "zustand", "ablegen", "entfernen", "loeschen"})
  void schreibenderWeg_atADeletedRechnung_thenAnswers409AsGeloescht(final String weg)
      throws Exception {
    // Given — #411, Kriterium 6; Plan #415, E16: Anna hat die Rechnung inzwischen geloescht.
    wirft(weg, new NachtragGeloescht(ANNA, ANGEMELDET, ANNAS_AENDERUNG));

    // When / Then
    mockMvc
        .perform(anfrageZu(weg).header(HttpHeaders.IF_MATCH, VORGELEGT))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.konflikt.art").value("gelöscht"))
        .andExpect(jsonPath("$.konflikt.geaendertVon").value("Anna"))
        .andExpect(jsonPath("$.konflikt.geaendertAm").value("2026-10-06T08:42:00Z"))
        .andExpect(jsonPath("$.konflikt.eigenesFenster").value(false));
    verifyNoInteractions(lesen);
  }
}
